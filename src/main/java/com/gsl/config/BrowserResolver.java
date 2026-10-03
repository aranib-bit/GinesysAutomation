package com.gsl.config;

import com.gsl.enums.Browser;
import com.gsl.utils.Log;

/**
 * Single answer to "which browser does this run use": the typed value the driver launches, also
 * served as the lower-case name the report shows, so the report can never disagree with the browser
 * that actually ran. Logs one line the first time it is resolved, naming the source, e.g.
 * {@code [Browser] Resolved: FIREFOX (source: -Dbrowser)}; blank or unknown values are handled by
 * {@link com.gsl.config.converter.StringToBrowserConverter} (WARN + CHROME, or an error listing the valid names).
 */
public final class BrowserResolver {

    private static Browser resolved;

    private BrowserResolver() {}

    /** The browser for this run, resolved (and logged) on first call. */
    public static synchronized Browser current() {
        if (resolved == null) {
            resolved = ConfigFactory.getBrowserConfig().browserType();
            String sys = System.getProperty("browser");
            String source = sys != null && !sys.isBlank() ? "-Dbrowser"
                    : sys != null ? "default (-Dbrowser was empty)" : "browser.properties / default";
            Log.info("[Browser] Resolved: {} (source: {})", resolved, source);
        }
        return resolved;
    }

    /** Lower-case browser name for reports and metadata, e.g. {@code chrome}. */
    public static String name() {
        return current().name().toLowerCase();
    }
}
