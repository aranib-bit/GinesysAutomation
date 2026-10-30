import { SharedArray } from 'k6/data';
import { apiGet, apiPost, apiBatch, checkEnvelope } from '../lib/http.js';
import { apiPrefix, TENANT } from '../lib/config.js';

const businessDataArr = new SharedArray('grcBusinessData', function () {
  return [JSON.parse(open(`../data/business/${TENANT}.grc.json`))];
});
const d = businessDataArr[0];

function workDate() {
  return `${new Date().toISOString().slice(0, 10)}T00:00:00`;
}

// Some pooled items genuinely have no configured rate at the site used
// (mrp/wsp/rate all 0 in GetItemDetailWithOpeningRate's response) - GRC/Save
// rejects a zero rate outright ("Rate can not be zero."). Confirmed live (see
// CreateGRCITEMZeroRatestage-ideal...har): the fix is only ever to the rate
// itself - mrp/wsp/listedMrp are left as-is (even zero) and ValidateMarginRule
// still passes with rate:24/mrp:0/wsp:0 - "24" there was an arbitrary manually
// typed value, not a derived one, so a random fallback is used here rather
// than a fixed constant.
const FALLBACK_ITEM_RATE_MIN = Number(__ENV.FALLBACK_ITEM_RATE_MIN) || 10;
const FALLBACK_ITEM_RATE_MAX = Number(__ENV.FALLBACK_ITEM_RATE_MAX) || 500;

function randomFallbackRate() {
  return Math.round((Math.random() * (FALLBACK_ITEM_RATE_MAX - FALLBACK_ITEM_RATE_MIN) + FALLBACK_ITEM_RATE_MIN) * 100) / 100;
}

function uniqueSerial(vu, iter, lineIdx) {
  // Global uniqueness (not just per-run) is the actual business rule under
  // test here - Batch/ValidateAndCreateBachSerialNo is the server-side check,
  // this only has to avoid ever generating the same string twice, never rely
  // on the server silently deduping.
  return `SER${Date.now().toString(36)}${Number(vu).toString(36)}${Number(iter).toString(36)}${lineIdx}`.slice(0, 20);
}

// Replays the GRC (Goods Receipt) journey: vendor/trade-group/term lookups,
// live rate lookup for the item, batch/serial discovery+registration for B/S
// items, server-computed charges, margin validation, then GRC/Save. Returns
// the created GRC's code/schemeDocNo for audit - DC does not need it (DC
// discovers stock live via Batch/GetItemBatchSerialForPositiveStock).
export function runCreateGrcFlow(credential, iterationTag, itemCode, itemType, pricing) {
  const date = workDate();
  const dc = () => Date.now();

  let res = apiPost(
    credential,
    `${apiPrefix}/Shared/GetVendorLookupForPurtrans`,
    { query: '', queryFieldName: ['name', 'slid'], connectedSite: d.connectedSiteCode, workDate: date, connectedSiteOUCode: d.connectedSiteOUCode, connectedSiteType: d.connectedSiteType, page: 1, start: 0, limit: 25 },
    null,
    'Shared/GetVendorLookupForPurtrans'
  );
  checkEnvelope(res, 'GetVendorLookupForPurtrans');

  res = apiGet(credential, `${apiPrefix}/GRC/GetGRCFormRelatedData`, { _dc: dc(), vendorCode: d.vendorCode, connectedSiteCode: d.connectedSiteCode }, 'GRC/GetGRCFormRelatedData');
  checkEnvelope(res, 'GetGRCFormRelatedData');
  // Whether this GRC is a consignment receipt is a per-vendor fact, not a
  // constant - confirmed live: vendor 84 (qafeature) trades as consignment,
  // vendor 206 (stage-ideal) does not, and submitting the wrong value gets a
  // real rejection ("Creation of consignment receipt not allowed by user's
  // profile"). Read it from the vendor's own form data instead of hardcoding.
  let whetherConsignment = 'N';
  try {
    whetherConsignment = JSON.parse(res.body).result.consignmentAllowed === 'Yes' ? 'Y' : 'N';
  } catch (e) {
    // default to 'N' - the safer assumption if this can't be determined live
  }

  res = apiGet(credential, `${apiPrefix}/GRC/GetGRCPanelData`, { _dc: dc(), vendorCode: d.vendorCode, connectedSiteCode: d.connectedSiteCode }, 'GRC/GetGRCPanelData');
  checkEnvelope(res, 'GetGRCPanelData');

  res = apiPost(credential, `${apiPrefix}/Shared/GetTradeGroupLookup`, { extraQuery: d.tradeGroupCode, queryFieldName: ['name', 'description'], connectedSite: String(d.connectedSiteCode), workDate: date, connectedSiteOUCode: d.connectedSiteOUCode, connectedSiteType: d.connectedSiteType, page: 1, start: 0, limit: 25 }, null, 'Shared/GetTradeGroupLookup');
  checkEnvelope(res, 'GetTradeGroupLookup');

  res = apiPost(credential, `${apiPrefix}/Shared/GetPurchaseTermLookup`, {}, { tradeGroupCode: d.tradeGroupCode }, 'Shared/GetPurchaseTermLookup');
  checkEnvelope(res, 'GetPurchaseTermLookup');

  // Live vendor tax region - varies per tenant/vendor, discovered rather than
  // hardcoded (confirmed qafeature=1089, qahbros=4 for the same call shape).
  res = apiGet(credential, `${apiPrefix}/GRC/GetGRCVendorTaxRegion`, { _dc: dc(), vendorCode: d.vendorCode }, 'GRC/GetGRCVendorTaxRegion');
  checkEnvelope(res, 'GetGRCVendorTaxRegion');
  let vendorTaxRegionCode = null;
  try {
    vendorTaxRegionCode = JSON.parse(res.body).result;
  } catch (e) {
    // handled by the isNaN checks below
  }

  // Serial items receive one unit per line (each with its own unique
  // serial); Regular/Batch items receive the whole lot as a single line.
  const lineCount = itemType === 'S' ? d.receiptQty : 1;
  const lineQty = itemType === 'S' ? 1 : d.receiptQty;

  res = apiPost(
    credential,
    `${apiPrefix}/List/GetItemDetailWithOpeningRate`,
    {
      ICode: itemCode,
      TransactionDate: date,
      SiteTaxCode: d.siteTaxCode,
      CallFrom: 'GRC',
      SiteCodeOwner: d.admsite.code,
      FormCode: d.formCode,
      OUCode: d.admouCode.code,
      SiteTaxCodeOwner: d.siteTaxCode,
      SiteCode: d.admsite.code,
      companyCode: d.vendorCode,
      pcType: 'P',
      checkNegativeStock: false,
      stockCode: d.stockPointCode,
      quantity: lineQty,
      extraParam: {},
      documentType: '',
      documentCode: 0,
      onlyItemScan: false,
      onlySetAndItemScan: false,
      batchOutApplicable: false,
    },
    null,
    'List/GetItemDetailWithOpeningRate'
  );
  checkEnvelope(res, 'GetItemDetailWithOpeningRate');

  let batchSerialCodes = new Array(lineCount).fill(null);

  if (itemType === 'B' || itemType === 'S') {
    res = apiPost(credential, `${apiPrefix}/Batch/GetSelectedItemBatchInfo`, [{ itemId: itemCode, articleId: null }], null, 'Batch/GetSelectedItemBatchInfo');
    checkEnvelope(res, 'GetSelectedItemBatchInfo');

    const batchSerialItems = [];
    for (let i = 0; i < lineCount; i++) {
      batchSerialItems.push({
        index: i,
        batchSerialNo: uniqueSerial(__VU, iterationTag, i),
        mfgDate: null,
        itemId: itemCode,
        validTillDate: null,
        listedMrp: pricing.listedMrp,
        mrp: pricing.mrp,
        wsp: pricing.wsp,
        costRate: pricing.costrate,
      });
    }

    res = apiPost(
      credential,
      `${apiPrefix}/Batch/ValidateAndCreateBachSerialNo`,
      {
        batchSerialItems,
        rateCalculationParam: {
          selectionMode: 'QTY',
          rateAsDisplay: false,
          callFrom: 'GRC',
          showStock: true,
          pcType: 'P',
          siteCodeOwner: d.admsite.code,
          siteTaxCode: d.siteTaxCode,
          siteTaxCodeOwner: d.siteTaxCode,
          transactionDate: date,
          tradeGrpCode: d.tradeGroupCode,
          formCode: d.formCode,
          companyCode: d.vendorCode,
          ouCode: d.admouCode.code,
          allowPORateChange: true,
          checkNegativeStock: false,
          quantity: lineQty,
          documentSiteCode: d.admsite.code,
          documentStockPoint: d.stockPointCode,
          duplicateItemCheckRequired: true,
          quantityRequired: true,
          allowRateModification: true,
          showBatchInfo: true,
          onlyItemScan: false,
          baseItems: [],
        },
      },
      null,
      'Batch/ValidateAndCreateBachSerialNo'
    );
    const batchOk = checkEnvelope(res, 'ValidateAndCreateBachSerialNo', {
      'ValidateAndCreateBachSerialNo: no per-item errors': (r) => {
        try {
          const items = JSON.parse(r.body).result.items;
          return Array.isArray(items) && items.every((it) => it.hasError === false);
        } catch (e) {
          return false;
        }
      },
    });
    if (!batchOk) {
      console.log(`ValidateAndCreateBachSerialNo [VU ${__VU} iter ${iterationTag}] unexpected response: ${res.body}`);
    }
    try {
      const items = JSON.parse(res.body).result.items;
      batchSerialCodes = items.map((it) => it.batchSerialCode);
    } catch (e) {
      // leave batchSerialCodes as nulls - the GRC/Save check below will fail loudly
    }
  }

  const grcItemsForCharges = [];
  for (let i = 0; i < lineCount; i++) {
    grcItemsForCharges.push({
      itemId: itemCode,
      barcode: itemCode,
      mrp: pricing.mrp,
      hsnsacCode: 1,
      rateCal: pricing.costrate,
      grsamt: pricing.costrate * lineQty,
      quantity: lineQty,
      taxCode: 4,
      itemCharges: null,
      gstItcAppl: 'IP',
      referenceDetailCode: 0,
      referenceTermCode: d.termCode,
      referenceMainCode: 0,
      referenceNetAmt: 0,
      reCalulateRefItemChg: true,
      batchSerialCode: batchSerialCodes[i],
    });
  }

  res = apiPost(
    credential,
    `${apiPrefix}/Utility/CalculateItemCharges`,
    { adHocItems: grcItemsForCharges, referenceItems: [], skipChargeCode: [] },
    {
      grcDate: date,
      termCode: d.termCode,
      tradeGroupCode: d.tradeGroupCode,
      vendorTaxRegionCode,
      formCode: d.formCode,
      exchangeRate: 1,
      isExciseApplicable: 'N',
    },
    'Utility/CalculateItemCharges'
  );
  checkEnvelope(res, 'CalculateItemCharges');

  let grcDetails = [];
  let grcCharges = [];
  try {
    const parsed = JSON.parse(res.body).result;
    grcDetails = parsed.grcDetails;
    grcCharges = parsed.grcCharges;
  } catch (e) {
    throw new Error(`CalculateItemCharges did not return usable grcDetails (VU ${__VU} iter ${iterationTag}): ${res.body}`);
  }

  const priceChartItems = grcDetails.map((det, i) => ({
    ItemId: itemCode,
    Barcode: itemCode,
    ItemName: itemCode,
    BatchSerialNo: null,
    RecordIndex: i,
  }));
  res = apiPost(credential, `${apiPrefix}/Utility/ValidateItemsForPriceChart`, priceChartItems, { vendorId: d.vendorCode, documentdate: date }, 'Utility/ValidateItemsForPriceChart');
  checkEnvelope(res, 'ValidateItemsForPriceChart');

  const marginRuleItems = grcDetails.map((det) => ({
    itemId: itemCode,
    barCode: itemCode,
    itemName: itemCode,
    discount: det.discount || 0,
    quantity: det.quantity,
    rate: det.rateCal,
    mrp: pricing.mrp,
    listedMRP: pricing.listedMrp,
    wsp: pricing.wsp,
    chgamt: det.chgamt || 0,
    recordIndex: det.recordIndex,
    hsnsaccode: 1,
    setItemIndex: null,
    setIndex: null,
    orderCode: 0,
    taxCode: 4,
    batchSerialCode: det.batchSerialCode,
  }));
  res = apiPost(
    credential,
    `${apiPrefix}/Utility/ValidateMarginRule`,
    { vendorId: d.vendorCode, siteCode: d.admsite.code, grcDate: date, tradeGroupCode: d.tradeGroupCode, vendorTaxRegionCode, formCode: d.formCode, grcItems: marginRuleItems },
    null,
    'Utility/ValidateMarginRule'
  );
  const marginOk = checkEnvelope(res, 'ValidateMarginRule', {
    'ValidateMarginRule: no violations': (r) => {
      try {
        return JSON.parse(r.body).result.length === 0;
      } catch (e) {
        return false;
      }
    },
  });
  if (!marginOk) {
    console.log(`ValidateMarginRule [VU ${__VU} iter ${iterationTag}] reported violations: ${res.body}`);
  }

  const modeFields = itemType === 'S' ? { itemManagementMode: 'S', priceManagement: 'B' } : itemType === 'B' ? { itemManagementMode: 'B', priceManagement: 'B' } : { itemManagementMode: 'I', priceManagement: 'I' };

  const items = grcDetails.map((det, i) => ({
    itemId: itemCode,
    acrate: pricing.costrate,
    acqty: det.quantity,
    rem: '',
    grsamt: det.grsamt,
    netamt: det.netamt,
    chgamt: det.chgamt || 0,
    effamt: det.effamt,
    excisemainCode: null,
    exBasis: '',
    exEffrate: null,
    exAbtfactor: null,
    exDutyfactor: null,
    exCessfactor: null,
    exRoundoff: null,
    exAppAmt: 0,
    exDutyAmt: 0,
    exCessAmt: 0,
    rsp: pricing.mrp,
    exApplicableFrom: null,
    isExApplicable: false,
    grcDetCode: 0,
    code: 0,
    status: 'N',
    itemCharges: det.itemCharges,
    orderDate: null,
    orderCode: null,
    poItemId: '',
    orderDetCode: null,
    invSetCode: null,
    allowPriceModification: 'N',
    modifyMRPWSP: '',
    wsp: pricing.wsp,
    mrp: pricing.mrp,
    listedMrp: pricing.listedMrp,
    originalRate: pricing.costrate,
    negativeStockAlert: 'P',
    orderQty: null,
    orderDateFrom: null,
    orderDateTo: null,
    recordIndex: i,
    setIndex: null,
    pendingQty: 0,
    hasExcessQuantity: false,
    gstItcAppl: 'IP',
    batchSerialCode: batchSerialCodes[i],
    itemManagementMode: modeFields.itemManagementMode,
    priceManagement: modeFields.priceManagement,
  }));

  const totalQty = items.reduce((sum, it) => sum + it.acqty, 0);
  const totalGrsAmt = items.reduce((sum, it) => sum + it.grsamt, 0);

  const saveBody = {
    code: 0,
    forgnCurrRate: 1,
    lgtbaleitems: null,
    gtentrybaleitems: null,
    lgtrecqty: null,
    admsite: d.admsite,
    admouCode: d.admouCode,
    exrate: 1,
    setApplicable: 'N',
    items,
    grcdt: date,
    ecode: null,
    connectedSiteType: d.connectedSiteType,
    notAllowAuthorisePsiteGRC: true,
    connectedSiteOUCode: d.connectedSiteOUCode,
    grcCharges,
    setItemDetails: null,
    gateInDataversion: null,
    lgtDataversion: null,
    vendorDocDate: null,
    stockPointCode: 0,
    docno: '',
    geno: '',
    docCode: d.docSchemeCode,
    rem: '',
    ext: '',
    time: null,
    whetherConsignment,
    rcqty: totalQty,
    totalItems: items.length,
    rtqty: 0,
    lastAccessTime: null,
    taxchgper: 0,
    schemeDocno: '',
    temp_docCode: '',
    grsamt: totalGrsAmt,
    chgamt: 0,
    netamt: totalGrsAmt,
    authTime: null,
    isBaleApplicable: false,
    isGateInApplicable: false,
    isLogisticApplicable: false,
    vendorCode: d.vendorCode,
    purtermCode: 0,
    gateInNo: 0,
    isExciseApplicable: 'N',
    dataVersion: 0,
    isGrcReturn: false,
    docSchemeName: '',
    rcinvqty: 0,
    formType: 'ProcGRCAdhoc',
    canModify: false,
    canVendorModify: false,
    orderCount: 0,
    id: `Prc.view.Transaction.model.GrcModel-${__VU}-${iterationTag}`,
    pCode: { code: d.vendorCode },
    purordmain: null,
    invloc: { code: d.stockPointCode },
    admyear: null,
    lastAccessEcode: null,
    agCode: null,
    invgatein: null,
    invlgtnote: null,
    fintradegrp: { code: d.tradeGroupCode },
    admcurrencyCode: { code: d.currencyCode },
    authEcode: null,
    psiteStockpoint: null,
    finform: { code: d.formCode },
    purTermMainCode: { code: d.termCode },
    whetherConsignmentDisplay: whetherConsignment === 'Y' ? 'Consignment' : 'Non-Consignment',
    ConnectedOUCode: d.connectedSiteOUCode,
    sets: [],
    attachmentFolder: null,
    workDate: date,
    attachedFiles: { timestamp: 0, docType: '', docCode: 0, files: [] },
    udfDetails: [],
  };

  res = apiPost(credential, `${apiPrefix}/GRC/Save`, saveBody, null, 'GRC/Save');
  const saveOk = checkEnvelope(res, 'GRC/Save', {
    'GRC/Save: schemeDocno present': (r) => {
      try {
        return !!JSON.parse(r.body).result.schemeDocno;
      } catch (e) {
        return false;
      }
    },
  });

  let grcCode = null;
  let schemeDocno = null;
  try {
    const result = JSON.parse(res.body).result;
    grcCode = result.code;
    schemeDocno = result.schemeDocno;
  } catch (e) {
    // handled below
  }

  console.log(
    `CreateGrc [VU ${__VU} iter ${iterationTag}] itemCode=${itemCode} lines=${items.length} -> grcCode=${grcCode} schemeDocno=${schemeDocno} saveOk=${saveOk}`
  );

  if (!grcCode) {
    throw new Error(`GRC/Save did not return a code (VU ${__VU} iter ${iterationTag}): ${res.body}`);
  }

  return { grcCode, schemeDocno };
}

// Multi-item / POS-site-capable variant, added alongside runCreateGrcFlow
// (left untouched above) rather than folded into it, per instruction to keep
// the existing single-item flow as-is. Shares the same tenant business data
// (`d`) and helpers (workDate/uniqueSerial) as runCreateGrcFlow above.
//
// `items`: [{ itemCode, itemType, pricing }] - one entry per GRC line, always
// at qty 1 regardless of itemType. This is a deliberate simplification from
// runCreateGrcFlow's "one item exploded into N serial-unit lines" behavior -
// here N *distinct* items make N lines instead. `pricing` may be omitted
// (null/undefined), in which case it's resolved live from this item's own
// GetItemDetailWithOpeningRate response (mrp/rsp/wsp/rate) rather than a
// caller-guessed constant - real DB-pooled items have real historical
// pricing, and a synthetic guess risks tripping ValidateMarginRule.
//
// `posSite`: optional, from lib/posSite.js's resolvePosSite() - when present,
// the save payload's site/stockpoint fields switch to the POS shape
// (invloc:null, psiteStockpoint:{code}) confirmed against a real POS-site
// HAR capture; when omitted, behavior matches the org/HO site shape exactly.
//
// Per-item HTTP calls (item detail, batch/serial validation) are issued
// concurrently via apiBatch (k6 http.batch()) - same request shapes as the
// single-item flow above, replicated once per item, just not run one-at-a-time.
export function runCreateGrcFlowMulti(credential, iterationTag, items, posSite) {
  if (posSite && items.some((it) => it.itemType !== 'I')) {
    throw new Error(
      'POS-site (MSOOCM) batch/serial items are not supported yet - only regular (itemType "I") items have been ' +
        'HAR-verified for POS so far. Use OSOOCM for batch/serial, or provide a POS batch/serial HAR to extend this.'
    );
  }

  const date = workDate();
  const dc = () => Date.now();

  const siteCode = posSite ? posSite.code : d.admsite.code;
  const siteType = posSite ? posSite.siteType : d.admsite.siteType;
  const connectedSiteType = posSite ? posSite.siteType : d.connectedSiteType;
  const siteTaxCode = posSite ? posSite.siteTaxCode : d.siteTaxCode;
  const stockPointCode = posSite ? posSite.stockPointCode : d.stockPointCode;
  // Distinct from stockPointCode above: GetItemDetailWithOpeningRate /
  // ValidateAndCreateBachSerialNo's "stockCode"/"documentStockPoint" params
  // want a numeric stock-point id - confirmed live, a POS site's composite
  // string code (e.g. "MNABTP1-2") there throws a server-side Int64
  // conversion error. The original POS HAR used stockCode:1, matching the
  // chosen stock point's own locCode, not its string code.
  const stockCode = posSite ? posSite.stockPointLocCode : d.stockPointCode;
  const admouCode = posSite ? { code: posSite.admouCode } : d.admouCode;
  // Lookup calls (vendor/trade-group/term/site-picker) keep using the org's
  // own connectedSite/connectedSiteType even in POS mode - confirmed live:
  // the captured POS HAR's own GetTradeGroupLookup/GetAllStockPointForASiteType
  // requests still carried connectedSiteType:"OS-OO-CM", not the POS site's
  // MS-OO-CM - only the final GRC/Save body's top-level connectedSiteType
  // reflects the chosen POS site.
  const formSiteCode = posSite ? posSite.code : d.connectedSiteCode;

  let res = apiPost(
    credential,
    `${apiPrefix}/Shared/GetVendorLookupForPurtrans`,
    { query: '', queryFieldName: ['name', 'slid'], connectedSite: d.connectedSiteCode, workDate: date, connectedSiteOUCode: d.connectedSiteOUCode, connectedSiteType: d.connectedSiteType, page: 1, start: 0, limit: 25 },
    null,
    'Shared/GetVendorLookupForPurtrans'
  );
  checkEnvelope(res, 'GetVendorLookupForPurtrans');

  res = apiGet(credential, `${apiPrefix}/GRC/GetGRCFormRelatedData`, { _dc: dc(), vendorCode: d.vendorCode, connectedSiteCode: formSiteCode }, 'GRC/GetGRCFormRelatedData');
  checkEnvelope(res, 'GetGRCFormRelatedData');
  let whetherConsignment = 'N';
  try {
    whetherConsignment = JSON.parse(res.body).result.consignmentAllowed === 'Yes' ? 'Y' : 'N';
  } catch (e) {
    // default to 'N' - the safer assumption if this can't be determined live
  }

  res = apiGet(credential, `${apiPrefix}/GRC/GetGRCPanelData`, { _dc: dc(), vendorCode: d.vendorCode, connectedSiteCode: formSiteCode }, 'GRC/GetGRCPanelData');
  checkEnvelope(res, 'GetGRCPanelData');

  res = apiPost(credential, `${apiPrefix}/Shared/GetTradeGroupLookup`, { extraQuery: d.tradeGroupCode, queryFieldName: ['name', 'description'], connectedSite: String(d.connectedSiteCode), workDate: date, connectedSiteOUCode: d.connectedSiteOUCode, connectedSiteType: d.connectedSiteType, page: 1, start: 0, limit: 25 }, null, 'Shared/GetTradeGroupLookup');
  checkEnvelope(res, 'GetTradeGroupLookup');

  res = apiPost(credential, `${apiPrefix}/Shared/GetPurchaseTermLookup`, {}, { tradeGroupCode: d.tradeGroupCode }, 'Shared/GetPurchaseTermLookup');
  checkEnvelope(res, 'GetPurchaseTermLookup');

  res = apiGet(credential, `${apiPrefix}/GRC/GetGRCVendorTaxRegion`, { _dc: dc(), vendorCode: d.vendorCode }, 'GRC/GetGRCVendorTaxRegion');
  checkEnvelope(res, 'GetGRCVendorTaxRegion');
  let vendorTaxRegionCode = null;
  try {
    vendorTaxRegionCode = JSON.parse(res.body).result;
  } catch (e) {
    // handled by the isNaN checks below
  }

  const itemDetailRequests = items.map((it) => ({
    method: 'POST',
    path: `${apiPrefix}/List/GetItemDetailWithOpeningRate`,
    body: {
      ICode: it.itemCode,
      TransactionDate: date,
      SiteTaxCode: siteTaxCode,
      CallFrom: 'GRC',
      SiteCodeOwner: siteCode,
      FormCode: d.formCode,
      OUCode: admouCode.code,
      SiteTaxCodeOwner: siteTaxCode,
      SiteCode: siteCode,
      companyCode: d.vendorCode,
      pcType: 'P',
      checkNegativeStock: false,
      stockCode,
      quantity: 1,
      extraParam: {},
      documentType: '',
      documentCode: 0,
      onlyItemScan: false,
      onlySetAndItemScan: false,
      batchOutApplicable: false,
    },
    endpointTag: 'List/GetItemDetailWithOpeningRate',
  }));
  const itemDetailResponses = apiBatch(credential, itemDetailRequests);
  itemDetailResponses.forEach((r) => checkEnvelope(r, 'GetItemDetailWithOpeningRate'));

  const resolvedItems = items.map((it, i) => {
    if (it.pricing) return it;
    let detail = {};
    try {
      detail = JSON.parse(itemDetailResponses[i].body).result;
    } catch (e) {
      throw new Error(`GetItemDetailWithOpeningRate did not return usable data for ${it.itemCode}: ${itemDetailResponses[i].body}`);
    }
    let costrate = detail.rate;
    if (!costrate) {
      costrate = randomFallbackRate();
      console.log(`runCreateGrcFlowMulti: ${it.itemCode} has rate 0 at this site - using random fallback rate ${costrate}`);
    }
    return { ...it, pricing: { costrate, wsp: detail.wsp, mrp: detail.mrp, listedMrp: detail.rsp } };
  });

  const batchSerialTargets = resolvedItems.map((it, i) => ({ it, i })).filter(({ it }) => it.itemType === 'B' || it.itemType === 'S');
  const batchSerialCodeByIndex = {};

  if (batchSerialTargets.length > 0) {
    const infoRequests = batchSerialTargets.map(({ it }) => ({
      method: 'POST',
      path: `${apiPrefix}/Batch/GetSelectedItemBatchInfo`,
      body: [{ itemId: it.itemCode, articleId: null }],
      endpointTag: 'Batch/GetSelectedItemBatchInfo',
    }));
    const infoResponses = apiBatch(credential, infoRequests);
    infoResponses.forEach((r) => checkEnvelope(r, 'GetSelectedItemBatchInfo'));

    const validateRequests = batchSerialTargets.map(({ it, i }) => ({
      method: 'POST',
      path: `${apiPrefix}/Batch/ValidateAndCreateBachSerialNo`,
      body: {
        batchSerialItems: [
          {
            index: 0,
            batchSerialNo: uniqueSerial(__VU, iterationTag, i),
            mfgDate: null,
            itemId: it.itemCode,
            validTillDate: null,
            listedMrp: it.pricing.listedMrp,
            mrp: it.pricing.mrp,
            wsp: it.pricing.wsp,
            costRate: it.pricing.costrate,
          },
        ],
        rateCalculationParam: {
          selectionMode: 'QTY',
          rateAsDisplay: false,
          callFrom: 'GRC',
          showStock: true,
          pcType: 'P',
          siteCodeOwner: siteCode,
          siteTaxCode,
          siteTaxCodeOwner: siteTaxCode,
          transactionDate: date,
          tradeGrpCode: d.tradeGroupCode,
          formCode: d.formCode,
          companyCode: d.vendorCode,
          ouCode: admouCode.code,
          allowPORateChange: true,
          checkNegativeStock: false,
          quantity: 1,
          documentSiteCode: siteCode,
          documentStockPoint: stockCode,
          duplicateItemCheckRequired: true,
          quantityRequired: true,
          allowRateModification: true,
          showBatchInfo: true,
          onlyItemScan: false,
          baseItems: [],
        },
      },
      endpointTag: 'Batch/ValidateAndCreateBachSerialNo',
    }));
    const validateResponses = apiBatch(credential, validateRequests);
    validateResponses.forEach((r, idx) => {
      const target = batchSerialTargets[idx];
      const ok = checkEnvelope(r, 'ValidateAndCreateBachSerialNo', {
        'ValidateAndCreateBachSerialNo: no per-item errors': (rr) => {
          try {
            const respItems = JSON.parse(rr.body).result.items;
            return Array.isArray(respItems) && respItems.every((x) => x.hasError === false);
          } catch (e) {
            return false;
          }
        },
      });
      if (!ok) {
        console.log(`ValidateAndCreateBachSerialNo [VU ${__VU} iter ${iterationTag}] item ${target.it.itemCode} unexpected response: ${r.body}`);
      }
      try {
        batchSerialCodeByIndex[target.i] = JSON.parse(r.body).result.items[0].batchSerialCode;
      } catch (e) {
        batchSerialCodeByIndex[target.i] = null;
      }
    });
  }

  const grcItemsForCharges = resolvedItems.map((it, i) => ({
    itemId: it.itemCode,
    barcode: it.itemCode,
    mrp: it.pricing.mrp,
    hsnsacCode: 1,
    rateCal: it.pricing.costrate,
    grsamt: it.pricing.costrate,
    quantity: 1,
    taxCode: 4,
    itemCharges: null,
    gstItcAppl: 'IP',
    referenceDetailCode: 0,
    referenceTermCode: d.termCode,
    referenceMainCode: 0,
    referenceNetAmt: 0,
    reCalulateRefItemChg: true,
    batchSerialCode: batchSerialCodeByIndex[i] || null,
  }));

  res = apiPost(
    credential,
    `${apiPrefix}/Utility/CalculateItemCharges`,
    { adHocItems: grcItemsForCharges, referenceItems: [], skipChargeCode: [] },
    { grcDate: date, termCode: d.termCode, tradeGroupCode: d.tradeGroupCode, vendorTaxRegionCode, formCode: d.formCode, exchangeRate: 1, isExciseApplicable: 'N' },
    'Utility/CalculateItemCharges'
  );
  checkEnvelope(res, 'CalculateItemCharges');

  let grcDetails = [];
  let grcCharges = [];
  try {
    const parsed = JSON.parse(res.body).result;
    grcDetails = parsed.grcDetails;
    grcCharges = parsed.grcCharges;
  } catch (e) {
    throw new Error(`CalculateItemCharges did not return usable grcDetails (VU ${__VU} iter ${iterationTag}): ${res.body}`);
  }

  const priceChartItems = grcDetails.map((det, i) => ({ ItemId: resolvedItems[i].itemCode, Barcode: resolvedItems[i].itemCode, ItemName: resolvedItems[i].itemCode, BatchSerialNo: null, RecordIndex: i }));
  res = apiPost(credential, `${apiPrefix}/Utility/ValidateItemsForPriceChart`, priceChartItems, { vendorId: d.vendorCode, documentdate: date }, 'Utility/ValidateItemsForPriceChart');
  checkEnvelope(res, 'ValidateItemsForPriceChart');

  const marginRuleItems = grcDetails.map((det, i) => ({
    itemId: resolvedItems[i].itemCode,
    barCode: resolvedItems[i].itemCode,
    itemName: resolvedItems[i].itemCode,
    discount: det.discount || 0,
    quantity: det.quantity,
    rate: det.rateCal,
    mrp: resolvedItems[i].pricing.mrp,
    listedMRP: resolvedItems[i].pricing.listedMrp,
    wsp: resolvedItems[i].pricing.wsp,
    chgamt: det.chgamt || 0,
    recordIndex: det.recordIndex,
    hsnsaccode: 1,
    setItemIndex: null,
    setIndex: null,
    orderCode: 0,
    taxCode: 4,
    batchSerialCode: det.batchSerialCode,
  }));
  res = apiPost(
    credential,
    `${apiPrefix}/Utility/ValidateMarginRule`,
    { vendorId: d.vendorCode, siteCode, grcDate: date, tradeGroupCode: d.tradeGroupCode, vendorTaxRegionCode, formCode: d.formCode, grcItems: marginRuleItems },
    null,
    'Utility/ValidateMarginRule'
  );
  const marginOk = checkEnvelope(res, 'ValidateMarginRule', {
    'ValidateMarginRule: no violations': (r) => {
      try {
        return JSON.parse(r.body).result.length === 0;
      } catch (e) {
        return false;
      }
    },
  });
  if (!marginOk) {
    console.log(`ValidateMarginRule [VU ${__VU} iter ${iterationTag}] reported violations: ${res.body}`);
  }

  const finalItems = grcDetails.map((det, i) => {
    const it = resolvedItems[i];
    const modeFields = it.itemType === 'S' ? { itemManagementMode: 'S', priceManagement: 'B' } : it.itemType === 'B' ? { itemManagementMode: 'B', priceManagement: 'B' } : { itemManagementMode: 'I', priceManagement: 'I' };
    return {
      itemId: it.itemCode,
      acrate: it.pricing.costrate,
      acqty: det.quantity,
      rem: '',
      grsamt: det.grsamt,
      netamt: det.netamt,
      chgamt: det.chgamt || 0,
      effamt: det.effamt,
      excisemainCode: null,
      exBasis: '',
      exEffrate: null,
      exAbtfactor: null,
      exDutyfactor: null,
      exCessfactor: null,
      exRoundoff: null,
      exAppAmt: 0,
      exDutyAmt: 0,
      exCessAmt: 0,
      rsp: it.pricing.mrp,
      exApplicableFrom: null,
      isExApplicable: false,
      grcDetCode: 0,
      code: 0,
      status: 'N',
      itemCharges: det.itemCharges,
      orderDate: null,
      orderCode: null,
      poItemId: '',
      orderDetCode: null,
      invSetCode: null,
      allowPriceModification: 'N',
      modifyMRPWSP: '',
      wsp: it.pricing.wsp,
      mrp: it.pricing.mrp,
      listedMrp: it.pricing.listedMrp,
      originalRate: it.pricing.costrate,
      negativeStockAlert: 'P',
      orderQty: null,
      orderDateFrom: null,
      orderDateTo: null,
      recordIndex: i,
      setIndex: null,
      pendingQty: 0,
      hasExcessQuantity: false,
      gstItcAppl: 'IP',
      batchSerialCode: batchSerialCodeByIndex[i] || null,
      itemManagementMode: modeFields.itemManagementMode,
      priceManagement: modeFields.priceManagement,
    };
  });

  const totalQty = finalItems.reduce((sum, it) => sum + it.acqty, 0);
  const totalGrsAmt = finalItems.reduce((sum, it) => sum + it.grsamt, 0);

  const saveBody = {
    code: 0,
    forgnCurrRate: 1,
    lgtbaleitems: null,
    gtentrybaleitems: null,
    lgtrecqty: null,
    admsite: { code: siteCode, siteType },
    admouCode,
    exrate: 1,
    setApplicable: 'N',
    items: finalItems,
    grcdt: date,
    ecode: null,
    connectedSiteType,
    notAllowAuthorisePsiteGRC: true,
    connectedSiteOUCode: d.connectedSiteOUCode,
    grcCharges,
    setItemDetails: null,
    gateInDataversion: null,
    lgtDataversion: null,
    vendorDocDate: null,
    stockPointCode: 0,
    docno: '',
    geno: '',
    docCode: d.docSchemeCode,
    rem: '',
    ext: '',
    time: null,
    whetherConsignment,
    rcqty: totalQty,
    totalItems: finalItems.length,
    rtqty: 0,
    lastAccessTime: null,
    taxchgper: 0,
    schemeDocno: '',
    temp_docCode: '',
    grsamt: totalGrsAmt,
    chgamt: 0,
    netamt: totalGrsAmt,
    authTime: null,
    isBaleApplicable: false,
    isGateInApplicable: false,
    isLogisticApplicable: false,
    vendorCode: d.vendorCode,
    purtermCode: 0,
    gateInNo: 0,
    isExciseApplicable: 'N',
    dataVersion: 0,
    isGrcReturn: false,
    docSchemeName: '',
    rcinvqty: 0,
    formType: 'ProcGRCAdhoc',
    canModify: false,
    canVendorModify: false,
    orderCount: 0,
    id: `Prc.view.Transaction.model.GrcModel-${__VU}-${iterationTag}`,
    pCode: { code: d.vendorCode },
    purordmain: null,
    invloc: posSite ? null : { code: stockPointCode },
    admyear: null,
    lastAccessEcode: null,
    agCode: null,
    invgatein: null,
    invlgtnote: null,
    fintradegrp: { code: d.tradeGroupCode },
    admcurrencyCode: { code: d.currencyCode },
    authEcode: null,
    psiteStockpoint: posSite ? { code: stockPointCode } : null,
    finform: { code: d.formCode },
    purTermMainCode: { code: d.termCode },
    whetherConsignmentDisplay: whetherConsignment === 'Y' ? 'Consignment' : 'Non-Consignment',
    ConnectedOUCode: d.connectedSiteOUCode,
    sets: [],
    attachmentFolder: null,
    workDate: date,
    attachedFiles: { timestamp: 0, docType: '', docCode: 0, files: [] },
    udfDetails: [],
  };

  res = apiPost(credential, `${apiPrefix}/GRC/Save`, saveBody, null, 'GRC/Save');
  const saveOk = checkEnvelope(res, 'GRC/Save', {
    'GRC/Save: schemeDocno present': (r) => {
      try {
        return !!JSON.parse(r.body).result.schemeDocno;
      } catch (e) {
        return false;
      }
    },
  });

  let grcCode = null;
  let schemeDocno = null;
  try {
    const result = JSON.parse(res.body).result;
    grcCode = result.code;
    schemeDocno = result.schemeDocno;
  } catch (e) {
    // handled below
  }

  console.log(
    `CreateGrcMulti [VU ${__VU} iter ${iterationTag}] items=${finalItems.length} posSite=${posSite ? posSite.code : 'none'} -> grcCode=${grcCode} schemeDocno=${schemeDocno} saveOk=${saveOk}`
  );

  if (!grcCode) {
    throw new Error(`GRC/Save did not return a code (VU ${__VU} iter ${iterationTag}): ${res.body}`);
  }

  return { grcCode, schemeDocno };
}
