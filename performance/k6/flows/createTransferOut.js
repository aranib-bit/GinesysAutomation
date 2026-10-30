import { SharedArray } from 'k6/data';
import { apiPost, checkEnvelope } from '../lib/http.js';
import { apiPrefix, TENANT } from '../lib/config.js';

const businessDataArr = new SharedArray('transferOutBusinessData', function () {
  return [JSON.parse(open(`../data/business/${TENANT}.transferOut.json`))];
});
const d = businessDataArr[0];

function workDate() {
  return `${new Date().toISOString().slice(0, 10)}T00:00:00`;
}

// Replays the TransferOut journey. Which DC to transfer is discovered live
// via SI/SearchChallan (site/date/party/tradeGroup filters only - no dcCode
// needed as input) and matched against the exact dcCode this iteration's own
// DC/Save just returned, so concurrent VUs never grab each other's DC even
// though the search can return many pending DCs from other VUs/runs.
export function runCreateTransferOutFlow(credential, iterationTag, dcCode) {
  const date = workDate();

  let res = apiPost(
    credential,
    `${apiPrefix}/SI/SearchChallan`,
    [],
    {
      formType: d.formType,
      invDate: date,
      partyCode: d.partyCode,
      ownerSiteCode: d.ownerSiteCode,
      ouCode: d.ouCode,
      siteCode: d.siteCode,
      tradegpCode: d.tradeGrpCode,
      formCode: d.formCode,
      agentCode: '',
      transporterCode: '',
      fromDt: '',
      toDt: '',
      orderCode: '',
      toSiteCode: '',
    },
    'SI/SearchChallan'
  );
  checkEnvelope(res, 'SearchChallan');

  let challanList = [];
  try {
    challanList = JSON.parse(res.body).result;
  } catch (e) {
    throw new Error(`SearchChallan returned an unparseable response (VU ${__VU} iter ${iterationTag}): ${res.body}`);
  }

  const match = challanList.find((c) => c.dcCode === dcCode);
  if (!match) {
    throw new Error(
      `SearchChallan did not return the DC this iteration just created (dcCode=${dcCode}, VU ${__VU} iter ${iterationTag}) - ` +
        `it may not yet be visible as pending, or was already transferred out by a previous run.`
    );
  }

  res = apiPost(
    credential,
    `${apiPrefix}/SI/GetDCDetails`,
    [dcCode],
    { formType: d.formType, invDate: date, priceListCode: d.priceListCode, siteCode: d.siteCode, ownerSiteCode: d.ownerSiteCode, partyCode: '', tradeGrpCode: d.tradeGrpCode, formCode: d.formCode },
    'SI/GetDCDetails'
  );
  checkEnvelope(res, 'GetDCDetails');

  let itemDetails = [];
  try {
    itemDetails = JSON.parse(res.body).result[0].itemDetails;
  } catch (e) {
    throw new Error(`GetDCDetails did not return itemDetails for dcCode=${dcCode} (VU ${__VU} iter ${iterationTag}): ${res.body}`);
  }

  const transactionItems = itemDetails.map((it, i) => ({
    itemId: it.itemId,
    barcode: it.barcode,
    mrp: it.mrp,
    hsnsacCode: it.hsnsacCode,
    rateCal: it.rateCal,
    grsamt: it.rateCal * it.quantity,
    quantity: it.quantity,
    recordIndex: i,
    taxCode: it.taxCode,
    itemCharges: [],
    gstItcAppl: it.gstItcAppl,
    glCode: 0,
    slCode: 0,
    referenceDetailCode: it.dcDetCode,
    referenceMainCode: it.challanCode,
    setIndex: 0,
    setItemIndex: 0,
    batchMfgDate: null,
    batchSerialCode: it.batchSerialCode,
    batchSerialNo: null,
    batchValidTillDate: null,
  }));

  res = apiPost(
    credential,
    `${apiPrefix}/SI/CalculateItemCharges`,
    { transactionItems, applicableItems: [], chargeData: [], skipChargeCode: [] },
    {
      siteCode: d.ownerSiteCode,
      inputGLCode: d.glCode,
      inputSLCode: '',
      tranDate: date,
      termCode: 0,
      tradeGroupCode: d.tradeGrpCode,
      vendorTaxRegionCode: 0,
      formCode: d.formCode,
      exchangeRate: 1,
      isExciseApplicable: false,
      formType: d.formType,
      orderTerm: '',
    },
    'SI/CalculateItemCharges'
  );
  checkEnvelope(res, 'SI/CalculateItemCharges');

  const items = itemDetails.map((it, i) => ({
    code: 0,
    siInvCode: 0,
    dcCode: it.challanCode,
    outLocationCode: it.dcInLocationCode,
    itemCode: it.itemId,
    invQty: it.quantity,
    rtQty: 0,
    mrp: it.mrp,
    remarks: '',
    invAmount: it.rateCal * it.quantity,
    rate: it.rateCal,
    costRate: null,
    chgamt: null,
    effAmt: it.rateCal * it.quantity,
    invDcDetCode: it.dcDetCode,
    excisemainCode: 0,
    exBasis: '',
    exEffRate: 0,
    exAbtfactor: 0,
    exDutyfactor: 0,
    exCessfactor: 0,
    exAppAmt: 0,
    exDutyAmt: 0,
    exCessAmt: 0,
    exRoundOff: 0,
    taxAmt: null,
    factor: it.factor,
    discount: it.discount,
    basicRate: it.basicRate,
    roundOff: null,
    exApplicableFrom: null,
    hsnSacCode: it.hsnsacName,
    gSTITCAppl: it.gstItcAppl,
    gLCODE: 0,
    slCode: 0,
    gLCCAppl: 'N',
    barcode: it.barcode,
    negativeStockAlert: it.negativeStockAlert,
    itemCharges: [],
    dcDataVersion: it.refHdrDataVersion,
    dataVersion: 0,
    dcIndex: i,
    batchSerialCode: it.batchSerialCode,
    challanNo: it.challanNo,
  }));

  const saveBody = {
    code: 0,
    entryMode: 'Add',
    invoiceDate: date,
    invoiceNo: 0,
    partyCode: null,
    documentNo: '',
    documentDate: null,
    dueDate: date,
    agentCode: null,
    agRate: null,
    gLCode: d.glCode,
    remarks: '',
    termCode: null,
    schemeDocNo: '',
    documentCode: '',
    docSchemeCode: d.docSchemeCode,
    transporterCode: null,
    saleType: 'C',
    sLCode: null,
    glCCAppl: 'Y',
    transitDays: 0,
    destinationSiteOUCode: null,
    ownerSiteCode: d.ownerSiteCode,
    ownerSiteOUCode: d.ouCode,
    transitDueDate: null,
    destinationSite: d.destinationSite,
    tradeGroupCode: d.tradeGrpCode,
    ownerGSTINNo: '',
    ownerGSTINStatus: '',
    ownerTaxPayerType: '',
    ownerGSTINVerifiedOn: null,
    ownerGSTINStateCode: '',
    counterPartyGSTINNo: null,
    counterPartyGSTINStateCode: null,
    gSTAppl: 'N',
    lgtCode: null,
    sICharges: [],
    stockPointCode: 0,
    stockSiteCode: 0,
    dataVersion: 0,
    allowPrint: true,
    mdMenuShrtCode: 'transferout',
    releaseStatus: null,
    createdBy: null,
    ignoreCreditLimitCheck: false,
    ignoreDocumentSchemeCheck: false,
    authorizeBy: null,
    dataversion: null,
    logisticCode: null,
    dataSendOn: null,
    adjAmount: null,
    saveAndRelease: 1,
    partySLCode: null,
    partyGLCode: d.partyGLCode,
    intgCode: null,
    crationTime: null,
    priceType: 'C',
    priceListCode: d.priceListCode,
    discountFactor: itemDetails[0] ? itemDetails[0].factor || 0 : 0,
    priceRoundOff: null,
    roundOffLimit: 'N',
    inclVATInDist: 'Y',
    discountMode: 'D',
    discountBasis: 'B',
    ignoreBatchExpiryCheck: false,
    items,
    deletedItems: [],
    attachedFiles: { timestamp: 0, docType: '', docCode: 0, files: [] },
    udfDetails: [],
  };

  res = apiPost(credential, `${apiPrefix}/SI/Save`, saveBody, { isFIFO: false, sessionId: `${Date.now()}_${__VU}${iterationTag}` }, 'SI/Save');
  const saveOk = checkEnvelope(res, 'SI/Save', {
    'SI/Save: schemeDocNo present': (r) => {
      try {
        return !!JSON.parse(r.body).result.schemeDocNo;
      } catch (e) {
        return false;
      }
    },
  });

  let schemeDocNo = null;
  try {
    schemeDocNo = JSON.parse(res.body).result.schemeDocNo;
  } catch (e) {
    // handled below
  }

  // Log line intentionally starts with "Save [" and ends with
  // "-> schemeDocNo=..." to match the existing reporting convention -
  // scripts/run-multi-tenant.ps1 greps exactly this shape to count documents
  // created, the same way it already does for serviceInvoiceAdhoc.js.
  console.log(`Save [VU ${__VU} iter ${iterationTag}] TransferOut dcCode=${dcCode} -> schemeDocNo=${schemeDocNo} saveOk=${saveOk}`);

  if (!schemeDocNo) {
    throw new Error(`SI/Save did not return a schemeDocNo (VU ${__VU} iter ${iterationTag}): ${res.body}`);
  }

  return { schemeDocNo };
}
