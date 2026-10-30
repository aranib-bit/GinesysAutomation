import { sleep } from 'k6';
import { assignCredential, assertPoolCapacity } from '../../lib/userPool.js';
import { loginWithCredential, useSharedAuth } from '../../lib/auth.js';
import { resolveSiteMasterData } from '../../lib/posSite.js';
import { runCreateCloudMposSiteFlow } from '../../flows/createCloudMposSite.js';
import { insecureSkipTLSVerify } from '../../lib/config.js';
export { handleSummary } from '../../lib/report.js';

// Standalone variant of the site-creation half of
// scenarios/backfill/cloudMposSiteAllocationChain.js - creates sites only, no item
// allocation. Same single-VU, START_INDEX(+override)+__ITER sequencing.
const COUNT = Number(__ENV.SEED_COUNT) || 1;
const START_INDEX_OVERRIDE = __ENV.SEED_START_INDEX !== undefined ? Number(__ENV.SEED_START_INDEX) : undefined;
const DELAY_SECONDS = __ENV.SEED_DELAY_SECONDS !== undefined ? Number(__ENV.SEED_DELAY_SECONDS) : 0.5;

export const options = {
  insecureSkipTLSVerify,
  scenarios: {
    createCloudMposSite: {
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
  assertPoolCapacity(1);
  const credential = assignCredential(1);
  const sharedAuth = loginWithCredential(credential);
  useSharedAuth(sharedAuth);
  const masterData = resolveSiteMasterData(credential);
  return { sharedAuth, masterData };
}

export default function (data) {
  useSharedAuth(data.sharedAuth);
  const credential = { username: 'seed-admin' };

  runCreateCloudMposSiteFlow(credential, __ITER, data.masterData, START_INDEX_OVERRIDE);

  if (DELAY_SECONDS > 0) {
    sleep(DELAY_SECONDS);
  }
}
