package com.gsl.browser;

import com.gsl.driver.DriverManager;
import com.gsl.reports.ReportLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fetches entries from window.testLogs[] (populated by AdvancedJsLogger) and maps
 * each raw JS object to a typed BrowserLogRecord using SeverityClassifier.
 */
public final class AdvancedLogFetcher {

    private static final Logger LOG = LogManager.getLogger(AdvancedLogFetcher.class);

    private AdvancedLogFetcher() {}

    @SuppressWarnings("unchecked")
    public static List<BrowserLogRecord> fetchLogs() {
        List<BrowserLogRecord> records = new ArrayList<>();
        try {
            JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();
            // Prefer window.testLogs; fall back to sessionStorage when the page just navigated
            // and the shim hasn't restored the array yet (post-save redirect race condition).
            Object raw = js.executeScript(
                "var logs = window.testLogs;" +
                "if (!logs || logs.length === 0) {" +
                "  try {" +
                "    var s = sessionStorage.getItem('_fwTestLogs');" +
                "    if (s) { logs = JSON.parse(s); }" +
                "  } catch(e) {}" +
                "}" +
                "return logs || [];");
            if (!(raw instanceof List<?> entries)) return records;

            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> rawMap)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) rawMap;

                String message   = String.valueOf(map.getOrDefault("message",   ""));
                String source    = String.valueOf(map.getOrDefault("source",    "unknown"));
                String stack     = String.valueOf(map.getOrDefault("stack",     ""));
                String url       = String.valueOf(map.getOrDefault("url",       ""));
                String rawLevel  = String.valueOf(map.getOrDefault("level",     "log"));
                Object tsRaw     = map.getOrDefault("timestamp", 0L);
                long   timestamp = tsRaw instanceof Number n ? n.longValue() : 0L;

                // Structured error position (JS_ERROR only; 0 for CONSOLE entries)
                Object lineRaw   = map.getOrDefault("line", 0);
                Object colRaw    = map.getOrDefault("col",  0);
                int lineNumber   = lineRaw instanceof Number n ? n.intValue() : 0;
                int columnNumber = colRaw  instanceof Number n ? n.intValue() : 0;

                // Timing correlation fields (JS_ERROR only)
                String capturedAtStep        = String.valueOf(map.getOrDefault("capturedAtStep", "n/a"));
                Object afterMsRaw            = map.getOrDefault("capturedAfterActionMs", -1);
                int    capturedAfterActionMs = afterMsRaw instanceof Number n ? n.intValue() : -1;
                String pageReadyState        = String.valueOf(map.getOrDefault("pageReadyState", "n/a"));

                LogSeverity severity = SeverityClassifier.classify(message, rawLevel);
                if (severity != LogSeverity.IGNORE) {
                    records.add(new BrowserLogRecord(
                            message, severity, timestamp, source, stack, url,
                            lineNumber, columnNumber,
                            capturedAtStep, capturedAfterActionMs, pageReadyState));
                }
            }
        } catch (Exception e) {
            LOG.warn("[AdvancedLogFetcher] Error fetching browser logs: {}", e.getMessage());
            ReportLogger.jsWarn("[AdvancedLogFetcher] Error fetching browser logs: {}", e.getMessage());
        }
        return records;
    }
}
