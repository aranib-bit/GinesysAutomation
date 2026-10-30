import { apiGet, apiPost, checkEnvelope } from './http.js';
import { apiPrefix } from './config.js';

function workDate() {
  return `${new Date().toISOString().slice(0, 10)}T00:00:00`;
}

// Live-discovers a POS site + one of its stock points, the same way the
// codebase already live-discovers whetherConsignment/vendorTaxRegionCode
// instead of hardcoding them - site codes drift, and the exact live filter
// used here (isPos==='Y' && ext==='N' && siteType==='MS-OO-CM') is confirmed
// to match the SQL filter used to seed real POS sites in idealdb.
//
// Site/stock-point master data doesn't change per-iteration, so callers
// should invoke this once (e.g. in a scenario's setup()) and reuse the
// result across VUs/iterations, not call it every GRC.
export function resolvePosSite(credential, businessData, opts) {
  const options = opts || {};
  const date = workDate();

  const siteRes = apiPost(
    credential,
    `${apiPrefix}/Shared/GetOwnerSiteLookupForMangedAndOrganization`,
    {
      extraQuery: '1',
      queryFieldName: ['name', 'shrtname', 'siteType'],
      availableSites: businessData.availableSites || [],
      connectedSite: String(businessData.connectedSiteCode),
      workDate: date,
      connectedSiteOUCode: businessData.connectedSiteOUCode,
      connectedSiteType: businessData.connectedSiteType,
      page: 1,
      start: 0,
      limit: 25,
      sort: '[{"property":"name","direction":"ASC"}]',
    },
    null,
    'Shared/GetOwnerSiteLookupForMangedAndOrganization'
  );
  checkEnvelope(siteRes, 'GetOwnerSiteLookupForMangedAndOrganization');

  let items = [];
  try {
    items = JSON.parse(siteRes.body).result.items;
  } catch (e) {
    throw new Error(`GetOwnerSiteLookupForMangedAndOrganization did not return usable items: ${siteRes.body}`);
  }

  const candidates = items
    .filter((it) => it.isPos === 'Y' && it.ext === 'N' && it.siteType === 'MS-OO-CM')
    .sort((a, b) => a.code - b.code);

  if (candidates.length === 0) {
    throw new Error(
      "No POS site found matching isPos='Y' && ext='N' && siteType='MS-OO-CM' in GetOwnerSiteLookupForMangedAndOrganization's response."
    );
  }

  const siteCodeOverride = __ENV.POS_SITE_CODE ? Number(__ENV.POS_SITE_CODE) : null;
  const chosenSite = siteCodeOverride ? candidates.find((c) => c.code === siteCodeOverride) : candidates[0];
  if (!chosenSite) {
    throw new Error(`POS_SITE_CODE=${siteCodeOverride} is not among the live POS-site candidates: ${candidates.map((c) => c.code).join(', ')}`);
  }

  const stockRes = apiPost(
    credential,
    `${apiPrefix}/Shared/GetAllStockPointForASiteType`,
    {
      extraQuery: null,
      queryFieldName: ['name', 'typeDisplay', 'siteName'],
      availableSites: businessData.availableSites || [],
      connectedSite: chosenSite.code,
      workDate: date,
      connectedSiteOUCode: businessData.connectedSiteOUCode,
      connectedSiteType: businessData.connectedSiteType,
      page: 1,
      start: 0,
      limit: 25,
    },
    null,
    'Shared/GetAllStockPointForASiteType'
  );
  checkEnvelope(stockRes, 'GetAllStockPointForASiteType');

  let stockPoints = [];
  try {
    stockPoints = JSON.parse(stockRes.body).result.items;
  } catch (e) {
    throw new Error(`GetAllStockPointForASiteType did not return usable items: ${stockRes.body}`);
  }
  if (stockPoints.length === 0) {
    throw new Error(`No stock points found for POS site ${chosenSite.code}.`);
  }

  const stockpointTypeHint = options.stockpointType || __ENV.POS_STOCKPOINT_TYPE || 'Godown';
  const chosenStockPoint = stockPoints.find((sp) => sp.typeDisplay === stockpointTypeHint) || stockPoints[0];

  console.log(
    `resolvePosSite: chose site ${chosenSite.code} (${chosenSite.name}, isPos=${chosenSite.isPos}, siteType=${chosenSite.siteType}) ` +
      `stockPoint ${chosenStockPoint.code} (${chosenStockPoint.name}/${chosenStockPoint.typeDisplay}, locCode=${chosenStockPoint.locCode})`
  );

  return {
    code: chosenSite.code,
    siteType: chosenSite.siteType,
    admouCode: chosenSite.admou_Code,
    siteTaxCode: chosenSite.siteTaxCode,
    // Two distinct identifiers for the same stock point, used in different
    // places - confirmed live (a numeric-string mismatch here throws a
    // server-side type-conversion error, not a silent failure):
    // - stockPointCode (string, e.g. "MNABTP1-2"): GRC/Save's psiteStockpoint.code.
    // - stockPointLocCode (numeric, e.g. 1): the "stockCode"/"documentStockPoint"
    //   param on GetItemDetailWithOpeningRate/ValidateAndCreateBachSerialNo -
    //   the original POS HAR used stockCode:1 there, not the composite string code.
    stockPointCode: chosenStockPoint.code,
    stockPointLocCode: chosenStockPoint.locCode,
  };
}

// Live-discovers the full doc-series/ledger code lists a new site's
// site/create payload needs. Confirmed against the CreateCloudMPOSSite HAR
// capture: the real UI's siteDocSeriesAssigns/siteLedgerAssigns arrays are
// simply *every* code list/sitedocnosources and list/siteledgersources
// return - not a hand-picked subset - so this fetches those two lookups
// once and forwards every code, the same "resolve live, don't hardcode"
// approach used elsewhere in this file/codebase for drift-prone tenant data.
//
// This is master data, not per-iteration - call it once (e.g. in a
// scenario's setup()) and reuse the result across all created sites.
export function resolveSiteMasterData(credential) {
  const date = workDate();

  const docRes = apiGet(
    credential,
    `${apiPrefix}/list/sitedocnosources`,
    { id: 0, workDate: date, page: 1, start: 0, limit: 500 },
    'list/sitedocnosources'
  );
  checkEnvelope(docRes, 'list/sitedocnosources');
  let siteDocSeriesAssigns = [];
  try {
    siteDocSeriesAssigns = JSON.parse(docRes.body).result.map((it) => it.code);
  } catch (e) {
    throw new Error(`list/sitedocnosources did not return usable items: ${docRes.body}`);
  }
  if (siteDocSeriesAssigns.length === 0) {
    throw new Error('list/sitedocnosources returned no doc-series codes.');
  }

  const ledgerRes = apiGet(
    credential,
    `${apiPrefix}/list/siteledgersources`,
    { id: 0, workDate: date, page: 1, start: 0, limit: 500 },
    'list/siteledgersources'
  );
  checkEnvelope(ledgerRes, 'list/siteledgersources');
  let siteLedgerAssigns = [];
  try {
    siteLedgerAssigns = JSON.parse(ledgerRes.body).result.map((it) => it.code);
  } catch (e) {
    throw new Error(`list/siteledgersources did not return usable items: ${ledgerRes.body}`);
  }
  if (siteLedgerAssigns.length === 0) {
    throw new Error('list/siteledgersources returned no ledger codes.');
  }

  console.log(
    `resolveSiteMasterData: ${siteDocSeriesAssigns.length} doc-series codes, ${siteLedgerAssigns.length} ledger codes`
  );

  return { siteDocSeriesAssigns, siteLedgerAssigns };
}
