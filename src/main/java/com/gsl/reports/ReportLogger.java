package com.gsl.reports;

import com.gsl.utils.Log;

public final class ReportLogger {

    private ReportLogger() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** INFO message — Extent info + Log4j2 INFO */
    public static void info(String message) {
        Log.info(message);
        ExtentLogger.info(message);
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

    /** ERROR with exception — stack trace to log file, abbreviated message to Extent */
    public static void error(String message, Throwable t) {
        Log.error(message, t);
        ExtentLogger.fail(message + " — " + t.getClass().getSimpleName() + ": " + t.getMessage());
    }

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
