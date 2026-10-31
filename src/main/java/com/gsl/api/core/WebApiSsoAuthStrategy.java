package com.gsl.api.core;

import com.gsl.config.ConfigFactory;
import com.gsl.testdata.UserCredential;

/**
 * WEBAPI auth for users whose {@code logintype} is SSO. The ERP-via-SSO flow (SSO login, then how the ERP accepts its token) is not captured yet - an
 * HAR of an SSO user logging in to the ERP is needed - so this strategy only fails fast, with the missing piece named: a blank
 * {@code webapi.sso.loginUrl} first, then the unimplemented flow. It never falls back to the ERP login, which would hide that the user is an SSO user.
 */
public final class WebApiSsoAuthStrategy implements ApiAuthStrategy {

    @Override
    public AuthResult authenticate(String baseUri, UserCredential credential) {
        throw new ApiException(failureMessage(credential.username(), ConfigFactory.getApiConfig().ssoLoginUrl()), 0);
    }

    /** Why an SSO login cannot proceed; pure, so it can be unit-tested without a configuration. */
    public static String failureMessage(String username, String ssoLoginUrl) {
        if (ssoLoginUrl == null || ssoLoginUrl.isBlank()) {
            return "User '" + username + "' has logintype=SSO but webapi.sso.loginUrl is not configured for this environment "
                    + "(set it in env/<env>/api.properties).";
        }
        return "User '" + username + "' has logintype=SSO (SSO login URL " + ssoLoginUrl.trim() + "), but the SSO login flow is not implemented yet: "
                + "the ERP-via-SSO request/response has not been captured (an HAR of an SSO user logging in to the ERP is needed).";
    }
}