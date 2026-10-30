import { assignCredential, assertPoolCapacity } from '../../lib/userPool.js';
import { loginWithCredential, useSharedAuth } from '../../lib/auth.js';
import { runItemGrcDcTransferOutChain } from '../../flows/itemGrcDcTransferOutChain.js';
import { insecureSkipTLSVerify } from '../../lib/config.js';
export { handleSummary } from '../../lib/report.js';

// "Run until TARGET_DOCS full chains (Item->GRC->DC->TransferOut) are
// completed, capped at MAX_DURATION, whichever comes first." Mirrors
// scenarios/backfill/targetDocs.js's convention for the Service Invoice Adhoc flow -
// iterations count attempts, not confirmed successful chains, but the true
// count is always visible via each step's own "-> schemeDocNo=..."/"->
// itemCode=..." log lines (see flows/itemGrcDcTransferOutChain.js and its
// per-step flow files).
const TARGET_DOCS = Number(__ENV.TARGET_DOCS) || 20;
const TARGET_VUS = Number(__ENV.TARGET_VUS) || 5;
const MAX_DURATION = __ENV.MAX_DURATION || '5m';
const ITEM_TYPE = __ENV.ITEM_TYPE || 'I';
// See scenarios/performance/load.js for what 'pool' vs 'shared' means.
const AUTH_MODE = __ENV.AUTH_MODE || 'pool';

export const options = {
  insecureSkipTLSVerify,
  scenarios: {
    itemGrcDcTransferOutChainTargetDocs: {
      executor: 'shared-iterations',
      vus: TARGET_VUS,
      iterations: TARGET_DOCS,
      maxDuration: MAX_DURATION,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export function setup() {
  if (AUTH_MODE === 'shared') {
    const credential = assignCredential(1);
    return { sharedAuth: loginWithCredential(credential) };
  }
  assertPoolCapacity(TARGET_VUS);
  return {};
}

export default function (data) {
  let credential;
  if (AUTH_MODE === 'shared') {
    useSharedAuth(data.sharedAuth);
    credential = { username: 'shared-token' };
  } else {
    credential = assignCredential(__VU);
  }
  runItemGrcDcTransferOutChain(credential, __ITER, ITEM_TYPE);
}
