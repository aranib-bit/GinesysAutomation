package com.gsl.browser.injector;

import com.gsl.config.ConfigFactory;

/**
 * Returns the correct {@link FrontendDiagnosticsInjector} based on
 * {@code application.type} in config.properties.
 *
 * Supported values (case-insensitive):
 *   extjs   → {@link ExtJsFrontendInjector}  (default)
 *   react   → {@link ReactFrontendInjector}
 *   angular → {@link AngularFrontendInjector}
 *
 * To add a new framework: implement {@link FrontendDiagnosticsInjector}
 * and add one {@code case} here.
 */
public final class FrontendDiagnosticsInjectorFactory {

    private FrontendDiagnosticsInjectorFactory() {}

    public static FrontendDiagnosticsInjector get() {
        String type = ConfigFactory.getBrowserConfig().applicationType().toLowerCase().trim();
        return switch (type) {
            case "react"   -> new ReactFrontendInjector();
            case "angular" -> new AngularFrontendInjector();
            default        -> new ExtJsFrontendInjector();
        };
    }
}
