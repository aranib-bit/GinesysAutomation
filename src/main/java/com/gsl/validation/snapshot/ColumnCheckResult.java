package com.gsl.validation.snapshot;

/**
 * One row of the "Column Diff" report table (plan §8, Table 1) — every column check, pass AND
 * fail, in persistence-map order. One instance per (table, business key, column) triple that
 * {@link DiffEngine} evaluated.
 */
public record ColumnCheckResult(
        String table,
        String businessKey,
        String column,
        String expectationLabel,
        String beforeDisplay,
        String afterDisplay,
        boolean passed,
        ValidationFailure failure,
        String knownIssueNote
) {
    public static ColumnCheckResult pass(String table, String businessKey, String column,
            String expectationLabel, String beforeDisplay, String afterDisplay) {
        return new ColumnCheckResult(table, businessKey, column, expectationLabel, beforeDisplay, afterDisplay, true, null, null);
    }

    public static ColumnCheckResult fail(String table, String businessKey, String column,
            String expectationLabel, String beforeDisplay, String afterDisplay, ValidationFailure failure) {
        return new ColumnCheckResult(table, businessKey, column, expectationLabel, beforeDisplay, afterDisplay, false, failure, null);
    }

    /**
     * A comparison that mismatched but is a KNOWN, already-tracked issue (e.g. a confirmed backend
     * defect reported separately) — {@code passed=true} so it never reaches {@code SoftAssertions}/
     * {@code assertAll()} and never blocks the test, but {@code knownIssueNote} keeps it just as
     * visible in the report as a real failure would be (see {@code SnapshotValidationReporter}'s
     * "⚠️ Known Issues" table).
     */
    public static ColumnCheckResult knownIssue(String table, String businessKey, String column,
            String expectationLabel, String beforeDisplay, String afterDisplay, String note) {
        return new ColumnCheckResult(table, businessKey, column, expectationLabel, beforeDisplay, afterDisplay, true, null, note);
    }
}
