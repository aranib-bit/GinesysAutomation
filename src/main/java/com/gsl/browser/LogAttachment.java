package com.gsl.browser;

import com.gsl.config.ConfigFactory;
import com.gsl.reports.AdvancedExtentLogger;
import com.gsl.browser.DecisionEngine;
import com.gsl.reports.ExtentLogger;
import org.testng.ITestResult;

import java.util.List;

/**
 * Attaches browser log entries to the ExtentReport for a given test result.
 * Controlled by config:
 *   browser.logs.enabled            — master switch
 *   browser.logs.capture.on.failure — attach all logs on test failure
 *   browser.logs.level              — on success, only attach if logs match level (SEVERE / WARNING / INFO)
 */
public final class LogAttachment {

    private LogAttachment() {}

    /**
     * Fetches this test's browser logs and attaches them to the Extent report: on failure, attaches
     * everything (gated by {@code browser.logs.capture.on.failure}) and logs a fail/warn entry per
     * {@link DecisionEngine}'s verdict; on success, attaches only entries at or above
     * {@code browser.logs.level}. No-op when browser-log capture is disabled or there's nothing to attach.
     */
    public static void attach(ITestResult result) {
        if (!ConfigFactory.getNetworkConfig().browserLogsEnabled()) return;

        List<BrowserLogRecord> logs = AdvancedLogFetcher.fetchLogs();
        if (logs == null || logs.isEmpty()) return;

        String description = result.getMethod().getDescription();

        // Always attach everything on failure
        if (result.getStatus() == ITestResult.FAILURE
                && ConfigFactory.getNetworkConfig().browserLogsCaptureOnFailure()) {
            AdvancedExtentLogger.logBrowserLogs(logs, description);
            DecisionEngine.Decision decision = DecisionEngine.evaluate(logs, false);
            if (decision == DecisionEngine.Decision.FAIL) {
                ExtentLogger.fail("[BrowserLogs] Critical JS error detected.");
            } else if (decision == DecisionEngine.Decision.WARN) {
                ExtentLogger.warning("[BrowserLogs] JS warnings detected.");
            }
            return;
        }

        // On success: filter by configured level before attaching
        String level = ConfigFactory.getNetworkConfig().browserLogsLevel();
        List<BrowserLogRecord> filtered = filterByLevel(logs, level);
        if (!filtered.isEmpty()) {
            AdvancedExtentLogger.logBrowserLogs(filtered, description);
        }
    }

    /** @return {@code logs} filtered to CRITICAL only ("SEVERE"/"CRITICAL"), CRITICAL+WARNING ("WARNING"), or unfiltered (any other value, e.g. "INFO"). */
    private static List<BrowserLogRecord> filterByLevel(List<BrowserLogRecord> logs, String level) {
        return switch (level.toUpperCase()) {
            case "SEVERE", "CRITICAL" -> logs.stream()
                    .filter(r -> r.level() == LogSeverity.CRITICAL)
                    .toList();
            case "WARNING" -> logs.stream()
                    .filter(r -> r.level() == LogSeverity.CRITICAL || r.level() == LogSeverity.WARNING)
                    .toList();
            default -> logs; // INFO — attach all
        };
    }
}
