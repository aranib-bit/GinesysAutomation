package com.gsl.api.publicapi.constants;

/** Protocol constants of the PUBLICAPI OAuth2 login; URLs, client id/secret and the refresh margin come from {@code ApiConfig}. */
public final class PublicApiAuthDefaults {

    private PublicApiAuthDefaults() {}

    public static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";
    public static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";
    public static final String BEARER_PREFIX = "Bearer ";
}
