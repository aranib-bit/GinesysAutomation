package com.gsl.api.core;

import com.gsl.config.ConfigFactory;

/** The WEBAPI base the flow clients' specs are built against ({@code webApiBaseUri + webApiApiPrefix}), for code outside this package that rebuilds a spec from tokens. */
public final class ApiConfigAccess {

    private ApiConfigAccess() {}

    public static String webApiBase() {
        var cfg = ConfigFactory.getApiConfig();
        return cfg.webApiBaseUri() + cfg.webApiApiPrefix();
    }
}
