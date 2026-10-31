import { SharedArray } from 'k6/data';

// Users come from the generated users file named by USERS_FILE: the PERF accounts of the environment's UsersList.xlsx, written by the Java side
// (com.gsl.api.bulk.K6UsersExport; the Java launchers set USERS_FILE themselves). Shape: {tenant, ssoLoginUrl, users:[{username, password, loginType}]}
// (a plain array of {username, password} is still read). A path that is not absolute is relative to the k6 project root.
const USERS_FILE = __ENV.USERS_FILE;
export const usersFileLabel = USERS_FILE || '(USERS_FILE not set)';

function readUsersFile() {
  if (!USERS_FILE) {
    throw new Error(
      'USERS_FILE is not set. The Java launchers set it; for a standalone run write the file first ' +
        '(mvn -o test -Dtest=K6UsersExportTest -Dk6.users.write=true -Denv=<env>) and pass -e USERS_FILE=<path it prints>.'
    );
  }
  const path = /^([A-Za-z]:|[\\/])/.test(USERS_FILE) ? USERS_FILE : `../${USERS_FILE}`;
  return JSON.parse(open(path));
}

// Each credential carries the environment's SSO login URL (webapi.sso.loginUrl on the Java side; '' when none is configured) so lib/auth.js needs
// nothing but the credential to log an SSO user in.
export const credentials = new SharedArray('credentials', function () {
  const file = readUsersFile();
  const users = Array.isArray(file) ? file : file.users || [];
  const ssoLoginUrl = Array.isArray(file) ? '' : file.ssoLoginUrl || '';
  return users.map((u) => ({ username: u.username, password: u.password, loginType: u.loginType || 'ERP', ssoLoginUrl }));
});
export function assignCredential(vuId) {
  if (credentials.length === 0) {
    throw new Error(`No credentials loaded - ${usersFileLabel} has no users`);
  }
  return credentials[(vuId - 1) % credentials.length];
}

const PLACEHOLDER_PATTERN = /^REPLACE_WITH_/i;

// Call from a scenario's setup() so a misconfigured pool fails the whole run
// immediately instead of two VUs colliding on the same login mid-test, or
// silently hammering the real server with leftover scaffold placeholders
// (seen live: rows left as "REPLACE_WITH_PERF_USER_N" fail every login with a
// genuine bad-credentials error, indistinguishable from a real typo until you
// read the row).
export function assertPoolCapacity(maxConcurrentVUs) {
  if (credentials.length < maxConcurrentVUs) {
    throw new Error(
      `Credential pool has ${credentials.length} user(s) but this scenario needs ` +
        `${maxConcurrentVUs} concurrent VUs. Add more [PERF] rows to UsersList.xlsx (${usersFileLabel}) - ` +
        `each concurrent VU must own a distinct login, or the app will force a kill-session conflict.`
    );
  }

  // Only the rows assignCredential() will actually hand to VUs 1..maxConcurrentVUs
  // matter here (with maxConcurrentVUs <= credentials.length, that's rows 0..N-1,
  // no wraparound) - a placeholder sitting in an unused tail row shouldn't block
  // a smaller run from using the real credentials that precede it.
  const placeholderRows = credentials
    .slice(0, maxConcurrentVUs)
    .map((c, i) => ({ row: i + 1, username: c.username }))
    .filter((c) => PLACEHOLDER_PATTERN.test(c.username));

  if (placeholderRows.length > 0) {
    const rows = placeholderRows.map((c) => `row ${c.row} ("${c.username}")`).join(', ');
    throw new Error(
      `${usersFileLabel} still has unreplaced placeholder credentials among the rows this ` +
        `run needs: ${rows}. Fill these in with real dedicated accounts in UsersList.xlsx, or lower TARGET_VUS/SMOKE_VUS ` +
        `to stay within the real credentials configured before the first placeholder row.`
    );
  }
}