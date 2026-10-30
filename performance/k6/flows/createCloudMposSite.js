import { SharedArray } from 'k6/data';
import { apiGet, apiPost, checkEnvelope } from '../lib/http.js';
import { apiPrefix, TENANT } from '../lib/config.js';

const businessDataArr = new SharedArray('cloudMposSiteBusinessData', function () {
  return [JSON.parse(open(`../data/business/${TENANT}.site.json`))];
});
const d = businessDataArr[0];

function workDate() {
  return `${new Date().toISOString().slice(0, 10)}T00:00:00`;
}

function padNum(num, width) {
  return String(num).padStart(width, '0');
}

// Replays the Cloud MPOS Site creation journey: the 3 live availability
// checks (siteInitial/shortName/name), then site/create, then
// CopyReplenishmentSourceSite - confirmed against the
// CreateCloudMPOSSiteqahbros-erpapp.ginesys.cloud.har capture (site CM002).
// siteDocSeriesAssigns/siteLedgerAssigns come from lib/posSite.js's
// resolveSiteMasterData() (call once in setup(), not per iteration - master
// data doesn't change per created site, same convention as resolvePosSite()/
// resolveUserMasterData()).
//
// The numeric suffix (CM002 -> CM003 -> ...) is startIndex + iterationTag,
// the same "single-VU, START_INDEX + __ITER" pattern already used by
// scenarios/backfill/seedPerfUsers.js to generate collision-free sequential entities.
export function runCreateCloudMposSiteFlow(credential, iterationTag, masterData, startIndexOverride) {
  const date = workDate();
  const dc = () => Date.now();

  const startIndex = startIndexOverride !== undefined ? Number(startIndexOverride) : d.startIndex;
  const suffixNum = startIndex + Number(iterationTag);
  const suffix = padNum(suffixNum, d.sitePadWidth);
  const shortName = `${d.sitePrefix}${suffix}`;
  const siteInitial = `${d.cuidPrefix}${shortName}`;
  const name = `${d.siteNamePrefix} ${shortName}`;
  const mobile = `${d.mobilePrefix}${suffix}`;

  let res = apiGet(credential, `${apiPrefix}/site/checksiteinitial`, { _dc: dc(), id: 0, name: siteInitial }, 'site/checksiteinitial');
  checkEnvelope(res, 'site/checksiteinitial');
  let initialCheck = { valid: false, message: 'unparseable response' };
  try {
    initialCheck = JSON.parse(res.body).result;
  } catch (e) {
    // handled by the !valid check below
  }
  if (!initialCheck.valid) {
    console.log(`CreateCloudMposSite: skipping "${siteInitial}" - siteInitial check failed: ${initialCheck.message}`);
    return { skipped: true, reason: 'siteInitial taken', shortName };
  }

  res = apiGet(credential, `${apiPrefix}/site/checksiteshortname`, { _dc: dc(), id: 0, name: shortName }, 'site/checksiteshortname');
  checkEnvelope(res, 'site/checksiteshortname');
  let shortNameCheck = { valid: false, message: 'unparseable response' };
  try {
    shortNameCheck = JSON.parse(res.body).result;
  } catch (e) {
    // handled by the !valid check below
  }
  if (!shortNameCheck.valid) {
    console.log(`CreateCloudMposSite: skipping "${shortName}" - shortName check failed: ${shortNameCheck.message}`);
    return { skipped: true, reason: 'shortName taken', shortName };
  }

  res = apiGet(credential, `${apiPrefix}/site/checkname`, { _dc: dc(), id: 0, name }, 'site/checkname');
  checkEnvelope(res, 'site/checkname');
  let nameCheck = { valid: false, message: 'unparseable response' };
  try {
    nameCheck = JSON.parse(res.body).result;
  } catch (e) {
    // handled by the !valid check below
  }
  if (!nameCheck.valid) {
    console.log(`CreateCloudMposSite: skipping "${name}" - name check failed: ${nameCheck.message}`);
    return { skipped: true, reason: 'name taken', shortName };
  }

  const createBody = {
    admoucodeCode: d.admoucodeCode,
    code: 0,
    cmpCode: 0,
    name,
    shortName,
    address: null,
    cityName: null,
    contactPerson: d.contactPerson,
    cp: null,
    cstDate: null,
    cstNo: null,
    customerCode: null,
    dataVersion: '',
    email1: null,
    email2: null,
    fax: null,
    gateInApplicable: false,
    isCentrallyManaged: false,
    isExtinct: false,
    isLogisticApplicable: false,
    isPermitApplicable: false,
    isPromosendBlocked: false,
    isSalesPersonManaged: false,
    isSecondary: false,
    ispos: false,
    isdCode: d.isdCode,
    lstDate: null,
    lstNo: null,
    mobile,
    modeOfOperation: d.modeOfOperation,
    operationstartdate: null,
    oph1: null,
    oph2: null,
    oph3: null,
    outLogisticApplicable: false,
    outPermitApplicable: false,
    pin: null,
    priceFactor: 0,
    priceType: d.priceType,
    promoAllocationCode: null,
    rph1: null,
    saleTermCode: null,
    saleTradeGroupCode: null,
    siteCompanyTaxCode: d.siteCompanyTaxCode,
    siteGroupCode: null,
    siteInitial,
    siteIntransitLoccode: null,
    siteIsPublished: false,
    siteKey: '',
    localPolicyCode: null,
    siteMasterAllocationCode: null,
    siteStopSync: false,
    siteTimeZone: null,
    siteTransitLocationCode: null,
    siteType: d.siteType,
    stockPointCode: null,
    storeCloseDate: null,
    storeSize: null,
    storeStartDate: null,
    storeTargetSales: null,
    transitDays: 0,
    transporterCode: null,
    version: null,
    website: null,
    cpgstincode: null,
    cpgstinno: null,
    cpgstindate: null,
    cpgstinstatecode: null,
    siteDocIN: null,
    shipLegalName: null,
    isBillingShippingSame: d.isBillingShippingSame,
    shipCPCategoryCode: d.shipCPCategoryCode,
    shipCPGSTINNo: null,
    shipCPGSTINStateCode: null,
    shipCPGSTINDate: null,
    shipCINNo: null,
    shipAddress: null,
    shipCTName: null,
    shipPin: null,
    shipOPh1: null,
    shipOPh2: null,
    shipOPh3: null,
    shipRPh1: null,
    shipMobile: null,
    shipFax: null,
    shipEmail1: null,
    shipEmail2: null,
    shipWebsite: null,
    shipContactPerson: null,
    siteInstallationType: d.siteInstallationType,
    gstinTaxpayerType: null,
    gstinStatus: null,
    gstinVerifiedOn: null,
    shipGstinVerifiedOn: null,
    replenishmentAssigns: [],
    siteDocSeriesAssigns: masterData.siteDocSeriesAssigns,
    siteLedgerAssigns: masterData.siteLedgerAssigns,
    sitePluginAssigns: [],
    udfDetails: [],
  };

  res = apiPost(credential, `${apiPrefix}/site/create`, createBody, null, 'site/create');
  const createOk = checkEnvelope(res, 'site/create');

  let siteCode = null;
  try {
    siteCode = JSON.parse(res.body).result.id;
  } catch (e) {
    // handled below
  }

  if (!siteCode) {
    throw new Error(`site/create did not return an id for "${name}" (VU ${__VU} iter ${iterationTag}): ${res.body}`);
  }

  const replenishBody = {
    name,
    siteCode,
    ouCode: d.admoucodeCode,
    siteType: d.siteType,
    replenishmentAssigns: [
      {
        code: d.replenishmentSourceCode,
        formCode: null,
        gateinApplicable: false,
        inLgtApplicable: false,
        includeTaxInCost: 0,
        isExtinct: false,
        outLgtApplicable: false,
        returnPricelistCode: null,
        returnRateBasis: 'L',
        salPricelistCode: d.replenishmentSalPricelistCode,
        salTermCode: null,
        salTradeGroupCode: null,
        strictlyFollowTransitRoute: false,
        transitViaCode: null,
        packetCreationMode: 'Adhoc',
        defaultDueDaysForTrnOrder: null,
        isDefault: 0,
      },
    ],
    siteInstallationType: d.siteInstallationType,
  };

  res = apiPost(credential, `${apiPrefix}/site/CopyReplenishmentSourceSite`, replenishBody, null, 'site/CopyReplenishmentSourceSite');
  // Non-fatal: the site itself already exists at this point even if
  // replenishment-source tagging fails.
  checkEnvelope(res, 'site/CopyReplenishmentSourceSite');

  console.log(`CreateCloudMposSite [VU ${__VU} iter ${iterationTag}] name=${name} -> siteCode=${siteCode} createOk=${createOk}`);
  console.log(`CreatedCloudMposSite: ${shortName}|${siteCode}`);

  return { siteCode, siteInitial, shortName, name };
}
