import { assignCredential, assertPoolCapacity } from '../../lib/userPool.js';
import { loginWithCredential, useSharedAuth } from '../../lib/auth.js';
import { runFillVoucherFlow } from '../../flows/fillDocSchemeVoucher.js';
import { insecureSkipTLSVerify } from '../../lib/config.js';
import { handleSummary as baseHandleSummary } from '../../lib/report.js';

// Fills a dedicated test document scheme: creates EXACTLY TARGET_DOCS vouchers of
// VOUCHER_TYPE (GV | AR | AP) under DOC_SCHEME_NAME, spread over TARGET_VUS
// parallel users. 'shared-iterations' means TARGET_DOCS is the TOTAL across all
// VUs (not per VU - unlike scenarios/backfill/createGeneralVoucher.js). Iterations count
// attempts; the Selenium side's DocSchemeFiller verifies the real result from
// the DB and re-runs once for any shortfall.
//
// Launched by com.gsl.api.bulk.DocSchemeFiller, e.g.:
//   TARGET_DOCS=999 TARGET_VUS=5 VOUCHER_TYPE=AR DOC_SCHEME_NAME="AR AP Test" TENANT=clone-stage-ideal k6 run scenarios/backfill/fillDocScheme.js
const TARGET_DOCS = Number(__ENV.TARGET_DOCS) || 20;
const TARGET_VUS = Number(__ENV.TARGET_VUS) || 5;
const MAX_DURATION = __ENV.MAX_DURATION || '15m';
const VOUCHER_TYPE = (__ENV.VOUCHER_TYPE || '').toUpperCase();
// See scenarios/performance/load.js for what 'pool' vs 'shared' means.
const AUTH_MODE = __ENV.AUTH_MODE || 'pool';

export const options = {
  insecureSkipTLSVerify,
  scenarios: {
    fillDocScheme: {
      executor: 'shared-iterations',
      vus: TARGET_VUS,
      iterations: TARGET_DOCS,
      maxDuration: MAX_DURATION,
    },
  },
};

export function setup() {
  if (!['GV', 'AR', 'AP'].includes(VOUCHER_TYPE)) {
    throw new Error(`VOUCHER_TYPE must be GV, AR or AP (got '${__ENV.VOUCHER_TYPE || ''}')`);
  }
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
  runFillVoucherFlow(credential, __ITER, VOUCHER_TYPE);
}

// Same report files as every other scenario, plus the K6_SUMMARY line the
// Selenium side parses (the counters come from flows/fillDocSchemeVoucher.js).
export function handleSummary(data) {
  const out = baseHandleSummary(data);
  const created = data.metrics.fill_created ? data.metrics.fill_created.values.count : 0;
  const failed = data.metrics.fill_failed ? data.metrics.fill_failed.values.count : 0;
  out.stdout = `${out.stdout}\nK6_SUMMARY created=${created} failed=${failed}\n`;
  return out;
}
