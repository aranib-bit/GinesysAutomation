import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import { Counter, Trend } from 'k6/metrics';
import { apiGet, checkEnvelope } from '../lib/http.js';
import { pathOf } from '../lib/api.js';
import * as Endpoints from '../lib/generated/endpoints.generated.js';

// Read-only Document Adjustment journey for one sub-ledger, as the Select Document / Reference Site screens do it: the documents with a pending
// balance (paged) and the pending amount per reference site. The sub-ledgers come from a Java-written file (DOC_ADJ_FILE, see
// com.gsl.api.bulk.DocumentAdjustmentBulk): module, document type, OU, date and the {glCode, slCode} targets; one target per iteration, round robin.
// Output line: DocAdjReads [VU n iter m] OK module=... sl=... docs=... pages=... refSites=... ms=...
export const docAdjPendingMs = new Trend('docadj_pending_ms', true);
export const docAdjReferenceMs = new Trend('docadj_reference_ms', true);
export const docAdjReads = new Counter('docadj_reads');

const DATA_FILE = __ENV.DOC_ADJ_FILE;
const PAGE_SIZE = Number(__ENV.PAGE_SIZE) || 500;
const MAX_PAGES = Number(__ENV.MAX_PAGES) || 20;

const dataArr = new SharedArray('docAdjData', function () {
  if (!DATA_FILE) return [];
  return [JSON.parse(open(DATA_FILE))];
});

export function targetCount() {
  return dataArr.length === 0 || !dataArr[0].targets ? 0 : dataArr[0].targets.length;
}

export function runDocumentAdjustmentReadsFlow(credential) {
  const data = dataArr[0];
  if (!data) throw new Error('Set DOC_ADJ_FILE (a file written by the Java DocumentAdjustmentBulk) - refusing to invent sub-ledgers.');
  const pendingEndpoint = Endpoints.DocumentAdjustment[`${data.endpointPrefix}_PENDING`];
  const referenceEndpoint = Endpoints.DocumentAdjustment[`${data.endpointPrefix}_REFERENCE`];
  if (!pendingEndpoint || !referenceEndpoint) {
    throw new Error(`Unknown module prefix ${data.endpointPrefix} - regenerate lib/generated/endpoints.generated.js (K6ContractTest).`);
  }
  const target = data.targets[exec.scenario.iterationInTest % data.targets.length];
  const tag = `DocAdjReads [VU ${__VU} iter ${__ITER}]`;
  const started = Date.now();

  // 1. Pending documents, paged until exhausted.
  let docs = 0;
  let pages = 0;
  for (let page = 1; page <= MAX_PAGES; page++) {
    const res = apiGet(
      credential,
      pathOf(pendingEndpoint),
      { glCode: target.glCode, slCode: target.slCode, ouCode: data.ouCode, date: data.date, docType: data.docType, workDate: data.date, page, start: (page - 1) * PAGE_SIZE, limit: PAGE_SIZE },
      'DocumentAdjustment.pending'
    );
    docAdjPendingMs.add(res.timings.duration);
    if (!checkEnvelope(res, 'pending documents')) {
      console.log(`${tag} FAIL pending sl=${target.slCode} status=${res.status} body=${String(res.body).slice(0, 300)}`);
      return;
    }
    const result = JSON.parse(res.body).result || {};
    const items = result.items || [];
    pages++;
    docs += items.length;
    if (items.length === 0 || items.length < PAGE_SIZE || docs >= (result.totalRecords || 0)) break;
  }

  // 2. Pending amount per reference site.
  const ref = apiGet(
    credential,
    pathOf(referenceEndpoint),
    {
      searchText: '',
      target: 'referencesiteforfifo',
      module: data.module,
      ouCode: data.ouCode,
      slCode: target.slCode,
      glCode: target.glCode,
      connectedOUCode: data.ouCode,
      date: data.date,
      limit: 50,
      workDate: data.date,
      page: 1,
      start: 0,
    },
    'DocumentAdjustment.reference'
  );
  docAdjReferenceMs.add(ref.timings.duration);
  if (!checkEnvelope(ref, 'reference sites')) {
    console.log(`${tag} FAIL reference sl=${target.slCode} status=${ref.status} body=${String(ref.body).slice(0, 300)}`);
    return;
  }
  const refSites = (JSON.parse(ref.body).result || []).length;
  docAdjReads.add(1);
  console.log(`${tag} OK module=${data.module} sl=${target.slCode} docs=${docs} pages=${pages} refSites=${refSites} ms=${Date.now() - started}`);
}
