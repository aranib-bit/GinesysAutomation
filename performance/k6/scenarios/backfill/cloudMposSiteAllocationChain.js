import { assignCredential, assertPoolCapacity } from '../../lib/userPool.js';
import { loginWithCredential, useSharedAuth } from '../../lib/auth.js';
import { resolveSiteMasterData } from '../../lib/posSite.js';
import { resolveConnectedAppInfo } from '../../flows/allocateItemToCloudMposSite.js';
import { runCloudMposSiteAllocationChain } from '../../flows/cloudMposSiteAllocationChain.js';
import { insecureSkipTLSVerify } from '../../lib/config.js';
export { handleSummary } from '../../lib/report.js';

// Primary entry point: creates SEED_COUNT new Cloud MPOS Site(s) (CM<n>,
// CM<n+1>, ...) and bulk-allocates a real, pre-existing item list to each -
// single VU, START_INDEX(+override)+__ITER for the site-number sequence,
// same collision-free pattern scenarios/backfill/seedPerfUsers.js uses for user
// seeding. Per instruction, no throwaway items are created - every icode
// comes from ITEM_LIST_FILE and each is sent exactly once.
//
// Default (SEED_COUNT=1): one new site gets the entire list, chunked into
// ITEM_BATCH_SIZE-sized ConnectedPOSItems calls. If SEED_COUNT>1, the SAME
// full list is allocated to EACH new site (items are not repeated *within*
// a site's own allocation, but will appear on more than one site) - a
// warning is logged so that's not silently surprising.
//
// Reading/chunking the file must happen at module (init) scope - k6's
// open() is only callable there, not inside default().
const ITEM_LIST_FILE = __ENV.ITEM_LIST_FILE;
if (!ITEM_LIST_FILE) {
  throw new Error('ITEM_LIST_FILE env var is required - pass -e ITEM_LIST_FILE="<path to a file with one icode per line>".');
}
const ITEM_BATCH_SIZE = Number(__ENV.ITEM_BATCH_SIZE) || 1000;
const COUNT = Number(__ENV.SEED_COUNT) || 1;
const START_INDEX_OVERRIDE = __ENV.SEED_START_INDEX !== undefined ? Number(__ENV.SEED_START_INDEX) : undefined;

const icodes = open(ITEM_LIST_FILE)
  .split('\n')
  .map((s) => s.trim())
  .filter(Boolean);
if (icodes.length === 0) {
  throw new Error(`ITEM_LIST_FILE "${ITEM_LIST_FILE}" contained no non-blank lines.`);
}

const itemCodeBatches = [];
for (let i = 0; i < icodes.length; i += ITEM_BATCH_SIZE) {
  itemCodeBatches.push(icodes.slice(i, i + ITEM_BATCH_SIZE));
}

console.log(
  `cloudMposSiteAllocationChain: ${icodes.length} icodes from "${ITEM_LIST_FILE}", batchSize=${ITEM_BATCH_SIZE}, ${itemCodeBatches.length} batches per site, ${COUNT} new site(s)`
);
if (COUNT > 1) {
  console.log(`cloudMposSiteAllocationChain: SEED_COUNT=${COUNT} - the full item list will be allocated to EACH new site (items will repeat across sites, not within one site).`);
}

export const options = {
  insecureSkipTLSVerify,
  scenarios: {
    cloudMposSiteAllocationChain: {
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
  // Prime this (setup's own) context's auth cache so resolveSiteMasterData's/
  // resolveConnectedAppInfo's apiGet calls below don't trigger a second login.
  useSharedAuth(sharedAuth);
  const masterData = resolveSiteMasterData(credential);
  const connectedAppInfo = resolveConnectedAppInfo(credential);
  return { sharedAuth, masterData, connectedAppInfo };
}

export default function (data) {
  // setup() and default() run in separate JS contexts - re-seed this VU's
  // auth cache from setup()'s single login rather than logging in again.
  useSharedAuth(data.sharedAuth);
  const credential = { username: 'seed-admin' };

  runCloudMposSiteAllocationChain(credential, __ITER, data.masterData, data.connectedAppInfo, itemCodeBatches, START_INDEX_OVERRIDE);
}
