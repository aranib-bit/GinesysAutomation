import { SharedArray } from 'k6/data';
import { assignCredential, assertPoolCapacity } from '../../lib/userPool.js';
import { loginWithCredential, useSharedAuth } from '../../lib/auth.js';
import { resolveUserMasterData } from '../../flows/createUser.js';
import { listAllAppUsers, verifyPerfTestLogin } from '../../flows/verifyUsers.js';
import { insecureSkipTLSVerify, TENANT } from '../../lib/config.js';

const businessDataArr = new SharedArray('userBusinessData', function () {
  return [JSON.parse(open(`../../data/business/${TENANT}.user.json`))];
});
const d = businessDataArr[0];

const NAME_PATTERN = new RegExp(`^${d.userNamePrefix}\\d+$`, 'i');

export const options = {
  insecureSkipTLSVerify,
  scenarios: {
    verifyPerfUsers: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '30m',
    },
  },
};

export default function () {
  assertPoolCapacity(1);
  const credential = assignCredential(1);
  const sharedAuth = loginWithCredential(credential);
  useSharedAuth(sharedAuth);

  const masterData = resolveUserMasterData(credential, d.defaultSiteCode);
  const allUsers = listAllAppUsers(credential, masterData, d.defaultSiteCode);

  const candidates = allUsers.filter((u) => NAME_PATTERN.test(u.abbre || u.eno || ''));
  console.log(`[${TENANT}] found ${candidates.length} ${d.userNamePrefix}* account(s) out of ${allUsers.length} total app users`);

  let verifiedCount = 0;
  candidates.forEach((u, i) => {
    const username = u.abbre || u.eno;
    const ok = verifyPerfTestLogin(username);
    if (ok) {
      verifiedCount++;
      // Same "SeededUser: user|pass" line format createUser.js uses, so the
      // existing list-seeded-users.ps1 / merge-seeded-users.ps1 tooling can
      // be reused unchanged to fold these into data/users/<tenant>.users.json.
      console.log(`SeededUser: ${username}|${username}`);
    } else {
      console.log(`VerifyLoginFailed: ${username} (exists but password!=username or account not usable)`);
    }
    if ((i + 1) % 25 === 0) {
      console.log(`[${TENANT}] verified ${i + 1}/${candidates.length} so far (${verifiedCount} confirmed)`);
    }
  });

  console.log(
    `[${TENANT}] RESULT: ${verifiedCount}/${candidates.length} ${d.userNamePrefix}* accounts confirmed working ` +
      `(total app users on tenant: ${allUsers.length})`
  );
}
