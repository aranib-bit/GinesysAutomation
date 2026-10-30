import { assignCredential, assertPoolCapacity } from '../../lib/userPool.js';
import { loginWithCredential, useSharedAuth } from '../../lib/auth.js';
import { runCreateDcFlow } from '../../flows/createDc.js';
import { insecureSkipTLSVerify } from '../../lib/config.js';
export { handleSummary } from '../../lib/report.js';

const VUS = Number(__ENV.SMOKE_VUS) || 1;
const ITERATIONS = Number(__ENV.SMOKE_ITERATIONS) || 1;
const ITEM_TYPE = __ENV.ITEM_TYPE || 'I';
const ITEM_CODE = __ENV.ITEM_CODE;
const AUTH_MODE = __ENV.AUTH_MODE || 'pool';

export const options = {
  insecureSkipTLSVerify,
  scenarios: {
    createDcDirect: {
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
  if (!ITEM_CODE) {
    throw new Error('Set -e ITEM_CODE=<code> - this scenario tests DC/Save directly against an already-existing item, skipping Create/GRC entirely.');
  }
  if (AUTH_MODE === 'shared') {
    const credential = assignCredential(1);
    return { sharedAuth: loginWithCredential(credential) };
  }
  assertPoolCapacity(VUS);
  return {};
}

// Diagnostic: exercises DC/Save on qafeature directly against a pre-existing
// item code, bypassing ItemMaster/Create and GRC entirely - to test whether
// qafeature's block is specific to ItemMaster/Create or affects DC/Save too.
export default function (data) {
  let credential;
  if (AUTH_MODE === 'shared') {
    useSharedAuth(data.sharedAuth);
    credential = { username: 'shared-token' };
  } else {
    credential = assignCredential(__VU);
  }
  const pricing = { costrate: 300, wsp: 400, mrp: 500, listedMrp: 600 };
  runCreateDcFlow(credential, __ITER, ITEM_CODE, ITEM_TYPE, pricing);
}
