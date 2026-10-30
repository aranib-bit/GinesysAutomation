import { assignCredential, assertPoolCapacity } from '../../lib/userPool.js';
import { loginWithCredential, useSharedAuth } from '../../lib/auth.js';
import { resolveItemCode } from '../../lib/itemSource.js';
import { runCreateGrcFlow } from '../../flows/createGrc.js';
import { runCreateDcFlow } from '../../flows/createDc.js';
import { insecureSkipTLSVerify } from '../../lib/config.js';
export { handleSummary } from '../../lib/report.js';

const VUS = Number(__ENV.SMOKE_VUS) || 2;
const ITERATIONS = Number(__ENV.SMOKE_ITERATIONS) || 1;
const ITEM_TYPE = __ENV.ITEM_TYPE || 'I';
const AUTH_MODE = __ENV.AUTH_MODE || 'pool';

export const options = {
  insecureSkipTLSVerify,
  scenarios: {
    createDc: {
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
  if (AUTH_MODE === 'shared') {
    const credential = assignCredential(1);
    return { sharedAuth: loginWithCredential(credential) };
  }
  assertPoolCapacity(VUS);
  return {};
}

// Standalone DC run. DC needs stock, which needs a GRC, which needs an item -
// today (ITEM_SOURCE=inline) this builds that prerequisite state itself each
// iteration. Once ITEM_SOURCE=db exists, the item/GRC step here would be
// replaced by resolving a real item that already has stock, making this a
// genuinely independent DC-only load test.
export default function (data) {
  let credential;
  if (AUTH_MODE === 'shared') {
    useSharedAuth(data.sharedAuth);
    credential = { username: 'shared-token' };
  } else {
    credential = assignCredential(__VU);
  }
  const { itemCode, itemType, pricing } = resolveItemCode(credential, __ITER, ITEM_TYPE);
  runCreateGrcFlow(credential, __ITER, itemCode, itemType, pricing);
  runCreateDcFlow(credential, __ITER, itemCode, itemType, pricing);
}
