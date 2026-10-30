import { check } from 'k6';
import { loginWithCredential, useSharedAuth, decodeJwtClaims } from '../lib/auth.js';
import { apiGet } from '../lib/http.js';
import { apiPrefix } from '../lib/config.js';

// Same claim WebApiSessionClient.java (Selenium repo) decodes to get the
// logged-in user's own numeric id for Session/Logout.
const NAMEIDENTIFIER_CLAIM = 'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier';

// WebApiSessionClient.java's literal-"null" sessionId is only confirmed for
// single-tenant; on qafeature/qahbros (multi-tenant) it makes Session/Logout
// return 200-but-success:false with a bare "InternalServerError" (confirmed
// live). The login response's "LicenseToken" claim (shape <userId>-WEB-
// <timestamp>) was tried as an alternative sessionId value on the theory
// that it's the real multi-tenant session identifier, but it produces the
// exact same InternalServerError (also confirmed live - this was NOT a fix,
// despite an earlier version of this comment claiming otherwise). The
// correct multi-tenant Session/Logout shape is still unknown; a real HAR
// capture of a logout action in the qafeature/qahbros UI is needed to find
// it. Kept as the sessionId value here anyway since it's no worse than the
// literal "null" fallback and is at least a real, session-specific value.
const LICENSE_TOKEN_CLAIM = 'LicenseToken';

export function testLoginLogout(credential) {
  const result = { username: credential.username, loginOk: false, logoutOk: false, userId: null, error: null };

  let auth;
  try {
    auth = loginWithCredential(credential);
  } catch (e) {
    result.error = `login failed: ${e.message}`;
    check(null, { [`${credential.username}: login succeeded`]: () => false });
    return result;
  }
  // A 200 status doesn't guarantee a usable body - under the heavy
  // concurrent load this scenario generates, the server occasionally
  // returns 200 with a malformed/incomplete response (confirmed live: this
  // crashed the whole iteration with an uncaught "Cannot read property
  // 'split' of undefined" inside decodeJwtClaims before this check existed).
  // Record it as a clean login failure instead.
  if (!auth || !auth.accessToken2) {
    result.error = 'login: HTTP 200 but response was missing accessToken2';
    check(null, { [`${credential.username}: login succeeded`]: () => false });
    return result;
  }

  result.loginOk = true;
  check(null, { [`${credential.username}: login succeeded`]: () => true });

  // Seed this VU's auth cache with the token pair we just obtained, so the
  // Logout call below reuses it via ensureLoggedIn() instead of logging in
  // a second time.
  useSharedAuth(auth);

  const claims = decodeJwtClaims(auth.accessToken2);
  result.userId = claims[NAMEIDENTIFIER_CLAIM] || null;
  const sessionId = claims[LICENSE_TOKEN_CLAIM] || 'null';

  const res = apiGet(
    credential,
    `${apiPrefix}/Session/Logout`,
    { _dc: Date.now(), userId: result.userId, sessionId },
    'Session/Logout'
  );

  let body = null;
  try {
    body = JSON.parse(res.body);
  } catch (e) {
    result.error = `logout: non-JSON response (status ${res.status}): ${res.body}`;
  }

  result.logoutOk = res.status === 200 && body && body.success === true;
  check(res, { [`${credential.username}: logout succeeded`]: () => result.logoutOk });

  if (!result.logoutOk && !result.error) {
    result.error = `logout: status=${res.status} success=${body ? body.success : 'n/a'} error=${body ? JSON.stringify(body.error) : 'n/a'}`;
  }

  return result;
}
