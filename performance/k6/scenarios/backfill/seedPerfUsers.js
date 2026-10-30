import { sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { assignCredential, assertPoolCapacity } from '../../lib/userPool.js';
import { loginWithCredential, useSharedAuth } from '../../lib/auth.js';
import { resolveUserMasterData, runCreateUserFlow } from '../../flows/createUser.js';
import { insecureSkipTLSVerify, TENANT } from '../../lib/config.js';
export { handleSummary } from '../../lib/report.js';

const businessDataArr = new SharedArray('userBusinessData', function () {
  return [JSON.parse(open(`../../data/business/${TENANT}.user.json`))];
});
const d = businessDataArr[0];

const COUNT = Number(__ENV.SEED_COUNT) || d.count;
const START_INDEX = __ENV.SEED_START_INDEX !== undefined ? Number(__ENV.SEED_START_INDEX) : d.startIndex;
const DELAY_SECONDS = __ENV.SEED_DELAY_SECONDS !== undefined ? Number(__ENV.SEED_DELAY_SECONDS) : 0.5;

export const options = {
  insecureSkipTLSVerify,
  scenarios: {
    seedPerfUsers: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: COUNT,
      maxDuration: '4h',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
  },
};

export function setup() {
  if (String(d.defaultSiteCode) === 'TODO_FILL_IN') {
    throw new Error(
      `data/business/${TENANT}.user.json still has defaultSiteCode="TODO_FILL_IN" - fill in the real default ` +
        'site code before seeding users (see the seeding plan).'
    );
  }

  assertPoolCapacity(1);
  const credential = assignCredential(1);
  const sharedAuth = loginWithCredential(credential);
  // Prime this (setup's own) context's auth cache so resolveUserMasterData's
  // apiGet/apiPost calls below don't trigger a second login.
  useSharedAuth(sharedAuth);
  // Tenants whose business-data file carries a `hardcodedMasterData` block
  // (e.g. qaprod, captured from a real HAR when DB access wasn't available
  // to derive it) skip the live discovery calls entirely and reuse those
  // known-good values instead - see the qaprod seeding plan.
  const masterData = d.hardcodedMasterData || resolveUserMasterData(credential, d.defaultSiteCode);

  return { sharedAuth, masterData };
}

export default function (data) {
  // setup() and default() run in separate JS contexts - re-seed this VU's
  // auth cache from setup()'s single login rather than logging in again.
  useSharedAuth(data.sharedAuth);
  const credential = { username: 'seed-admin' };

  const username = `${d.userNamePrefix}${START_INDEX + __ITER}`;
  runCreateUserFlow(credential, username, data.masterData);

  if (DELAY_SECONDS > 0) {
    sleep(DELAY_SECONDS);
  }
}
