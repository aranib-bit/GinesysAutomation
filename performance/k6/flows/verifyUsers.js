import { apiPost } from '../lib/http.js';
import { apiPrefix } from '../lib/config.js';
import { loginWithCredential } from '../lib/auth.js';

function workDate() {
  return `${new Date().toISOString().slice(0, 10)}T00:00:00`;
}

// Paginates GlobalUser/GetListOfGlobalAppEmpUsers (the same grid-refresh
// endpoint the real UI calls after CreateAppUser, seen in the original HAR
// with totalCount/items) to get every app user actually on the tenant right
// now - not just the ones this session created, since other testers/prior
// sessions may already own accounts in the perftestN numeric range.
export function listAllAppUsers(credential, masterData, defaultSiteCode) {
  const date = workDate();
  const availableSites = masterData.sites.map((s) => s.Siteid);
  const defaultSite = masterData.sites.find((s) => s.Siteid === Number(defaultSiteCode));
  const connectedSiteOUCode = defaultSite ? defaultSite.Ouid : 1;

  const limit = 50;
  let start = 0;
  let totalCount = Infinity;
  const items = [];

  while (start < totalCount) {
    const res = apiPost(
      credential,
      `${apiPrefix}/GlobalUser/GetListOfGlobalAppEmpUsers`,
      {
        availableSites,
        connectedSite: String(defaultSiteCode),
        connectedSiteOUCode,
        workDate: date,
        page: Math.floor(start / limit) + 1,
        start,
        limit,
      },
      null,
      'GlobalUser/GetListOfGlobalAppEmpUsers'
    );
    const body = JSON.parse(res.body);
    if (!body.success) {
      throw new Error(`GetListOfGlobalAppEmpUsers failed at start=${start}: ${res.body}`);
    }
    totalCount = body.result.totalCount;
    items.push(...body.result.items);
    start += limit;
  }

  return items;
}

// A username existing doesn't mean we know its real password - only accounts
// this session itself created are guaranteed password===username. For any
// other perftestN account already sitting on the tenant, the only trustworthy
// way to add it to the credential pool is to actually prove the password
// live, via a real login (loginWithCredential already retries the code-012
// "already logged in elsewhere" case, same as every other flow in this repo).
export function verifyPerfTestLogin(username) {
  try {
    loginWithCredential({ username, password: username });
    return true;
  } catch (e) {
    return false;
  }
}
