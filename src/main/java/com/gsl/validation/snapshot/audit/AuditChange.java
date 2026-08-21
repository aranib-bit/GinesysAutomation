package com.gsl.validation.snapshot.audit;

/**
 * One field-level business change reconstructed from an {@code aud2_<table>} audit-shadow table —
 * the "Actual Audit" primitive (doc §7.2/§29 of {@code Software_Data_Audit_Testing_and_Representation.md}).
 *
 * <p>Deliberately mirrors {@link com.gsl.validation.snapshot.ColumnCheckResult}'s shape (table,
 * business key, column) rather than inventing a parallel vocabulary — {@code recordId} uses the
 * exact same "col1=val1, col2=val2" display format as
 * {@link com.gsl.validation.snapshot.DiffEngine#businessKeyOf} so the two can be correlated
 * directly in a report.
 *
 * @param table      the base (non-audit) table this change belongs to, e.g. {@code "finvchmain"}
 * @param recordId   business-key display string, e.g. {@code "vchcode=53579"}
 * @param operation  the business-level operation this change represents
 * @param column     the changed column (lower-case); {@code null} for a whole-row INSERT/DELETE
 *                   where no single column is being compared
 * @param oldValue   value before the change; {@code null} for {@link AuditOperation#INSERT}
 * @param newValue   value after the change; {@code null} for {@link AuditOperation#DELETE}
 */
public record AuditChange(
        String table,
        String recordId,
        AuditOperation operation,
        String column,
        Object oldValue,
        Object newValue
) {
}
