import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import { apiGet, apiPost } from '../lib/http.js';
import { apiPrefix, TENANT } from '../lib/config.js';
import { ensureLoggedIn, decodeJwtClaims } from '../lib/auth.js';

// Lean "create one voucher" flows used ONLY to fill a dedicated test doc scheme
// to its cap (scenarios/backfill/fillDocScheme.js) - driven by the Selenium framework's
// DocSchemeFiller, which decides how many are needed and verifies the result
// from the DB. Three voucher types, selected by VOUCHER_TYPE: GV, AR, AP.
//
// Deliberately NOT a faithful UI replay (unlike flows/createGeneralVoucher.js):
// no attachment uploads (that flow ships ~10MB per VU), no balance/narration/
// ledger-picker GETs. Per-voucher cost is just the create call; everything that
// does not change between iterations (scheme code, cash/bank ledger, sub-ledger,
// reference site, UDF metadata, voucher-date validation) is resolved ONCE per VU.
//
//   GV  - request shape from flows/createGeneralVoucher.js (proven live), minus attachments
//         (UNVERIFIED: that the server accepts a General Voucher with an empty attachment list).
//   AR/AP - ported from the Selenium framework's ArVoucherApiClient / ApVoucherApiClient
//         (proven live there); the JSON below is a Jackson dump of those request models.
//
// Output (parsed by DocSchemeFiller):
//   FillVoucher [VU n iter m] OK   type=AR docNo=<scheme number> status=200 ms=345
//   FillVoucher [VU n iter m] FAIL type=AR status=400 ms=120 msg=<first 300 chars of body>
//   K6_SUMMARY created=<n> failed=<n>      (see scenarios/backfill/fillDocScheme.js)

const financeApiPrefix = '/Finance/api';
const SITE_TYPE = 'OS-OO-CM';
const FILL_AMOUNT = Number(__ENV.FILL_AMOUNT) || 100;
const DOC_SCHEME_NAME = __ENV.DOC_SCHEME_NAME || '';
const DOC_SCHEME_CODE_OVERRIDE = Number(__ENV.DOC_SCHEME_CODE) || 0;
const MAX_CONSECUTIVE_FAILURES = Number(__ENV.FILL_MAX_CONSECUTIVE_FAILURES) || 5;

export const fillCreated = new Counter('fill_created');
export const fillFailed = new Counter('fill_failed');

// Site / OU / GV ledgers - same tenant business-data file the GV flow reads.
const businessDataArr = new SharedArray('fillDocSchemeBusinessData', function () {
  return [JSON.parse(open(`../data/business/${TENANT}.generalVoucher.json`))];
});
const d = businessDataArr[0];

const MENU = { GV: 'FinanceGenVoucher', AR: 'FinanceARVoucher', AP: 'FinanceAPVoucher' };
const MODULE = { GV: 'generalvoucher', AR: 'arvoucher', AP: 'apvoucher' };

// Per-VU state (each VU has its own module instance): resolved refs per voucher type + failure streak.
const refsCache = {};
let consecutiveFailures = 0;

function workDate() {
  return `${new Date().toISOString().slice(0, 10)}T00:00:00`;
}

// Same claim lookup as flows/createGeneralVoucher.js (kept local: importing that
// module would make every VU read its ~10MB attachments at init time).
function resolveEmpCode(credential) {
  const auth = ensureLoggedIn(credential);
  const claims = decodeJwtClaims(auth.accessToken2);
  const keys = ['http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier', 'EmpCode', 'empCode', 'nameid', 'sub'];
  for (const key of keys) {
    const val = claims[key];
    if (val !== undefined && val !== null && !isNaN(Number(val))) return Number(val);
  }
  throw new Error(`resolveEmpCode: no employee-code claim on the token (tried ${keys.join(', ')}); claims: ${JSON.stringify(claims)}`);
}

function parseItems(res, label) {
  if (res.status !== 200) throw new Error(`${label}: HTTP ${res.status} - ${String(res.body).slice(0, 300)}`);
  try {
    return JSON.parse(res.body).result.items || [];
  } catch (e) {
    throw new Error(`${label}: unparseable response - ${String(res.body).slice(0, 300)}`);
  }
}

// docnumberschemes -> code of the scheme named DOC_SCHEME_NAME (or DOC_SCHEME_CODE if given).
function resolveSchemeCode(credential, type, date) {
  if (DOC_SCHEME_CODE_OVERRIDE) return DOC_SCHEME_CODE_OVERRIDE;
  if (!DOC_SCHEME_NAME) throw new Error('Set DOC_SCHEME_NAME (or DOC_SCHEME_CODE) - refusing to guess which doc scheme to fill.');
  // casing of "connectedsite" follows the captured HARs (AR: connectedSite, GV/AP: connectedsite)
  const siteKey = type === 'AR' ? 'connectedSite' : 'connectedsite';
  const res = apiGet(
    credential,
    `${financeApiPrefix}/docnumberschemes`,
    { [siteKey]: d.connectedSiteCode, menusysshortcode: MENU[type], pagingNotRequired: true, workDate: date, page: 1, start: 0, limit: 25 },
    'Finance/docnumberschemes'
  );
  const items = parseItems(res, 'docnumberschemes');
  const scheme = items.find((s) => String(s.name).toLowerCase() === DOC_SCHEME_NAME.toLowerCase());
  if (!scheme) {
    throw new Error(
      `doc scheme '${DOC_SCHEME_NAME}' is not offered for ${MENU[type]} (offered: ${items.map((s) => s.name).join(', ')}) - ` +
        'is it created, tagged to the menu and tagged to the owner site?'
    );
  }
  if (scheme.manual && String(scheme.manual).toUpperCase() === 'Y') {
    throw new Error(`doc scheme '${DOC_SCHEME_NAME}' is a manual-numbering scheme - the filler only supports auto-numbered schemes.`);
  }
  return scheme.code;
}

function fetchUdfValues(credential, label) {
  const res = apiGet(credential, `${apiPrefix}/udf/GetUDFMetaData`, { _dc: Date.now(), tableName: 'FINVCHMAIN', lookupData: true }, 'WebAPI/udf/GetUDFMetaData');
  let meta;
  try {
    meta = JSON.parse(res.body).result.metaData;
  } catch (e) {
    throw new Error(`${label}: GetUDFMetaData unusable - ${String(res.body).slice(0, 300)}`);
  }
  const mandatory = meta.filter((m) => m.isMandatory);
  if (mandatory.length > 0) {
    throw new Error(`${label}: FINVCHMAIN has mandatory UDF(s) ${mandatory.map((m) => m.columnName).join(', ')} - the filler does not generate UDF values.`);
  }
  return meta.map((m) => {
    const datatype = (m.datatype || '').toLowerCase();
    return { fieldName: m.columnName.toLowerCase(), value: datatype === 'string' || datatype === 'memo' ? '' : null, datatype };
  });
}

function fetchCashBank(credential, type, empCode, date) {
  const res = apiGet(
    credential,
    `${financeApiPrefix}/ledgers/cashbankledgers?query=&searchText=&queryFieldName=glName&queryFieldName=abbreviation` +
      `&connectedSite=${d.connectedSiteCode}&connectedUserCode=${empCode}&module=${MODULE[type]}&workDate=${encodeURIComponent(date)}` +
      `&connectedOUCode=${d.connectedSiteOUCode}&connectedSiteType=${SITE_TYPE}&pagingNotRequired=false&page=1&start=0&limit=50&sort=glName%20ASC`,
    null,
    'Finance/ledgers/cashbankledgers'
  );
  const items = parseItems(res, 'cashbankledgers');
  if (items.length === 0) throw new Error(`cashbankledgers returned nothing for site ${d.connectedSiteCode}`);
  return items[0];
}

function fetchSubLedger(credential, type, empCode, date) {
  const res = apiGet(
    credential,
    `${financeApiPrefix}/subledgers?searchText=&module=${MODULE[type]}&connectedOUCode=${d.connectedSiteOUCode}&connectedUserCode=${empCode}` +
      `&queryFieldName=slName&queryFieldName=className&connectedSite=${d.connectedSiteCode}&workDate=${encodeURIComponent(date)}` +
      `&connectedSiteOUCode=${d.connectedSiteOUCode}&connectedSiteType=${SITE_TYPE}&pagingNotRequired=true&page=1&start=0&limit=25`,
    null,
    'Finance/subledgers'
  );
  let items = parseItems(res, 'subledgers');
  // AP voucher here is the on-account "Customer Advance" shape (ApVoucherApiClient.resolveSubLedger): Customer-classified SLs only.
  if (type === 'AP') items = items.filter((sl) => sl.classType === 'C');
  if (items.length === 0) throw new Error(`no usable sub-ledger for ${type} voucher at site ${d.connectedSiteCode}`);
  return items[0];
}

function fetchReferenceSite(credential, type, sl, date) {
  const res = apiGet(
    credential,
    `${financeApiPrefix}/sites?target=referencesite&module=${MODULE[type]}&connectedOUCode=${d.connectedSiteOUCode}&slCode=${sl.slCode}` +
      (type === 'AP' ? `&glCode=${sl.glCode}` : '') +
      `&queryFieldName=name&queryFieldName=siteType&queryFieldName=ouName&connectedSite=${d.connectedSiteCode}` +
      `&workDate=${encodeURIComponent(date)}&connectedSiteOUCode=${d.connectedSiteOUCode}&connectedSiteType=${SITE_TYPE}&page=1&start=0&limit=500`,
    null,
    'Finance/sites/referencesite'
  );
  const items = parseItems(res, 'sites?target=referencesite');
  return items.find((s) => s.code === d.connectedSiteCode) || items[0] || null;
}

function validateVoucherDate(credential, type, empCode, date) {
  const path = type === 'GV' ? `${financeApiPrefix}/vouchers/general/validations/voucherdate` : `${financeApiPrefix}/vouchers/${type.toLowerCase()}/validations/voucherdate`;
  const res = apiPost(
    credential,
    path,
    { OldYCODE: 0, VoucherDate: date, EmpCode: empCode, VoucherCode: 0, IsNewEntity: true, ConnectedOUCode: d.connectedSiteOUCode },
    null,
    `Finance/vouchers/${type}/validations/voucherdate`
  );
  if (res.status !== 200) throw new Error(`voucherdate validation: HTTP ${res.status} - ${String(res.body).slice(0, 300)}`);
}

// Everything that is the same for every iteration of this VU - resolved once.
function resolveRefs(credential, type) {
  if (refsCache[type]) return refsCache[type];
  const date = workDate();
  const empCode = resolveEmpCode(credential);
  const refs = { empCode, schemeCode: resolveSchemeCode(credential, type, date) };
  refs.cashBank = fetchCashBank(credential, type, empCode, date);
  if (type === 'AR' || type === 'AP') {
    refs.subLedger = fetchSubLedger(credential, type, empCode, date);
    refs.refSite = fetchReferenceSite(credential, type, refs.subLedger, date);
  }
  refs.udf = fetchUdfValues(credential, type);
  validateVoucherDate(credential, type, empCode, date);
  refsCache[type] = refs;
  return refs;
}

// ---- request bodies -------------------------------------------------------

function gvBody(refs, date, iterationTag) {
  const cb = refs.cashBank;
  return {
    code: 0,
    empCode: refs.empCode,
    connectedOUCode: d.connectedSiteOUCode,
    ownerSite: {
      code: d.admsite.code,
      Name: d.admsite.Name,
      SiteType: d.admsite.siteType,
      ShortName: d.admsite.ShortName,
      OUName: d.admsite.OUName,
      OUCode: d.admsite.OUCode,
    },
    docScheme: { code: refs.schemeCode },
    docNumber: null,
    cashBankLedger: {
      glSiteCount: cb.glSiteCount || 0,
      slCount: cb.slCount || 0,
      glCode: cb.glCode,
      glName: cb.glName,
      abbreviation: cb.abbreviation,
      groupName: null,
      siteApplicable: null,
      siteApplicableDisplay: null,
      subLedgerApplicable: null,
      subLedgerApplicableDisplay: null,
      accountNumber: cb.accountNumber || '',
      type: cb.type,
      typeDisplay: cb.typeDisplay,
      sourceType: cb.sourceType,
      sourceTypeDisplay: cb.sourceTypeDisplay,
      chequeLabel: null,
      id: `extModel-${__VU}-${iterationTag}-cashbank`,
    },
    voucherDate: date,
    yCode: 0,
    reference: '',
    remarks: null,
    documentStatus: 'N',
    expenseIncomeDetail: [
      {
        code: 0,
        glCode: d.expenseLedger.glCode,
        glName: d.expenseLedger.glName,
        siteApplicable: d.expenseLedger.siteApplicable,
        groupName: d.expenseLedger.groupName,
        referenceNumber: null,
        slCode: null,
        slName: '',
        ledgerNarration: '',
        isReversal: 'N',
        expense: FILL_AMOUNT,
        income: 0,
        paymentMode: d.paymentMode,
        bookCode: null,
        instrumentNumber: '',
        instrumentDate: null,
        chequeLabel: '',
        drawnOn: '',
        costCenterAllocations: null,
        dataVersion: 0,
      },
    ],
    isAutoReleaseApplied: false,
    isReadOnlyInstance: false,
    IsMarkedForSaveAndRelease: false,
    requestId: String(Date.now()),
    attachment: { doccode: 0, foldername: '', Files: [] },
    udfDetails: refs.udf,
  };
}

function arBody(refs, date, requestId) {
  const sl = refs.subLedger;
  const site = refs.refSite;
  return {
    code: 0,
    docNumber: null,
    voucherDate: date,
    docSchemeCode: refs.schemeCode,
    empCode: refs.empCode,
    connectedOUCode: d.connectedSiteOUCode,
    connectedSiteCode: d.connectedSiteCode,
    ownerSiteCode: d.connectedSiteCode,
    reference: '',
    remarks: '',
    cashBankGLCode: refs.cashBank.glCode,
    arVoucherDetail: [
      {
        code: 0,
        subLedgerCode: sl.slCode,
        subLedgerName: sl.slName,
        subLedgerId: sl.slid,
        subLedgerClassName: sl.className,
        subLedgerBalance: null,
        ledgerCode: sl.glCode,
        ledgerName: sl.glName,
        ledgerGroupName: sl.generalLedgerGroupName,
        ledgerAbbreviation: sl.generalLedgerAbbre,
        referenceSiteName: site ? site.name : null,
        referenceSiteCode: site ? site.code : d.connectedSiteCode,
        referenceSiteType: null,
        referenceSiteOUName: null,
        paymentMode: 'C',
        bookCode: null,
        instrumentNumber: null,
        instrumentDate: null,
        chequeLabel: null,
        drawnOn: null,
        paymentReference: null,
        ledgerNarration: null,
        amount: FILL_AMOUNT,
        arVoucherAdjustmentDetail: null,
        dataVersion: 0,
        reversal: false,
      },
    ],
    attachment: { timestamp: 0, docType: '', docCode: 0, files: [] },
    udfValues: refs.udf,
    readOnlyInstance: false,
    autoReleaseApplied: false,
    IsMarkedForSaveAndRelease: true,
    RequestId: requestId,
  };
}

function apBody(refs, date, requestId) {
  const sl = refs.subLedger;
  const site = refs.refSite;
  return {
    code: 0,
    empCode: refs.empCode,
    connectedOUCode: d.connectedSiteOUCode,
    ownerSiteCode: d.connectedSiteCode,
    connectedSiteCode: d.connectedSiteCode,
    autoCreatedJournalDocSchemeCode: null,
    docSchemeCode: refs.schemeCode,
    docNumber: null,
    cashBankGLCode: refs.cashBank.glCode,
    voucherDate: date,
    remarks: null,
    documentStatus: 'N',
    onAccount: true,
    apVoucherDetail: [
      {
        code: null,
        subLedgerCode: sl.slCode,
        subLedgerName: sl.slName,
        ledgerCode: sl.glCode,
        generalLedgerAbbre: sl.generalLedgerAbbre || '',
        generalLedgerGroupName: sl.generalLedgerGroupName,
        referenceSiteCode: site ? site.code : d.connectedSiteCode,
        ledgerNarration: '',
        amount: FILL_AMOUNT,
        deductTDS: false,
        tdsRate: null,
        tdsTypeCode: null,
        tdsAppAmt: null,
        tdsAmt: null,
        tdsGrossAmt: null,
        tdsRoundOffAmt: null,
        netPayable: null,
        useExceptionRate: false,
        paymentMode: 'C',
        bookCode: null,
        instrumentNumber: null,
        instrumentDate: null,
        chequeLabel: null,
        paymentReference: null,
        dataVersion: 0,
        apVoucherAdjustmentDetail: null,
        reversal: false,
      },
    ],
    requestId,
    attachment: { doccode: 0, foldername: '0', Files: [] },
    udfValues: refs.udf,
    ycode: 0,
    readOnlyInstance: false,
    autoReleaseApplied: false,
    IsMarkedForSaveAndRelease: false,
  };
}

function oneLine(text) {
  return String(text || '').replace(/\s+/g, ' ').slice(0, 300);
}

// Creates ONE voucher of `type` and prints exactly one FillVoucher line. Never
// throws for a per-document failure (the run keeps going and DocSchemeFiller
// tops up from the DB); aborts the whole run after MAX_CONSECUTIVE_FAILURES in a
// row on one VU so a misconfiguration is not hammered 100s of times.
export function runFillVoucherFlow(credential, iterationTag, type) {
  const started = Date.now();
  let status = 0;
  try {
    if (!MENU[type]) throw new Error(`VOUCHER_TYPE must be one of ${Object.keys(MENU).join(', ')}, got '${type}'`);
    const refs = resolveRefs(credential, type);
    const date = workDate();
    const requestId = `${Date.now()}${__VU}${iterationTag}`;
    const path = type === 'GV' ? `${financeApiPrefix}/vouchers/general` : `${financeApiPrefix}/vouchers/${type.toLowerCase()}`;
    const body = type === 'GV' ? gvBody(refs, date, iterationTag) : type === 'AR' ? arBody(refs, date, requestId) : apBody(refs, date, requestId);

    const res = apiPost(credential, path, body, null, `Finance/vouchers/${type.toLowerCase()} (fill)`);
    status = res.status;
    let result = null;
    try {
      const parsed = JSON.parse(res.body);
      if (parsed.success !== false) result = parsed.result;
    } catch (e) {
      // handled below
    }
    if ((status === 200 || status === 201) && result && result.code) {
      fillCreated.add(1);
      consecutiveFailures = 0;
      console.log(`FillVoucher [VU ${__VU} iter ${iterationTag}] OK type=${type} docNo=${result.docScheme} status=${status} ms=${Date.now() - started}`);
      return;
    }
    throw new Error(`HTTP ${status} - ${oneLine(res.body)}`);
  } catch (e) {
    fillFailed.add(1);
    consecutiveFailures++;
    console.log(`FillVoucher [VU ${__VU} iter ${iterationTag}] FAIL type=${type} status=${status} ms=${Date.now() - started} msg=${oneLine(e.message)}`);
    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
      exec.test.abort(`fillDocScheme: VU ${__VU} had ${consecutiveFailures} consecutive failures - aborting instead of hammering the server. Last: ${oneLine(e.message)}`);
    }
  }
}
