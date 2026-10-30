import { SharedArray } from 'k6/data';
import { apiGet, apiPost, checkEnvelope } from '../lib/http.js';
import { apiPrefix, TENANT } from '../lib/config.js';

const businessDataArr = new SharedArray('dcBusinessData', function () {
  return [JSON.parse(open(`../data/business/${TENANT}.dc.json`))];
});
const d = businessDataArr[0];

function workDate() {
  return `${new Date().toISOString().slice(0, 10)}T00:00:00`;
}

// Replays the DC (Delivery Challan / dispatch) journey. For Batch/Serial
// items, which unit currently has positive stock is discovered live via
// Batch/GetItemBatchSerialForPositiveStock rather than passed in - since this
// item was just created+GRC'd by this same iteration there is exactly one
// candidate, so no cross-VU race even under concurrency. Enforces
// ignoreNegativeStockCheck:false so the server rejects (rather than allows)
// an over-dispatch, and asserts the response reflects that.
export function runCreateDcFlow(credential, iterationTag, itemCode, itemType, pricing, qtyOverride) {
  const date = workDate();
  const dc = () => Date.now();
  const requestedQty = qtyOverride || d.dispatchQty;
  const negativeStockAlert = d.negativeStockAlertByItemType[itemType] || 'W';

  let liveRate = pricing.costrate;
  let liveMrp = pricing.mrp;
  let liveWsp = pricing.wsp;
  let batchSerialCode = null;
  let availableStockQty = null;

  if (itemType === 'B' || itemType === 'S') {
    const res = apiPost(
      credential,
      `${apiPrefix}/Batch/GetItemBatchSerialForPositiveStock`,
      {
        selectionMode: 'QTY',
        callFrom: 'DC',
        transactionDate: date,
        priceListCode: d.priceListCode,
        priceType: d.priceType,
        siteTaxCode: d.siteTaxCode,
        ouCode: d.ouCode,
        partyCode: null,
        basicRateIn: null,
        discountFactor: null,
        documentSiteCode: d.ownerSiteCode,
        siteCodeOwner: d.ownerSiteCode,
        siteCode: d.transactionSiteCode,
        documentStockPoint: d.outStockpointCode,
        pcType: 'P',
        showStock: true,
        duplicateItemCheckRequired: true,
        quantityRequired: true,
        checkNegativeStock: true,
        stockCode: d.outStockpointCode,
        saleType: d.saleType,
        dcCode: 0,
        SetAPIName: 'DC/GetSetItemsDetailsWithOpeningRate',
        binId: 0,
      },
      { itemId: itemCode },
      'Batch/GetItemBatchSerialForPositiveStock'
    );
    const ok = checkEnvelope(res, 'GetItemBatchSerialForPositiveStock', {
      'GetItemBatchSerialForPositiveStock: candidate with stock found': (r) => {
        try {
          return JSON.parse(r.body).result.length > 0;
        } catch (e) {
          return false;
        }
      },
    });
    if (!ok) {
      throw new Error(
        `No batch/serial unit with positive stock found for itemCode=${itemCode} (VU ${__VU} iter ${iterationTag}): ${res.body}`
      );
    }
    const candidate = JSON.parse(res.body).result[0];
    batchSerialCode = candidate.batchSerialCode;
    liveRate = candidate.rateCal;
    liveMrp = candidate.mrp;
    liveWsp = candidate.wsp;
    availableStockQty = candidate.stockQty;
  } else {
    const res = apiPost(
      credential,
      `${apiPrefix}/List/GetItemDetailWithOpeningRate`,
      {
        callFrom: 'DC',
        transactionDate: date,
        priceListCode: d.priceListCode,
        priceType: d.priceType,
        siteTaxCode: d.siteTaxCode,
        ouCode: d.ouCode,
        partyCode: null,
        basicRateIn: null,
        discountFactor: null,
        documentSiteCode: d.ownerSiteCode,
        siteCodeOwner: d.ownerSiteCode,
        siteCode: d.transactionSiteCode,
        documentStockPoint: null,
        pcType: 'P',
        showStock: true,
        duplicateItemCheckRequired: true,
        checkNegativeStock: true,
        enableScanSet: true,
        quantity: requestedQty,
        stockCode: d.outStockpointCode,
        baseItems: [],
        saleType: d.saleType,
        icode: itemCode,
        previouslyScannedQuantity: 0,
        batchOutApplicable: true,
      },
      null,
      'List/GetItemDetailWithOpeningRate'
    );
    checkEnvelope(res, 'GetItemDetailWithOpeningRate');
    try {
      const result = JSON.parse(res.body).result;
      liveRate = result.rate;
      liveMrp = result.mrp;
      liveWsp = result.wsp;
    } catch (e) {
      // fall back to the item's own creation pricing set above
    }
  }

  // The DC document code/packet-barcode are server-issued, not client-invented
  // - confirmed live: DC/GetPacketBarcode's response is the exact {code,label}
  // pair every successful real capture goes on to submit as DC/Save's
  // code/packetBarcode. Sending code:0 gets a real, graceful rejection
  // ("Delivery challan code or delivery challan barcode cannot be 0") -
  // proving qafeature's DC/Save endpoint works fine via k6, unlike Create.
  let res = apiGet(credential, `${apiPrefix}/DC/GetPacketBarcode`, { _dc: dc() }, 'DC/GetPacketBarcode');
  checkEnvelope(res, 'GetPacketBarcode');
  let docCode = 0;
  let packetBarcode = '';
  try {
    const result = JSON.parse(res.body).result;
    docCode = result.code;
    packetBarcode = result.label;
  } catch (e) {
    // leave docCode/packetBarcode as-is - the Save check below will fail loudly
  }

  res = apiGet(
    credential,
    `${apiPrefix}/DC/IsPreviouslyDeliveredItem`,
    {
      _dc: dc(),
      ownerOuCode: d.ouCode,
      siteCode: d.transactionSiteCode,
      dcDate: date,
      iCode: itemCode,
      batchSerialCode: batchSerialCode || '',
      barcode: itemCode,
      timeBetween: 0,
      dcCode: 0,
      pCode: '',
    },
    'DC/IsPreviouslyDeliveredItem'
  );
  checkEnvelope(res, 'IsPreviouslyDeliveredItem');

  const discount = Math.max(0, Math.round((pricing.costrate - liveRate) * 100) / 100);

  const item = {
    status: 'N',
    code: 0,
    itemId: itemCode,
    negativeStockAlert,
    barcode: itemCode,
    rate: liveRate,
    mrp: liveMrp,
    listedMrp: pricing.listedMrp,
    wsp: liveWsp,
    costRate: pricing.costrate,
    quantity: requestedQty,
    remarks: '',
    oldQty: 0,
    orderCode: 0,
    orderDetailCode: 0,
    pickListDetailCode: 0,
    binCode: 0,
    salOrdMainOrdDate: null,
    dataVersion: 0,
    recordIndex: 0,
    discountFactor: pricing.costrate ? Math.round((discount / pricing.costrate) * 100) : 0,
    discount,
    basicRate: pricing.costrate,
    roundOff: null,
    pendingQty: 0,
    hasExcessQuantity: false,
    batchSerialCode,
    itemManagementMode: itemType,
  };

  const saveBody = {
    code: docCode,
    entryMode: 'Add',
    docStatus: null,
    status: 'P',
    dcType: d.dcType,
    ownerSiteCode: d.ownerSiteCode,
    ownerSiteType: d.ownerSiteType,
    siteTaxCode: d.siteTaxCode,
    ouCode: d.ouCode,
    transactionSiteCode: d.transactionSiteCode,
    dcDate: date,
    packetBarcode,
    partyCode: d.partyCode || null,
    schemeDocNo: '',
    docCode: d.docSchemeCode,
    transporterCode: null,
    agentCode: null,
    outStockpointCode: d.outStockpointCode,
    priceListCode: d.priceListCode,
    priceType: d.priceType,
    tradeGrpCode: null,
    termCode: null,
    formCode: null,
    discountFactor: item.discountFactor,
    cmpTaxCodeBasis: d.cmpTaxCodeBasis,
    discountBasis: d.discountBasis,
    discountMode: d.discountMode,
    includeVatInDiscount: d.includeVatInDiscount,
    saleType: d.saleType,
    remarks: '',
    // The negative-stock business rule: must never silently allow an
    // over-dispatch. The server enforces it when this stays false.
    ignoreNegativeStockCheck: false,
    ignoreCreditLimitCheck: false,
    ignoreBatchExpiryCheck: false,
    dataversion: 0,
    allowPrint: true,
    allowPrintPacket: true,
    mdMenuShrtCode: d.menuCode,
    totalItemCount: 1,
    items: [item],
    deletedItems: [],
    attachedFiles: { timestamp: 0, docType: '', docCode: 0, files: [] },
    udfDetails: [],
  };

  res = apiPost(credential, `${apiPrefix}/DC/Save`, saveBody, { isFIFO: false, sessionId: `${Date.now()}_${__VU}${iterationTag}` }, 'DC/Save');
  const saveOk = checkEnvelope(res, 'DC/Save', {
    'DC/Save: schemeDocNo present': (r) => {
      try {
        return !!JSON.parse(r.body).result.schemeDocNo;
      } catch (e) {
        return false;
      }
    },
    'DC/Save: negative stock not silently allowed': (r) => {
      try {
        const result = JSON.parse(r.body).result;
        if (requestedQty <= (availableStockQty === null ? Infinity : availableStockQty)) return true;
        // We deliberately over-requested - the server must have blocked it.
        return !JSON.parse(r.body).success || !!result.negativeStockResultForUI || !!result.stockCheckResult;
      } catch (e) {
        return false;
      }
    },
  });

  let dcCode = null;
  let schemeDocNo = null;
  try {
    const result = JSON.parse(res.body).result;
    dcCode = result.dcCode;
    schemeDocNo = result.schemeDocNo;
  } catch (e) {
    // handled below
  }

  console.log(
    `CreateDc [VU ${__VU} iter ${iterationTag}] itemCode=${itemCode} qty=${requestedQty} negativeStockAlert=${negativeStockAlert} -> dcCode=${dcCode} schemeDocNo=${schemeDocNo} saveOk=${saveOk}`
  );

  if (!dcCode) {
    throw new Error(`DC/Save did not return a dcCode (VU ${__VU} iter ${iterationTag}): ${res.body}`);
  }

  return { dcCode, schemeDocNo };
}
