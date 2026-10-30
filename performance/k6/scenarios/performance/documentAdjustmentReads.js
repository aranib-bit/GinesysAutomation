import { assignCredential, assertPoolCapacity } from '../../lib/userPool.js';
import { insecureSkipTLSVerify } from '../../lib/config.js';
import { handleSummary as baseHandleSummary } from '../../lib/report.js';
import { runDocumentAdjustmentReadsFlow, targetCount } from '../../flows/documentAdjustmentReads.js';

// Select Document / Reference Site screens under load (read-only; see flows/documentAdjustmentReads.js).
//   DOC_ADJ_FILE=<project>/performance/k6/data/generated/docadj-<runId>.json TENANT=qaprod k6 run scenarios/performance/documentAdjustmentReads.js
const TARGET_VUS = Number(__ENV.TARGET_VUS) || 5;
const DURATION = __ENV.DURATION || '1m';
// No latency threshold is invented here: set DOCADJ_PENDING_P95_MS / DOCADJ_REFERENCE_P95_MS to the agreed budgets to enforce them.
const thresholds = { http_req_failed: ['rate<0.01'], checks: ['rate>0.99'] };
if (__ENV.DOCADJ_PENDING_P95_MS) thresholds.docadj_pending_ms = [`p(95)<${Number(__ENV.DOCADJ_PENDING_P95_MS)}`];
if (__ENV.DOCADJ_REFERENCE_P95_MS) thresholds.docadj_reference_ms = [`p(95)<${Number(__ENV.DOCADJ_REFERENCE_P95_MS)}`];

export const options = {
  insecureSkipTLSVerify,
  scenarios: {
    documentAdjustmentReads: {
      executor: 'constant-vus',
      vus: TARGET_VUS,
      duration: DURATION,
    },
  },
  thresholds,
};

export function setup() {
  if (targetCount() === 0) throw new Error('DOC_ADJ_FILE holds no sub-ledger targets (or is not set).');
  assertPoolCapacity(TARGET_VUS);
}

export default function () {
  runDocumentAdjustmentReadsFlow(assignCredential(__VU));
}

export function handleSummary(data) {
  return baseHandleSummary(data);
}
