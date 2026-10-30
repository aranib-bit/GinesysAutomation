package com.tests.API;

import com.gsl.api.core.QueryParams;
import com.gsl.api.publicapi.model.request.auth.ClientCredentialsTokenRequest;
import com.gsl.api.publicapi.model.response.auth.OAuthTokenResponse;
import com.gsl.api.webapi.model.request.auth.KillSessionQuery;
import com.gsl.api.webapi.model.request.auth.LogoutQuery;
import com.gsl.api.webapi.model.request.auth.PasswordTokenRequest;
import com.gsl.api.webapi.model.response.auth.LoginError;
import com.gsl.api.webapi.model.response.auth.LogoutResponse;
import com.gsl.api.webapi.model.response.auth.TokenResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/** The login/session request and response POJOs against the shapes the WebAPI and the PUBLICAPI OAuth endpoint send and return. */
public class AuthWireTest {

    @Test
    public void passwordGrantIsSentAsTheThreeFormFields() {
        Map<String, Object> form = QueryParams.toMap(PasswordTokenRequest.of("webapi1", "secret-value"));
        assertEquals(form, Map.of("grant_type", "password", "username", "webapi1", "password", "secret-value"));
    }

    @Test
    public void thePasswordNeverAppearsInToString() {
        assertFalse(PasswordTokenRequest.of("webapi1", "secret-value").toString().contains("secret-value"));
        assertFalse(ClientCredentialsTokenRequest.of("client", "client-secret").toString().contains("client-secret"));
    }

    @Test
    public void snakeCaseTokensAreReadOnASingleTenantDeployment() {
        TokenResponse token = TokenResponse.parse("{\"access_token\":\"A1\",\"access_token2\":\"A2\",\"token_type\":\"bearer\",\"expires_in\":3599}");
        assertEquals(token.getAccessToken(), "A1");
        assertEquals(token.getAccessToken2(), "A2");
        assertNull(token.loginError());
    }

    @Test
    public void camelCaseTokensAreReadOnAMultiTenantDeployment() {
        TokenResponse token = TokenResponse.parse("{\"accessToken\":\"B1\",\"accessToken2\":\"B2\"}");
        assertEquals(token.getAccessToken(), "B1");
        assertEquals(token.getAccessToken2(), "B2");
    }

    @Test
    public void anAlreadyLoggedInRefusalNamesTheSessionToKill() {
        TokenResponse refused = TokenResponse.parse("{\"error\":\"invalid_grant\",\"error_description\":\"012|User already logged in from some other browser|10533\"}");
        LoginError error = refused.loginError();
        assertNotNull(error);
        assertEquals(error.code(), "012");
        assertEquals(error.tag(), "10533");
        assertTrue(error.isAlreadyLoggedIn());
        assertFalse(error.isLicenseExceeded());
    }

    @Test
    public void aLicenceRefusalIsRecognisedAndNeedsNoSessionTag() {
        LoginError error = TokenResponse.parse("{\"error_description\":\"555555555|number of users allowed by your license has exceeded\"}").loginError();
        assertTrue(error.isLicenseExceeded());
        assertFalse(error.isAlreadyLoggedIn());
    }

    @Test
    public void aRefusalWithoutTheCodeFormatIsNotMistakenForOne() {
        LoginError error = TokenResponse.parse("{\"error_description\":\"The user name or password is incorrect.\"}").loginError();
        assertFalse(error.isAlreadyLoggedIn());
        assertFalse(error.isLicenseExceeded());
    }

    @Test
    public void aNonJsonBodyGivesAnEmptyResponseInsteadOfAnException() {
        TokenResponse html = TokenResponse.parse("<html><body>502 Bad Gateway</body></html>");
        assertNull(html.getAccessToken());
        assertNull(html.loginError());
        assertNull(TokenResponse.parse(null).getAccessToken());
        assertNull(TokenResponse.parse("  ").getAccessToken());
    }

    @Test
    public void killAndLogoutQueriesCarryTheirKeys() {
        assertEquals(QueryParams.toMap(KillSessionQuery.of("10533")), Map.of("userId", "10533"));
        Map<String, Object> logout = QueryParams.toMap(LogoutQuery.builder().cacheBuster(1L).userId("10533").sessionId("null").build());
        assertEquals(logout.keySet(), Set.of("_dc", "userId", "sessionId"));
        assertEquals(logout.get("sessionId"), "null");
    }

    @Test
    public void logoutSuccessIsReadFromTheSuccessFlag() {
        assertTrue(LogoutResponse.parse("{\"result\":true,\"success\":true,\"error\":null}").succeeded());
        assertFalse(LogoutResponse.parse("{\"success\":false}").succeeded());
        assertFalse(LogoutResponse.parse("not json").succeeded());
    }

    @Test
    public void clientCredentialsGrantIsSentAsTheThreeFormFields() {
        assertEquals(QueryParams.toMap(ClientCredentialsTokenRequest.of("client", "client-secret")),
                Map.of("grant_type", "client_credentials", "client_id", "client", "client_secret", "client-secret"));
    }

    @Test
    public void theOAuthResponseKeepsTheTokenAndItsLifetime() throws Exception {
        OAuthTokenResponse token = new ObjectMapper().readValue("{\"access_token\":\"T\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"scope\":\"x\"}", OAuthTokenResponse.class);
        assertEquals(token.getAccessToken(), "T");
        assertEquals(token.expiresInSeconds(), 3600);
        assertEquals(new ObjectMapper().readValue("{\"access_token\":\"T\"}", OAuthTokenResponse.class).expiresInSeconds(), 0);
    }
}
