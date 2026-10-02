package com.gsl.api.core;

import com.gsl.config.ApiConfig;
import com.gsl.config.ConfigFactory;
import com.gsl.config.TenantUtils;
import com.gsl.testdata.UserCredential;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

/**
 * WEBAPI auth: calls {@link WebApiSessionClient#login} to obtain both the {@code ginesysauth}
 * JWT and the opaque {@code authorization} token, attaches them under config-driven header
 * names — single-tenant's names are confirmed via a captured HAR, but k6's own auth.js already
 * has to special-case multi-tenant token *response* format (snake_case vs camelCase), so the
 * header *names* (and any extra header multi-tenant needs, e.g. a tenant id) are equally
 * plausible to differ and are never hardcoded here.
 */
public final class WebApiTokenAuthStrategy implements ApiAuthStrategy {

    /**
     * {@inheritDoc}
     *
     * <p>Logs in via {@link WebApiSessionClient#login}, then picks the config-driven header
     * names for the active tenant (single- vs multi-tenant, per {@link TenantUtils#isMultiTenant})
     * and attaches the resulting tokens — {@code ginesysauth} is only attached when the login
     * actually returned one (absent on some multi-tenant responses), and the multi-tenant extra
     * header is only attached when configured.
     */
    @Override
    public AuthResult authenticate(String baseUri, UserCredential credential) {
        WebApiLoginResult login = WebApiSessionClient.login(credential);
        ApiConfig cfg = ConfigFactory.getApiConfig();
        boolean multiTenant = TenantUtils.isMultiTenant(cfg, cfg.activeTenant());

        String authorizationHeaderName = multiTenant
                ? cfg.multiTenantAuthorizationHeaderName()
                : cfg.singleTenantAuthorizationHeaderName();
        String ginesysAuthHeaderName = multiTenant
                ? cfg.multiTenantGinesysAuthHeaderName()
                : cfg.singleTenantGinesysAuthHeaderName();

        RequestSpecification spec = RestAssured.given()
                .baseUri(baseUri)
                .contentType("application/json")
                .header(authorizationHeaderName, "Bearer " + login.getAuthorizationToken());

        if (login.getGinesysAuthToken() != null) {
            spec = spec.header(ginesysAuthHeaderName, "Bearer " + login.getGinesysAuthToken());
        }

        if (multiTenant && !cfg.multiTenantExtraHeaderName().isBlank()) {
            spec = spec.header(cfg.multiTenantExtraHeaderName(), cfg.multiTenantExtraHeaderValue());
        }

        return AuthResult.builder()
                .spec(spec)
                .userId(login.getUserId())
                .sessionId(login.getSessionId())
                .build();
    }
}
