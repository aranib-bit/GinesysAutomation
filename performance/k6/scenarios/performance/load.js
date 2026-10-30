import { assignCredential, assertPoolCapacity } from '../../lib/userPool.js';
import { loginWithCredential, useSharedAuth } from '../../lib/auth.js';
import { runServiceInvoiceAdhocFlow } from '../../flows/serviceInvoiceAdhoc.js';
import { buildRampingVusProfile } from '../../lib/loadProfile.js';
import { insecureSkipTLSVerify } from '../../lib/config.js';
export { handleSummary } from '../../lib/report.js';

const TARGET_VUS = Number(__ENV.TARGET_VUS) || 10;
const RAMP_UP_DURATION = __ENV.RAMP_UP_DURATION || '2m';
const RAMP_HOLD_DURATION = __ENV.RAMP_HOLD_DURATION || '5m';
const RAMP_DOWN_DURATION = __ENV.RAMP_DOWN_DURATION || '2m';
// 'pool' (default): each VU logs in with its own dedicated credential - real
// concurrent-user simulation. 'shared': one login, every VU reuses the same
// token pair - exploratory mode to see how the app handles one token/session
// being replayed concurrently across many VUs (see plan's shared-token
// concurrency mode follow-up).
const AUTH_MODE = __ENV.AUTH_MODE || 'pool';

export const options = {
  insecureSkipTLSVerify,
  scenarios: {
    load: buildRampingVusProfile({
      targetVUs: TARGET_VUS,
      rampUpDuration: RAMP_UP_DURATION,
      holdDuration: RAMP_HOLD_DURATION,
      rampDownDuration: RAMP_DOWN_DURATION,
    }),
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{endpoint:ServiceInvoice/Save}': ['p(95)<3000'],
    'http_req_duration{endpoint:RT/GetAdmSchemeDocList}': ['p(95)<1500'],
    'http_req_duration{endpoint:ServiceInvoice/GetOtherDetails}': ['p(95)<1500'],
    'http_req_duration{endpoint:ServiceInvoice/CalculateServiceCharges}': ['p(95)<1500'],
    'http_req_duration{endpoint:GridState/SaveState}': ['p(95)<800'],
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
