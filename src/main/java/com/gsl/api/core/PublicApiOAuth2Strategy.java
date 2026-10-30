package com.gsl.api.core;

import com.gsl.config.ConfigFactory;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.UserCredential;
import com.gsl.api.publicapi.constants.PublicApiAuthDefaults;
import com.gsl.api.publicapi.model.request.auth.ClientCredentialsTokenRequest;
import com.gsl.api.publicapi.model.response.auth.OAuthTokenResponse;
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

    /** {@inheritDoc} {@code credential} is ignored — PUBLICAPI authenticates at the app level, not per-user. */
    @Override
    public AuthResult authenticate(String baseUri, UserCredential credential) {
        String token = currentToken();

        RequestSpecification spec = RestAssured.given()
                .baseUri(baseUri)
                .contentType("application/json")
                .header("authorization", PublicApiAuthDefaults.BEARER_PREFIX + token);

        // OAuth2 client-credentials is app-level, not per-user — no userId/sessionId to track for logout.
        return AuthResult.builder().spec(spec).userId(null).sessionId(null).build();
    }

    /** Returns the cached bearer token if still valid (with a 60s early-refresh margin), else fetches and caches a new one. */
    private static synchronized String currentToken() {
        long now = System.currentTimeMillis();
        if (cachedToken != null && now < cachedTokenExpiryEpochMs) {
            return cachedToken;
        }

        var apiConfig = ConfigFactory.getApiConfig();
        Response response = RestAssured.given()
                .baseUri(apiConfig.publicApiTokenUrl())
                .contentType(PublicApiAuthDefaults.FORM_CONTENT_TYPE)
                .formParams(QueryParams.toMap(ClientCredentialsTokenRequest.of(apiConfig.publicApiClientId(), apiConfig.publicApiClientSecret())))
                .post();

        if (response.getStatusCode() >= 400) {
            throw new ApiException("PUBLICAPI OAuth2 token fetch failed — HTTP " + response.getStatusCode(),
                    response.getStatusCode());
        }

        OAuthTokenResponse token = response.getBody().as(OAuthTokenResponse.class, io.restassured.mapper.ObjectMapperType.JACKSON_2);
        cachedToken = token.getAccessToken();
        int expiresInSeconds = token.expiresInSeconds();
        // Refresh early (publicapi.token.refreshMarginSeconds) so a request never races the expiry.
        cachedTokenExpiryEpochMs = now + Math.max(0, (expiresInSeconds - apiConfig.publicApiTokenRefreshMarginSeconds()) * 1000L);

        ReportLogger.session("[PublicApiOAuth2Strategy] Fetched new PUBLICAPI token, expires in {}s", expiresInSeconds);
        return cachedToken;
    }
}
