package com.gsl.api.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsl.config.ApiConfig;
import com.gsl.config.ConfigFactory;
import com.gsl.config.TenantUtils;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.UserCredential;
import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * WEBAPI login/logout. Both flows are now mirrored from D:\PerformanceAutomationTesting\lib\auth.js
 * — a proven, already-working implementation — rather than guessed:
 * <ul>
 *   <li>Login: {@code POST /token}, {@code grant_type=password&username=..&password=..},
 *       form-urlencoded. Response carries {@code access_token} (-> authorization header) and
 *       {@code access_token2} (-> ginesysauth header) — snake_case on single-tenant
 *       (stage-ideal), camelCase ({@code accessToken}/{@code accessToken2}) on multi-tenant
 *       (qafeature/qahbros), confirmed live per auth.js's own comment.</li>
 *   <li>Code 012 ("user already logged in from some other browser") — Ginesys encodes this as
 *       {@code error_description = "012|<message>|<userId>"}. auth.js's proven fix: call
 *       {@code GET /api/Session/KillUser?userId=<tag>} then retry the login once.</li>
 *   <li>Logout: {@code GET /api/Session/Logout?userId=..&sessionId=..} — confirmed via a
 *       captured HAR (single-tenant only; multi-tenant sessionId shape still TBD).</li>
 * </ul>
 */
public final class WebApiSessionClient {

    private static final String NAMEIDENTIFIER_CLAIM =
            "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WebApiSessionClient() {}

    public static WebApiLoginResult login(UserCredential credential) {
        ApiConfig cfg = ConfigFactory.getApiConfig();
        String loginUrl = cfg.webApiBaseUri() + cfg.webApiLoginPath();
        long start = System.currentTimeMillis();
        Response response = performLogin(credential);
        logRedactedCall("POST", loginUrl, "(omitted — contains password)", response, System.currentTimeMillis() - start);
        boolean retried = false;

        if (response.getStatusCode() != 200) {
            // Log the first attempt's full detail explicitly, separately from any exception
            // message — TestNG's failure/skip handling does not reliably surface a multi-line
            // exception message intact into the Extent report (same reasoning UserPool's
            // acquire-timeout path already applies), so the diagnosable detail has to be a
            // report line of its own, not just baked into what gets thrown.
            ReportLogger.sessionWarn("[WebApiSessionClient] Login attempt #1 for '{}' failed — HTTP {} — body: {}",
                    credential.username(), response.getStatusCode(), response.getBody().asString());

            ParsedLoginError parsedError = tryParseErrorDescription(response);

            if (parsedError != null && "012".equals(parsedError.code) && parsedError.tag != null) {
                ReportLogger.sessionWarn("[WebApiSessionClient] Login for '{}' hit code 012 (already logged in "
                        + "elsewhere, userId={}) — killing that session and retrying once.",
                        credential.username(), parsedError.tag);
                killSession(parsedError.tag);
                long retryStart = System.currentTimeMillis();
                response = performLogin(credential);
                logRedactedCall("POST", loginUrl, "(omitted — contains password)", response, System.currentTimeMillis() - retryStart);
                retried = true;

                if (response.getStatusCode() != 200) {
                    ReportLogger.sessionWarn("[WebApiSessionClient] Login attempt #2 (post-KillUser) for '{}' also " +
                            "failed — HTTP {} — body: {}",
                            credential.username(), response.getStatusCode(), response.getBody().asString());
                }
            }
        }

        if (response.getStatusCode() != 200) {
            String summary = "WEBAPI login failed for user '" + credential.username() + "'"
                    + (retried ? " (after KillUser retry)" : "") + " — HTTP " + response.getStatusCode()
                    + ". See [WebApiSessionClient] SESSION log entries above for the full response body(ies).";
            ReportLogger.sessionError(summary);
            throw new ApiException(summary, response.getStatusCode());
        }

        // Response key naming differs by deployment mode: stage-ideal (single-tenant) uses
        // snake_case (access_token/access_token2); qafeature/qahbros (multi-tenant) use
        // camelCase (accessToken/accessToken2) — confirmed live in auth.js. Without this
        // fallback a multi-tenant host silently returns null for both tokens.
        String authorizationToken = firstNonNull(
                response.jsonPath().getString("access_token"),
                response.jsonPath().getString("accessToken"));
        String ginesysAuthToken = firstNonNull(
                response.jsonPath().getString("access_token2"),
                response.jsonPath().getString("accessToken2"));

        String userId = ginesysAuthToken != null ? extractClaim(ginesysAuthToken, NAMEIDENTIFIER_CLAIM) : null;

        ReportLogger.session("[WebApiSessionClient] Login — user={}, userId={}, ginesysAuthPresent={}",
                credential.username(), userId, ginesysAuthToken != null);

        return WebApiLoginResult.builder()
                .authorizationToken(authorizationToken)
                .ginesysAuthToken(ginesysAuthToken)
                .userId(userId)
                .sessionId(null) // single-tenant: null is the confirmed-correct value; multi-tenant: TBD
                .build();
    }

    private static Response performLogin(UserCredential credential) {
        ApiConfig cfg = ConfigFactory.getApiConfig();
        return RestAssured.given()
                .baseUri(cfg.webApiBaseUri())
                .contentType("application/x-www-form-urlencoded")
                .formParam("grant_type", "password")
                .formParam("username", credential.username())
                .formParam("password", credential.password())
                .post(cfg.webApiLoginPath());
    }

    /** GET {apiPrefix}/Session/KillUser?userId=<tag> — forces the stale session (from code 012) to end. */
    private static void killSession(String userId) {
        ApiConfig cfg = ConfigFactory.getApiConfig();
        String url = cfg.webApiBaseUri() + cfg.webApiApiPrefix() + "/Session/KillUser?userId=" + userId;
        long start = System.currentTimeMillis();
        Response response = RestAssured.given()
                .baseUri(cfg.webApiBaseUri())
                .queryParam("userId", userId)
                .get(cfg.webApiApiPrefix() + "/Session/KillUser");
        logRedactedCall("GET", url, "(none)", response, System.currentTimeMillis() - start);
    }

    /**
     * Renders a Method/URL/Payload/Response table for auth calls, styled like
     * {@link com.gsl.reports.ApiCallReportLogger#logCall} but with tokens redacted (and, for
     * login, the payload omitted entirely since it carries the plaintext password) — mirroring
     * D:\PerformanceAutomationTesting\lib\auth.js's own redactTokens()/debugLog() restraint. Auth
     * calls don't go through ApiClient/ApiCallReportLogger (they predate an authenticated spec),
     * so without this they'd be the only calls in the framework with no request/response trace.
     */
    private static void logRedactedCall(String method, String url, String payload, Response response, long durationMs) {
        String[][] table = {
            {"Field", "Value"},
            {"Method", method},
            {"URL", url},
            {"Payload", payload},
            {"Response", prettyJson(redactTokens(response.getBody().asString()))},
        };
        ReportLogger.infoLabeledTable(
                method + " " + url + " (" + durationMs + "ms, HTTP " + response.getStatusCode() + ")", table);
    }

    private static String redactTokens(String json) {
        if (json == null) {
            return json;
        }
        return json.replaceAll(
                "(\"(?:access_token2?|accessToken2?)\"\\s*:\\s*\")[^\"]*(\")",
                "$1***REDACTED***$2");
    }

    private static String prettyJson(String json) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(MAPPER.readTree(json));
        } catch (Exception e) {
            return json;
        }
    }

    /** Ginesys encodes login failures as "<code>|<message>|<tag>" in error_description, e.g. code 012. */
    private static ParsedLoginError tryParseErrorDescription(Response response) {
        try {
            String errorDescription = response.jsonPath().getString("error_description");
            if (errorDescription == null) return null;
            String[] parts = errorDescription.split("\\|");
            return new ParsedLoginError(
                    parts.length > 0 ? parts[0] : null,
                    parts.length > 2 ? parts[2] : null);
        } catch (Exception e) {
            return null; // response wasn't the expected JSON shape — caller falls through to a hard failure
        }
    }

    private static final class ParsedLoginError {
        final String code;
        final String tag;
        ParsedLoginError(String code, String tag) { this.code = code; this.tag = tag; }
    }

    /** Mirrors the captured HAR exactly for single-tenant; branches for multi-tenant (shape TBD). */
    public static void logout(ApiSessionBundle session) {
        ApiConfig cfg = ConfigFactory.getApiConfig();
        String tenant = cfg.activeTenant();
        boolean multiTenant = TenantUtils.isMultiTenant(cfg, tenant);
        String sessionIdParam = multiTenant && session.getSessionId() != null
                ? session.getSessionId()
                : "null"; // literal string "null" — matches captured single-tenant Session/Logout behavior exactly

        if (multiTenant && session.getSessionId() == null) {
            ReportLogger.sessionWarn("[WebApiSessionClient] Logout for multi-tenant '{}' has no captured sessionId " +
                    "— falling back to literal \"null\" (confirmed only for single-tenant). Multi-tenant logout " +
                    "shape needs its own HAR capture.", tenant);
        }

        // session.getBaseSpec()'s baseUri already includes webApiApiPrefix — ApiSessionFactory
        // builds the flow-client spec against {webApiBaseUri}{webApiApiPrefix} (see §0.10), so
        // the path here must stay relative ("/Session/Logout"), not re-prepend the prefix.
        Response response = session.getBaseSpec()
                .queryParam("_dc", System.currentTimeMillis())
                .queryParam("userId", session.getUserId())
                .queryParam("sessionId", sessionIdParam)
                .get("/Session/Logout");

        boolean success = Boolean.TRUE.equals(response.jsonPath().get("success"));
        if (success) {
            ReportLogger.session("[WebApiSessionClient] Logout — tenant={}, userId={}, sessionId={}, success=true",
                    tenant, session.getUserId(), sessionIdParam);
        } else {
            // Full body logged explicitly — a failed logout is easy to miss later otherwise
            // (it never throws; a leaked server-side session should still be diagnosable).
            ReportLogger.sessionWarn("[WebApiSessionClient] Logout — tenant={}, userId={}, sessionId={}, " +
                    "success=false — HTTP {} — body: {}",
                    tenant, session.getUserId(), sessionIdParam, response.getStatusCode(), response.getBody().asString());
        }
    }

    private static String extractClaim(String jwt, String claimUri) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return null;
            byte[] payloadBytes = Base64.getUrlDecoder().decode(padBase64Url(parts[1]));
            JsonNode payload = MAPPER.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
            JsonNode claim = payload.get(claimUri);
            return claim != null ? claim.asText() : null;
        } catch (Exception e) {
            ReportLogger.sessionWarn("[WebApiSessionClient] Failed to parse JWT claim '{}': {}", claimUri, e.getMessage());
            return null;
        }
    }

    private static String padBase64Url(String segment) {
        int padding = (4 - segment.length() % 4) % 4;
        return segment + "=".repeat(padding);
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
