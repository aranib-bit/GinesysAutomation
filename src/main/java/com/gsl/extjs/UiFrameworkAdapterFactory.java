package com.gsl.extjs;

import com.gsl.config.ConfigFactory;

/**
 * Factory selecting the correct {@link UiFrameworkAdapter} implementation based on
 * {@code application.type} in config.properties — the single switch point behind
 * {@link UiFrameworkAdapter}'s "no code change required" migration story.
 *
 * <p>Supported values (case-insensitive):
 * <ul>
 *   <li>{@code extjs} → {@link ExtJsUiAdapter} (default)</li>
 *   <li>{@code react} → {@link ReactUiAdapter}</li>
 *   <li>{@code angular} → {@link AngularUiAdapter}</li>
 * </ul>
 *
 * <p>To migrate the application to a new framework: change {@code application.type} in
 * config.properties — zero code changes elsewhere. To add support for a new framework:
 * implement {@link UiFrameworkAdapter} and add a case here.
 */
public final class UiFrameworkAdapterFactory {

    private UiFrameworkAdapterFactory() {}

    /**
     * Returns the {@link UiFrameworkAdapter} for the configured {@code application.type} (see
     * class Javadoc), defaulting to {@link ExtJsUiAdapter} for an unrecognized value.
     */
    public static UiFrameworkAdapter get() {
        String type = ConfigFactory.getBrowserConfig().applicationType().toLowerCase().trim();
        return switch (type) {
            case "react"   -> new ReactUiAdapter();
            case "angular" -> new AngularUiAdapter();
            default        -> new ExtJsUiAdapter();
        };
    }
}
