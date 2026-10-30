// Bulk-creates SSO users (perftest<N>) on the SSO admin API, replaying the
// 4-call flow captured in CreateSSOUsersadmin.qa-sso.ginesys.one.har:
//   login (once, setup) -> POST users -> POST enterprise-users -> POST instances/attach
//
// Standalone on purpose: this talks to the SSO admin API, not a tenant, so it
// does not use lib/http.js / lib/auth.js (tenant-specific).
//
// Run (cmd):
//   k6 run -e SSO_ADMIN_PASSWORD=<pwd> -e START=1 -e END=2000 scenarios\backfill\createSsoUsers.js
//
// Rate: each user = 3 API calls. USERS_PER_SEC=10 -> ~30 req/s, well under the
// 100 req/s API limit. Keep USERS_PER_SEC <= 25 to stay safe.
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const ADMIN_PASSWORD = __ENV.SSO_ADMIN_PASSWORD;
const ADMIN_USERNAME = __ENV.SSO_ADMIN_USERNAME || 'admin@ginesys.one';
const API = (__ENV.SSO_API_URL || 'https://api.qa-sso.ginesys.one').replace(/\/$/, '');
const ORIGIN = __ENV.SSO_ORIGIN || 'https://admin.qa-sso.ginesys.one';
const ENTERPRISE_ID = __ENV.ENTERPRISE_ID || '01KZDY6JKV2RN8DF37M89Y43MQ';
const INSTANCE_ID = __ENV.INSTANCE_ID || '01KZE2TZT75P89FQV4321AY14W';
const USER_PASSWORD = __ENV.USER_PASSWORD || 'Default1@';
const ACCESS_TYPE = __ENV.ACCESS_TYPE || 'ADMIN';
const EMAIL_DOMAIN = __ENV.EMAIL_DOMAIN || 'gmail.com';
const NAME_PREFIX = __ENV.NAME_PREFIX || 'perftest';
const FIRST_NAME = __ENV.FIRST_NAME || 'perf';
const LAST_NAME_PREFIX = __ENV.LAST_NAME_PREFIX || 'test';
const PHONE_BASE = Number(__ENV.PHONE_BASE) || 1000000000; // N=1 -> +91 1000000001
const AUTH_SCHEME = __ENV.AUTH_SCHEME === undefined ? 'Bearer ' : __ENV.AUTH_SCHEME;

const START = Number(__ENV.START) || 1;
const END = Number(__ENV.END) || 2000;
const USERS_PER_SEC = Number(__ENV.USERS_PER_SEC) || 10;
const TOTAL = END - START + 1;

export const created = new Counter('sso_users_created');
export const skipped = new Counter('sso_users_resumed');
export const failed = new Counter('sso_users_failed');

export const options = {
  scenarios: {
    createUsers: {
      executor: 'constant-arrival-rate',
      rate: USERS_PER_SEC,
      timeUnit: '1s',
      duration: `${Math.ceil(TOTAL / USERS_PER_SEC) + 1}s`,
      preAllocatedVUs: Math.max(10, USERS_PER_SEC * 2),
      // Each VU is held for the whole 3-call chain, so VUs needed ~= users/s x seconds per user.
      // Raise MAX_VUS if k6 warns "Insufficient VUs" (the API is slower than expected).
      maxVUs: Number(__ENV.MAX_VUS) || USERS_PER_SEC * 6,
      // Let in-flight 3-call chains finish at the end instead of being cut mid-chain
      // (a cut chain leaves a user created but not linked/attached).
      gracefulStop: __ENV.GRACEFUL_STOP || '180s',
    },
  },
  thresholds: { sso_users_failed: ['count==0'] },
};

function baseHeaders(token) {
  const h = { 'Content-Type': 'application/json', Accept: 'application/json', Origin: ORIGIN, Referer: `${ORIGIN}/` };
  if (token) h.Authorization = `${AUTH_SCHEME}${token}`;
  return h;
}

export function setup() {
  if (!ADMIN_PASSWORD) {
    throw new Error('SSO_ADMIN_PASSWORD is required: k6 run -e SSO_ADMIN_PASSWORD=<pwd> ...');
  }
  const res = http.post(
    `${API}/auth/a/login`,
    JSON.stringify({ username: ADMIN_USERNAME, password: ADMIN_PASSWORD, strategy: 'email-password' }),
    { headers: baseHeaders() }
  );
  const token = res.status < 300 && res.json('data.token');
  if (!token) {
    throw new Error(`SSO admin login failed: HTTP ${res.status} ${String(res.body).slice(0, 200)}`);
  }
  return { token };
}

function post(path, body, token, n, step) {
  const res = http.post(`${API}${path}`, JSON.stringify(body), { headers: baseHeaders(token), tags: { step } });
  return res;
}

function get(path, token, step) {
  return http.get(`${API}${path}`, { headers: baseHeaders(token), tags: { step } });
}

function isDuplicate(res) {
  return res.status === 409 || (res.status === 400 && /exist|already|duplicate|taken/i.test(String(res.body)));
}

// Resume support: find an already-created user by username.
function findUserId(username, token) {
  const r = get(`/users/a/users?perPage=20&page=1&q=${encodeURIComponent(username)}`, token, 'findUser');
  if (r.status !== 200) return null;
  const rows = r.json('data') || [];
  const hit = rows.find((u) => u.username === username);
  return hit ? hit.id : null;
}

// Resume support: find the enterprise-user row for an already-linked user.
function findEnterpriseUserId(userId, username, token) {
  const r = get(
    `/enterprises/a/enterprise-users?include=user&enterpriseId=${ENTERPRISE_ID}&perPage=20&page=1&q=${encodeURIComponent(username)}`,
    token, 'findEnterpriseUser'
  );
  if (r.status !== 200) return null;
  const rows = r.json('data') || [];
  const hit = rows.find((e) => e.user && e.user.id === userId);
  return hit ? hit.id : null;
}

function ok(res) {
  return res.status >= 200 && res.status < 300 && res.json('success') !== false;
}

function fail(n, step, res) {
  failed.add(1);
  console.error(`FAILED n=${n} step=${step} HTTP ${res.status} ${String(res.body).slice(0, 200)}`);
}

export default function (data) {
  const n = START + exec.scenario.iterationInTest;
  if (n > END) return;
  const token = data.token;
  const username = `${NAME_PREFIX}${n}`;

  // 1) create user
  const userRes = post(
    '/users/a/users',
    {
      firstName: FIRST_NAME,
      lastName: `${LAST_NAME_PREFIX}${n}`,
      email: `${username}@${EMAIL_DOMAIN}`,
      username,
      phoneNumber: `+91 ${PHONE_BASE + n}`,
      isTestUser: false,
    },
    token, n, 'createUser'
  );
  let userId;
  let resumed = false;
  if (ok(userRes)) {
    userId = userRes.json('data.id');
  } else if (isDuplicate(userRes)) {
    // Already created (earlier/interrupted run): resume the remaining steps instead of skipping.
    userId = findUserId(username, token);
    if (!userId) return fail(n, 'findUser', userRes);
    resumed = true;
  } else {
    return fail(n, 'createUser', userRes);
  }

  // 2) link to enterprise
  const euRes = post(
    '/enterprises/a/enterprise-users',
    {
      enterpriseId: ENTERPRISE_ID,
      userId,
      accessType: ACCESS_TYPE,
      passwordPolicy: { generationType: 'MANUAL', resetOnFirstLogin: false, useDefaultPasswordStrength: true },
      password: USER_PASSWORD,
    },
    token, n, 'linkEnterprise'
  );
  let enterpriseUserId;
  if (ok(euRes)) {
    enterpriseUserId = euRes.json('data.id');
  } else if (isDuplicate(euRes)) {
    enterpriseUserId = findEnterpriseUserId(userId, username, token);
    if (!enterpriseUserId) return fail(n, 'findEnterpriseUser', euRes);
    resumed = true;
  } else {
    return fail(n, 'linkEnterprise', euRes);
  }

  // 3) attach to instance
  const attachRes = post(
    '/enterprises/a/instances/attach',
    { enterpriseId: ENTERPRISE_ID, enterpriseUserId, instanceIds: [INSTANCE_ID], instanceRoles: {} },
    token, n, 'attachInstance'
  );
  if (!ok(attachRes) && !isDuplicate(attachRes)) return fail(n, 'attachInstance', attachRes);

  if (resumed) skipped.add(1);
  created.add(1);
  check(attachRes, { 'user fully provisioned': () => true });
}

export function handleSummary(data) {
  const v = (m) => (data.metrics[m] && data.metrics[m].values.count) || 0;
  const reqRate = data.metrics.http_reqs ? data.metrics.http_reqs.values.rate.toFixed(1) : 'n/a';
  const msg =
    `\nSSO user creation (${START}..${END}): created=${v('sso_users_created')} ` +
    `resumed(already existed)=${v('sso_users_resumed')} failed=${v('sso_users_failed')} ` +
    `avg req/s=${reqRate}\n` +
    (v('sso_users_failed') > 0 ? 'Failures: see FAILED lines above; rerun with -e START=<n> -e END=<n> for those users.\n' : '');
  return { stdout: msg };
}
