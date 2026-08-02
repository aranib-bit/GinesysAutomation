package com.gsl.api.core;

import com.gsl.config.ConfigFactory;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.UserCredential;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * PUBLICAPI auth: OAuth2 client-credentials flow against {@code publicapi.tokenUrl} using
 * {@code publicapi.clientId}/{@code publicapi.clientSecret} from config — genuinely different
 * from WEBAPI's UserPool-backed two-token login. Caches the bearer token with expiry and
 * refetches once expired; never touches UserPool (no per-user credential for PUBLICAPI).
 */
public final class PublicApiOAuth2Strategy implements ApiAuthStrategy {

    private static volatile String cachedToken;
    private static volatile long cachedTokenExpiryEpochMs;

    @Override
    public AuthResult authenticate(String baseUri, UserCredential credential) {
        String token = currentToken();

        RequestSpecification spec = RestAssured.given()
                .baseUri(baseUri)
                .contentType("application/json")
                .header("authorization", "Bearer " + token);

        // OAuth2 client-credentials is app-level, not per-user — no userId/sessionId to track for logout.
        return AuthResult.builder().spec(spec).userId(null).sessionId(null).build();
    }

    private static synchronized String currentToken() {
        long now = System.currentTimeMillis();
        if (cachedToken != null && now < cachedTokenExpiryEpochMs) {
            return cachedToken;
        }

        var apiConfig = ConfigFactory.getApiConfig();
        Response response = RestAssured.given()
                .baseUri(apiConfig.publicApiTokenUrl())
                .contentType("application/x-www-form-urlencoded")
                .formParam("grant_type", "client_credentials")
                .formParam("client_id", apiConfig.publicApiClientId())
                .formParam("client_secret", apiConfig.publicApiClientSecret())
                .post();

        if (response.getStatusCode() >= 400) {
            throw new ApiException("PUBLICAPI OAuth2 token fetch failed — HTTP " + response.getStatusCode(),
                    response.getStatusCode());
        }

        cachedToken = response.jsonPath().getString("access_token");
        int expiresInSeconds = response.jsonPath().getInt("expires_in");
        // Refresh 60s early to avoid a request racing right at expiry.
        cachedTokenExpiryEpochMs = now + Math.max(0, (expiresInSeconds - 60) * 1000L);

        ReportLogger.session("[PublicApiOAuth2Strategy] Fetched new PUBLICAPI token, expires in {}s", expiresInSeconds);
        return cachedToken;
    }
}
