package com.gsl.validation.snapshot;

import com.gsl.validation.snapshot.audit.AuditFieldCheckResult;

import java.util.List;

/**
 * Aggregate output of {@link DiffEngine#diff} — every column check (pass and fail), every
 * audit-trail row-count check, every field-level audit check ({@link AuditFieldCheckResult} —
 * the audit framework's Expected-vs-Actual comparison, one row per {@code aud2_<table>} column
 * change), and the flattened list of {@link ValidationFailure}s for the shared
 * {@code SoftAssertions}/{@code DatabaseLogger.checkFailed} reporting — plan §8.
 */
public record DiffResult(
        List<ColumnCheckResult> columnChecks,
        List<AuditTableCheckResult> auditChecks,
        List<AuditFieldCheckResult> auditFieldChecks,
        List<ValidationFailure> failures
) {
}
