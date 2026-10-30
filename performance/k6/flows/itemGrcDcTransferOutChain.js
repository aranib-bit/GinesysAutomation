import { resolveItemCode } from '../lib/itemSource.js';
import { runCreateGrcFlow } from './createGrc.js';
import { runCreateDcFlow } from './createDc.js';
import { runCreateTransferOutFlow } from './createTransferOut.js';

// Chains Item -> GRC -> DC -> TransferOut within a single iteration. Every
// iteration creates and uses its own item, so there's no cross-VU race over
// which item/batch-serial/DC to touch, and nothing needs a DB or a pool file
// (see lib/itemSource.js for the one seam that changes once DB access and a
// data-export item query exist).
export function runItemGrcDcTransferOutChain(credential, iterationTag, itemType) {
  const { itemCode, itemType: resolvedType, pricing } = resolveItemCode(credential, iterationTag, itemType);
  const grc = runCreateGrcFlow(credential, iterationTag, itemCode, resolvedType, pricing);
  const dcResult = runCreateDcFlow(credential, iterationTag, itemCode, resolvedType, pricing);
  const transferOut = runCreateTransferOutFlow(credential, iterationTag, dcResult.dcCode);

  return { itemCode, itemType: resolvedType, grcCode: grc.grcCode, dcCode: dcResult.dcCode, schemeDocNo: transferOut.schemeDocNo };
}
