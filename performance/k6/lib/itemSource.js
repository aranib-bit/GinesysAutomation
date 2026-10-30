import { runCreateItemFlow } from '../flows/createItem.js';
import { pickRegularItems } from './itemPool.js';

// The one real cross-step dependency in the Item->GRC->DC->TransferOut chain:
// GRC needs an item code to receipt stock against, and there's no "search any
// item" endpoint - a specific item must be chosen. Everything else in the
// chain (which batch/serial has stock, which DC is pending transfer) is
// discovered live from the app itself, so it never needs a resolver like this.
//
// ITEM_SOURCE=inline (default, no DB access): create a fresh throwaway item
// this iteration and use its code immediately.
// ITEM_SOURCE=db (future, once qafeature/qahbros DB access is restored): read
// a real pre-existing item code from a DB-populated source instead, so
// createGrc/createDc/createTransferOut can run standalone against real data.
export function resolveItemCode(credential, iterationTag, itemType) {
  const source = __ENV.ITEM_SOURCE || 'inline';

  if (source === 'inline') {
    const created = runCreateItemFlow(credential, iterationTag, itemType);
    return { itemCode: created.itemCode, itemType: created.itemType, pricing: created.pricing };
  }

  if (source === 'db') {
    if (itemType !== 'I') {
      throw new Error(
        `ITEM_SOURCE=db only has a regular-item (itemType 'I') pool populated so far - got itemType "${itemType}". ` +
          'Batch/serial pools are pending their own HAR capture (see the plan).'
      );
    }
    // Real, pre-existing items pulled from the tenant DB via
    // data-export/src/exportItemPool.ts - no pricing is known ahead of time
    // (only icode/barunit are queried), so pricing is left null here and
    // resolved live by the GRC flow itself from GetItemDetailWithOpeningRate's
    // own mrp/rsp/wsp/rate, the same way the real UI derives it - a synthetic
    // guessed price risks tripping ValidateMarginRule against a real item's
    // actual historical pricing.
    const [picked] = pickRegularItems(1, __VU, iterationTag);
    return { itemCode: picked.itemCode, itemType: picked.itemType, pricing: null };
  }

  throw new Error(`Unknown ITEM_SOURCE "${source}" - expected "inline" or "db".`);
}
