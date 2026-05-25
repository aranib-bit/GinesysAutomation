package com.gsl.extjs;

import com.gsl.config.ConfigFactory;

/**
 * Returns the correct UiFrameworkAdapter based on application.type in config.properties.
 *
 * Supported values (case-insensitive):
 *   extjs   → ExtJsUiAdapter   (default)
 *   react   → ReactUiAdapter
 *   angular → AngularUiAdapter
 *
 * To migrate the application to a new framework:
 *   1. Change application.type in config.properties — zero code changes elsewhere.
 *   2. To add a new framework: implement UiFrameworkAdapter + add a case here.
 */
public final class UiFrameworkAdapterFactory {

    private UiFrameworkAdapterFactory() {}

    public static UiFrameworkAdapter get() {
        String type = ConfigFactory.getConfig().applicationType().toLowerCase().trim();
        return switch (type) {
            case "react"   -> new ReactUiAdapter();
            case "angular" -> new AngularUiAdapter();
            default        -> new ExtJsUiAdapter();
        };
    }
}
