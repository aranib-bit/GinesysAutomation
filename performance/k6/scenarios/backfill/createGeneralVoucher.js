import { assignCredential, assertPoolCapacity } from '../../lib/userPool.js';
import { loginWithCredential, useSharedAuth } from '../../lib/auth.js';
import { runCreateGeneralVoucherFlow } from '../../flows/createGeneralVoucher.js';
import { insecureSkipTLSVerify } from '../../lib/config.js';
export { handleSummary } from '../../lib/report.js';

const VUS = Number(__ENV.SMOKE_VUS) || 1;
const ITERATIONS = Number(__ENV.SMOKE_ITERATIONS) || 1;
const AUTH_MODE = __ENV.AUTH_MODE || 'pool';
// Was hardcoded at '5m' - fine for a 1-iteration smoke run, but a bulk run
// (e.g. thousands of iterations per VU) needs this raised well above the
// scenario's actual expected runtime, or per-vu-iterations silently cuts the
// run off mid-way with only a fraction of the requested documents created.
const MAX_DURATION = __ENV.MAX_DURATION || '5m';

export const options = {
  insecureSkipTLSVerify,
  scenarios: {
    createGeneralVoucher: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: ITERATIONS,
      maxDuration: MAX_DURATION,
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

// Standalone General Voucher run - no item-creation prerequisite (unlike
// GRC/DC), so unlike createGrc.js this is already the "direct" shape: no
// separate ...Direct.js variant needed.
export default function (data) {
  let credential;
  if (AUTH_MODE === 'shared') {
    useSharedAuth(data.sharedAuth);
    credential = { username: 'shared-token' };
  } else {
    credential = assignCredential(__VU);
  }
  runCreateGeneralVoucherFlow(credential, __ITER);
}
