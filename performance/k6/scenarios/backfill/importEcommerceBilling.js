import { assignCredential, assertPoolCapacity } from '../../lib/userPool.js';
import { runImportEcommerceBillingFlow } from '../../flows/importEcommerceBilling.js';
import { insecureSkipTLSVerify } from '../../lib/config.js';
export { handleSummary } from '../../lib/report.js';

// One VU = one distinct login = one generated xlsx per iteration, so VUS must
// not exceed the credential pool (assertPoolCapacity) - two VUs sharing a
// login would trigger the app's "already logged in" kill-session conflict.
const VUS = Number(__ENV.VUS) || 20;
const ITERATIONS = Number(__ENV.ITERATIONS) || 1;
const MAX_DURATION = __ENV.MAX_DURATION || '3h';

export const options = {
  insecureSkipTLSVerify,
  scenarios: {
    importEcommerceBilling: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: ITERATIONS,
      maxDuration: MAX_DURATION,
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
  },
};

export function setup() {
  assertPoolCapacity(VUS);
  // Fixed once per run so REFERENCE_NO is unique run-to-run; override with
  // -e RUN_ID=... only if you need a predictable prefix.
  return { runId: __ENV.RUN_ID || Date.now().toString(36) };
}

export default function (data) {
  const credential = assignCredential(__VU);
  runImportEcommerceBillingFlow(credential, data.runId, __ITER);
}
