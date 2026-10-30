import { assignCredential, assertPoolCapacity } from '../../lib/userPool.js';
import { insecureSkipTLSVerify } from '../../lib/config.js';
import { handleSummary as baseHandleSummary } from '../../lib/report.js';
import { runListPartiesFlow } from '../../flows/listParties.js';

// Party list screens under load (Vendor Master family or Customer, see flows/listParties.js for the environment).
//   PARTY_FAMILY=VENDOR CONNECTED_SITE=1 CONNECTED_OU=1 TENANT=qaprod k6 run scenarios/performance/listParties.js
const TARGET_VUS = Number(__ENV.TARGET_VUS) || 5;
const DURATION = __ENV.DURATION || '1m';
// No latency threshold is invented here: set LIST_PAGE_P95_MS to the agreed budget to enforce one.
const thresholds = { http_req_failed: ['rate<0.01'], checks: ['rate>0.99'] };
if (__ENV.LIST_PAGE_P95_MS) thresholds.party_list_page_ms = [`p(95)<${Number(__ENV.LIST_PAGE_P95_MS)}`];

export const options = {
  insecureSkipTLSVerify,
  scenarios: {
    listParties: {
      executor: 'constant-vus',
      vus: TARGET_VUS,
      duration: DURATION,
    },
  },
  thresholds,
};

export function setup() {
  assertPoolCapacity(TARGET_VUS);
}

export default function () {
  runListPartiesFlow(assignCredential(__VU));
}

export function handleSummary(data) {
  return baseHandleSummary(data);
}
