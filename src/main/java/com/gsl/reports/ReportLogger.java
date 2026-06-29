package com.gsl.reports;

import com.gsl.enums.LogCategory;
import com.gsl.enums.ReportSeverity;
import com.gsl.utils.Log;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.slf4j.MDC;

public final class ReportLogger {

    private static final Logger TRACE_LOG = LogManager.getLogger("MethodTrace");

    private static final java.time.format.DateTimeFormatter TRACE_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private ReportLogger() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** INFO message — Extent info + Log4j2 INFO. Routes to ValidationBuffer when active. */
    public static void info(String message) {
        Log.info(message);
        if (ValidationBuffer.isActive()) {
            ValidationBuffer.add(message);
        } else {
            ExtentLogger.info(message);
        }
    }

    /** INFO step — Extent info + Log4j2 INFO */
    public static void step(String message) {
        Log.info(message);
        ExtentLogger.info(message);
    }

    /** PASS — Extent pass + Log4j2 INFO */
    public static void pass(String message) {
        Log.info("[PASS] " + message);
        ExtentLogger.pass(message);
    }

    /** FAIL — Extent fail (with screenshot) + Log4j2 ERROR */
    public static void fail(String message) {
        Log.error("[FAIL] " + message);
        ExtentLogger.fail(message);
    }

    /** WARN — Log4j2 WARN + Extent yellow warning node */
    public static void warn(String message) {
        Log.warn(message);
        ExtentLogger.warning("⚠ " + message);
    }

    /** DEBUG — Log4j2 only; keeps Extent Report clean */
    public static void debug(String message) {
        Log.debug(message);
    }

    /**
     * Writes a data-flow marker to trace.log (MethodTrace logger) at the current AOP call depth,
     * AND to framework.log at DEBUG level. Use this to surface key in-method data values —
     * field values set on the UI, DB row counts, or assertion context — inline in the trace hierarchy.
     * Format in trace.log: indented [DATA] line between the enclosing [START]/[END] lines.
     */
    public static void trace(String message) {
        try {
            int depth = com.gsl.aspects.LoggingAspect.currentDepth();
            String indent = "  ".repeat(depth);
            String ts = java.time.LocalDateTime.now().format(TRACE_FMT);
            String traceId = ThreadContext.get("testId");
            if (traceId == null) traceId = ThreadContext.get("testName");
            if (traceId == null) traceId = "T-" + Thread.currentThread().getId();
            TRACE_LOG.info("{}[{}] [TRACE_ID={}] [DATA]   {}", indent, ts, traceId, message);
        } catch (Exception ignored) {}
        Log.debug("[TRACE_DATA] {}", message);
    }

    /** ERROR with exception — stack trace to log file, abbreviated message to Extent */
    public static void error(String message, Throwable t) {
        Log.error(message, t);
        ExtentLogger.fail(message + " — " + t.getClass().getSimpleName() + ": " + t.getMessage());
    }

    // ── Categorized overloads — badge + Log4j2 + audience routing ───────────
    // Each method sets MDC "logCategory" before the Log call so the Log4j2
    // RoutingAppender in log4j2.xml can write to the correct audience file.
    // The key is always removed in a finally block to prevent MDC leakage.

    /**
     * Logs to Extent with a colored category badge and to Log4j2 with a [CATEGORY] prefix.
     * Severity defaults to MEDIUM (badge suppressed).
     */
    public static void info(LogCategory category, String message) {
        MDC.put("logCategory", category.name());
        try {
            Log.info("[{}] {}", category.name(), message);
        } finally {
            MDC.remove("logCategory");
        }
        ExtentLogger.infoCategorized(category, message);
    }

    /**
     * Logs to Extent with colored category + severity badges and to Log4j2 with both prefixes.
     * MEDIUM severity suppresses the severity badge in Extent but still logs the level to file.
     */
    public static void info(LogCategory category, ReportSeverity severity, String message) {
        MDC.put("logCategory", category.name());
        try {
            Log.info("[{}][{}] {}", category.name(), severity.name(), message);
        } finally {
            MDC.remove("logCategory");
        }
        ExtentLogger.infoCategorized(category, severity, message);
    }

    /**
     * Logs a CRITICAL severity entry to Extent (red badges) and Log4j2 ERROR.
     * Use for blocking failures, data corruption, or unexpected app state.
     */
    public static void critical(LogCategory category, String message) {
        MDC.put("logCategory", category.name());
        try {
            Log.error("[{}][CRITICAL] {}", category.name(), message);
        } finally {
            MDC.remove("logCategory");
        }
        ExtentLogger.infoCategorized(category, ReportSeverity.CRITICAL, message);
    }

    /**
     * Logs a HIGH severity entry — business-rule failure, unexpected state.
     */
    public static void high(LogCategory category, String message) {
        MDC.put("logCategory", category.name());
        try {
            Log.warn("[{}][HIGH] {}", category.name(), message);
        } finally {
            MDC.remove("logCategory");
        }
        ExtentLogger.infoCategorized(category, ReportSeverity.HIGH, message);
    }

    /** Logs raw HTML inline on the current Extent test. Log4j2 is skipped (HTML not readable in file). */
    public static void infoHtml(String html) {
        ExtentLogger.infoHtml(html);
    }

    // ── Table ─────────────────────────────────────────────────────────────────

    /**
     * Logs a 2-D table to:
     *   • Extent Report — HTML scrollable table with borders (via ExtentLogger)
     *   • Log file      — plain text, header + data rows, no borders
     *
     * table[0] = header row; table[1..n] = data rows.
     *
     * Plain-text output example:
     *   [TABLE] Field | Value
     *           Scenario | SEL_DOC
     *           TestCase ID | TC-42
     */
    public static void infoTable(String[][] table) {
        ExtentLogger.infoTable(table);
        Log.info(toPlainText(table));
    }

    /** Logs a labeled table: bold label above the styled table in one Extent Info entry. */
    public static void infoLabeledTable(String label, String[][] table) {
        ExtentLogger.infoLabeledTable(label, table);
        Log.info("[TABLE: " + label + "] " + toPlainText(table));
    }

    private static String toPlainText(String[][] table) {
        if (table == null || table.length == 0) return "[TABLE] (empty)";
        StringBuilder sb = new StringBuilder("[TABLE]");
        for (int r = 0; r < table.length; r++) {
            sb.append(r == 0 ? " " : "\n        ");
            for (int c = 0; c < table[r].length; c++) {
                if (c > 0) sb.append(" | ");
                sb.append(table[r][c] == null ? "—" : table[r][c]);
            }
        }
        return sb.toString();
    }
}
