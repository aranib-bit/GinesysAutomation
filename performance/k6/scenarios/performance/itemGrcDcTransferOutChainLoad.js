import { assignCredential, assertPoolCapacity } from '../../lib/userPool.js';
import { loginWithCredential, useSharedAuth } from '../../lib/auth.js';
import { runItemGrcDcTransferOutChain } from '../../flows/itemGrcDcTransferOutChain.js';
import { insecureSkipTLSVerify } from '../../lib/config.js';
export { handleSummary } from '../../lib/report.js';

const TARGET_VUS = Number(__ENV.TARGET_VUS) || 5;
const DURATION = __ENV.DURATION || '20s';
const ITEM_TYPE = __ENV.ITEM_TYPE || 'I';
// See scenarios/performance/load.js for what 'pool' vs 'shared' means.
const AUTH_MODE = __ENV.AUTH_MODE || 'pool';

export const options = {
  insecureSkipTLSVerify,
  scenarios: {
    itemGrcDcTransferOutChainLoad: {
      executor: 'constant-vus',
      vus: TARGET_VUS,
      duration: DURATION,
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
