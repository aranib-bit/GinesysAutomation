package com.gsl.config;

import com.gsl.enums.TargetOs;
import com.gsl.utils.Log;

/**
 * Single answer to "which OS does the Grid node for this run use" (see {@link TargetOs}). Mirrors
 * {@link BrowserResolver}: resolved once, logged once with its source, and served as both the typed
 * value the driver uses and the lower-case name the report shows.
 */
public final class TargetOsResolver {

    private static TargetOs resolved;

    private TargetOsResolver() {}

    /** The target OS for this run, resolved (and logged) on first call. Unknown values throw {@link IllegalArgumentException}. */
    public static synchronized TargetOs current() {
        if (resolved == null) {
            resolved = TargetOs.parse(ConfigFactory.getBrowserConfig().targetOsRaw());
            String sys = System.getProperty("target.os");
            String source = sys != null && !sys.isBlank() ? "-Dtarget.os" : "browser.properties / default";
            Log.info("[TargetOs] Resolved: {} (source: {})", resolved, source);
        }
        return resolved;
    }

    /** Lower-case OS name for reports and metadata, e.g. {@code windows}. */
    public static String name() {
        return current().key();
    }
}
