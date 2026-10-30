import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import { Counter } from 'k6/metrics';
import { apiPost } from '../lib/http.js';
import { pathOf } from '../lib/api.js';
import * as Endpoints from '../lib/generated/endpoints.generated.js';

// Bulk cheque-book creation for a bank ledger. The Java side (com.gsl.api.bulk.ChequeBookBulkDataFile) builds, per book, a list of candidate
// ranges with ready-made request bodies (the same factory the Java ChequeBookService uses); this flow does the search the service does:
// for each candidate range ask duplicatecheques, and when it is free create the book; a create refused as a duplicate (another session took
// the range between the two calls) moves on to the next candidate. Any other failure stops that book and is reported.
//
// Output line: CreateChequeBook [VU n iter m] OK book=... start=... attempt=... ms=...
export const chequeBooksCreated = new Counter('bulk_created');
export const chequeBooksFailed = new Counter('bulk_failed');

const DATA_FILE = __ENV.BULK_DATA_FILE;

const dataArr = new SharedArray('chequeBookData', function () {
  if (!DATA_FILE) return [];
  return [JSON.parse(open(DATA_FILE))];
});

export function chequeBookCount() {
  return dataArr.length === 0 || !dataArr[0].books ? 0 : dataArr[0].books.length;
}

export function currentBookIndex() {
  return exec.scenario.iterationInTest;
}

function isDuplicateConflict(res) {
  return (res.status === 400 || res.status === 409) && String(res.body).toLowerCase().indexOf('duplicate') >= 0;
}

export function runCreateChequeBookFlow(credential, bookIndex) {
  const data = dataArr[0];
  if (!data) throw new Error('Set BULK_DATA_FILE (a file written by the Java ChequeBookBulkDataFile) - refusing to invent cheque ranges.');
  const tag = `${data.logTag || 'CreateChequeBook'} [VU ${__VU} iter ${__ITER}]`;
  const started = Date.now();
  const attempts = data.books[bookIndex].attempts;
  for (let a = 0; a < attempts.length; a++) {
    const attempt = attempts[a];
    const dup = apiPost(credential, pathOf(Endpoints.Cheque.DUPLICATES), attempt.duplicateCheck, null, 'Cheque.DUPLICATES');
    let used;
    try {
      used = JSON.parse(dup.body).result;
    } catch (e) {
      used = null;
    }
    if (dup.status !== 200 || !Array.isArray(used)) {
      chequeBooksFailed.add(1);
      console.log(`${tag} FAIL duplicatecheques status=${dup.status} attempt=${a} body=${String(dup.body).slice(0, 300)}`);
      return;
    }
    if (used.length > 0) continue; // range occupied: next candidate

    const res = apiPost(credential, pathOf(Endpoints.Cheque.CREATE), attempt.create, null, 'Cheque.CREATE');
    if (isDuplicateConflict(res)) continue; // taken between the check and the create
    let book = null;
    try {
      book = JSON.parse(res.body).result.item2;
    } catch (e) {
      book = null;
    }
    if ((res.status === 200 || res.status === 201) && book) {
      chequeBooksCreated.add(1);
      console.log(`${tag} OK book=${book} start=${attempt.start} attempt=${a} ms=${Date.now() - started}`);
    } else {
      chequeBooksFailed.add(1);
      console.log(`${tag} FAIL create status=${res.status} start=${attempt.start} attempt=${a} body=${String(res.body).slice(0, 300)}`);
    }
    return;
  }
  chequeBooksFailed.add(1);
  console.log(`${tag} FAIL no free range among ${attempts.length} candidate(s) for glCode=${data.glCode}`);
}
