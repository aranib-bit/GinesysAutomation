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

    public static void attach(ITestResult result) {
        if (!ConfigFactory.getConfig().browserLogsEnabled()) return;

        List<BrowserLogRecord> logs = AdvancedLogFetcher.fetchLogs();
        if (logs == null || logs.isEmpty()) return;

        String description = result.getMethod().getDescription();

        // Always attach everything on failure
        if (result.getStatus() == ITestResult.FAILURE
                && ConfigFactory.getConfig().browserLogsCaptureOnFailure()) {
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
        String level = ConfigFactory.getConfig().browserLogsLevel();
        List<BrowserLogRecord> filtered = filterByLevel(logs, level);
        if (!filtered.isEmpty()) {
            AdvancedExtentLogger.logBrowserLogs(filtered, description);
        }
    }

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
