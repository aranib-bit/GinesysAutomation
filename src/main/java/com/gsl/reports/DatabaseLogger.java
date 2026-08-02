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

    public static void checkPassed(String checkName, String detail) {
        ReportLogger.db("✓ {}: {}", checkName, detail);
    }

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

    public static void validationStart(String label) {
        ReportLogger.db("{} — START", label);
    }

    public static void validationComplete(String label, boolean allPassed) {
        ReportLogger.db("{} — COMPLETE [{}]", label, allPassed ? "ALL PASSED" : "FAILURES FOUND");
    }
}
