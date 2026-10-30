import http from 'k6/http';
import { sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import { apiPost, apiPostMultipart, checkEnvelope } from '../lib/http.js';
import { ensureLoggedIn, decodeJwtClaims } from '../lib/auth.js';
import { apiPrefix, TENANT } from '../lib/config.js';
import { buildXlsx } from '../lib/xlsxBuilder.js';

const templateArr = new SharedArray('ecommerceBillingTemplate', function () {
  return [JSON.parse(open(`../data/business/${TENANT}.ecommerceBillingByBrand.json`))];
});
const t = templateArr[0];

const ROWS_PER_FILE = Number(__ENV.ROWS) || t.rowsPerFile;
const JOB_POLL_INTERVAL_SECONDS = Number(__ENV.JOB_POLL_INTERVAL_SECONDS) || 15;
const JOB_MAX_WAIT_SECONDS = Number(__ENV.JOB_MAX_WAIT_SECONDS) || 3600;
const JOB_POLL_LIMIT = Number(__ENV.JOB_POLL_LIMIT) || 200;
const TERMINAL_STATUSES = ['completed', 'failed', 'cancelled', 'error'];

const invoicesImported = new Counter('billing_import_invoices');
const jobDuration = new Trend('billing_import_job_duration', true);

const XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

function claim(claims, suffix) {
  const key = Object.keys(claims).find((k) => k === suffix || k.endsWith(`/${suffix}`));
  return key ? claims[key] : undefined;
}

function excelSerial(isoDate) {
  const [y, m, d] = isoDate.split('-').map(Number);
  return Math.floor(Date.UTC(y, m - 1, d) / 86400000) + 25569;
}

// Builds one user's workbook: the template row repeated ROWS_PER_FILE times,
// with only REFERENCE_NO varying. REFERENCE_NO = <runId>-<vu>-<iter>-<row>
// makes every invoice reference unique across users, iterations and runs.
function buildWorkbook(runId, vu, iter, workDateIso) {
  const columns = t.columns.map((c) => ({ name: c.name, kind: c.kind === 'empty' ? 'string' : c.kind }));
  const base = t.columns.map((c) => c.value);
  const refIdx = t.columns.findIndex((c) => c.name === t.referenceNoColumn);
  const saleSerial = excelSerial(workDateIso);
  t.saleDateColumns.forEach((name) => {
    const i = t.columns.findIndex((c) => c.name === name);
    if (i >= 0) base[i] = saleSerial;
  });

  const rows = [];
  for (let n = 1; n <= ROWS_PER_FILE; n++) {
    const row = base.slice();
    row[refIdx] = `${runId}-${vu}-${iter}-${n}`;
    rows.push(row);
  }
  const refs = { first: rows[0][refIdx], last: rows[rows.length - 1][refIdx] };
  return { bytes: buildXlsx({ sheetName: t.fileLabel, columns, rows }), refs };
}

function findJob(res, jobId) {
  try {
    const list = JSON.parse(res.body).result || [];
    return list.find((j) => j.jobid === jobId);
  } catch (e) {
    return undefined;
  }
}

// Replays the "E-commerce - Billing by Brand" import journey for the calling
// VU's own user: upload own generated xlsx -> map columns -> stage -> validate
// -> create job -> wait for the async import job to finish.
export function runImportEcommerceBillingFlow(credential, runId, iter) {
  const auth = ensureLoggedIn(credential);
  const claims = decodeJwtClaims(auth.accessToken2);
  const userId = claim(claims, 'nameidentifier');
  const roleId = claim(claims, 'roleId');
  if (!userId || !roleId) {
    throw new Error(`Could not read userId/roleId from JWT claims for ${credential.username}`);
  }

  const workDateIso = new Date().toISOString().slice(0, 10);
  const workDate = workDateIso;
  const workDateTs = `${workDateIso}T00:00:00`;

  const { bytes, refs } = buildWorkbook(runId, __VU, iter, workDateIso);
  const fileName = `${t.fileLabel}_${ROWS_PER_FILE}.xlsx`;

  // 1. upload
  let res = apiPostMultipart(
    credential,
    `${apiPrefix}/import/getuploadedfile`,
    {
      fileName: http.file(bytes, fileName, XLSX_MIME),
      userId: String(userId),
      roleId: String(roleId),
      sheetNo: t.sheetNo,
      className: t.className,
    },
    'import/getuploadedfile'
  );
  if (!checkEnvelope(res, 'getuploadedfile')) {
    throw new Error(`getuploadedfile failed for ${credential.username}: HTTP ${res.status} ${res.body}`);
  }
  const uploaded = JSON.parse(res.body).result;
  const uploadId = uploaded.id;
  // The app now requires the uploaded file's name on the next two calls (a missing
  // fileName makes getsamplewithclass fail with "stringToEscape" null -> doimport 500).
  const uploadedFileName = uploaded.fileName || fileName;

  // 2. column metadata for the uploaded sheet
  res = apiPost(
    credential,
    `${apiPrefix}/import/getsamplewithclass`,
    {},
    { id: uploadId, header: 'true', sheetNo: t.sheetNo, className: t.className, fileName: uploadedFileName, _buster: Date.now() },
    'import/getsamplewithclass'
  );
  checkEnvelope(res, 'getsamplewithclass');

  // 3. map + stage
  res = apiPost(
    credential,
    `${apiPrefix}/import/doimport`,
    {
      id: uploadId,
      header: true,
      sheetNo: t.sheetNo,
      startLineNo: 1,
      className: t.className,
      connectedSite: t.connectedSite,
      workDate,
      coreFeatureExpiryDate: null,
      formParam: t.formParam,
      extraInfo: '',
      connectedOUCode: t.connectedOUCode,
      fileName: uploadedFileName,
      map: t.map,
    },
    null,
    'import/doimport'
  );
  if (!checkEnvelope(res, 'doimport')) {
    throw new Error(`doimport failed for ${credential.username}: HTTP ${res.status} ${res.body}`);
  }
  const imp = JSON.parse(res.body).result;
  if (!imp.noError) {
    throw new Error(`doimport reported row errors for ${credential.username}: ${JSON.stringify(imp.resultData).slice(0, 1500)}`);
  }

  // 4. validate staged data
  res = apiPost(
    credential,
    `${apiPrefix}/import/validatestaggingdata`,
    {
      userId: String(userId),
      className: t.className,
      connectedSite: t.connectedSite,
      workDate: workDateTs,
      connectedOUCode: t.connectedOUCode,
      formParam: t.formParam,
      fileName: imp.fileName,
      fileHash: imp.fileHash,
      processingMode: imp.processingMode,
    },
    null,
    'import/validatestaggingdata'
  );
  checkEnvelope(res, 'validatestaggingdata');

  // 5. create the async import job
  res = apiPost(
    credential,
    `${apiPrefix}/Import/createjobfromstaging`,
    {
      className: t.className,
      fileName: imp.fileName,
      fileHash: imp.fileHash,
      spParams: {
        p_userid: Number(userId),
        p_connect_site: Number(t.connectedSite),
        p_logging_date: workDateTs,
        p_connect_ou: t.connectedOUCode,
      },
    },
    null,
    'import/createjobfromstaging'
  );
  if (!checkEnvelope(res, 'createjobfromstaging')) {
    throw new Error(`createjobfromstaging failed for ${credential.username}: HTTP ${res.status} ${res.body}`);
  }
  const jobId = JSON.parse(res.body).result.jobId;

  // 6. wait for the job (1000 invoices took ~25 min on the server)
  const started = Date.now();
  let job;
  while ((Date.now() - started) / 1000 < JOB_MAX_WAIT_SECONDS) {
    sleep(JOB_POLL_INTERVAL_SECONDS);
    res = apiPost(credential, `${apiPrefix}/import/jobs`, { page: 1, start: 0, limit: JOB_POLL_LIMIT }, null, 'import/jobs');
    job = findJob(res, jobId);
    if (job && TERMINAL_STATUSES.indexOf(String(job.status).toLowerCase()) >= 0) break;
  }
  jobDuration.add(Date.now() - started);

  const status = job ? job.status : 'Unknown(timeout)';
  const completed = String(status).toLowerCase() === 'completed';
  if (completed) invoicesImported.add(job.processedrows || ROWS_PER_FILE);

  // one machine-readable line per upload - groupable per user afterwards
  console.log(
    JSON.stringify({
      event: 'billingImport',
      user: credential.username,
      vu: __VU,
      iter,
      runId,
      rows: ROWS_PER_FILE,
      firstRef: refs.first,
      lastRef: refs.last,
      jobId,
      status,
      processedRows: job ? job.processedrows : null,
      error: job ? job.errormessage : null,
    })
  );

  if (!completed) {
    throw new Error(`Import job ${jobId} for ${credential.username} ended as ${status}: ${job ? job.errormessage : 'no job row found'}`);
  }
}
