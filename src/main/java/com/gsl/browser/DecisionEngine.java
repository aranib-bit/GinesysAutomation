package com.gsl.browser;

import com.gsl.config.ConfigFactory;
import com.gsl.config.NetworkConfig;

import java.util.List;

/**
 * Evaluates browser log records and returns a pass/warn/fail decision.
 *
 * STRICT  (browser.logs.fail.on.js.error=true)         — any CRITICAL → FAIL
 * SMART   (fail.on.critical.js.error=true, default)    — CRITICAL only when the test already failed
 * AUDIT   (browser.logs.capture.only=true)             — capture only, never alter result
 */
public final class DecisionEngine {

    public enum Decision { PASS, WARN, FAIL }

    private DecisionEngine() {}

    /**
     * @param testPassed the test's outcome so far (excluding this decision) — used by the SMART policy
     * @return {@link Decision#FAIL} under STRICT/SMART policy conditions (see class Javadoc),
     * {@link Decision#WARN} when only warning-level impact is present, else {@link Decision#PASS}
     * (including whenever browser-log capture is disabled or capture-only)
     */
    public static Decision evaluate(List<BrowserLogRecord> logs, boolean testPassed) {
        NetworkConfig cfg = ConfigFactory.getNetworkConfig();

        if (!cfg.browserLogsEnabled() || cfg.browserLogsCaptureOnly()) {
            return Decision.PASS;
        }

        boolean hasCritical = FunctionalImpactAnalyzer.hasCriticalImpact(logs);
        boolean hasWarning  = FunctionalImpactAnalyzer.hasWarningImpact(logs);

        if (hasCritical) {
            if (cfg.browserLogsFailOnJsError()) return Decision.FAIL;                 // STRICT
            if (!testPassed && cfg.browserLogsFailOnCriticalJsError()) return Decision.FAIL; // SMART
        }

        if (hasWarning) return Decision.WARN;

        return Decision.PASS;
    }
}
