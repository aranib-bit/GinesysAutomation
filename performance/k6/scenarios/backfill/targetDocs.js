import { assignCredential, assertPoolCapacity } from '../../lib/userPool.js';
import { loginWithCredential, useSharedAuth } from '../../lib/auth.js';
import { runServiceInvoiceAdhocFlow } from '../../flows/serviceInvoiceAdhoc.js';
import { insecureSkipTLSVerify } from '../../lib/config.js';
export { handleSummary } from '../../lib/report.js';

// "Run until TARGET_DOCS documents are created, capped at MAX_DURATION,
// whichever comes first." iterations here count attempts, not confirmed
// successful Saves - with the current 100% pass rate they're equivalent, but
// the true count is always visible via each iteration's "Save [...] ->
// schemeDocNo=..." log line (see flows/serviceInvoiceAdhoc.js).
const TARGET_DOCS = Number(__ENV.TARGET_DOCS) || 20;
const TARGET_VUS = Number(__ENV.TARGET_VUS) || 5;
const MAX_DURATION = __ENV.MAX_DURATION || '2m';
// See scenarios/performance/load.js for what 'pool' vs 'shared' means.
const AUTH_MODE = __ENV.AUTH_MODE || 'pool';

export const options = {
  insecureSkipTLSVerify,
  scenarios: {
    targetDocs: {
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
  runServiceInvoiceAdhocFlow(credential, __ITER);
}
