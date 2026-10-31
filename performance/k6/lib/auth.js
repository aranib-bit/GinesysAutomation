import http from 'k6/http';
import { check } from 'k6';
import { b64decode } from 'k6/encoding';
import { baseUrl, loginPath, apiPrefix } from './config.js';

// Per-VU cache: k6 re-executes this module's init code for every VU, so this
// is naturally a fresh cache per VU, not shared/global - login happens once
// per VU and is reused across that VU's iterations.
let cachedAuth = null;

function parseErrorDescription(errorDescription) {
  // Ginesys encodes login failures as "<code>|<message>|<tag>", e.g. code 012
  // means "user already logged in elsewhere" and tag is that session's userId.
  if (!errorDescription) return null;
  const parts = errorDescription.split('|');
  return { code: parts[0], message: parts[1], tag: parts[2] };
}

// Same convention as lib/http.js: -e DEBUG_HTTP=true logs request/response
// via console.log, meant to be captured with `k6 run --console-output=...`.
// Never logs the password or the live bearer tokens, even in debug mode -
// this file is meant to be shareable for debugging without leaking session
// credentials.
function redactTokens(body) {
  if (!body) return body;
  return body.replace(/("(?:access_token|accessToken|encryptedAccessToken)2?"\s*:\s*")[^"]*(")/g, '$1***REDACTED***$2');
}

function debugLog(method, url, res) {
  if (!__ENV.DEBUG_HTTP) return;
  console.log(`[HTTP] ${method} ${url} -> ${res.status}\n  res: ${redactTokens(res.body)}`);
}

function performLogin(credential) {
  if (credential.loginType === 'SSO') {
    // Never fall back to the ERP login for an SSO user: it would fail with a misleading bad-credentials error.
    throw new Error(
      `User ${credential.username} has loginType SSO, but the SSO login flow is not implemented yet (the ERP-via-SSO request/response `
        + `has not been captured - an HAR of an SSO user logging in to the ERP is needed). SSO URL from the users file: ${credential.ssoLoginUrl || '(none configured)'}.`
    );
  }
  const body = `grant_type=password&username=${encodeURIComponent(
    credential.username
  )}&password=${encodeURIComponent(credential.password)}`;
  const res = http.post(`${baseUrl}${loginPath}`, body, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    tags: { endpoint: 'token' },
    // A 400 here can be a legitimate "already logged in elsewhere" (code 012)
    // that we handle below via killSession + retry - don't let it pollute the
    // http_req_failed metric/threshold as if it were an unhandled error.
    responseCallback: http.expectedStatuses(200, 400),
  });
  debugLog('POST', `${baseUrl}${loginPath}`, res);
  return res;
}

function killSession(tag) {
  // Reverse-engineered from app.js, not yet observed live (see plan's Risks
  // section) - the dedicated per-VU credential pool should make this path
  // rare in practice.
  const url = `${baseUrl}${apiPrefix}/Session/KillUser?userId=${tag}`;
  const res = http.get(url, { tags: { endpoint: 'Session/KillUser' } });
  debugLog('GET', url, res);
  return res;
}

// Full login flow (including the 012/KillUser retry), independent of caching -
// used both by ensureLoggedIn() (pool mode, one login per VU) and by a
// scenario's setup() when it wants to log in exactly once and hand the same
// token pair to every VU (shared-token mode, see useSharedAuth below).
export function loginWithCredential(credential) {
  let res = performLogin(credential);

  if (res.status !== 200) {
    let parsedError = null;
    try {
      parsedError = parseErrorDescription(JSON.parse(res.body).error_description);
    } catch (e) {
      // response wasn't the expected JSON shape - fall through to hard failure below
    }

    if (parsedError && parsedError.code === '012' && parsedError.tag) {
      killSession(parsedError.tag);
      res = performLogin(credential);
    }
  }

  const ok = check(res, { 'login succeeded': (r) => r.status === 200 });
  if (!ok) {
    throw new Error(`Login failed for user ${credential.username}: HTTP ${res.status} - ${res.body}`);
  }

  // Response key naming differs by deployment mode: stage-ideal (singleTenant)
  // uses snake_case (access_token/access_token2); qafeature/qahbros
  // (multiTenant) use camelCase (accessToken/accessToken2), confirmed live via
  // Loginqafeature-erpapp.ginesys.cloud.har - without this fallback, ensureLoggedIn()
  // silently returned undefined for both tokens on multiTenant hosts, sending
  // "Authorization: Bearer undefined" on every subsequent call.
  const parsed = JSON.parse(res.body);
  return {
    accessToken: parsed.access_token || parsed.accessToken,
    accessToken2: parsed.access_token2 || parsed.accessToken2,
  };
}

export function ensureLoggedIn(credential) {
  if (cachedAuth) {
    return cachedAuth;
  }
  cachedAuth = loginWithCredential(credential);
  return cachedAuth;
}

// Shared-token mode: a scenario's setup() logs in once and passes the token
// pair to every VU via its return value; each VU calls this once (e.g. at the
// top of default()) to seed its own cache with that shared pair instead of
// logging in itself. Exploratory/diagnostic mode for probing how the app
// handles one token being replayed concurrently - see the plan's "shared-token
// concurrency mode" follow-up section.
export let authMode = 'pool';

export function useSharedAuth(tokenPair) {
  cachedAuth = tokenPair;
  authMode = 'shared';
}

export function authHeaders(credential) {
  const auth = ensureLoggedIn(credential);
  return {
    Authorization: `Bearer ${auth.accessToken}`,
    GinesysAuth: `Bearer ${auth.accessToken2}`,
  };
}

// Decodes a JWT's claims without verifying the signature - fine here since we
// only ever read back claims from a token this same process just received
// from the real login endpoint, never a token from an untrusted source.
// Used by flows/createUser.js to reuse the provisioning admin's own RoleId
// claim as the roleId given to newly created users, instead of a hardcoded
// per-tenant role code (see the seeding plan's "Design finding").
export function decodeJwtClaims(token) {
  const payload = token.split('.')[1];
  return JSON.parse(b64decode(payload, 'rawurl', 's'));
}
