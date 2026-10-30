import { SharedArray } from 'k6/data';
import { TENANT } from './config.js';

// Mirrors lib/userPool.js's pattern for credentials, but for regular
// (item_management_mode='I') items pulled from the tenant DB via
// data-export/src/exportItemPool.ts - see that tool's README-style header
// comment for how to (re)generate data/pools/<tenant>.items.json.
export const itemPool = new SharedArray('itemPool', function () {
  try {
    return JSON.parse(open(`../data/pools/${TENANT}.items.json`));
  } catch (e) {
    // No pool file yet for this tenant (e.g. DB access not set up) - fail
    // loudly only when something actually tries to use the pool, not here,
    // so tenants that never use ITEM_SOURCE=pool aren't blocked by this.
    return [];
  }
});

export function assertPoolCapacity(neededDistinctItems) {
  if (itemPool.length === 0) {
    throw new Error(
      `No regular items loaded for tenant "${TENANT}" - populate data/pools/${TENANT}.items.json ` +
        `(cd data-export && npm run export:items -- ${TENANT} ${TENANT}).`
    );
  }
  if (itemPool.length < neededDistinctItems) {
    throw new Error(
      `Item pool for tenant "${TENANT}" has ${itemPool.length} item(s) but this run needs ` +
        `${neededDistinctItems} distinct regular items. Re-export with a higher limit ` +
        `(cd data-export && npm run export:items -- ${TENANT} ${TENANT} ${neededDistinctItems}).`
    );
  }
}

// Windowed round-robin: VU/iteration-scoped offset so concurrent VUs don't
// all draw the identical leading slice of the pool for the same iteration,
// while still being deterministic (same offset every time for a given
// vuId/iterationTag, so a run is reproducible).
export function pickRegularItems(count, vuId, iterationTag) {
  if (count === 0) return [];
  assertPoolCapacity(count);
  const offset = ((vuId - 1) * 7919 + Number(iterationTag) * 104729) % itemPool.length;
  const picked = [];
  for (let i = 0; i < count; i++) {
    const entry = itemPool[(offset + i) % itemPool.length];
    picked.push({ itemCode: entry.itemCode, itemType: entry.itemType });
  }
  return picked;
}
