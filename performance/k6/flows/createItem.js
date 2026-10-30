import { SharedArray } from 'k6/data';
import { apiGet, apiPost, checkEnvelope } from '../lib/http.js';
import { apiPrefix, TENANT } from '../lib/config.js';

const businessDataArr = new SharedArray('itemBusinessData', function () {
  return [JSON.parse(open(`../data/business/${TENANT}.item.json`))];
});
const d = businessDataArr[0];

function workDate() {
  return `${new Date().toISOString().slice(0, 10)}T00:00:00`;
}

// Small proportional jitter so no two iterations submit identical pricing
// (repeat item data risks "item already exists"-shaped collisions - the same
// reason Selenium's ItemAddPricing.java randomizes price). Scaling all four
// fields by the same factor preserves the required ascending ordering
// (Standard/Cost Rate <= WSP <= RSP <= MRP, confirmed against real HAR data:
// costrate 300 < wsp 400 < mrp 500 < listedMrp 600) instead of risking an
// out-of-order combination that Utility/ValidateMarginRule would reject.
function jitteredPricing(vu, iter) {
  const jitter = 1 + ((Number(vu) * 7 + Number(iter) * 3) % 50) / 100;
  const b = d.pricingBase;
  return {
    costrate: Math.round(b.costrate * jitter),
    wsp: Math.round(b.wsp * jitter),
    mrp: Math.round(b.mrp * jitter),
    listedMrp: Math.round(b.listedMrp * jitter),
  };
}

function uniqueSuffix(vu, iter) {
  return `${Date.now().toString(36)}${Number(vu).toString(36)}${Number(iter).toString(36)}`;
}

// Replays the Create Item journey captured in the HAR (barcode/GS1 checks,
// department/article/HSN/tax lookups, then ItemMaster/Create) for one of the
// 3 item-management modes. Returns the created item's code/type/pricing so
// GRC/DC/TransferOut can chain off it within the same iteration.
export function runCreateItemFlow(credential, iterationTag, itemType) {
  const type = itemType || 'I';
  const modeFields = d.itemTypeOverrides[type];
  if (!modeFields) {
    throw new Error(`Unknown itemType "${type}" - expected one of ${Object.keys(d.itemTypeOverrides).join(', ')}`);
  }

  const suffix = uniqueSuffix(__VU, iterationTag);
  // Real captures across all 3 tenants (qafeature/qahbros/stage-ideal) never
  // needed a unique barcode or imageData to succeed - barcode stays empty
  // (see below) and imageData is only sent when the tenant's business data
  // actually provides one. cname1/pricing still get varied per iteration so
  // repeat runs don't submit identical item data ("item already exists").
  // DIAGNOSTIC: send cname1 exactly as namePrefix, no uniqueness suffix.
  const cname1 = __ENV.DIAG_EXACT_PRICING === 'true' ? d.namePrefix : `${d.namePrefix}-${suffix}`.slice(0, 40);
  // DIAGNOSTIC: send pricingBase exactly as-is (no jitter) to test specific
  // decimal values requested.
  const pricing = __ENV.DIAG_EXACT_PRICING === 'true' ? d.pricingBase : jitteredPricing(__VU, iterationTag);
  const dc = () => Date.now();
  const date = workDate();

  // Item List grid-load preamble. Confirmed live: ItemMaster/Create 500s
  // (unhandled server exception, empty body) when the create-item popup
  // sequence below runs without this having loaded first in the same
  // session/token - present in every successful capture (old and new HARs),
  // absent from a bare popup-only replay, which reproduces the failure.
  let res = apiGet(credential, `${apiPrefix}/udf/GetUDFConfigList`, { _dc: dc(), tableName: 'INVITEM' }, 'udf/GetUDFConfigList');
  checkEnvelope(res, 'GetUDFConfigList');

  res = apiGet(
    credential,
    `${apiPrefix}/criteria/getcriterialist`,
    { _dc: dc(), listShortCode: 'Ginesys.Core.Domain.Inventory.ProductDefinition.ItemHierarchy.ItemListShortView' },
    'criteria/getcriterialist'
  );
  checkEnvelope(res, 'getcriterialist');

  res = apiPost(
    credential,
    `${apiPrefix}/ItemMaster/GetList`,
    {
      availableSites: d.availableSites,
      connectedSite: d.connectedSite,
      connectedSiteOUCode: d.connectedSiteOUCode,
      workDate: date,
      page: 1,
      start: 0,
      limit: 50,
      sort: '[{"property":"itemcode","direction":"ASC"}]',
    },
    null,
    'ItemMaster/GetList'
  );
  checkEnvelope(res, 'GetList');

  res = apiPost(credential, `${apiPrefix}/GridState/SaveState`, { StateKey: 'invitemlist', StateValue: JSON.stringify({ ts: Date.now() }) }, null, 'GridState/SaveState');
  checkEnvelope(res, 'SaveState (invitemlist)');

  res = apiPost(credential, `${apiPrefix}/GridState/SaveState`, { StateKey: 'invitemmainlist', StateValue: JSON.stringify({ ts: Date.now() }) }, null, 'GridState/SaveState');
  checkEnvelope(res, 'SaveState (invitemmainlist)');

  res = apiGet(credential, `${apiPrefix}/ItemMaster/IsAgingDefinedForCurrentDate`, { _dc: dc() }, 'ItemMaster/IsAgingDefinedForCurrentDate');
  checkEnvelope(res, 'IsAgingDefinedForCurrentDate');

  res = apiGet(credential, `${apiPrefix}//udf/GetUDFMetaData`, { _dc: dc(), tableName: 'INVITEM', lookupData: true }, 'udf/GetUDFMetaData');
  checkEnvelope(res, 'GetUDFMetaData');

  res = apiGet(credential, `${apiPrefix}/GridState/GetStateByValue`, { _dc: dc(), stateKey: 'gslPopupGslPanelState#invitem' }, 'GridState/GetStateByValue');
  checkEnvelope(res, 'GetStateByValue (gslPopupGslPanelState)');

  res = apiGet(credential, `${apiPrefix}/ItemMaster/GetItemDefaultImage`, { _dc: dc() }, 'ItemMaster/GetItemDefaultImage');
  checkEnvelope(res, 'GetItemDefaultImage');

  res = apiPost(credential, `${apiPrefix}/Shared/GetLedger_General_Lookup`, {}, null, 'Shared/GetLedger_General_Lookup');
  checkEnvelope(res, 'GetLedger_General_Lookup');

  res = apiGet(credential, `${apiPrefix}/ItemMaster/CheckIsGS1BarcodeApplicable`, { _dc: dc() }, 'ItemMaster/CheckIsGS1BarcodeApplicable');
  checkEnvelope(res, 'CheckIsGS1BarcodeApplicable');

  res = apiGet(credential, `${apiPrefix}/GridState/GetStateByValue`, { _dc: dc(), stateKey: 'invItemLockingFieldsState' }, 'GridState/GetStateByValue');
  checkEnvelope(res, 'GetStateByValue (invItemLockingFieldsState)');

  // DIAGNOSTIC: all 3 real successful captures (MB15605/15606/15607) left
  // barcode empty - matching that exactly rather than generating one.
  const barcode = '';

  if (barcode) {
    res = apiPost(credential, `${apiPrefix}/ItemMaster/CheckIsBarcodeExists`, {}, { barcode }, 'ItemMaster/CheckIsBarcodeExists');
    checkEnvelope(res, 'CheckIsBarcodeExists');
  }

  res = apiPost(credential, `${apiPrefix}/Shared/GetDepartments`, {}, null, 'Shared/GetDepartments');
  checkEnvelope(res, 'GetDepartments');

  res = apiPost(credential, `${apiPrefix}/ItemMaster/GetGroupDefaultValues`, {}, { departmentCode: d.departmentCode }, 'ItemMaster/GetGroupDefaultValues');
  checkEnvelope(res, 'GetGroupDefaultValues');

  res = apiPost(credential, `${apiPrefix}/Shared/GetArticles`, {}, { departmentId: d.departmentCode }, 'Shared/GetArticles');
  checkEnvelope(res, 'GetArticles');

  res = apiPost(credential, `${apiPrefix}/Shared/GetGSTHSNLookup`, {}, null, 'Shared/GetGSTHSNLookup');
  checkEnvelope(res, 'GetGSTHSNLookup');

  res = apiPost(credential, `${apiPrefix}/ItemMaster/GetCategoryPromptAndMandetory`, {}, { articleCode: d.articleCode }, 'ItemMaster/GetCategoryPromptAndMandetory');
  checkEnvelope(res, 'GetCategoryPromptAndMandetory');

  res = apiPost(credential, `${apiPrefix}/ItemMaster/GetOtherDetailsByArticle`, {}, { articleCode: d.articleCode }, 'ItemMaster/GetOtherDetailsByArticle');
  checkEnvelope(res, 'GetOtherDetailsByArticle');

  res = apiGet(credential, `${apiPrefix}/ItemMaster/GetGenericCategory`, { _dc: dc(), grpCode: d.articleCode }, 'ItemMaster/GetGenericCategory');
  checkEnvelope(res, 'GetGenericCategory');

  res = apiPost(credential, `${apiPrefix}/Shared/GetTaxGroupLookup`, {}, null, 'Shared/GetTaxGroupLookup');
  checkEnvelope(res, 'GetTaxGroupLookup');

  res = apiPost(credential, `${apiPrefix}/ItemMaster/GetUnitOfMeasurement`, {}, null, 'ItemMaster/GetUnitOfMeasurement');
  checkEnvelope(res, 'GetUnitOfMeasurement');

  res = apiPost(credential, `${apiPrefix}/Shared/GetCategory1Lookup`, {}, null, 'Shared/GetCategory1Lookup');
  checkEnvelope(res, 'GetCategory1Lookup');

  // Final barcode re-check right before Create, exactly as the real UI does.
  if (barcode) {
    res = apiPost(credential, `${apiPrefix}/ItemMaster/CheckIsBarcodeExists`, {}, { barcode }, 'ItemMaster/CheckIsBarcodeExists');
    checkEnvelope(res, 'CheckIsBarcodeExists (final)');
  }

  res = apiGet(
    credential,
    `${apiPrefix}/ItemMaster/GetItemImageByName`,
    { _dc: dc(), itemImageName: `${cname1}.jpg`, thumbImage: false, thumbWidth: 204, thumbHeight: 154 },
    'ItemMaster/GetItemImageByName'
  );
  checkEnvelope(res, 'GetItemImageByName');

  const createBody = {
    code: '',
    barcode,
    materialType: 'F',
    negativeStockAlert: 'P',
    noninventory: 'N',
    barunit: 1,
    posMultipriceAction: 'N',
    allowPriceModification: 'N',
    autoqtypopup: 'N',
    posReturnBehavior: 'P',
    ispriceExcludesTax: 0,
    invhsnsacmain: d.invhsnsacmain,
    considerinorder: 'Y',
    priceTerms: 'F',
    priceBasis: 'G',
    itemPrice: [],
    itcEligibility: 'IP',
    itemManagementMode: modeFields.itemManagementMode,
    priceManagement: modeFields.priceManagement,
    manageExpiry: 'N',
    validityMode: 'N',
    posBatchSelection: 'M',
    cname1,
    cname2: '',
    cname3: '',
    cname4: '',
    cname5: '',
    cname6: '',
    rate: 0,
    mrp: pricing.mrp,
    barrq: '',
    rem: '',
    ext: '',
    partyname: '',
    generated: null,
    lastChanged: null,
    cmpcode: d.cmpcode,
    unitname: d.unitname,
    listedMrp: pricing.listedMrp,
    expiryDate: null,
    partyalias: '',
    stockindate: null,
    shrtname: '',
    considerasfree: '',
    wsp: pricing.wsp,
    altMrp: 0,
    altListedMrp: 0,
    isservice: '',
    serviceDays: 0,
    ismetal: '',
    itemName: '',
    netWeight: 0,
    grsWeight: 0,
    jobCostBasis: '',
    jobCostAmt: 0,
    partQty: 0,
    certificateNo: '',
    dCount: 0,
    dSize: '',
    dWeight: 0,
    dValue: 0,
    dDesc: '',
    cCount: 0,
    cWeight: 0,
    cValue: 0,
    cDesc: '',
    oCount: 0,
    oWeight: 0,
    oValue: 0,
    oDesc: '',
    otherChg: 0,
    desc1: '',
    desc2: '',
    desc3: '',
    desc4: '',
    desc5: '',
    desc6: '',
    blockSale: 0,
    blockSaleBefore: null,
    blockSaleAfter: null,
    blockSaleReason: '',
    searchString: '',
    udfstring01: '',
    udfstring02: '',
    udfstring03: '',
    udfstring04: '',
    udfstring05: '',
    udfstring06: '',
    udfstring07: '',
    udfstring08: '',
    udfstring09: '',
    udfstring10: '',
    udfstring11: '',
    udfstring12: '',
    udfstring13: '',
    udfstring14: '',
    udfstring15: '',
    udfstring16: '',
    udfstring17: '',
    udfstring18: '',
    udfstring19: '',
    udfstring20: '',
    udfstring21: '',
    udfstring22: '',
    udfstring23: '',
    udfstring24: '',
    udfstring25: '',
    udfstring26: '',
    udfstring27: '',
    udfstring28: '',
    udfstring29: '',
    udfstring30: '',
    udfstring31: '',
    udfstring32: '',
    udfstring33: '',
    udfstring34: '',
    udfstring35: '',
    udfstring36: '',
    udfstring37: '',
    udfstring38: '',
    udfstring39: '',
    udfstring40: '',
    udfdate01: null,
    udfdate02: null,
    udfdate03: null,
    udfdate04: null,
    udfdate05: null,
    quantity: 0,
    itemInWardRate: 0,
    validityPeriod: 0,
    id: `App.view.inventory.ItemHierarchy.Item.Model.ItemMasterModel-${__VU}`,
    invgrp: d.invgrp,
    invcat1: d.invcat1,
    invcat2: null,
    invcat3: null,
    invcat4: null,
    invcat5: null,
    invcat6: null,
    fintaxmain: d.fintaxmain,
    finsl: null,
    invarticle: d.invarticle,
    invmetal: null,
    prdroutemain: null,
    prdcostsheethead: null,
    costrate: pricing.costrate,
    imageData: d.imageData,
    priceChangeLimit: null,
    imageFileName: d.imageData ? '' : cname1,
    imageOriginalFileName: d.imageData ? 'EMPTY_BARCODE' : cname1,
    deleteImageFileName: '',
    udfDetails: d.udfDetails || [],
    generateUniqueBarcode: false,
    createFromOtherSources: false,
    pricingQty: 1,
  };

  res = apiPost(credential, `${apiPrefix}/ItemMaster/Create`, createBody, null, 'ItemMaster/Create');
  const saveOk = checkEnvelope(res, 'ItemMaster/Create', {
    'ItemMaster/Create: itemCode present': (r) => {
      try {
        return !!JSON.parse(r.body).result.itemCode;
      } catch (e) {
        return false;
      }
    },
  });

  let itemCode = null;
  try {
    itemCode = JSON.parse(res.body).result.itemCode;
  } catch (e) {
    // leave itemCode null, handled below
  }

  console.log(
    `CreateItem [VU ${__VU} iter ${iterationTag}] type=${type} barcode=${barcode} -> itemCode=${itemCode} saveOk=${saveOk}`
  );

  if (!itemCode) {
    throw new Error(`ItemMaster/Create did not return an itemCode (VU ${__VU} iter ${iterationTag}): ${res.body}`);
  }

  res = apiPost(credential, `${apiPrefix}/ItemMaster/GetItemView`, null, { code: itemCode }, 'ItemMaster/GetItemView');
  checkEnvelope(res, 'GetItemView');

  return { itemCode, itemType: type, pricing };
}
