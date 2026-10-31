import { sleep } from 'k6';
import exec from 'k6/execution';
import { credentials as allCredentials } from '../../lib/userPool.js';
import { testLoginLogout } from '../../flows/loginLogoutTest.js';
import { buildRampingVusProfile } from '../../lib/loadProfile.js';
import { insecureSkipTLSVerify, TENANT } from '../../lib/config.js';
export { handleSummary } from '../../lib/report.js';

// Every PERF credential of the generated users file (UsersList.xlsx) - not a
// sample, the whole pool - since the point of this scenario is to prove
// (or disprove) each one live, including the ones added on the assumption
// password===username without an individual login check at seed time.
// (allCredentials: every PERF user of the generated users file, via lib/userPool.js)

// TEST_LIMIT: optional smoke-test override to run against only the first N
// credentials instead of the whole pool - does not touch the pool file.
const LIMIT = __ENV.TEST_LIMIT ? Number(__ENV.TEST_LIMIT) : allCredentials.length;
const credentials = allCredentials.slice(0, LIMIT);
const N = credentials.length;

const DELAY_SECONDS = __ENV.TEST_DELAY_SECONDS !== undefined ? Number(__ENV.TEST_DELAY_SECONDS) : 0;

// EXEC_MODE=exhaustive (default): fixed VU count, every credential in the
// (possibly TEST_LIMIT-capped) pool hit exactly once - the mode already
// validated at TEST_VUS=500 against qafeature/qahbros, kept as default so
// that exact command keeps working unchanged.
// EXEC_MODE=ramp: traffic-shaped instead of an instant full-scale hit -
// ramps to RAMP_BURST_VUS over RAMP_BURST_DURATION, continues ramping up to
// TARGET_VUS over RAMP_UP_DURATION, then ramps back down to 0 over
// RAMP_DOWN_DURATION (see lib/loadProfile.js for the shared stage-builder).
const EXEC_MODE = __ENV.EXEC_MODE || 'exhaustive';

// TEST_VUS only applies to exhaustive mode - ramp mode's VU count comes from
// the stages instead, not a fixed VUS value.
const VUS = Math.min(N, __ENV.TEST_VUS ? Number(__ENV.TEST_VUS) : 500);
// Each VU covers ceil(N/VUS) credentials across its iterations so every
// credential in the slice gets tested exactly once, however many VUS asked
// for (e.g. 502 credentials / 500 VUs -> most VUs get 1 iteration, 2 VUs get
// a 2nd to cover the remainder).
const ITERATIONS_PER_VU = Math.ceil(N / VUS);

const TARGET_VUS = Number(__ENV.TARGET_VUS) || 500;
const RAMP_BURST_VUS = __ENV.RAMP_BURST_VUS !== undefined ? Number(__ENV.RAMP_BURST_VUS) : 50;
const RAMP_BURST_DURATION = __ENV.RAMP_BURST_DURATION || '1m';
const RAMP_UP_DURATION = __ENV.RAMP_UP_DURATION || '2m';
const RAMP_DOWN_DURATION = __ENV.RAMP_DOWN_DURATION || '2m';

const scenarioConfig =
  EXEC_MODE === 'ramp'
    ? buildRampingVusProfile({
        targetVUs: TARGET_VUS,
        burstVUs: RAMP_BURST_VUS,
        burstDuration: RAMP_BURST_DURATION,
        rampUpDuration: RAMP_UP_DURATION,
        rampDownDuration: RAMP_DOWN_DURATION,
      })
    : {
        executor: 'per-vu-iterations',
        vus: VUS,
        iterations: ITERATIONS_PER_VU,
        maxDuration: '30m',
      };

export const options = {
  insecureSkipTLSVerify,
  scenarios: {
    testLoginLogout: scenarioConfig,
  },
  thresholds: {
    // Intentionally loose - this scenario EXPECTS some logins to fail (the
    // unverified perftestN accounts) and exists to measure exactly that
    // rate, not to gate a build on 100% passing.
    checks: ['rate>0'],
  },
};

export function setup() {
  if (EXEC_MODE === 'ramp' && TARGET_VUS > N) {
    console.warn(
      `[${TENANT}] TARGET_VUS (${TARGET_VUS}) exceeds the credential pool size (${N}) - concurrent VUs will ` +
        'frequently land on the same credential. Not fatal (each iteration fully logs out, and the existing ' +
        'code-012 killSession+retry path self-heals a same-credential collision), just expect more of them.'
    );
  }
}

export default function () {
  let credential;

  if (EXEC_MODE === 'ramp') {
    // ramping-vus keeps calling default() for as long as a VU is active -
    // VU count changes over time and total iterations aren't known in
    // advance, so the old fixed-pass formula doesn't apply. iterationInTest
    // is a globally-unique, monotonically increasing counter across all VUs
    // regardless of executor, so modulo N cycles through the pool
    // continuously through burst/ramp-up/ramp-down instead of assuming one
    // bounded pass.
    const index = exec.scenario.iterationInTest % N;
    credential = credentials[index];
  } else {
    // Global index derived from (VU, iteration) so every credential in the
    // pool is covered exactly once across all VUs, regardless of how VUS
    // vs. N divide - not every VU necessarily gets ITERATIONS_PER_VU real
    // work if N isn't an exact multiple of VUS (the tail VUs simply return
    // early).
    const index = (__VU - 1) + __ITER * VUS;
    if (index >= N) return;
    credential = credentials[index];
  }

  const result = testLoginLogout(credential);

  console.log(
    `LoginLogoutTest [${TENANT}] username=${result.username} login=${result.loginOk ? 'OK' : 'FAIL'} ` +
      `logout=${result.logoutOk ? 'OK' : 'FAIL'}${result.error ? ' error=' + result.error : ''}`
  );

  if (DELAY_SECONDS > 0) {
    sleep(DELAY_SECONDS);
  }
}
