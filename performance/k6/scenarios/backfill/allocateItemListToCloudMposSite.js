import { sleep } from 'k6';
import { assignCredential, assertPoolCapacity } from '../../lib/userPool.js';
import { loginWithCredential, useSharedAuth } from '../../lib/auth.js';
import { resolveConnectedAppInfo, runAllocateItemBatchToCloudMposSiteFlow } from '../../flows/allocateItemToCloudMposSite.js';
import { insecureSkipTLSVerify } from '../../lib/config.js';
export { handleSummary } from '../../lib/report.js';

// Bulk-allocates a flat list of real, pre-existing item codes (one icode per
// line) to the tenant's Connected App - for allocating a large externally
// supplied list (e.g. invitem_<date>.txt) rather than a small number of
// framework-generated/resolved items. ITEM_LIST_FILE is required; confirmed
// live that ConnectedPOSItems accepts many items per call (tested up to
// 1000 icodes in ~3.2s), so the list is chunked into ITEM_BATCH_SIZE-sized
// POSTs instead of one call per item - at the default batch size a ~147k-line
// file is ~148 requests, not 147k.
//
// Reading/chunking the file must happen at module (init) scope - k6's
// open() is only callable there, not inside default().
const ITEM_LIST_FILE = __ENV.ITEM_LIST_FILE;
if (!ITEM_LIST_FILE) {
  throw new Error('ITEM_LIST_FILE env var is required - pass -e ITEM_LIST_FILE="<path to a file with one icode per line>".');
}
const ITEM_BATCH_SIZE = Number(__ENV.ITEM_BATCH_SIZE) || 1000;
const DELAY_SECONDS = __ENV.SEED_DELAY_SECONDS !== undefined ? Number(__ENV.SEED_DELAY_SECONDS) : 0;

const icodes = open(ITEM_LIST_FILE)
  .split('\n')
  .map((s) => s.trim())
  .filter(Boolean);
if (icodes.length === 0) {
  throw new Error(`ITEM_LIST_FILE "${ITEM_LIST_FILE}" contained no non-blank lines.`);
}

const chunks = [];
for (let i = 0; i < icodes.length; i += ITEM_BATCH_SIZE) {
  chunks.push(icodes.slice(i, i + ITEM_BATCH_SIZE));
}

console.log(`allocateItemListToCloudMposSite: ${icodes.length} icodes from "${ITEM_LIST_FILE}", batchSize=${ITEM_BATCH_SIZE}, ${chunks.length} batches`);

export const options = {
  insecureSkipTLSVerify,
  scenarios: {
    allocateItemListToCloudMposSite: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: chunks.length,
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

  const batch = chunks[__ITER];
  runAllocateItemBatchToCloudMposSiteFlow(credential, __ITER, batch, data.connectedAppInfo);

  if (DELAY_SECONDS > 0) {
    sleep(DELAY_SECONDS);
  }
}
