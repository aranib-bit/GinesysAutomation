import { Counter, Trend } from 'k6/metrics';
import { check } from 'k6';
import { apiGet, apiPost, checkEnvelope } from '../lib/http.js';
import { pathOf } from '../lib/api.js';
import * as Endpoints from '../lib/generated/endpoints.generated.js';

// Read-only Audit module journey, as the Audit screens do it: which modules audit -> the module's documents with audit activity in a date range
// -> the per-table history of a few of those documents. Nothing is written, so it is safe on any tenant.
//   AUDIT_MODULE   module short code (default FinVoucher)
//   AUDIT_DAYS     how many days back the document range starts (default 30)
//   AUDIT_TABLES   comma-separated history tables to read per document (none = stop after the document list)
//   AUDIT_DOCS     how many documents get their history read per iteration (default 3)
// Output line: AuditReads [VU n iter m] OK module=... docs=... historyCalls=... rows=... ms=...
export const auditHistoryCalls = new Counter('audit_history_calls');
export const auditListMs = new Trend('audit_list_ms', true);
export const auditHistoryMs = new Trend('audit_history_ms', true);

const MODULE = __ENV.AUDIT_MODULE || 'FinVoucher';
const DAYS = Number(__ENV.AUDIT_DAYS) || 30;
const TABLES = (__ENV.AUDIT_TABLES || '').split(',').map((t) => t.trim()).filter((t) => t.length > 0);
const DOCS = Number(__ENV.AUDIT_DOCS) || 3;

function stamp(date) {
  return `${date.toISOString().slice(0, 10)}T00:00:00`;
}

export function runAuditReadsFlow(credential) {
  const started = Date.now();
  const tag = `AuditReads [VU ${__VU} iter ${__ITER}]`;
  const to = new Date();
  const from = new Date(to.getTime() - DAYS * 24 * 60 * 60 * 1000);

  // 1. Which modules audit (the module must be listed; whether it is enabled is reported, not assumed).
  const modules = apiPost(credential, pathOf(Endpoints.Audit.APPLICABLE_MODULES), { workDate: stamp(to), page: 1, start: 0, limit: 500 }, null, 'Audit.APPLICABLE_MODULES');
  if (!checkEnvelope(modules, 'audit modules')) {
    console.log(`${tag} FAIL modules status=${modules.status} body=${String(modules.body).slice(0, 300)}`);
    return;
  }

  // 2. The module's documents with audit activity in the range.
  const list = apiPost(
    credential,
    pathOf(Endpoints.Audit.MODULE_LOG),
    { ModuleShortCode: MODULE, ModuleShortName: MODULE === 'FinVoucher' ? 'Voucher' : MODULE, FromDate: stamp(from), ToDate: stamp(to), User: null },
    null,
    'Audit.MODULE_LOG'
  );
  auditListMs.add(list.timings.duration);
  let docs = null;
  try {
    docs = JSON.parse(list.body).result;
  } catch (e) {
    docs = null;
  }
  if (!checkEnvelope(list, 'audit documents') || !Array.isArray(docs)) {
    console.log(`${tag} FAIL documents status=${list.status} body=${String(list.body).slice(0, 300)}`);
    return;
  }

  // 3. Per-table history of the first few documents.
  let calls = 0;
  let rows = 0;
  let failed = 0;
  const picked = TABLES.length === 0 ? [] : docs.slice(0, DOCS);
  for (const doc of picked) {
    for (const table of TABLES) {
      const res = apiGet(
        credential,
        pathOf(Endpoints.Audit.HISTORY),
        { code: doc.docCode, tableName: table, moduleShortCode: MODULE, fromDate: stamp(from), toDate: stamp(to), revisionFilter: false },
        'Audit.HISTORY'
      );
      auditHistoryMs.add(res.timings.duration);
      auditHistoryCalls.add(1);
      calls++;
      if (!checkEnvelope(res, 'audit history')) {
        failed++;
        console.log(`${tag} FAIL history table=${table} doc=${doc.docCode} status=${res.status} body=${String(res.body).slice(0, 300)}`);
        continue;
      }
      try {
        rows += (JSON.parse(res.body).result || []).length;
      } catch (e) {
        failed++;
      }
    }
  }
  const outcome = failed === 0 ? 'OK' : 'FAIL';
  console.log(`${tag} ${outcome} module=${MODULE} docs=${docs.length} historyCalls=${calls} rows=${rows} ms=${Date.now() - started}`);
}
