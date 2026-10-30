import http from 'k6/http';
import { SharedArray } from 'k6/data';
import { apiGet, apiPost, apiPostMultipart, checkEnvelope } from '../lib/http.js';
import { apiPrefix, TENANT } from '../lib/config.js';
import { ensureLoggedIn, decodeJwtClaims } from '../lib/auth.js';

// Finance's own vouchers/ledgers endpoints live under a different API root
// (confirmed live via GeneralVoucherAttachmentStageIdeal.har: every
// docnumberschemes/ledgers/narrations/vouchers/OrganizationUnit call hit
// /Finance/api/..., not /WebAPI/api/... like GRC/DC/Item do) - kept local to
// this flow rather than added to lib/config.js since nothing else uses it yet.
const financeApiPrefix = '/Finance/api';

const businessDataArr = new SharedArray('generalVoucherBusinessData', function () {
  return [JSON.parse(open(`../data/business/${TENANT}.generalVoucher.json`))];
});
const d = businessDataArr[0];

// Attachment bytes are read once per VU at init time (same convention as the
// SharedArray load above) - k6 only allows file reads during the init stage,
// not inside the exported flow function while VUs are iterating. Kept as
// plain module state (not SharedArray, which is JSON-only) since these are
// binary blobs; per-VU memory cost is the tradeoff, called out in the plan's
// Risks section given Volume1.pdf alone is ~10MB.
const attachmentFiles = d.attachments.map((a) => ({
  fileName: a.fileName,
  contentType: a.contentType,
  bytes: open(a.path, 'b'),
}));

function workDate() {
  return `${new Date().toISOString().slice(0, 10)}T00:00:00`;
}

// EmpCode/connectedUserCode (50997 in the HAR) is the logged-in user's own
// employee code, not a tenant-wide constant - it has to be resolved per
// credential. Confirmed live (2026-08-20 smoke run against stage-ideal,
// logged in as aranibarman): it's carried on the WS-identity "nameidentifier"
// claim (value "50997", matching the HAR exactly), not any EmpCode-shaped key
// - createUser.js's RoleId is a separate, plain-named claim on the same
// token. Falls back to a couple of plausible plain names in case a
// differently-issued token (e.g. a future multiTenant tenant) shapes it
// differently, and fails loudly with the full claim set if none match rather
// than silently sending a wrong/undefined EmpCode into voucherdate
// validation and the create call.
function resolveEmpCode(credential) {
  const auth = ensureLoggedIn(credential);
  const claims = decodeJwtClaims(auth.accessToken2);
  const candidateKeys = ['http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier', 'EmpCode', 'empCode', 'nameid', 'sub'];
  for (const key of candidateKeys) {
    const val = claims[key];
    if (val !== undefined && val !== null && !isNaN(Number(val))) {
      return Number(val);
    }
  }
  throw new Error(
    `resolveEmpCode: could not find an employee-code claim on the logged-in user's token (tried ${candidateKeys.join(
      ', '
    )}) - actual claims were: ${JSON.stringify(claims)}`
  );
}

// Replays the General Voucher (Finance) journey: cash/bank & expense ledger
// lookups, live UDF metadata, uploading every configured attachment via the
// same shared folder token the real UI uses, voucher-date validation, then
// General Voucher/Save. Returns the created voucher's code/docScheme for
// audit, plus how many attachments the server confirmed via rootview.
export function runCreateGeneralVoucherFlow(credential, iterationTag) {
  const date = workDate();
  const dc = () => Date.now();
  const empCode = resolveEmpCode(credential);

  // 1. Doc number scheme for the General Voucher menu.
  let res = apiGet(
    credential,
    `${financeApiPrefix}/docnumberschemes`,
    { connectedsite: d.connectedSiteCode, menusysshortcode: 'FinanceGenVoucher', page: 1, limit: 25, workDate: date, start: 0 },
    'Finance/docnumberschemes'
  );
  checkEnvelope(res, 'docnumberschemes');

  // 2. Cash/bank ledger picker - queryFieldName is repeated in the real
  // request (glName + abbreviation), which apiGet's plain-object query
  // builder can't express (one JS key can't repeat) - the full query string
  // is passed as part of `path` instead, with queryParams left null.
  res = apiGet(
    credential,
    `${financeApiPrefix}/ledgers/cashbankledgers?query=&searchText=&queryFieldName=glName&queryFieldName=abbreviation` +
      `&connectedSite=${d.connectedSiteCode}&connectedUserCode=${empCode}&module=generalvoucher&workDate=${encodeURIComponent(date)}` +
      `&connectedOUCode=${d.connectedSiteOUCode}&connectedSiteType=${d.connectedSiteType}&pagingNotRequired=false&page=1&start=0&limit=50&sort=glName%20ASC`,
    null,
    'Finance/ledgers/cashbankledgers'
  );
  checkEnvelope(res, 'ledgers/cashbankledgers');

  // 3. Selected cash/bank ledger's live balance.
  res = apiGet(credential, `${financeApiPrefix}/ledgers/${d.cashBankLedger.glCode}/balance`, { _dc: dc() }, 'Finance/ledgers/balance');
  checkEnvelope(res, 'ledgers/{glCode}/balance (cash/bank)');

  // 4. Narration lookup for that ledger.
  res = apiGet(credential, `${financeApiPrefix}/narrations`, { glcode: d.cashBankLedger.glCode, workDate: date, page: 1, start: 0, limit: 25 }, 'Finance/narrations');
  checkEnvelope(res, 'narrations');

  // 5. Expense/income ledger picker - same repeated-queryFieldName shape as
  // step 2, this time with 5 field names.
  res = apiGet(
    credential,
    `${financeApiPrefix}/ledgers?searchText=&module=generalvoucher&connectedOUCode=${d.connectedSiteOUCode}&connectedUserCode=${empCode}` +
      `&queryFieldName=glName&queryFieldName=abbreviation&queryFieldName=groupName&queryFieldName=siteApplicable&queryFieldName=subLedgerApplicable` +
      `&connectedSite=${d.connectedSiteCode}&workDate=${encodeURIComponent(date)}&connectedSiteOUCode=${d.connectedSiteOUCode}` +
      `&connectedSiteType=${d.connectedSiteType}&page=1&start=0&limit=25`,
    null,
    'Finance/ledgers'
  );
  checkEnvelope(res, 'ledgers (expense/income)');

  // 6. Selected expense/income ledger's live balance.
  res = apiGet(credential, `${financeApiPrefix}/ledgers/${d.expenseLedger.glCode}/balance`, { _dc: dc() }, 'Finance/ledgers/balance');
  checkEnvelope(res, 'ledgers/{glCode}/balance (expense)');

  // 7. Live UDF metadata for FINVCHMAIN, used to build udfDetails[] below
  // ("resolve live, don't hardcode" - same convention createGrc.js uses for
  // consignment/tax-region flags) instead of a hand-copied static array.
  res = apiGet(credential, `${apiPrefix}/udf/GetUDFMetaData`, { _dc: dc(), tableName: 'FINVCHMAIN', lookupData: true }, 'WebAPI/udf/GetUDFMetaData');
  let udfDetails = [];
  try {
    const metaData = JSON.parse(res.body).result.metaData;
    udfDetails = metaData.map((m) => ({ fieldName: m.columnName.toLowerCase(), value: null, datatype: (m.datatype || '').toLowerCase() }));
  } catch (e) {
    console.log(`GetUDFMetaData [VU ${__VU} iter ${iterationTag}] did not return usable metaData - udfDetails will be empty: ${res.body}`);
  }

  // 8. Org unit info for the owner site.
  res = apiGet(credential, `${financeApiPrefix}/OrganizationUnit/admou`, { _dc: dc(), code: d.admsite.code }, 'Finance/OrganizationUnit/admou');
  checkEnvelope(res, 'OrganizationUnit/admou');

  // 9. Upload every configured attachment. All uploads share one folder
  // token (the real UI's "imagedata" field) which is then referenced as
  // attachment.foldername in the create call below - this is how the server
  // ties the just-uploaded files to the voucher it's about to create.
  // Plain Date.now() collides across concurrent VUs (1ms resolution, and all
  // VUs start together) - confirmed live: every VU failed on iteration 0
  // simultaneously with "Empty path name is not legal" from the create call
  // despite each individual upload reporting success, consistent with two
  // VUs racing on the identical server-side temp folder. Folding in __VU/
  // iterationTag guarantees uniqueness the same way uniqueSerial() does in
  // createGrc.js, regardless of timestamp collisions.
  const folderName = `${Date.now()}_vu${__VU}_i${iterationTag}`;
  attachmentFiles.forEach((file, idx) => {
    const uploadRes = apiPostMultipart(
      credential,
      `${apiPrefix}/Utility%2FUploadedDocAttachment`,
      {
        imagedata: folderName,
        filedata: '',
        fileName: http.file(file.bytes, file.fileName, file.contentType),
      },
      'WebAPI/Utility/UploadedDocAttachment'
    );
    const uploadOk = checkEnvelope(uploadRes, `UploadedDocAttachment[${idx}] ${file.fileName}`);
    if (!uploadOk) {
      // Was previously logged and allowed to fall through - the create call
      // below references every configured file by name in attachment.Files[]
      // regardless, so a silently-failed upload here reliably reappeared two
      // steps later as a confusing server-side "Supplied file path ... is
      // invalid" / "Empty path name is not legal" error out of vouchers/general,
      // instead of failing loudly at its actual point of failure. Confirmed
      // live under 5-VU load (connection resets on the upload POST itself -
      // see this iteration's own console output for the underlying HTTP
      // error) - abort here so the failure is attributed correctly.
      throw new Error(
        `UploadedDocAttachment failed for ${file.fileName} (VU ${__VU} iter ${iterationTag}): HTTP ${uploadRes.status} - ${uploadRes.body || uploadRes.error}`
      );
    }
  });

  // 10. Voucher-date validation.
  res = apiPost(
    credential,
    `${financeApiPrefix}/vouchers/general/validations/voucherdate`,
    { OldYCODE: 0, VoucherDate: date, EmpCode: empCode, IsNewEntity: true, ConnectedOUCode: d.connectedSiteOUCode },
    null,
    'Finance/vouchers/general/validations/voucherdate'
  );
  checkEnvelope(res, 'validations/voucherdate', {
    'validations/voucherdate: date accepted': (r) => {
      try {
        return JSON.parse(r.body).result === true;
      } catch (e) {
        return false;
      }
    },
  });

  // 11. Create the General Voucher itself.
  const ownerSite = {
    code: d.admsite.code,
    Name: d.admsite.Name,
    SiteType: d.admsite.siteType,
    ShortName: d.admsite.ShortName,
    OUName: d.admsite.OUName,
    OUCode: d.admsite.OUCode,
  };
  const cashBankLedger = {
    glSiteCount: d.cashBankLedger.glSiteCount,
    slCount: d.cashBankLedger.slCount,
    glCode: d.cashBankLedger.glCode,
    glName: d.cashBankLedger.glName,
    abbreviation: d.cashBankLedger.abbreviation,
    groupName: null,
    siteApplicable: null,
    siteApplicableDisplay: null,
    subLedgerApplicable: null,
    subLedgerApplicableDisplay: null,
    accountNumber: d.cashBankLedger.accountNumber,
    type: d.cashBankLedger.type,
    typeDisplay: d.cashBankLedger.typeDisplay,
    sourceType: d.cashBankLedger.sourceType,
    sourceTypeDisplay: d.cashBankLedger.sourceTypeDisplay,
    chequeLabel: null,
    id: `extModel-${__VU}-${iterationTag}-cashbank`,
  };
  const expenseIncomeDetail = [
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
      expense: d.expenseAmount,
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
  ];
  const filesArr = attachmentFiles.map((file, idx) => ({
    fileName: file.fileName,
    fileSize: file.bytes.byteLength,
    status: 'N',
    code: 0,
    progress: 0,
    uploadedBy: '',
    uploadedOn: null,
    id: `extModel-${__VU}-${iterationTag}-file${idx}`,
  }));

  const saveBody = {
    code: 0,
    empCode,
    connectedOUCode: d.connectedSiteOUCode,
    ownerSite,
    docScheme: { code: d.docSchemeCode },
    docNumber: null,
    cashBankLedger,
    voucherDate: date,
    yCode: 0,
    reference: '',
    remarks: null,
    documentStatus: 'N',
    expenseIncomeDetail,
    isAutoReleaseApplied: false,
    isReadOnlyInstance: false,
    IsMarkedForSaveAndRelease: false,
    requestId: String(Date.now()),
    attachment: { doccode: 0, foldername: folderName, Files: filesArr },
    udfDetails,
  };

  res = apiPost(credential, `${financeApiPrefix}/vouchers/general`, saveBody, null, 'Finance/vouchers/general');
  // This create call genuinely returns 201 (REST create semantics), not 200
  // - confirmed live - so checkEnvelope's default "status 200" check has to
  // be overridden here (same key name replaces it via Object.assign) rather
  // than loosened/ignored, since 201 is the correct expected status, not a
  // real failure to mask.
  const createLabel = 'vouchers/general (create)';
  const saveOk = checkEnvelope(res, createLabel, {
    [`${createLabel}: status 200`]: (r) => r.status === 201,
    [`${createLabel}: docScheme present`]: (r) => {
      try {
        return !!JSON.parse(r.body).result.docScheme;
      } catch (e) {
        return false;
      }
    },
  });

  let code = null;
  let docScheme = null;
  try {
    const result = JSON.parse(res.body).result;
    code = result.code;
    docScheme = result.docScheme;
  } catch (e) {
    // handled below
  }

  console.log(
    `CreateGeneralVoucher [VU ${__VU} iter ${iterationTag}] attachments=${attachmentFiles.length} -> code=${code} docScheme=${docScheme} saveOk=${saveOk}`
  );

  if (!code) {
    throw new Error(`vouchers/general did not return a code (VU ${__VU} iter ${iterationTag}): ${res.body}`);
  }

  // 12. Confirm the voucher + its attachments actually landed server-side.
  res = apiGet(credential, `${financeApiPrefix}/vouchers/generalvouchers/rootview`, { _dc: dc(), code, connectedOUCode: d.connectedSiteOUCode, connectedUserCode: empCode }, 'Finance/vouchers/generalvouchers/rootview');
  const rootviewOk = checkEnvelope(res, 'generalvouchers/rootview', {
    'generalvouchers/rootview: attachment count matches': (r) => {
      try {
        return JSON.parse(r.body).result.attachment.files.length === attachmentFiles.length;
      } catch (e) {
        return false;
      }
    },
  });
  if (!rootviewOk) {
    console.log(`generalvouchers/rootview [VU ${__VU} iter ${iterationTag}] attachment count mismatch or bad response: ${res.body}`);
  }

  return { code, docScheme, attachmentCount: attachmentFiles.length };
}
