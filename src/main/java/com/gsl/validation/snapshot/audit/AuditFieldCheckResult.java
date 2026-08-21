package com.gsl.validation.snapshot.audit;

import com.gsl.validation.snapshot.ValidationFailure;

/**
 * One row of the field-level "Audit Trail" report table (doc §18's {@code Field | Expected |
 * Actual | Result} format) — the audit-side parallel to
 * {@link com.gsl.validation.snapshot.ColumnCheckResult}, produced by {@link AuditComparator}
 * comparing the Expected Audit (derived from the data table's own {@code ColumnCheckResult}s)
 * against the Actual Audit (from {@link AuditChangeReconstructor}).
 */
public record AuditFieldCheckResult(
        String table,
        String recordId,
        AuditOperation operation,
        String column,
        String expectedOld,
        String expectedNew,
        String actualOld,
        String actualNew,
        boolean passed,
        ValidationFailure failure
) {
    public static AuditFieldCheckResult pass(String table, String recordId, AuditOperation operation,
            String column, String expectedOld, String expectedNew, String actualOld, String actualNew) {
        return new AuditFieldCheckResult(table, recordId, operation, column,
                expectedOld, expectedNew, actualOld, actualNew, true, null);
    }

    public static AuditFieldCheckResult fail(String table, String recordId, AuditOperation operation,
            String column, String expectedOld, String expectedNew, String actualOld, String actualNew,
            ValidationFailure failure) {
        return new AuditFieldCheckResult(table, recordId, operation, column,
                expectedOld, expectedNew, actualOld, actualNew, false, failure);
    }
}
