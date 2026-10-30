// Usage examples:
//   k6 run -e TENANT=qafeature -e SITE_GROUP=OSOOCM -e ITEM_COUNT=1 scenarios/backfill/createGrcScaled.js
//   k6 run -e TENANT=qafeature -e SITE_GROUP=MSOOCM -e ITEM_COUNT=10 -e DEBUG_HTTP=true scenarios/backfill/createGrcScaled.js
//   k6 run -e TENANT=qafeature -e SITE_GROUP=OSOOCM -e ITEM_COUNT=1000 -e MIX=regular:100 scenarios/backfill/createGrcScaled.js
//
// SITE_GROUP=OSOOCM (default) | MSOOCM - which group() the iteration runs
// under; MSOOCM resolves a live POS site via lib/posSite.js.
// ITEM_COUNT - total distinct items in the one GRC this iteration creates.
// MIX - percentage split across item types, e.g. "regular:70,batch:20,serial:10".
// Only "regular" has a DB-backed pool populated so far - batch/serial shares
// fail fast in setup() until their own HAR is captured (see the plan).
import { group } from 'k6';
import { assignCredential, assertPoolCapacity } from '../../lib/userPool.js';
import { loginWithCredential, useSharedAuth } from '../../lib/auth.js';
import { runCreateGrcFlowMulti } from '../../flows/createGrc.js';
import { resolvePosSite } from '../../lib/posSite.js';
import { pickRegularItems, assertPoolCapacity as assertItemPoolCapacity } from '../../lib/itemPool.js';
import { insecureSkipTLSVerify, TENANT } from '../../lib/config.js';
export { handleSummary } from '../../lib/report.js';

const VUS = Number(__ENV.SMOKE_VUS) || 1;
const ITERATIONS = Number(__ENV.SMOKE_ITERATIONS) || 1;
const AUTH_MODE = __ENV.AUTH_MODE || 'pool';
const SITE_GROUP = (__ENV.SITE_GROUP || 'OSOOCM').toUpperCase();
const ITEM_COUNT = Number(__ENV.ITEM_COUNT) || 1;
const MIX = __ENV.MIX || 'regular:100';

const businessDataArr = [JSON.parse(open(`../../data/business/${TENANT}.grc.json`))];
const businessData = businessDataArr[0];

// Percentage split -> exact per-type counts, remainder assigned to the
// largest share so the total always equals ITEM_COUNT exactly.
export function parseMix(mixString, totalCount) {
  const parts = mixString.split(',').map((p) => {
    const [type, pct] = p.split(':');
    return { type: type.trim().toLowerCase(), pct: Number(pct) };
  });
  const totalPct = parts.reduce((sum, p) => sum + p.pct, 0);
  if (Math.abs(totalPct - 100) > 0.01) {
    throw new Error(`MIX percentages must sum to 100, got ${totalPct} (MIX="${mixString}")`);
  }

  const counts = { regular: 0, batch: 0, serial: 0 };
  let assigned = 0;
  let largest = parts[0];
  parts.forEach((p) => {
    const count = Math.floor((p.pct / 100) * totalCount);
    counts[p.type] = count;
    assigned += count;
    if (p.pct > largest.pct) largest = p;
  });
  const remainder = totalCount - assigned;
  if (remainder > 0) counts[largest.type] += remainder;
  return counts;
}

export const options = {
  insecureSkipTLSVerify,
  scenarios: {
    createGrcScaled: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: ITERATIONS,
      maxDuration: '30m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const counts = parseMix(MIX, ITEM_COUNT);
  ['batch', 'serial'].forEach((type) => {
    if (counts[type] > 0) {
      throw new Error(
        `MIX requests ${counts[type]} "${type}" item(s), but only "regular" items have a DB-backed pool ` +
          'populated so far - batch/serial pools are pending their own HAR capture. Use MIX=regular:100 for now.'
      );
    }
  });
  assertItemPoolCapacity(counts.regular);

  if (AUTH_MODE === 'shared') {
    const credential = assignCredential(1);
    const sharedAuth = loginWithCredential(credential);
    const posSite = SITE_GROUP === 'MSOOCM' ? resolvePosSite(credential, businessData) : null;
    return { sharedAuth, posSite };
  }

  assertPoolCapacity(VUS);
  const bootstrapCredential = assignCredential(1);
  const posSite = SITE_GROUP === 'MSOOCM' ? resolvePosSite(bootstrapCredential, businessData) : null;
  return { posSite };
}

export default function (data) {
  let credential;
  if (AUTH_MODE === 'shared') {
    useSharedAuth(data.sharedAuth);
    credential = { username: 'shared-token' };
  } else {
    credential = assignCredential(__VU);
  }

  const groupName = SITE_GROUP === 'MSOOCM' ? 'createGRCMSOOCM' : 'createGRCOSOOCM';
  group(groupName, function () {
    const counts = parseMix(MIX, ITEM_COUNT);
    const items = pickRegularItems(counts.regular, __VU, __ITER).map((p) => ({ itemCode: p.itemCode, itemType: 'I', pricing: null }));
    runCreateGrcFlowMulti(credential, __ITER, items, data.posSite);
  });
}
