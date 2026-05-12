package com.gsl.browser;

import com.gsl.driver.DriverManager;
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
            Object raw = js.executeScript("return window.testLogs || [];");
            if (!(raw instanceof List<?> entries)) return records;

            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> rawMap)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) rawMap;
                String message   = String.valueOf(map.getOrDefault("message",   ""));
                String source    = String.valueOf(map.getOrDefault("source",    "unknown"));
                String stack     = String.valueOf(map.getOrDefault("stack",     ""));
                String url       = String.valueOf(map.getOrDefault("url",       ""));
                Object tsRaw     = map.getOrDefault("timestamp", 0L);
                long   timestamp = tsRaw instanceof Number n ? n.longValue() : 0L;

                LogSeverity severity = SeverityClassifier.classify(message);
                if (severity != LogSeverity.IGNORE) {
                    records.add(new BrowserLogRecord(message, severity, timestamp, source, stack, url));
                }
            }
        } catch (Exception e) {
            LOG.warn("[AdvancedLogFetcher] Error fetching browser logs: {}", e.getMessage());
        }
        return records;
    }
}
