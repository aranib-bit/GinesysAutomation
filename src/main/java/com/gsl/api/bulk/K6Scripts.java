package com.gsl.api.bulk;

import com.gsl.api.core.DataSetupException;
import com.gsl.config.ApiConfig;
import com.gsl.config.ConfigFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Resolves a k6 script key to the scenario file. The k6 project lives inside this project ({@code k6.home}, default
 * {@code performance/k6}, resolved against the project directory), so no absolute path is configured anywhere. Scenarios are split by
 * purpose: {@code scenarios/backfill/} (bulk data entry, what {@code BackfillStrategy.k6ScriptKey()} launches) and
 * {@code scenarios/performance/} (load and smoke runs). An explicit {@code datafallback.k6.scriptPath.<key>} still overrides the default.
 */
public final class K6Scripts {

    /** The two scenario families under {@code <k6.home>/scenarios}. */
    public enum Kind {
        BACKFILL("backfill"),
        PERFORMANCE("performance");

        private final String folder;

        Kind(String folder) {
            this.folder = folder;
        }

        public String folder() {
            return folder;
        }
    }

    /** Script key -> scenario file relative to {@code scenarios/}, for keys whose file name differs from the key or lives in another family. */
    private static final Map<String, String> SCENARIOS = Map.of(
            "createDc", "backfill/createDc.js",
            // Existing mapping kept as is: this key has always launched smoke.js (one Service Invoice Adhoc); a dedicated backfill scenario follows with the k6 parity work.
            "serviceInvoiceAdhoc", "performance/smoke.js",
            "fillDocScheme", "backfill/fillDocScheme.js");

    private K6Scripts() {}

    /** The k6 project directory (absolute). */
    public static Path home() {
        Path configured = Path.of(ConfigFactory.getApiConfig().k6Home());
        return configured.isAbsolute() ? configured : Path.of(System.getProperty("user.dir")).resolve(configured).normalize();
    }

    /** The scenario file for {@code scriptKey}: the configured override if any, else the default under {@link #home()}. */
    public static Path resolve(String scriptKey) {
        ApiConfig cfg = ConfigFactory.getApiConfig();
        String override = cfg.k6ScriptPathFor(scriptKey);
        Path script = (override != null && !override.isBlank()) ? Path.of(override) : defaultFor(scriptKey);
        if (!Files.isRegularFile(script)) {
            throw new DataSetupException("[K6Scripts] k6 script for key '" + scriptKey + "' not found: " + script
                    + " (k6.home=" + home() + ")");
        }
        return script;
    }

    /** A scenario of the given family: {@code <k6.home>/scenarios/<family>/<name>.js}. */
    public static Path scenario(Kind kind, String name) {
        return home().resolve("scenarios").resolve(kind.folder()).resolve(name + ".js");
    }

    private static Path defaultFor(String scriptKey) {
        String mapped = SCENARIOS.get(scriptKey);
        if (mapped != null) return home().resolve("scenarios").resolve(mapped);
        // Unmapped keys are data-creating by definition (a BackfillStrategy asked for them): backfill first, then performance.
        for (Kind kind : List.of(Kind.BACKFILL, Kind.PERFORMANCE)) {
            Path candidate = scenario(kind, scriptKey);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return scenario(Kind.BACKFILL, scriptKey);
    }
}
