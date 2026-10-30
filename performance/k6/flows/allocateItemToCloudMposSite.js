import { SharedArray } from 'k6/data';
import { apiGet, apiPost, checkEnvelope, sessionId } from '../lib/http.js';
import { ginesysApiPrefix, TENANT } from '../lib/config.js';

const businessDataArr = new SharedArray('cloudMposSiteBusinessDataForAllocation', function () {
  return [JSON.parse(open(`../data/business/${TENANT}.site.json`))];
});
const d = businessDataArr[0];

function workDate() {
  return `${new Date().toISOString().slice(0, 10)}T00:00:00`;
}

// Live-discovers the tenant's Zwing/POS "Connected App" (code/appId) that
// item allocations are POSTed against - confirmed against the
// AllocateItemCloudMPOSSiteqahbros-erpapp.ginesys.cloud.har capture (qahbros:
// appName "ZWING", code 521, appId 2). This is OU-level, not per-site: a
// Cloud MPOS Site becomes servable by this app automatically once created
// with siteInstallationType "ZPOS" (see flows/createCloudMposSite.js) - the
// capture has no separate "attach site to app" mutating call, only this
// read-only lookup plus a read-only .../Sites/AllocatedZwingSites listing.
//
// ASSUMPTION TO VERIFY LIVE: the captured call also sent
// connectedUser=<a numeric admin user id>. That looks like the logged-in
// admin's own id (UI-descriptive), not a result filter, so it's passed here
// as 0 - confirm on the first live run that the same connected-app list
// still comes back; if not, this needs to decode the real user id the same
// way flows/createUser.js decodes RoleId from the JWT (lib/auth.js's
// decodeJwtClaims).
//
// Master data - call once (e.g. in a scenario's setup()), not per iteration.
export function resolveConnectedAppInfo(credential) {
  const date = workDate();

  const res = apiGet(
    credential,
    `${ginesysApiPrefix}/admin/ConnectedApps/list`,
    {
      availableSites: '',
      connectedSite: d.connectedSiteCode,
      connectedSiteOUCode: d.connectedSiteOUCode,
      workDate: date,
      connectedSiteType: d.connectedSiteType,
      typeSortCode: d.connectedAppTypeSortCode,
      connectedUser: 0,
      criteriaId: 0,
      page: 1,
      start: 0,
      limit: 500,
    },
    'admin/ConnectedApps/list'
  );
  checkEnvelope(res, 'admin/ConnectedApps/list');

  let apps = [];
  try {
    apps = JSON.parse(res.body).result;
  } catch (e) {
    throw new Error(`admin/ConnectedApps/list did not return usable items: ${res.body}`);
  }

  const app = apps.find((a) => a.appName === d.connectedAppName);
  if (!app) {
    throw new Error(
      `No connected app named "${d.connectedAppName}" found in admin/ConnectedApps/list: ${apps.map((a) => a.appName).join(', ')}`
    );
  }

  console.log(`resolveConnectedAppInfo: ${app.appName} connectedAppCode=${app.code} connectedAppId=${app.appId}`);

  return { connectedAppCode: app.code, connectedAppId: app.appId };
}

// Replays the item allocation journey: POST ConnectedPOSItems assigns one or
// more items (by icode) to the tenant's Connected App, making them available
// on every Cloud MPOS Site tied to that app - confirmed against the
// AllocateItemCloudMPOSSiteqahbros-erpapp.ginesys.cloud.har capture (icode
// US137107 -> "Assortment of Item saved successfully."), and confirmed live
// that a single call also accepts many items at once (tested up to 1000
// icodes in one request, ~3.2s, still 201/success) - allocatedItems was
// already an array in the capture, just never exercised with more than one
// entry there.
export function runAllocateItemBatchToCloudMposSiteFlow(credential, iterationTag, itemCodes, connectedAppInfo) {
  const res = apiPost(
    credential,
    `${ginesysApiPrefix}/admin/ConnectedPOSItems`,
    {
      connectedAppCode: connectedAppInfo.connectedAppCode,
      connectedAppId: connectedAppInfo.connectedAppId,
      allocatedItems: itemCodes.map((icode) => ({ code: 0, icode, isExtinct: false })),
    },
    { sessionId: sessionId() },
    'admin/ConnectedPOSItems'
  );
  const ok = checkEnvelope(res, 'admin/ConnectedPOSItems', {
    // Overrides checkEnvelope's default "status 200" check - confirmed live
    // (both in the HAR capture and against a real qahbros run) that this
    // endpoint returns 201 Created on success, not 200.
    'admin/ConnectedPOSItems: status 200': (r) => r.status === 200 || r.status === 201,
    'admin/ConnectedPOSItems: message present': (r) => {
      try {
        return !!JSON.parse(r.body).message;
      } catch (e) {
        return false;
      }
    },
  });

  let message = null;
  try {
    message = JSON.parse(res.body).message;
  } catch (e) {
    // handled below
  }

  console.log(
    `AllocateItemToCloudMposSite [VU ${__VU} iter ${iterationTag}] items=${itemCodes.length} connectedAppCode=${connectedAppInfo.connectedAppCode} -> message=${message} ok=${ok}`
  );

  if (!ok) {
    throw new Error(
      `ConnectedPOSItems failed for batch of ${itemCodes.length} items (VU ${__VU} iter ${iterationTag}): ${res.body}`
    );
  }

  itemCodes.forEach((icode) => console.log(`AllocatedPosItem: ${icode}|${connectedAppInfo.connectedAppCode}`));

  return { itemCodes, connectedAppCode: connectedAppInfo.connectedAppCode, connectedAppId: connectedAppInfo.connectedAppId };
}

// Single-item convenience wrapper around the batch flow above - kept for
// existing callers (flows/cloudMposSiteAllocationChain.js,
// scenarios/backfill/allocateItemToCloudMposSite.js) that allocate one resolved item
// at a time.
export function runAllocateItemToCloudMposSiteFlow(credential, iterationTag, itemCode, connectedAppInfo) {
  const result = runAllocateItemBatchToCloudMposSiteFlow(credential, iterationTag, [itemCode], connectedAppInfo);
  return { itemCode, connectedAppCode: result.connectedAppCode, connectedAppId: result.connectedAppId };
}
