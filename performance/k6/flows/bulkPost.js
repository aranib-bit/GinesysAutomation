import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { apiPost, apiPut, apiDelete, checkEnvelope } from '../lib/http.js';
import { pathOf } from '../lib/api.js';
import * as Endpoints from '../lib/generated/endpoints.generated.js';

// Bulk master-data writes from a Java-built data file: the Java framework builds every body (same request POJOs, environment codes and
// generated data as its own WebAPI path; see com.gsl.api.bulk.BulkDataFiles and its callers) and BULK_DATA_FILE says which endpoint to call
// and how to log it. This flow only posts them, one body per iteration. Used for party create / set-extinct (Vendor Master family, Customer,
// Others), ledger create, TDS masters, and voucher revert/release/delete (PUT/DELETE, see the data file's method).
//
// Output line (parsed by the Java side): <logTag> [VU n iter m] OK id=... status=... ms=...   (logTag e.g. CreateParty | SetExtinct | CreateLedger)
// For an update operation "created" counts the records updated.
export const bulkCreated = new Counter('bulk_created');
export const bulkFailed = new Counter('bulk_failed');

const DATA_FILE = __ENV.BULK_DATA_FILE;

const dataArr = new SharedArray('bulkData', function () {
  if (!DATA_FILE) return [];
  return [JSON.parse(open(DATA_FILE))];
});

export function bodyCount() {
  return dataArr.length === 0 || !dataArr[0].bodies ? 0 : dataArr[0].bodies.length;
}

// The record's own name for the log line: sub-ledger id, else ledger name, else the TDS master's name.
function identityOf(body) {
  if (Array.isArray(body)) return body.length > 0 ? `docCode=${body[0].docCode}` : '?';
  return body.slid || body.docName || body.BaseDocumentNumber || (body.code && body.code[0]) || (body.voucherCodes && body.voucherCodes[0]) || body.glname || body.tDSName || body.tdsSource || body.abbre || '?';
}

// For endpoints that answer without the {success,result} envelope (the TDS creates answer 201): only the HTTP status decides.
function checkStatus(res, okStatuses) {
  return check(res, { [`bulk post: status in ${okStatuses.join('/')}`]: (r) => okStatuses.indexOf(r.status) >= 0 });
}

// The voucher code of a create response, or null.
function createdCode(res) {
  try {
    const code = JSON.parse(res.body).result.code;
    return code === undefined || code === null || code === '' ? null : code;
  } catch (e) {
    return null;
  }
}

// What "success" means is set per data file (expect):
//   result-true  (default) HTTP 200, success true, result === true            - master creates
//   status       only the HTTP status (okStatuses, default 200)               - endpoints without the envelope (TDS creates answer 201)
//   result-only  HTTP 200, result === true (no success key)                   - doc scheme create answers {"result":true} only
//   voucher-created HTTP 200, success true, result.code present              - voucher create (the code is logged as code=<vchcode>)
//   action-items HTTP 200, success true, every result[].item3 is "success"   - voucher revert/release/delete: a refusal is still HTTP 200
function isSuccess(res, data) {
  const expect = data.expect || 'result-true';
  if (expect === 'status') return checkStatus(res, data.okStatuses || [200]);
  if (expect === 'success-only') {
    return checkEnvelope(res, 'bulk post');
  }
  if (expect === 'result-only') {
    return check(res, {
      'bulk post: status 200': (r) => r.status === 200,
      'bulk post: result true': (r) => {
        try {
          return JSON.parse(r.body).result === true;
        } catch (e) {
          return false;
        }
      },
    });
  }
  if (expect === 'voucher-created') {
    return checkEnvelope(res, 'bulk post', {
      'bulk post: voucher code returned': (r) => !!createdCode(r),
    });
  }
  if (expect === 'action-items') {
    return checkEnvelope(res, 'bulk post', {
      'bulk post: every item succeeded': (r) => {
        try {
          const items = JSON.parse(r.body).result;
          return Array.isArray(items) && items.length > 0 && items.every((i) => String(i.item3).trim().toLowerCase() === 'success');
        } catch (e) {
          return false;
        }
      },
    });
  }
  return checkEnvelope(res, 'bulk post', {
    'bulk post: result true': (r) => {
      try {
        return JSON.parse(r.body).result === true;
      } catch (e) {
        return false;
      }
    },
  });
}

export function runBulkPostFlow(credential, bodyIndex) {
  const data = dataArr[0];
  if (!data) throw new Error('Set BULK_DATA_FILE (a file written by the Java side) - refusing to invent data.');
  const endpoint = Endpoints[data.endpointGroup][data.endpoint];
  if (!endpoint) throw new Error(`Unknown endpoint ${data.endpointGroup}.${data.endpoint} - regenerate lib/generated/endpoints.generated.js (K6ContractTest).`);
  const body = data.bodies[bodyIndex];
  const started = Date.now();
  const send = { POST: apiPost, PUT: apiPut, DELETE: apiDelete }[(data.method || 'POST').toUpperCase()];
  if (!send) throw new Error(`Unsupported method ${data.method} in the data file (POST, PUT or DELETE).`);
  const res = send(credential, pathOf(endpoint), body, null, `${data.endpointGroup}.${data.endpoint}`);
  const ok = isSuccess(res, data);
  const tag = `${data.logTag || 'BulkPost'} [VU ${__VU} iter ${__ITER}]`;
  if (ok) {
    bulkCreated.add(1);
    const code = (data.expect === 'voucher-created') ? ` code=${createdCode(res)}` : '';
    console.log(`${tag} OK id=${identityOf(body)} status=${res.status}${code} ms=${Date.now() - started}`);
  } else {
    bulkFailed.add(1);
    console.log(`${tag} FAIL id=${identityOf(body)} status=${res.status} body=${String(res.body).slice(0, 300)}`);
  }
}

export function currentBodyIndex() {
  return exec.scenario.iterationInTest;
}
