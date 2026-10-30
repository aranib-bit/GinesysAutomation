import { runCreateCloudMposSiteFlow } from './createCloudMposSite.js';
import { runAllocateItemBatchToCloudMposSiteFlow } from './allocateItemToCloudMposSite.js';

// Composes the two Cloud MPOS Site journeys into one iteration: create the
// site, then bulk-allocate a real, pre-existing item list to it - same plain
// composition style as flows/itemGrcDcTransferOutChain.js. Per instruction,
// this never creates throwaway items (that path - lib/itemSource.js's
// resolveItemCode() - hits a pre-existing qahbros bug where ItemMaster/Create
// keeps returning a duplicate icode); every icode allocated here must come
// from the caller-supplied list, and each is sent exactly once.
//
// `itemCodeBatches`: an array of icode arrays, already chunked to a size
// ConnectedPOSItems can handle in one call (confirmed live: 1000 icodes in
// one request works, ~3.2s) - see scenarios/backfill/cloudMposSiteAllocationChain.js
// for how the source file is read/chunked. If the site's own name/shortName
// collided with an existing site (see runCreateCloudMposSiteFlow's skip
// convention), the chain stops there rather than allocating anything.
export function runCloudMposSiteAllocationChain(credential, iterationTag, masterData, connectedAppInfo, itemCodeBatches, startIndexOverride) {
  const site = runCreateCloudMposSiteFlow(credential, iterationTag, masterData, startIndexOverride);
  if (site.skipped) {
    console.log(
      `CloudMposSiteAllocationChain [VU ${__VU} iter ${iterationTag}] site creation skipped (${site.reason}) - not allocating any items.`
    );
    return { site };
  }

  let totalAllocated = 0;
  itemCodeBatches.forEach((batch, batchIdx) => {
    runAllocateItemBatchToCloudMposSiteFlow(credential, `${iterationTag}.${batchIdx}`, batch, connectedAppInfo);
    totalAllocated += batch.length;
  });

  console.log(
    `CloudMposSiteAllocationChain [VU ${__VU} iter ${iterationTag}] site=${site.shortName} -> allocated ${totalAllocated} items across ${itemCodeBatches.length} batches`
  );

  return { site, totalAllocated, batchCount: itemCodeBatches.length };
}
