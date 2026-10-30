import { assignCredential, assertPoolCapacity } from '../../lib/userPool.js';
import { loginWithCredential, useSharedAuth } from '../../lib/auth.js';
import { runCreateTransferOutFlow } from '../../flows/createTransferOut.js';
import { insecureSkipTLSVerify } from '../../lib/config.js';
export { handleSummary } from '../../lib/report.js';

const VUS = Number(__ENV.SMOKE_VUS) || 1;
const ITERATIONS = Number(__ENV.SMOKE_ITERATIONS) || 1;
const DC_CODE = Number(__ENV.DC_CODE);
const AUTH_MODE = __ENV.AUTH_MODE || 'pool';

export const options = {
  insecureSkipTLSVerify,
  scenarios: {
    createTransferOutDirect: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: ITERATIONS,
      maxDuration: '5m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  if (!DC_CODE) {
    throw new Error('Set -e DC_CODE=<code> - this scenario tests SI/Save directly against an already-existing pending DC.');
  }
  if (AUTH_MODE === 'shared') {
    const credential = assignCredential(1);
    return { sharedAuth: loginWithCredential(credential) };
  }
  assertPoolCapacity(VUS);
  return {};
}

// Diagnostic: exercises TransferOut (SI/Save) on directly against a
// pre-existing pending DC, bypassing Item/GRC/DC entirely - same purpose as
// createGrcDirect.js/createDcDirect.js, one step further down the chain.
export default function (data) {
  let credential;
  if (AUTH_MODE === 'shared') {
    useSharedAuth(data.sharedAuth);
    credential = { username: 'shared-token' };
  } else {
    credential = assignCredential(__VU);
  }
  runCreateTransferOutFlow(credential, __ITER, DC_CODE);
}
