package com.gsl.validation.snapshot;

/**
 * One row of the "Audit Trail" report table (plan §8, Table 2) — one per audit-active
 * {@code aud2_<table>} that participated.
 *
 * @param triggerStatus {@code ENABLED}/{@code DISABLED}/{@code UNKNOWN} — the live
 *                      {@code t_<table>_aud2} trigger state (see {@code AuditTriggerStateResolver}),
 *                      surfaced distinctly from {@code passed} so a report reader can tell "audit
 *                      disabled in this environment" (informational, not a failure) apart from a
 *                      genuine missing-audit-row finding.
 */
public record AuditTableCheckResult(
        String auditTable,
        int rowsBefore,
        int rowsAfter,
        String expectedLabel,
        boolean passed,
        String triggerStatus
) {
}
