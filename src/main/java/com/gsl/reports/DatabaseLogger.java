package com.gsl.reports;

/**
 * Named facade over the DB-category logging already used by the AP/AR Voucher validators
 * (e.g. "AP Voucher DB Validation START/COMPLETE", "Level 1 — finvchmain field checks").
 * That existing pattern is preserved as-is; this class only adds the one guaranteed structured
 * shape for a FAILED check that the enterprise reporting spec calls for — SQL + params +
 * execution time + row count + error — instead of leaving it to each validator to remember.
 */
public final class DatabaseLogger {

    private DatabaseLogger() {}

    /** Logs a passed DB check as a single {@code ✓ checkName: detail} line. */
    public static void checkPassed(String checkName, String detail) {
        ReportLogger.db("✓ {}: {}", checkName, detail);
    }

    /** Logs a failed DB check as a {@code ✗} headline plus the full expected/actual/SQL/params/rows/time table. */
    public static void checkFailed(String checkName, Object expected, Object actual,
                                    String sql, Object params, long executionTimeMs, int rowCount) {
        ReportLogger.dbError("✗ {}", checkName);
        ReportLogger.infoLabeledTable(checkName, new String[][] {
                {"Field", "Value"},
                {"Expected", String.valueOf(expected)},
                {"Actual", String.valueOf(actual)},
                {"SQL", sql},
                {"Params", String.valueOf(params)},
                {"Rows", String.valueOf(rowCount)},
                {"Time", executionTimeMs + "ms"},
        });
    }

    /** Logs the start marker for a named DB validation block. */
    public static void validationStart(String label) {
        ReportLogger.db("{} — START", label);
    }

    /** Logs the completion marker for a named DB validation block, stating whether every check passed. */
    public static void validationComplete(String label, boolean allPassed) {
        ReportLogger.db("{} — COMPLETE [{}]", label, allPassed ? "ALL PASSED" : "FAILURES FOUND");
    }
}
