import { apiGet, apiPost, checkEnvelope } from '../lib/http.js';
import { apiPrefix } from '../lib/config.js';
import { ensureLoggedIn, decodeJwtClaims } from '../lib/auth.js';

function workDate() {
  return `${new Date().toISOString().slice(0, 10)}T00:00:00`;
}

function dc() {
  return Date.now();
}

// All master data CreateAppUser needs turns out to be live-discoverable and
// tenant-agnostic - confirmed against the qafeature HAR capture: the sites[]
// it submitted was simply "every site GetSiteForAdmUser returned", not a
// hand-picked list, and roleId is just the provisioning admin's own RoleId
// JWT claim (2286 in that capture, matching the admin's own token). So this
// resolves everything live instead of hardcoding per-tenant numeric codes,
// letting the same flow work against qafeature, qahbros, or any future
// tenant given only a valid admin login + a default site code.
//
// Master data doesn't change per created user - call this once per run (e.g.
// from a scenario's setup()) and reuse the result, the same way
// lib/posSite.js's resolvePosSite() documents for site/stock-point data.
export function resolveUserMasterData(credential, defaultSiteCode) {
  const date = workDate();

  let res = apiGet(credential, `${apiPrefix}/ReportManagement/GetReportRoles`, { _dc: dc() }, 'ReportManagement/GetReportRoles');
  checkEnvelope(res, 'GetReportRoles');
  const reportRoles = JSON.parse(res.body).result;
  const adminReportRole = reportRoles.find((r) => r.role === 'Admin');
  if (!adminReportRole) {
    throw new Error(`GetReportRoles did not return a role named "Admin": ${res.body}`);
  }

  // Kept for parity with the captured UI sequence (grid-load preamble) -
  // roleId itself comes from the admin's own JWT claim, not this response.
  res = apiPost(
    credential,
    `${apiPrefix}/UserRolePolicy/GetListOfAdmRoles`,
    { workDate: date, page: 1, start: 0, limit: 20 },
    null,
    'UserRolePolicy/GetListOfAdmRoles'
  );
  checkEnvelope(res, 'GetListOfAdmRoles');

  res = apiPost(
    credential,
    `${apiPrefix}/Security/GetListOfProfiles`,
    { workDate: date, page: 1, start: 0, limit: 20 },
    null,
    'Security/GetListOfProfiles'
  );
  checkEnvelope(res, 'GetListOfProfiles');
  const profiles = JSON.parse(res.body).result.items;
  const defaultProfile = profiles.find((p) => p.profName === 'Default');
  if (!defaultProfile) {
    throw new Error(`GetListOfProfiles did not return a profile named "Default": ${res.body}`);
  }

  res = apiPost(
    credential,
    `${apiPrefix}/Security/GetListOfUserGroup`,
    { workDate: date, page: 1, start: 0, limit: 20 },
    null,
    'Security/GetListOfUserGroup'
  );
  checkEnvelope(res, 'GetListOfUserGroup');

  res = apiGet(
    credential,
    `${apiPrefix}/Security/GetSiteForAdmUser`,
    { id: 0, workDate: date, page: 1, start: 0, limit: 25 },
    'Security/GetSiteForAdmUser'
  );
  checkEnvelope(res, 'GetSiteForAdmUser');
  const allSites = JSON.parse(res.body).result;
  if (allSites.length === 0) {
    throw new Error('GetSiteForAdmUser returned no sites - nothing to assign to new users.');
  }
  const siteCodes = allSites.map((s) => s.code);
  if (!siteCodes.includes(Number(defaultSiteCode))) {
    throw new Error(
      `defaultSiteCode ${defaultSiteCode} is not among the sites GetSiteForAdmUser returned: ${siteCodes.join(', ')}`
    );
  }
  const sites = allSites.map((s) => ({
    Siteid: s.code,
    Ouid: s.ouCode,
    UserId: 0,
    DefaultSite: s.code === Number(defaultSiteCode) ? 'Y' : 'N',
    DefaultOU: 'N',
  }));

  res = apiGet(
    credential,
    `${apiPrefix}/Security/GetAssignedSiteForAdmUser`,
    { id: 0, workDate: date, page: 1, start: 0, limit: 25 },
    'Security/GetAssignedSiteForAdmUser'
  );
  checkEnvelope(res, 'GetAssignedSiteForAdmUser');

  const auth = ensureLoggedIn(credential);
  const claims = decodeJwtClaims(auth.accessToken2);
  const roleId = Number(claims.RoleId);
  if (!roleId) {
    throw new Error(`Could not decode a RoleId claim from the logged-in admin's token: ${JSON.stringify(claims)}`);
  }

  return {
    roleId,
    profCode: String(defaultProfile.code),
    deptCode: 0,
    reportRole: adminReportRole.code,
    sites,
  };
}

// Replays the per-user part of the Create App User journey: the two
// availability checks, then the actual create. Username/EmpNo/fullName/
// password all equal `username`, matching perftest1's own capture (and the
// existing weak-password QA convention already used by abarman/aranibarman).
export function runCreateUserFlow(credential, username, masterData) {
  let res = apiGet(
    credential,
    `${apiPrefix}/userrolepolicy/checkuserempno`,
    { _dc: dc(), id: 0, name: username },
    'userrolepolicy/checkuserempno'
  );
  checkEnvelope(res, 'checkuserempno');
  const empnoCheck = JSON.parse(res.body).result;
  if (!empnoCheck.valid) {
    console.log(`CreateUser: skipping "${username}" - empno check failed: ${empnoCheck.message}`);
    return { username, skipped: true, reason: 'empno taken' };
  }

  res = apiGet(
    credential,
    `${apiPrefix}/userrolepolicy/checkusername`,
    { _dc: dc(), id: 0, name: username },
    'userrolepolicy/checkusername'
  );
  checkEnvelope(res, 'checkusername');
  const usernameCheck = JSON.parse(res.body).result;
  if (!usernameCheck.valid) {
    console.log(`CreateUser: skipping "${username}" - username check failed: ${usernameCheck.message}`);
    return { username, skipped: true, reason: 'username taken' };
  }

  const createBody = {
    userId: 0,
    name: username,
    EmpNo: username,
    fullName: username,
    password: username,
    remarks: '',
    roleId: masterData.roleId,
    profCode: masterData.profCode,
    deptCode: masterData.deptCode,
    sites: masterData.sites,
    iSExtinct: false,
    locdays: null,
    reportRole: masterData.reportRole,
    allowLastFyDayMode: false,
    email: '',
    mobile: '',
    isd: '',
    source: '',
    intgid: '',
    isapiUser: false,
    apikey: null,
  };

  res = apiPost(credential, `${apiPrefix}/UserRolePolicy/CreateAppUser`, createBody, null, 'UserRolePolicy/CreateAppUser');
  const ok = checkEnvelope(res, 'CreateAppUser');

  let result = null;
  try {
    result = JSON.parse(res.body).result;
  } catch (e) {
    // leave result null, handled below
  }

  console.log(`CreateUser [VU ${__VU}] username=${username} -> result=${result} ok=${ok}`);

  if (!ok) {
    throw new Error(`CreateAppUser failed for "${username}": ${res.body}`);
  }

  // Every VU is an isolated k6 runtime, so a module-level "created users"
  // array doesn't survive into handleSummary() (same constraint documented
  // in scripts/list-created-docs.ps1 for schemeDocNo) - log a dedicated,
  // easily-parsed line instead and recover the full list from the console
  // log afterward (see scripts/list-seeded-users.ps1).
  console.log(`SeededUser: ${username}|${username}`);

  return { username, password: username, created: true, result };
}
