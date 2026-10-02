package com.gsl.reports;

import com.gsl.enums.LogCategory;
import com.gsl.enums.ReportSeverity;
import com.gsl.utils.Log;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

/**
 * Single front door for all test-authored logging — every call fans out to both the Extent
 * Report (visible to a human reviewer) and the matching Log4j2 audience file (see
 * {@link #loggerFor(LogCategory)}), so callers never have to remember to write to both places
 * themselves.
 *
 * <p>Use the plain {@link #info(String)}/{@link #pass(String)}/{@link #fail(String)}/
 * {@link #warn(String)} overloads for simple narration. Use a {@link LogCategory} shorthand
 * (e.g. {@link #db(String)}, {@link #uiAction(String)}, {@link #validation(String)}) when the
 * line should carry a colored category badge and route to that category's own audience file —
 * see the category shorthand section below for the full list. {@link #infoTable(String[][])}/
 * {@link #infoLabeledTable(String, String[][])} render a 2-D array as a styled table in both
 * Extent and the log file.
 */
public final class ReportLogger {

    private static final Logger TRACE_LOG = LogManager.getLogger("MethodTrace");

    // ── Direct, named audience loggers ──────────────────────────────────────────
    // Each writes straight to its own dedicated file (see log4j2.xml) with no MDC/context-lookup
    // indirection — replaces an earlier MDC-key + RoutingAppender design that left every audience
    // file completely empty in practice (see log4j2.xml's comment for the incident this fixed).
    private static final Logger BUSINESS_LOG      = LogManager.getLogger("Business");
    private static final Logger UI_ACTIONS_LOG    = LogManager.getLogger("UIActions");
    private static final Logger DB_VALIDATION_LOG = LogManager.getLogger("DBValidation");
    private static final Logger DIAGNOSTICS_LOG   = LogManager.getLogger("Diagnostics");

    /** Maps a {@link LogCategory} to the audience-specific Log4j2 logger its lines are routed to. */
    private static Logger loggerFor(LogCategory category) {
        return switch (category) {
            case BUSINESS -> BUSINESS_LOG;
            case UI_ACTION, FIELD_ENTRY, FIELD_VALIDATION, VALIDATION, MODULE, SUB_MODULE, MENU, SUB_MENU -> UI_ACTIONS_LOG;
            case DB -> DB_VALIDATION_LOG;
            case JS, PERF, RCA, FRAMEWORK, NETWORK, SESSION, NAVIGATION, RECOVERY, TEST_STEP_STATUS,
                 CONTEXT_SWITCH, DATA_BACKFILL, VERIFICATION, LOADER_WAIT, POPUP -> DIAGNOSTICS_LOG;
        };
    }

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
        Log.info("[{}] {}", category.name(), message);
        loggerFor(category).info("[{}] {}", category.name(), message);
        ExtentLogger.infoCategorized(category, message);
    }

    /**
     * Logs to Extent with colored category + severity badges and to Log4j2 with both prefixes.
     * MEDIUM severity suppresses the severity badge in Extent but still logs the level to file.
     */
    public static void info(LogCategory category, ReportSeverity severity, String message) {
        Log.info("[{}][{}] {}", category.name(), severity.name(), message);
        loggerFor(category).info("[{}][{}] {}", category.name(), severity.name(), message);
        ExtentLogger.infoCategorized(category, severity, message);
    }

    /**
     * Logs a CRITICAL severity entry to Extent (red badges) and Log4j2 ERROR.
     * Use for blocking failures, data corruption, or unexpected app state.
     */
    public static void critical(LogCategory category, String message) {
        Log.error("[{}][CRITICAL] {}", category.name(), message);
        loggerFor(category).error("[{}][CRITICAL] {}", category.name(), message);
        ExtentLogger.infoCategorized(category, ReportSeverity.CRITICAL, message);
    }

    /**
     * Logs a HIGH severity entry — business-rule failure, unexpected state.
     */
    public static void high(LogCategory category, String message) {
        Log.warn("[{}][HIGH] {}", category.name(), message);
        loggerFor(category).warn("[{}][HIGH] {}", category.name(), message);
        ExtentLogger.infoCategorized(category, ReportSeverity.HIGH, message);
    }

    // ── Category shorthand helpers ────────────────────────────────────────────
    // All methods delegate to info/critical/high above — no new MDC plumbing.

    public static void business(String msg)              { info(LogCategory.BUSINESS, msg); }
    public static void business(String fmt, Object... a) { info(LogCategory.BUSINESS, fmt(fmt, a)); }

    public static void db(String msg)                    { info(LogCategory.DB, msg); }
    public static void db(String fmt, Object... a)       { info(LogCategory.DB, fmt(fmt, a)); }
    public static void dbWarn(String msg)                { high(LogCategory.DB, msg); }
    public static void dbWarn(String fmt, Object... a)   { high(LogCategory.DB, fmt(fmt, a)); }
    public static void dbError(String msg)               { critical(LogCategory.DB, msg); }
    public static void dbError(String fmt, Object... a)  { critical(LogCategory.DB, fmt(fmt, a)); }

    public static void uiAction(String msg)              { info(LogCategory.UI_ACTION, msg); }
    public static void uiAction(String fmt, Object... a) { info(LogCategory.UI_ACTION, fmt(fmt, a)); }

    /** Field value entry/write (sendKeys/clear) — see {@link LogCategory#FIELD_ENTRY}. Always
     *  visible in the Extent report, unlike {@link #uiAction}'s debug-gated click noise. */
    public static void fieldEntry(String msg)              { info(LogCategory.FIELD_ENTRY, msg); }
    public static void fieldEntry(String fmt, Object... a) { info(LogCategory.FIELD_ENTRY, fmt(fmt, a)); }

    /** Field value read-back/validation after entry (getAttribute) — see
     *  {@link LogCategory#FIELD_VALIDATION}. Item P: kept visually distinct from
     *  {@link #fieldEntry} so a reader can tell "wrote this" apart from "confirmed this" at a
     *  glance. */
    public static void fieldValidation(String msg)              { info(LogCategory.FIELD_VALIDATION, msg); }
    public static void fieldValidation(String fmt, Object... a) { info(LogCategory.FIELD_VALIDATION, fmt(fmt, a)); }

    public static void validation(String msg)               { info(LogCategory.VALIDATION, msg); }
    public static void validation(String fmt, Object... a)  { info(LogCategory.VALIDATION, fmt(fmt, a)); }
    public static void validationWarn(String msg)           { high(LogCategory.VALIDATION, msg); }
    public static void validationWarn(String fmt, Object... a) { high(LogCategory.VALIDATION, fmt(fmt, a)); }
    public static void validationError(String msg)          { critical(LogCategory.VALIDATION, msg); }
    public static void validationError(String fmt, Object... a) { critical(LogCategory.VALIDATION, fmt(fmt, a)); }

    public static void network(String msg)               { info(LogCategory.NETWORK, msg); }
    public static void network(String fmt, Object... a)  { info(LogCategory.NETWORK, fmt(fmt, a)); }
    public static void networkWarn(String msg)           { high(LogCategory.NETWORK, msg); }
    public static void networkWarn(String fmt, Object... a) { high(LogCategory.NETWORK, fmt(fmt, a)); }
    public static void networkError(String msg)          { critical(LogCategory.NETWORK, msg); }
    public static void networkError(String fmt, Object... a) { critical(LogCategory.NETWORK, fmt(fmt, a)); }

    public static void js(String msg)                    { info(LogCategory.JS, msg); }
    public static void js(String fmt, Object... a)       { info(LogCategory.JS, fmt(fmt, a)); }
    public static void jsWarn(String msg)                { high(LogCategory.JS, msg); }
    public static void jsWarn(String fmt, Object... a)   { high(LogCategory.JS, fmt(fmt, a)); }
    public static void jsError(String msg)               { critical(LogCategory.JS, msg); }
    public static void jsError(String fmt, Object... a)  { critical(LogCategory.JS, fmt(fmt, a)); }

    public static void perf(String msg)                  { info(LogCategory.PERF, msg); }
    public static void perf(String fmt, Object... a)     { info(LogCategory.PERF, fmt(fmt, a)); }

    public static void rca(String msg)                   { info(LogCategory.RCA, msg); }
    public static void rca(String fmt, Object... a)      { info(LogCategory.RCA, fmt(fmt, a)); }
    public static void rcaWarn(String msg)               { high(LogCategory.RCA, msg); }
    public static void rcaWarn(String fmt, Object... a)  { high(LogCategory.RCA, fmt(fmt, a)); }
    public static void rcaError(String msg)              { critical(LogCategory.RCA, msg); }
    public static void rcaError(String fmt, Object... a) { critical(LogCategory.RCA, fmt(fmt, a)); }

    public static void framework(String msg)               { info(LogCategory.FRAMEWORK, msg); }
    public static void framework(String fmt, Object... a)  { info(LogCategory.FRAMEWORK, fmt(fmt, a)); }
    public static void frameworkWarn(String msg)           { high(LogCategory.FRAMEWORK, msg); }
    public static void frameworkWarn(String fmt, Object... a) { high(LogCategory.FRAMEWORK, fmt(fmt, a)); }
    public static void frameworkError(String msg)          { critical(LogCategory.FRAMEWORK, msg); }
    public static void frameworkError(String fmt, Object... a) { critical(LogCategory.FRAMEWORK, fmt(fmt, a)); }

    public static void session(String msg)                  { info(LogCategory.SESSION, msg); }
    public static void session(String fmt, Object... a)      { info(LogCategory.SESSION, fmt(fmt, a)); }
    public static void sessionWarn(String msg)                { high(LogCategory.SESSION, msg); }
    public static void sessionWarn(String fmt, Object... a)  { high(LogCategory.SESSION, fmt(fmt, a)); }
    public static void sessionError(String msg)               { critical(LogCategory.SESSION, msg); }
    public static void sessionError(String fmt, Object... a) { critical(LogCategory.SESSION, fmt(fmt, a)); }

    public static void navigation(String msg)                  { info(LogCategory.NAVIGATION, msg); }
    public static void navigation(String fmt, Object... a)      { info(LogCategory.NAVIGATION, fmt(fmt, a)); }
    public static void navigationWarn(String msg)                { high(LogCategory.NAVIGATION, msg); }
    public static void navigationWarn(String fmt, Object... a)  { high(LogCategory.NAVIGATION, fmt(fmt, a)); }
    public static void navigationError(String msg)               { critical(LogCategory.NAVIGATION, msg); }
    public static void navigationError(String fmt, Object... a) { critical(LogCategory.NAVIGATION, fmt(fmt, a)); }

    public static void recovery(String msg)                  { info(LogCategory.RECOVERY, msg); }
    public static void recovery(String fmt, Object... a)      { info(LogCategory.RECOVERY, fmt(fmt, a)); }
    public static void recoveryWarn(String msg)                { high(LogCategory.RECOVERY, msg); }
    public static void recoveryWarn(String fmt, Object... a)  { high(LogCategory.RECOVERY, fmt(fmt, a)); }
    public static void recoveryError(String msg)               { critical(LogCategory.RECOVERY, msg); }
    public static void recoveryError(String fmt, Object... a) { critical(LogCategory.RECOVERY, fmt(fmt, a)); }

    public static void module(String msg)                 { info(LogCategory.MODULE, msg); }
    public static void module(String fmt, Object... a)    { info(LogCategory.MODULE, fmt(fmt, a)); }

    public static void subModule(String msg)              { info(LogCategory.SUB_MODULE, msg); }
    public static void subModule(String fmt, Object... a) { info(LogCategory.SUB_MODULE, fmt(fmt, a)); }

    public static void menu(String msg)                   { info(LogCategory.MENU, msg); }
    public static void menu(String fmt, Object... a)      { info(LogCategory.MENU, fmt(fmt, a)); }

    public static void subMenu(String msg)                { info(LogCategory.SUB_MENU, msg); }
    public static void subMenu(String fmt, Object... a)   { info(LogCategory.SUB_MENU, fmt(fmt, a)); }

    public static void contextSwitch(String msg)                  { info(LogCategory.CONTEXT_SWITCH, msg); }
    public static void contextSwitch(String fmt, Object... a)      { info(LogCategory.CONTEXT_SWITCH, fmt(fmt, a)); }
    public static void contextSwitchWarn(String msg)                { high(LogCategory.CONTEXT_SWITCH, msg); }
    public static void contextSwitchWarn(String fmt, Object... a)  { high(LogCategory.CONTEXT_SWITCH, fmt(fmt, a)); }
    public static void contextSwitchError(String msg)               { critical(LogCategory.CONTEXT_SWITCH, msg); }
    public static void contextSwitchError(String fmt, Object... a) { critical(LogCategory.CONTEXT_SWITCH, fmt(fmt, a)); }

    public static void dataBackfill(String msg)                  { info(LogCategory.DATA_BACKFILL, msg); }
    public static void dataBackfill(String fmt, Object... a)      { info(LogCategory.DATA_BACKFILL, fmt(fmt, a)); }
    public static void dataBackfillWarn(String msg)                { high(LogCategory.DATA_BACKFILL, msg); }
    public static void dataBackfillWarn(String fmt, Object... a)  { high(LogCategory.DATA_BACKFILL, fmt(fmt, a)); }
    public static void dataBackfillError(String msg)               { critical(LogCategory.DATA_BACKFILL, msg); }
    public static void dataBackfillError(String fmt, Object... a) { critical(LogCategory.DATA_BACKFILL, fmt(fmt, a)); }

    /** Positive page/module-identity confirmation — e.g. "General Voucher Add page opened",
     *  "Finance Module is loaded" (see {@link com.gsl.pages.VerifiablePage}). The failure-side
     *  counterpart for this same check is {@link ExtentLogger#failCategorized}, called directly
     *  at the verification call sites — not mirrored here, since a failure must always reach the
     *  report regardless of category, whereas this info-level pass entry follows the normal
     *  business/debug visibility gate like every other category shorthand above. */
    public static void verification(String msg)                  { info(LogCategory.VERIFICATION, msg); }
    public static void verification(String fmt, Object... a)      { info(LogCategory.VERIFICATION, fmt(fmt, a)); }

    /** ExtJS loading-mask/splash-screen wait diagnostics (see {@code WaitUtils}) — e.g. the
     *  "Loader wait [...] summary: ..." lines, previously logged via the plain, uncategorized
     *  {@link #info(String)}/{@link #warn(String)} overloads (no badge, no color at all). */
    public static void loaderWait(String msg)                  { info(LogCategory.LOADER_WAIT, msg); }
    public static void loaderWait(String fmt, Object... a)      { info(LogCategory.LOADER_WAIT, fmt(fmt, a)); }
    public static void loaderWaitWarn(String msg)                { high(LogCategory.LOADER_WAIT, msg); }
    public static void loaderWaitWarn(String fmt, Object... a)  { high(LogCategory.LOADER_WAIT, fmt(fmt, a)); }

    /**
     * Logs a {@code LogCategory.POPUP} entry with its {@link com.gsl.enums.PopupOutcome}
     * (EXPECTED/UNEXPECTED/MAYBE) rendered as a second badge — mirrors the category+severity
     * shape of {@link #info(LogCategory, ReportSeverity, String)} above, but for popup outcome
     * instead of severity. Visibility gates on {@code outcome.isDebugCategory()}, not the parent
     * {@code LogCategory.POPUP}'s own flag (which carries no useful signal on its own once every
     * popup outcome shares one parent category) — see {@link com.gsl.enums.PopupOutcome}.
     */
    public static void popup(com.gsl.enums.PopupOutcome outcome, String message) {
        Log.info("[POPUP][{}] {}", outcome.name(), message);
        loggerFor(LogCategory.POPUP).info("[POPUP][{}] {}", outcome.name(), message);
        ExtentLogger.infoCategorized(LogCategory.POPUP, outcome, message);
    }

    public static void popup(com.gsl.enums.PopupOutcome outcome, String fmt, Object... a) {
        popup(outcome, fmt(fmt, a));
    }

    private static String fmt(String template, Object... args) {
        return org.slf4j.helpers.MessageFormatter.arrayFormat(template, args).getMessage();
    }

    /** Logs raw HTML inline on the current Extent test — Log4j2 is skipped entirely (HTML isn't
     *  readable in a plain-text log file). Delegates straight to {@link ExtentLogger#infoHtml}. */
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
