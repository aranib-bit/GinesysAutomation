package com.gsl.api.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsl.config.ApiConfig;
import com.gsl.config.ConfigFactory;
import com.gsl.config.TenantUtils;
import com.gsl.reports.BackfillStepLabel;
import com.gsl.reports.ExtentManager;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
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

    /**
     * Logs {@code credential} in against WEBAPI, retrying once through {@link #killSession} on a
     * code-012 ("already logged in elsewhere") response. Nests the call under a "Create Token"
     * Extent step when a test is active, otherwise runs the same logic flat (see {@link #loginInternal}).
     *
     * @throws ApiException if login still fails after the code-012 retry (or fails for any other reason)
     */
    public static WebApiLoginResult login(UserCredential credential) {
        // Nest as a "Create Token" step whenever an ExtentTest is active (e.g. inside DataBackFill's
        // own report entry) — falls back to the plain flat-logging path otherwise, since
        // StepExecutor.step() requires a non-null current test (no null-check inside it).
        if (!ExtentManager.hasTest()) {
            return loginInternal(credential);
        }
        WebApiLoginResult[] resultHolder = new WebApiLoginResult[1];
        StepExecutor.step(BackfillStepLabel.CREATE_TOKEN.label(), () -> resultHolder[0] = loginInternal(credential));
        return resultHolder[0];
    }

    /** Actual login logic behind {@link #login} — issues the call, handles the code-012 retry, and parses the token response. */
    private static WebApiLoginResult loginInternal(UserCredential credential) {
        ApiConfig cfg = ConfigFactory.getApiConfig();
        String loginUrl = cfg.webApiBaseUri() + cfg.webApiLoginPath();
        long start = System.currentTimeMillis();
        Response response = performLogin(credential);
        logRedactedCall("POST", loginUrl, "(omitted — contains password)", response, System.currentTimeMillis() - start);
        boolean retried = false;

        // "number of users allowed by your license has exceeded" is a transient capacity refusal, not a bad credential: wait for a seat.
        for (int wait = 1; response.getStatusCode() != 200 && isLicenseExceeded(response) && wait <= cfg.webApiLicenseRetries(); wait++) {
            ReportLogger.sessionWarn("[WebApiSessionClient] Login for '{}' refused — licence seat limit reached; waiting {}s for a seat "
                    + "(attempt {}/{}).", credential.username(), cfg.webApiLicenseWaitSeconds(), wait, cfg.webApiLicenseRetries());
            try {
                Thread.sleep(cfg.webApiLicenseWaitSeconds() * 1000L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
            response = performLogin(credential);
        }

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
                if (ExtentManager.hasTest()) {
                    StepExecutor.step(BackfillStepLabel.KILL_TOKEN.label(), () -> killSession(parsedError.tag));
                } else {
                    killSession(parsedError.tag);
                }
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

    /** POSTs the raw {@code grant_type=password} login request — no response handling, just the HTTP call. */
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

    /** Masks {@code access_token}/{@code access_token2} (and their camelCase multi-tenant forms) values in a JSON body for logging. */
    private static String redactTokens(String json) {
        if (json == null) {
            return json;
        }
        return json.replaceAll(
                "(\"(?:access_token2?|accessToken2?)\"\\s*:\\s*\")[^\"]*(\")",
                "$1***REDACTED***$2");
    }

    /** Pretty-prints {@code json} for report readability; returns it unchanged if it doesn't parse as JSON. */
    private static String prettyJson(String json) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(MAPPER.readTree(json));
        } catch (Exception e) {
            return json;
        }
    }

    private static boolean isLicenseExceeded(Response response) {
        ParsedLoginError e = tryParseErrorDescription(response);
        return e != null && "555555555".equals(e.code);
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

    /** Decodes a JWT's payload segment (base64url, unverified) and returns the named claim's value, or null if absent/unparseable. */
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

    /** Pads a base64url segment to a multiple of 4 chars with {@code "="}, as {@link Base64.Decoder} requires. */
    private static String padBase64Url(String segment) {
        int padding = (4 - segment.length() % 4) % 4;
        return segment + "=".repeat(padding);
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
