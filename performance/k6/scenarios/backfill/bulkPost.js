import { assignCredential, assertPoolCapacity } from '../../lib/userPool.js';
import { loginWithCredential, useSharedAuth } from '../../lib/auth.js';
import { runBulkPostFlow, bodyCount, currentBodyIndex } from '../../flows/bulkPost.js';
import { insecureSkipTLSVerify } from '../../lib/config.js';
import { handleSummary as baseHandleSummary } from '../../lib/report.js';

// Bulk master-data writes from a Java-generated data file (parties, extinct flags, ledgers): posts EXACTLY as many bodies as the file holds
// (TARGET_DOCS may lower it), spread over TARGET_VUS users. TARGET_DOCS is the TOTAL across VUs (shared-iterations).
//   BULK_DATA_FILE=<project>/performance/k6/data/generated/<kind>-<runId>.json TENANT=qaprod k6 run scenarios/backfill/bulkPost.js
const TARGET_VUS = Number(__ENV.TARGET_VUS) || 5;
const MAX_DURATION = __ENV.MAX_DURATION || '15m';
const AUTH_MODE = __ENV.AUTH_MODE || 'pool';
const AVAILABLE = bodyCount();
const TARGET_DOCS = Math.min(Number(__ENV.TARGET_DOCS) || AVAILABLE, AVAILABLE);

export const options = {
  insecureSkipTLSVerify,
  scenarios: {
    bulkPost: {
      executor: 'shared-iterations',
      vus: Math.min(TARGET_VUS, Math.max(TARGET_DOCS, 1)),
      iterations: TARGET_DOCS,
      maxDuration: MAX_DURATION,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  if (TARGET_DOCS === 0) throw new Error('BULK_DATA_FILE holds no bodies (or is not set).');
  if (AUTH_MODE === 'shared') {
    return { sharedAuth: loginWithCredential(assignCredential(1)) };
  }
  assertPoolCapacity(options.scenarios.bulkPost.vus);
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
  runBulkPostFlow(credential, currentBodyIndex());
}

export function handleSummary(data) {
  const out = baseHandleSummary(data);
  const created = data.metrics.bulk_created ? data.metrics.bulk_created.values.count : 0;
  const failed = data.metrics.bulk_failed ? data.metrics.bulk_failed.values.count : 0;
  out.stdout = `${out.stdout}\nK6_SUMMARY created=${created} failed=${failed}\n`;
  return out;
}
