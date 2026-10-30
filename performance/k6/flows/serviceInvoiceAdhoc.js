import { SharedArray } from 'k6/data';
import { apiGet, apiPost, checkEnvelope, sessionId } from '../lib/http.js';
import { businessDataFileName, apiPrefix } from '../lib/config.js';

const businessDataArr = new SharedArray('businessData', function () {
  return [JSON.parse(open(`../data/business/${businessDataFileName}`))];
});
const d = businessDataArr[0];

function workDate() {
  return `${new Date().toISOString().slice(0, 10)}T00:00:00`;
}

function saveGridState(credential, stateKey) {
  const res = apiPost(
    credential,
    `${apiPrefix}/GridState/SaveState`,
    { StateKey: stateKey, StateValue: JSON.stringify({ ts: Date.now() }) },
    null,
    'GridState/SaveState'
  );
  checkEnvelope(res, 'SaveState');
}

// Replays the 14-endpoint Service Invoice Adhoc voucher-creation journey
// captured in the HAR, in the same order, ending in a real Save transaction.
export function runServiceInvoiceAdhocFlow(credential, iterationTag) {
  const date = workDate();
  const dc = () => Date.now();

  // 1. UDF metadata - double-slash in the path is exactly what the app sends
  let res = apiGet(
    credential,
    `${apiPrefix}//udf/GetUDFMetaData`,
    { tableName: d.udf.tableName, lookupData: d.udf.lookupData || '', _dc: dc() },
    'udf/GetUDFMetaData'
  );
  checkEnvelope(res, 'GetUDFMetaData');

  // 2. Report layout list
  res = apiGet(
    credential,
    `${apiPrefix}/ReportLayout/GetListOfReportlayoutInOrder`,
    { menuCode: d.reportLayout.menuCode, _dc: dc() },
    'ReportLayout/GetListOfReportlayoutInOrder'
  );
  checkEnvelope(res, 'GetListOfReportlayoutInOrder');

  // 3. Owner site defaults
  res = apiGet(
    credential,
    `${apiPrefix}/ServiceInvoice/GetOwnerSiteOtherDetails`,
    { ownerSiteCode: d.ownerSiteCode, _dc: dc() },
    'ServiceInvoice/GetOwnerSiteOtherDetails'
  );
  checkEnvelope(res, 'GetOwnerSiteOtherDetails');

  // 4. Initial services list (grid load)
  res = apiPost(
    credential,
    `${apiPrefix}/ServiceInvoice/GetServices`,
    {
      srvDate: date,
      tdsTypeCode: null,
      tdsSection: null,
      queryFieldName: ['servicename', 'hsN_SAC_CODE', 'glname', 'slname', 'section', 'tdsagency', 'gsT_ITC_APPL_DIS'],
      availableSites: d.availableSites,
      connectedSite: d.connectedSite,
      workDate: date,
      connectedSiteOUCode: d.connectedSiteOUCode,
      connectedSiteType: d.connectedSiteType,
      page: 1,
      start: 0,
      limit: 50,
      sort: '[{"property":"servicename","direction":"ASC"}]',
    },
    null,
    'ServiceInvoice/GetServices'
  );
  checkEnvelope(res, 'GetServices (initial)');

  // 5. Doc scheme list
  res = apiGet(
    credential,
    `${apiPrefix}/RT/GetAdmSchemeDocList`,
    {
      siteCode: d.siteCode,
      menushcode: d.docScheme.menushcode,
      pagingNotRequired: true,
      isExcludeMannual: false,
      gstApplicability: d.docScheme.gstApplicability || '',
      workDate: date,
      page: 1,
      start: 0,
      limit: 25,
    },
    'RT/GetAdmSchemeDocList'
  );
  checkEnvelope(res, 'GetAdmSchemeDocList');

  // 6-7. Vendor picker grid/window state, then vendor search
  res = apiGet(credential, `${apiPrefix}/GridState/GetStateByValue`, { stateKey: d.gridStateKeys[0], _dc: dc() }, 'GridState/GetStateByValue');
  checkEnvelope(res, `GetStateByValue (${d.gridStateKeys[0]})`);
  res = apiGet(credential, `${apiPrefix}/GridState/GetStateByValue`, { stateKey: d.gridStateKeys[1], _dc: dc() }, 'GridState/GetStateByValue');
  checkEnvelope(res, `GetStateByValue (${d.gridStateKeys[1]})`);

  res = apiPost(
    credential,
    `${apiPrefix}/ServiceInvoice/GetVendors`,
    {
      query: null,
      hideTotalCount: false,
      queryFieldName: d.vendor.queryFieldName,
      connectedSite: d.connectedSite,
      workDate: date,
      connectedSiteOUCode: d.connectedSiteOUCode,
      connectedSiteType: d.connectedSiteType,
      pagingNotRequired: false,
      searchText: null,
      page: 1,
      start: 0,
      limit: 50,
      sort: '[{"property":"name","direction":"ASC"}]',
    },
    { ownerSiteCode: d.ownerSiteCode, ouCode: d.ouCode },
    'ServiceInvoice/GetVendors'
  );
  checkEnvelope(res, 'GetVendors');

  saveGridState(credential, 'psiaVendor');

  // 8. Vendor/term/TDS details after vendor + date selection
  res = apiGet(
    credential,
    `${apiPrefix}/ServiceInvoice/GetOtherDetails`,
    {
      _dc: dc(),
      ownerSiteCode: d.ownerSiteCode,
      ownerOuCode: d.otherDetails.ownerOuCode,
      vendorCode: d.vendor.vendorCode,
      transactionDate: date,
      serviceType: d.otherDetails.serviceType,
    },
    'ServiceInvoice/GetOtherDetails'
  );
  checkEnvelope(res, 'GetOtherDetails');

  // 9. Reference number validation - unique per iteration, avoids duplicate-invoice
  // rejection. GST rules cap this at 16 characters. (__VU, __ITER) alone already
  // guarantees uniqueness within a run - encode both fully in base36 (no lossy
  // prefix slicing, which previously collided e.g. iteration 10 vs 100) and let
  // the timestamp add uniqueness across separate runs.
  const referenceNo = `P${Date.now().toString(36)}${__VU.toString(36)}${Number(iterationTag).toString(36)}`.slice(0, 16);
  res = apiPost(
    credential,
    `${apiPrefix}/ServiceInvoice/ValidateReferenceNo`,
    {
      ownerSiteCode: d.ownerSiteCode,
      vendorCode: d.vendor.vendorCode,
      invoiceDate: date,
      referenceNo: referenceNo,
      invoiceCode: d.referenceValidation.invoiceCode,
    },
    null,
    'ServiceInvoice/ValidateReferenceNo'
  );
  checkEnvelope(res, 'ValidateReferenceNo');

  // 10-11. Service-name picker grid/window state, then service line search
  res = apiGet(credential, `${apiPrefix}/GridState/GetStateByValue`, { stateKey: d.gridStateKeys[2], _dc: dc() }, 'GridState/GetStateByValue');
  checkEnvelope(res, `GetStateByValue (${d.gridStateKeys[2]})`);
  res = apiGet(credential, `${apiPrefix}/GridState/GetStateByValue`, { stateKey: d.gridStateKeys[3], _dc: dc() }, 'GridState/GetStateByValue');
  checkEnvelope(res, `GetStateByValue (${d.gridStateKeys[3]})`);

  res = apiPost(
    credential,
    `${apiPrefix}/ServiceInvoice/GetServices`,
    {
      query: null,
      hideTotalCount: false,
      queryFieldName: ['servicename', 'hsN_SAC_CODE', 'glname', 'slname', 'tdsname', 'section', 'tdsagency', 'gsT_ITC_APPL_DIS'],
      connectedSite: d.connectedSite,
      workDate: date,
      connectedSiteOUCode: d.connectedSiteOUCode,
      connectedSiteType: d.connectedSiteType,
      pagingNotRequired: false,
      srvDate: date,
      tdsTypeCode: null,
      tdsSection: null,
      searchText: null,
      page: 1,
      start: 0,
      limit: 50,
      sort: '[{"property":"tdsname","direction":"ASC"}]',
    },
    null,
    'ServiceInvoice/GetServices'
  );
  checkEnvelope(res, 'GetServices (line item)');

  saveGridState(credential, 'serinvAdhoc_grd_servicename');

  // 12. GL validity check for the chosen service line's GL code
  res = apiGet(
    credential,
    `${apiPrefix}/ServiceInvoice/IsLedgerTaggedInFINGL_SITE`,
    { _dc: dc(), glCode: d.service.glCode, ownerSiteCode: d.ownerSiteCode },
    'ServiceInvoice/IsLedgerTaggedInFINGL_SITE'
  );
  checkEnvelope(res, 'IsLedgerTaggedInFINGL_SITE');

  // 13. Server-computed charges/tax for the entered line
  const lineForCalc = {
    code: 0,
    serviceCode: d.service.serviceCode,
    serviceAmount: d.service.srvAmt,
    recordIndex: 0,
    serviceCharges: [],
    glCode: d.service.glCode,
    slCode: null,
    quantity: 0,
    gstItcAppl: d.service.gstItcAppl,
  };
  res = apiPost(
    credential,
    `${apiPrefix}/ServiceInvoice/CalculateServiceCharges`,
    { services: [lineForCalc], applicableItems: [], chargeData: [], skipChargeCode: [] },
    {
      docSchemeCode: d.calculateServiceCharges.docSchemeCode,
      vendorCode: d.vendor.vendorCode,
      siteCode: d.siteCode,
      tranDate: date,
      tradeGroupCode: d.calculateServiceCharges.tradeGroupCode,
      termCode: d.calculateServiceCharges.termCode,
      formCode: d.calculateServiceCharges.formCode,
      isExciseApplicable: d.calculateServiceCharges.isExciseApplicable,
      exchangeRate: d.calculateServiceCharges.exchangeRate,
    },
    'ServiceInvoice/CalculateServiceCharges'
  );
  checkEnvelope(res, 'CalculateServiceCharges');

  // 14. TDS rate/amount for the selected service + vendor
  const lineForTds = {
    setIndex: 0,
    index: 0,
    rowIndex: 0,
    code: 0,
    status: 'N',
    srvAmt: d.service.srvAmt,
    quantity: null,
    glCode: d.service.glCode,
    gstItcAppl: d.service.gstItcAppl,
    hsnSacCode: d.service.hsnSacCode,
    rate: null,
    serviceCode: d.service.serviceCode,
    slCode: null,
    taxCode: d.service.taxCode,
    tdsSection: '',
    tagSites: [
      {
        ownerSiteName: d.ownerSite.name,
        ownerSiteCode: d.ownerSiteCode,
        refSiteName: d.refSite.name,
        refSiteCode: d.siteCode,
        ownerSiteType: d.ownerSite.siteType,
        refSiteType: d.refSite.siteType,
        amount: d.service.srvAmt,
      },
    ],
    serviceName: d.service.serviceName,
    serviceCharges: d.service.serviceCharges,
  };
  res = apiPost(
    credential,
    `${apiPrefix}/ServiceInvoice/GetCalculatedTdsRateAndAmount`,
    {
      services: [lineForTds],
      tdsSourceCode: d.tdsSourceCode,
      vendorCode: d.vendor.vendorCode,
      srvDate: date,
      admOuCode: d.ouCode,
      tdsAppAmt: 0,
      srvCode: 0,
    },
    null,
    'ServiceInvoice/GetCalculatedTdsRateAndAmount'
  );
  checkEnvelope(res, 'GetCalculatedTdsRateAndAmount');

  // 15. Save - the core transaction. Uses the known-good charge/header
  // structure captured in the HAR (rather than parsing step 13's response)
  // so a Save failure can't be masked by our own tax-calc parsing bugs;
  // steps 12-14 above still exercise the real charge/TDS endpoints for load.
  const vd = d.voucherDefaults;
  const saveBody = {
    code: 0,
    admOuCode: d.ouCode,
    certificateNo: '',
    cpGstinNo: vd.cpGstinNo,
    cpGstinStateCode: vd.cpGstinStateCode,
    dataVersion: 0,
    documentDate: date,
    documentNo: referenceNo,
    dueDate: date,
    entrySource: '',
    gstAppl: d.gstAppl,
    modifiedOn: null,
    ownerSiteGstinNo: vd.ownerSiteGstinNo,
    ownerSiteGstinStateCode: vd.ownerSiteGstinStateCode,
    refPeriod: '',
    releaseStatus: '',
    releaseOn: null,
    remarks: '',
    schemeDocNo: '',
    srvDate: date,
    tdsAppl: d.tdsAppl,
    tdsSection: '',
    createdOn: null,
    type: vd.type,
    ycode: 0,
    calculateChargeOnTermForOrder: vd.calculateChargeOnTermForOrder,
    id: `Prc.view.Transaction.model.ServiceInvoiceHeaderModel-${__VU}-${iterationTag}`,
    ownerSite: d.ownerSite,
    refSite: d.refSite,
    tradeGroup: d.tradeGroup,
    term: d.term,
    pcode: d.vendor.pcode,
    tdsSource: d.tdsSource,
    tdsType: null,
    dueDateBasis: vd.dueDateBasis,
    creditDays: vd.creditDays,
    canModify: vd.canModify,
    allowAuthorize: vd.allowAuthorize,
    tdsRate: null,
    tdsAmt: null,
    tdsGlCode: null,
    tdsSlCode: null,
    errorMsg: null,
    entryMode: 'Add',
    docSchemeCode: d.docScheme.docSchemeCode,
    attachedFiles: { timestamp: 0, docType: '', docCode: 0, files: [] },
    saveAndRelease: false,
    udfDetails: [],
    services: [
      {
        setIndex: 0,
        index: 0,
        rowIndex: 0,
        code: 0,
        status: 'N',
        srvAmt: d.service.srvAmt,
        quantity: null,
        glCode: d.service.glCode,
        gstItcAppl: d.service.gstItcAppl,
        hsnSacCode: d.service.hsnSacCode,
        rate: null,
        serviceCode: d.service.serviceCode,
        slCode: null,
        taxCode: d.service.taxCode,
        tdsSection: '',
        tagSites: [
          {
            ownerSiteName: d.ownerSite.name,
            ownerSiteCode: d.ownerSiteCode,
            refSiteName: d.refSite.name,
            refSiteCode: d.siteCode,
            ownerSiteType: d.ownerSite.siteType,
            refSiteType: d.refSite.siteType,
            amount: d.service.srvAmt,
          },
        ],
        serviceName: d.service.serviceName,
        serviceCharges: d.service.serviceCharges,
      },
    ],
    headerCharges: d.headerCharges,
  };

  res = apiPost(credential, `${apiPrefix}/ServiceInvoice/Save`, saveBody, { sessionId: sessionId() }, 'ServiceInvoice/Save');
  const saveOk = checkEnvelope(res, 'Save', {
    'Save: schemeDocNo present': (r) => {
      try {
        return !!JSON.parse(r.body).result.schemeDocNo;
      } catch (e) {
        return false;
      }
    },
  });

  // Always surface the outcome so every run leaves an audit trail that can be
  // cross-checked against the UI - not just failures.
  let parsedResult = null;
  try {
    parsedResult = JSON.parse(res.body).result;
  } catch (e) {
    // leave parsedResult null, handled below
  }
  if (parsedResult) {
    console.log(
      `Save [VU ${__VU} iter ${iterationTag}] referenceNo=${referenceNo} ownerSiteCode=${d.ownerSiteCode} ` +
        `vendorCode=${d.vendor.vendorCode} -> schemeDocNo=${parsedResult.schemeDocNo} code=${parsedResult.code} ` +
        `releaseStatus=${parsedResult.releaseStatus} validationErrors=${JSON.stringify(parsedResult.validationErrors)}`
    );
  } else {
    console.log(`Save [VU ${__VU} iter ${iterationTag}] returned an unparseable response: ${res.body}`);
  }

  return res;
}
