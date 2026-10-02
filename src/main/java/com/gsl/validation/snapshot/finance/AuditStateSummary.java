package com.gsl.validation.snapshot.finance;

import com.gsl.reports.ExtentLogger;
import com.gsl.validation.snapshot.TableRule;
import com.gsl.validation.snapshot.VoucherPersistenceMap;
import com.gsl.validation.snapshot.audit.AuditTriggerStateResolver;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Item U — the audit-active/trigger-disabled state for a voucher domain's persistence map, and
 * the shared table-rendering shape for it. Extracted from the resolution logic that used to be
 * inline in {@link VoucherDocumentContextResolver#resolve} (still used there, unchanged behavior)
 * so it can ALSO run before an existing document is even resolvable (Create's own case — a
 * freshly-added voucher has no {@code vchcode} yet, so
 * {@code VoucherDocumentContextResolver.resolve(...)} can't be called at all before Save, even
 * though the audit-active/trigger-disabled state is purely a property of the table list +
 * environment config, never of a specific document).
 *
 * <p>Renders the exact same {@code Field | Value} table shape
 * {@code EditGeneralVoucher.logSnapshotTable}'s own {@code "... — Audit State"} block already
 * uses for the AFTER snapshot, so a BEFORE-Save check (Create) and the existing Document-Context
 * check (Edit/Revert/Delete) look identical.
 */
public record AuditStateSummary(Set<String> auditActiveTables, Set<String> auditTriggerDisabledTables) {

    /**
     * Resolves the current audit-active/trigger-disabled state for every non-audit table in
     * {@code persistenceMap} — needs only the table list, never a document, so this is callable
     * before a Create-mode voucher exists.
     */
    public static AuditStateSummary resolveFor(VoucherPersistenceMap persistenceMap) {
        Set<String> tableNames = persistenceMap.getTableRules().stream()
                .filter(rule -> !rule.isAuditTable())
                .map(TableRule::getTableName)
                .collect(Collectors.toSet());
        Set<String> auditActiveTables = new Aud2AuditStateResolver().resolveAuditActiveTables(tableNames);

        // Trigger-drift check (user's explicit caution): aud2_active_table registration alone
        // doesn't guarantee the trigger is physically enabled on this environment — checked
        // per-table, only for tables that ARE registered active.
        Set<String> auditTriggerDisabledTables = new HashSet<>();
        AuditTriggerStateResolver triggerStateResolver = new AuditTriggerStateResolver();
        for (String tableName : auditActiveTables) {
            AuditTriggerStateResolver.TriggerStatus status = triggerStateResolver.resolveTriggerStatus(tableName);
            if (status == AuditTriggerStateResolver.TriggerStatus.DISABLED) {
                auditTriggerDisabledTables.add(tableName);
            }
        }
        return new AuditStateSummary(auditActiveTables, auditTriggerDisabledTables);
    }

    /** Renders the shared {@code Field | Value} table — same shape wherever this is logged
     *  (alongside Document Context for Edit/Revert/Delete). Trigger row states a plain
     *  Enabled/Disabled word first (not just a table list or "none") — a reader shouldn't have to
     *  infer "none listed" means "trigger is fine" versus genuinely not-yet-checked. */
    public void logTable(String label) {
        ExtentLogger.infoLabeledTable(label, new String[][]{
                {"Field", "Value"},
                {"Audit-Active Tables", auditActiveTables.isEmpty()
                        ? "none, audit inactive" : String.join(", ", auditActiveTables)},
                {"Audit Trigger", triggerStateDisplay()},
        });
    }

    /**
     * One-line, business-readable rendering for embedding directly into a
     * {@code StepSummaryBuffer} fragment (e.g. inside "Fill Expense/Income Detail"'s own
     * consolidated Summary box) instead of a separate standalone step/table — per explicit
     * request that this check not render as its own separate section.
     */
    public String toSummaryLine(String label) {
        String activeText = auditActiveTables.isEmpty()
                ? "none (audit inactive)" : String.join(", ", auditActiveTables);
        return "<b>" + label + "</b> — Audit-Active Tables: <b>" + activeText
                + "</b> | Audit Trigger: <b>" + triggerStateDisplay() + "</b>";
    }

    /** "Enabled" when no audit-active table has its trigger physically disabled; "Disabled
     *  (table1, table2)" otherwise — a plain Enabled/Disabled word first, per explicit request,
     *  with the affected table(s) still named for the disabled case (no information lost). */
    private String triggerStateDisplay() {
        return auditTriggerDisabledTables.isEmpty()
                ? "Enabled"
                : "Disabled (" + String.join(", ", auditTriggerDisabledTables) + ")";
    }
}
