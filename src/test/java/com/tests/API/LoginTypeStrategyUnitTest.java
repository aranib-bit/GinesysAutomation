package com.tests.API;

import com.gsl.api.bulk.K6Scripts;
import com.gsl.api.core.ApiException;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.api.core.WebApiSsoAuthStrategy;
import com.gsl.api.core.WebApiTokenAuthStrategy;
import com.gsl.config.ApiConfig;
import com.gsl.config.ConfigFactory;
import com.gsl.testdata.ExecutionSet;
import com.gsl.testdata.LoginType;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.UserType;
import org.testng.annotations.Test;

import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** ERP and SSO users get different WEBAPI logins; an SSO user never silently gets the ERP login; the k6 tenant and SSO URL keys resolve. */
public class LoginTypeStrategyUnitTest {

    private static UserCredential user(LoginType login) {
        return new UserCredential("u1", "pw", Set.of(UserType.UI, UserType.WEBAPI), login, ExecutionSet.SET1, null);
    }

    @Test
    public void theStrategyFollowsTheLoginType() {
        assertTrue(ApiSessionFactory.strategyFor(user(LoginType.ERP)) instanceof WebApiTokenAuthStrategy);
        assertTrue(ApiSessionFactory.strategyFor(user(LoginType.SSO)) instanceof WebApiSsoAuthStrategy);
    }

    @Test
    public void anSsoUserFailsFastAndNamesTheMissingConfiguration() {
        String url = ConfigFactory.getApiConfig().ssoLoginUrl();
        ApiException e = expectThrows(ApiException.class, () -> new WebApiSsoAuthStrategy().authenticate("http://unused", user(LoginType.SSO)));
        assertTrue(e.getMessage().contains("u1"), e.getMessage());
        assertTrue(url.isBlank() ? e.getMessage().contains("webapi.sso.loginUrl") : e.getMessage().contains("not implemented"), e.getMessage());
    }

    @Test
    public void theFailureMessageSaysWhatIsMissing() {
        assertTrue(WebApiSsoAuthStrategy.failureMessage("a", " ").contains("webapi.sso.loginUrl is not configured"));
        String withUrl = WebApiSsoAuthStrategy.failureMessage("a", " https://sso.example/login ");
        assertTrue(withUrl.contains("https://sso.example/login") && withUrl.contains("not implemented"), withUrl);
    }

    @Test
    public void theK6TenantFallsBackToTheApiTenantWhenNotSet() {
        ApiConfig cfg = ConfigFactory.getApiConfig();
        String expected = cfg.k6Tenant().isBlank() ? cfg.activeTenant() : cfg.k6Tenant().trim();
        assertEquals(K6Scripts.tenant(), expected);
        assertTrue(!K6Scripts.tenant().isBlank());
    }
}