import { sleep } from 'k6';
import { assignCredential, assertPoolCapacity } from '../../lib/userPool.js';
import { loginWithCredential, useSharedAuth } from '../../lib/auth.js';
import { resolveItemCode } from '../../lib/itemSource.js';
import { resolveConnectedAppInfo, runAllocateItemToCloudMposSiteFlow } from '../../flows/allocateItemToCloudMposSite.js';
import { insecureSkipTLSVerify } from '../../lib/config.js';
export { handleSummary } from '../../lib/report.js';

// Standalone variant of the item-allocation half of
// scenarios/backfill/cloudMposSiteAllocationChain.js - allocates SEED_COUNT resolved
// items to the tenant's Connected App directly, independent of site
// creation (allocation is OU-level, not per-site - see
// flows/allocateItemToCloudMposSite.js's resolveConnectedAppInfo comment).
const COUNT = Number(__ENV.SEED_COUNT) || 1;
const DELAY_SECONDS = __ENV.SEED_DELAY_SECONDS !== undefined ? Number(__ENV.SEED_DELAY_SECONDS) : 0.5;
const ITEM_TYPE = __ENV.ITEM_TYPE || 'I';

export const options = {
  insecureSkipTLSVerify,
  scenarios: {
    allocateItemToCloudMposSite: {
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
  const connectedAppInfo = resolveConnectedAppInfo(credential);
  return { sharedAuth, connectedAppInfo };
}

export default function (data) {
  useSharedAuth(data.sharedAuth);
  const credential = { username: 'seed-admin' };

  const { itemCode } = resolveItemCode(credential, __ITER, ITEM_TYPE);
  runAllocateItemToCloudMposSiteFlow(credential, __ITER, itemCode, data.connectedAppInfo);

  if (DELAY_SECONDS > 0) {
    sleep(DELAY_SECONDS);
  }
}
