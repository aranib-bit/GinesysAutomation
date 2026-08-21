package com.gsl.validation.snapshot.audit;

import com.gsl.validation.snapshot.ValidationFailure;

import java.util.List;

/**
 * Output of {@link AuditComparator#compare} for one audit table — feeds both
 * {@code DiffEngine}'s existence-check fix ({@link #expectedAnyChange()} — was the paired data
 * table's own diff outcome such that a new audit row should exist at all for this operation?) and
 * the field-level "Audit Trail" report table ({@link #fieldChecks()}).
 */
public record AuditComparisonResult(
        boolean expectedAnyChange,
        List<AuditFieldCheckResult> fieldChecks,
        List<ValidationFailure> failures
) {
    public static AuditComparisonResult empty(boolean expectedAnyChange) {
        return new AuditComparisonResult(expectedAnyChange, List.of(), List.of());
    }
}
