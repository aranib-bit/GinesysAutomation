package com.gsl.reports;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.markuputils.Markup;
import com.gsl.browser.BrowserLogRecord;
import com.gsl.browser.JsErrorCategory;
import com.gsl.browser.JsErrorClassification;
import com.gsl.browser.JsErrorClassificationEngine;
import com.gsl.browser.LogSeverity;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes browser log records as a collapsible, searchable HTML section in the ExtentReport.
 *
 * Features:
 *   - Live search input (filterLogs JS) — filters all log rows by typed text
 *   - Test description block above the log rows
 *   - Severity colours: CRITICAL=red, WARNING=orange, INFO=dark-gray
 *   - JS_ERROR type shown in red regardless of severity classification
 *   - All messages are HTML-escaped to prevent XSS in the report output
 */
public final class AdvancedExtentLogger {

    private AdvancedExtentLogger() {}

    /**
     * Builds a self-contained searchable HTML block for the given log records and description.
     * Can be passed directly to ExtentTest.info().
     */
    public static String buildUI(List<BrowserLogRecord> logs, String description) {
        StringBuilder html = new StringBuilder();

        // Search input + filterLogs JS
        html.append("<input type='text' placeholder='Search logs...' ")
            .append("onkeyup='filterLogs(this.value)' ")
            .append("style='width:100%;padding:4px;margin-bottom:6px;box-sizing:border-box;'>")
            .append("<script>")
            .append("function filterLogs(val){")
            .append("val=val.toLowerCase();")
            .append("document.querySelectorAll('.bl-row').forEach(function(e){")
            .append("e.style.display=e.innerText.toLowerCase().indexOf(val)>=0?'block':'none';")
            .append("});}")
            .append("</script>");

        // Test description block
        if (description != null && !description.isBlank()) {
            html.append("<div class='bl-row' style='margin-bottom:6px;'>")
                .append("<b>Test Description:</b> ")
                .append(escapeHtml(description))
                .append("</div>");
        }

        // Log rows
        for (BrowserLogRecord record : logs) {
            String color = colorFor(record);
            String ts    = formatTimestamp(record.timestamp());
            html.append("<div class='bl-row' style='color:")
                .append(color)
                .append(";padding:2px 0;font-family:monospace;font-size:12px;'>");

            // Timestamp prefix (always shown, greyed out)
            html.append("<span style='color:#aaa;margin-right:8px;font-size:11px;'>")
                .append(ts).append("</span>");

            // Distinguish JS_ERROR vs CONSOLE entries
            if ("JS_ERROR".equalsIgnoreCase(record.source()) || record.level() == LogSeverity.CRITICAL) {
                html.append("<span style='color:red;font-weight:bold'>[JS ERROR]</span> ")
                    .append(escapeHtml(record.message()));

                // Line : Column (only when captured) — own line for visibility
                if (record.lineNumber() > 0 || record.columnNumber() > 0) {
                    html.append("<br><span style='color:#888;font-size:11px;margin-left:8px;'>")
                        .append("&#128205; Line: <b>").append(record.lineNumber()).append("</b>")
                        .append(" &nbsp;|&nbsp; Col: <b>").append(record.columnNumber()).append("</b>")
                        .append("</span>");
                }

                // Timing correlation bar
                if (hasValue(record.capturedAtStep()) && !"n/a".equalsIgnoreCase(record.capturedAtStep())) {
                    String msColor = record.capturedAfterActionMs() > 500 ? "#cc6600" : "#006600";
                    String rdColor = "loading".equalsIgnoreCase(record.pageReadyState()) ? "red" : "#555";
                    html.append("<br><span style='font-size:11px;margin-left:8px;'>")
                        .append("&#x23F1; Captured at step: <b>").append(escapeHtml(record.capturedAtStep())).append("</b>")
                        .append(" &nbsp;|&nbsp; <span style='color:").append(msColor).append(";'>")
                        .append(record.capturedAfterActionMs() >= 0 ? record.capturedAfterActionMs() + " ms after action" : "timing unknown")
                        .append("</span>")
                        .append(" &nbsp;|&nbsp; Page: <span style='color:").append(rdColor).append(";'>")
                        .append(escapeHtml(record.pageReadyState())).append("</span>")
                        .append("</span>");
                }

                if (hasValue(record.url())) {
                    html.append("<br><span style='color:#888;font-size:11px;margin-left:8px;'>")
                        .append("Affected URL: ").append(escapeHtml(record.url()))
                        .append("</span>");
                }
                if (hasValue(record.stack())) {
                    html.append("<pre style='color:#cc0000;font-size:10px;margin:2px 0 6px 8px;")
                        .append("white-space:pre-wrap;background:#fff5f5;padding:4px;border-left:2px solid #cc0000;'>")
                        .append(escapeHtml(record.stack()))
                        .append("</pre>");
                }

                // Root cause classification (only for classified errors)
                JsErrorClassification cls = JsErrorClassificationEngine.classify(record);
                if (cls.category() != JsErrorCategory.UNKNOWN) {
                    String confColor = switch (cls.confidence()) {
                        case "HIGH"   -> "#c62828";
                        case "MEDIUM" -> "#e65100";
                        default       -> "#555555";
                    };
                    html.append("<div style='margin:2px 0 6px 8px;padding:5px 8px;")
                        .append("background:#fff8e1;border-left:3px solid #f9a825;font-size:11px;'>")
                        .append("<b style='color:#e65100;'>&#128270; Root Cause Analysis</b><br>")
                        .append("Category: <b>").append(escapeHtml(cls.category().name())).append("</b>")
                        .append(" &nbsp;|&nbsp; Cause: ").append(escapeHtml(cls.probableCause()))
                        .append(" &nbsp;|&nbsp; Confidence: <span style='color:").append(confColor)
                        .append(";font-weight:bold;'>").append(escapeHtml(cls.confidence())).append("</span>")
                        .append("</div>");
                }
            } else {
                html.append("[").append(record.level()).append("] ")
                    .append(escapeHtml(record.message()));
            }
            html.append("</div>");
        }

        return html.toString();
    }

    /**
     * Attaches browser logs to the current ExtentTest node as a collapsible section.
     * Uses buildUI() internally with an empty description.
     */
    public static void logBrowserLogs(List<BrowserLogRecord> logs) {
        logBrowserLogs(logs, null);
    }

    /**
     * Attaches browser logs to the current ExtentTest node with a test description header.
     */
    public static void logBrowserLogs(List<BrowserLogRecord> logs, String description) {
        if (logs == null || logs.isEmpty()) return;
        ExtentTest test = ExtentManager.getExtentTest();
        if (test == null) return;

        StringBuilder wrapper = new StringBuilder();
        wrapper.append("<details><summary style='cursor:pointer;font-weight:bold'>")
               .append("Browser Logs (").append(logs.size()).append(" entries)")
               .append("</summary><div style='margin-top:4px'>")
               .append(buildUI(logs, description))
               .append("</div></details>");

        String html = wrapper.toString();
        test.info((Markup) () -> html);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /** Formats epoch-ms to HH:mm:ss.SSS in the JVM default time-zone. */
    private static String formatTimestamp(long epochMs) {
        if (epochMs <= 0) return "--:--:--.---";
        return Instant.ofEpochMilli(epochMs)
                .atZone(ZoneId.systemDefault())
                .format(TS_FMT);
    }

    /** Text color for one log row — red for JS errors/CRITICAL, orange for WARNING, gray otherwise. */
    private static String colorFor(BrowserLogRecord record) {
        if ("JS_ERROR".equalsIgnoreCase(record.source())) return "#ff4444";
        return switch (record.level()) {
            case CRITICAL -> "#ff4444";
            case WARNING  -> "#ff8800";
            case INFO     -> "#444444";
            default       -> "#888888";
        };
    }

    /** {@code true} unless {@code s} is null/blank or one of the placeholder strings {@code "N/A"}/{@code "null"}. */
    private static boolean hasValue(String s) {
        return s != null && !s.isBlank() && !"N/A".equalsIgnoreCase(s) && !"null".equalsIgnoreCase(s);
    }

    /** HTML-escapes {@code input} (amp/lt/gt/quote/apostrophe) so log content can't break or inject into the report markup. */
    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&",  "&amp;")
                    .replace("<",  "&lt;")
                    .replace(">",  "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'",  "&#39;");
    }
}
