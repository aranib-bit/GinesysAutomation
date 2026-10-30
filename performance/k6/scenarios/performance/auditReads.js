import { assignCredential, assertPoolCapacity } from '../../lib/userPool.js';
import { insecureSkipTLSVerify } from '../../lib/config.js';
import { handleSummary as baseHandleSummary } from '../../lib/report.js';
import { runAuditReadsFlow } from '../../flows/auditReads.js';

// Audit screens under load (read-only; see flows/auditReads.js for the environment).
//   AUDIT_TABLES=aud2_finvchmain,aud2_finvchdet TENANT=qaprod k6 run scenarios/performance/auditReads.js
const TARGET_VUS = Number(__ENV.TARGET_VUS) || 5;
const DURATION = __ENV.DURATION || '1m';
// No latency threshold is invented here: set AUDIT_LIST_P95_MS / AUDIT_HISTORY_P95_MS to the agreed budgets to enforce them.
const thresholds = { http_req_failed: ['rate<0.01'], checks: ['rate>0.99'] };
if (__ENV.AUDIT_LIST_P95_MS) thresholds.audit_list_ms = [`p(95)<${Number(__ENV.AUDIT_LIST_P95_MS)}`];
if (__ENV.AUDIT_HISTORY_P95_MS) thresholds.audit_history_ms = [`p(95)<${Number(__ENV.AUDIT_HISTORY_P95_MS)}`];

export const options = {
  insecureSkipTLSVerify,
  scenarios: {
    auditReads: {
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
  runAuditReadsFlow(assignCredential(__VU));
}

export function handleSummary(data) {
  return baseHandleSummary(data);
}
