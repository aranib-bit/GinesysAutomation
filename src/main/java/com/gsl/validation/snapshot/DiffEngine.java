package com.gsl.validation.snapshot;

import com.gsl.validation.snapshot.audit.AuditComparator;
import com.gsl.validation.snapshot.audit.AuditComparisonResult;
import com.gsl.validation.snapshot.audit.AuditFieldCheckResult;
import com.gsl.validation.snapshot.audit.ExcludedAuditColumnsResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compares a BEFORE/AFTER {@link VoucherSnapshot} pair, table by table, per the
 * {@link DocumentContext}'s {@link VoucherPersistenceMap} — doc §13/§36, plan §1.
 *
 * <p>Runs in two passes so every {@code aud2_*} audit table can be checked against its OWN paired
 * data table's diff outcome for this specific operation, not a blanket assumption:
 * <ol>
 *   <li><b>Pass 1 — data tables.</b> Every non-audit {@link TableRule} is column-diffed as before
 *       ({@link #diffDataTable}), and its {@link ColumnCheckResult}s are kept, keyed by table name,
 *       for Pass 2 to consult.</li>
 *   <li><b>Pass 2 — audit tables.</b> For each {@code aud2_<table>} (paired to its base table by
 *       stripping the {@code aud2_} prefix), the existence check's expectation is now
 *       <b>derived from whether Pass 1 found a real, non-excluded change</b> in the paired data
 *       table for this document — not a hard-coded "always expect &ge;1 new row." This closes a
 *       real gap this session's own Extent report exposed: {@code aud2_finvchdet}/
 *       {@code aud2_fincosttag} both legitimately showed zero new rows for an operation where
 *       {@code dataversion} (excluded for {@code FINVCHDET}) was the only finvchdet-level change
 *       and cost-tag wasn't applicable — the old blanket check failed both as false negatives.
 *       When a data table DID show a real change, field-level {@link AuditComparator} results are
 *       layered on top of the existence check (Level 3/4 per the audit design doc).</li>
 * </ol>
 * A table whose {@code t_<table>_aud2} trigger is physically {@code DISABLED} on this environment
 * (per {@link DocumentContext#getAuditTriggerDisabledTables()}) has its audit checks skipped
 * entirely — reported as an informational row, never a failure; business-data checks for that
 * table are completely unaffected.
 */
public class DiffEngine {

    private final ColumnRuleEngine columnRuleEngine = new ColumnRuleEngine();
    private final AuditComparator auditComparator = new AuditComparator();
    private final ExcludedAuditColumnsResolver excludedColumnsResolver = new ExcludedAuditColumnsResolver();

    public DiffResult diff(DocumentContext context, VoucherSnapshot before, VoucherSnapshot after,
            ExpectedChangeSet changeSet) {

        List<ColumnCheckResult> columnChecks = new ArrayList<>();
        List<AuditTableCheckResult> auditChecks = new ArrayList<>();
        List<AuditFieldCheckResult> auditFieldChecks = new ArrayList<>();
        List<ValidationFailure> failures = new ArrayList<>();

        List<TableRule> activeTables = context.getPersistenceMap().activeTables(context);
        Map<String, TableRule> dataTableRulesByName = new LinkedHashMap<>();
        Map<String, List<ColumnCheckResult>> columnChecksByTable = new LinkedHashMap<>();

        // ── Pass 1 — data tables ────────────────────────────────────────────────
        for (TableRule tableRule : activeTables) {
            if (tableRule.isAuditTable()) continue;

            String tableName = tableRule.getTableName();
            TableSnapshot beforeTable = before.getTable(tableName);
            TableSnapshot afterTable = after.getTable(tableName);

            List<ColumnCheckResult> tableChecks = new ArrayList<>();
            diffDataTable(context, tableRule, changeSet, beforeTable, afterTable, tableChecks, failures);

            columnChecks.addAll(tableChecks);
            dataTableRulesByName.put(tableName.toLowerCase(), tableRule);
            columnChecksByTable.put(tableName.toLowerCase(), tableChecks);
        }

        // ── Pass 2 — audit tables, paired against Pass 1's results ─────────────
        for (TableRule tableRule : activeTables) {
            if (!tableRule.isAuditTable()) continue;
            diffAuditTable(context, tableRule, before, after, dataTableRulesByName, columnChecksByTable,
                    auditChecks, auditFieldChecks, failures);
        }

        return new DiffResult(columnChecks, auditChecks, auditFieldChecks, failures);
    }

    private void diffAuditTable(DocumentContext context, TableRule auditTableRule,
            VoucherSnapshot before, VoucherSnapshot after,
            Map<String, TableRule> dataTableRulesByName, Map<String, List<ColumnCheckResult>> columnChecksByTable,
            List<AuditTableCheckResult> auditChecks, List<AuditFieldCheckResult> auditFieldChecks,
            List<ValidationFailure> failures) {

        String auditTableName = auditTableRule.getTableName();
        TableSnapshot beforeAudit = before.getTable(auditTableName);
        TableSnapshot afterAudit = after.getTable(auditTableName);
        int rowsBefore = beforeAudit == null ? 0 : beforeAudit.rowCount();
        int rowsAfter = afterAudit == null ? 0 : afterAudit.rowCount();

        String baseTableName = stripAuditPrefix(auditTableName);

        if (context.getAuditTriggerDisabledTables().contains(baseTableName)) {
            auditChecks.add(new AuditTableCheckResult(auditTableName, rowsBefore, rowsAfter,
                    "SKIPPED — audit trigger physically disabled for '" + baseTableName + "' on this environment",
                    true, "DISABLED"));
            return;
        }

        TableRule dataTableRule = dataTableRulesByName.get(baseTableName.toLowerCase());
        if (dataTableRule == null) {
            // Paired data table did not participate in this diff for this document (e.g. its own
            // participationCondition excluded it — the audit table's participation should normally
            // already account for that, see VoucherPersistenceMapFactory#auditTable). Reported as an
            // honest skip rather than assuming an INSERT was still required: a data table that never
            // ran a diff can't have "shown a real change," so demanding a new audit row here would be
            // a false positive, not a genuine finding.
            auditChecks.add(new AuditTableCheckResult(auditTableName, rowsBefore, rowsAfter,
                    "SKIPPED — paired data table did not participate for this document", true, "ENABLED"));
            return;
        }

        List<ColumnCheckResult> dataTableChecks = columnChecksByTable.getOrDefault(baseTableName.toLowerCase(), List.of());
        TableSnapshot beforeDataRows = before.getTable(baseTableName);
        Set<String> excludedColumns = excludedColumnsResolver.resolveExcludedColumns(baseTableName);

        AuditComparisonResult comparison = auditComparator.compare(dataTableRule, dataTableChecks,
                beforeAudit, afterAudit, beforeDataRows, excludedColumns);

        boolean actualRowGrowth = rowsAfter > rowsBefore;
        // Only a real finding when the data table showed a change but no new audit row appeared —
        // extra audit activity beyond what the data-table diff declared is surfaced informationally
        // by AuditComparator's own fieldChecks, never failed here (see its Javadoc).
        boolean passed = !(comparison.expectedAnyChange() && !actualRowGrowth);
        String expectedLabel = comparison.expectedAnyChange()
                ? "INSERT (>=1 new row) — paired data table shows a real, non-excluded change"
                : "NO NEW ROW REQUIRED — paired data table shows no non-excluded change for this operation";

        auditChecks.add(new AuditTableCheckResult(auditTableName, rowsBefore, rowsAfter, expectedLabel, passed, "ENABLED"));
        auditFieldChecks.addAll(comparison.fieldChecks());
        failures.addAll(comparison.failures());
        if (!passed) failures.add(rowCountFailure(auditTableName, rowsBefore, rowsAfter));
    }

    private static ValidationFailure rowCountFailure(String auditTableName, int rowsBefore, int rowsAfter) {
        String actual = "rowsBefore=" + rowsBefore + ", rowsAfter=" + rowsAfter;
        return new ValidationFailure(auditTableName, "-", null, "INSERT (>=1 new row)", actual,
                "[" + auditTableName + "] the paired data table showed a real change for this operation but no "
                        + "new audit row was recorded — " + actual, ValidationFailure.Severity.ERROR);
    }

    /** {@code "aud2_finvchdet"} → {@code "finvchdet"} — the naming convention every {@code aud2_*}
     *  audit-shadow table in this schema follows (confirmed live across finance and non-finance
     *  registrations alike), not a Finance-specific assumption. */
    private static String stripAuditPrefix(String auditTableName) {
        String prefix = "aud2_";
        return auditTableName.toLowerCase().startsWith(prefix)
                ? auditTableName.substring(prefix.length())
                : auditTableName;
    }

    private void diffDataTable(DocumentContext context, TableRule tableRule, ExpectedChangeSet changeSet,
            TableSnapshot beforeTable, TableSnapshot afterTable,
            List<ColumnCheckResult> columnChecks, List<ValidationFailure> failures) {

        Map<String, Map<String, Object>> beforeByKey = indexByKey(tableRule, beforeTable);
        Map<String, Map<String, Object>> afterByKey = indexByKey(tableRule, afterTable);

        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(beforeByKey.keySet());
        allKeys.addAll(afterByKey.keySet());

        String tableName = tableRule.getTableName();
        for (String key : allKeys) {
            Map<String, Object> beforeRow = beforeByKey.get(key);
            Map<String, Object> afterRow = afterByKey.get(key);

            if (beforeRow == null || afterRow == null) {
                boolean isInsert = beforeRow == null;
                Map<String, Object> presentRow = isInsert ? afterRow : beforeRow;
                ChangeExpectation expectedDirection = isInsert ? ChangeExpectation.INSERT : ChangeExpectation.DELETE;
                String beforeDisplay = beforeRow == null ? "(absent)" : "(present)";
                String afterDisplay = afterRow == null ? "(absent)" : "(present)";

                if (changeSet != null && changeSet.isRowChangeExpected(tableName, expectedDirection, presentRow)) {
                    columnChecks.add(ColumnCheckResult.pass(tableName, key, "<row>",
                            "EXPECTED " + expectedDirection, beforeDisplay, afterDisplay));
                    continue;
                }

                String label = isInsert ? "unexpectedly appeared (INSERT)" : "unexpectedly disappeared (DELETE)";
                ValidationFailure failure = new ValidationFailure(tableName, key, "<row>",
                        "ROW PRESENT BOTH BEFORE/AFTER", afterDisplay,
                        "[" + tableName + "] row [" + key + "] " + label, ValidationFailure.Severity.CRITICAL);
                columnChecks.add(ColumnCheckResult.fail(tableName, key, "<row>", "ROW PRESENT BOTH BEFORE/AFTER",
                        beforeDisplay, afterDisplay, failure));
                failures.add(failure);
                continue;
            }

            Set<String> columns = new LinkedHashSet<>();
            columns.addAll(afterRow.keySet());
            columns.addAll(beforeRow.keySet());

            for (String column : columns) {
                ColumnRule rule = resolveColumnRule(changeSet, tableRule, tableName, key, column);
                ColumnCheckResult result = columnRuleEngine.evaluate(tableName, key, column, rule,
                        beforeRow.get(column), afterRow.get(column), context.getCurrentUser());
                columnChecks.add(result);
                if (!result.passed()) failures.add(result.failure());
            }
        }
    }

    private ColumnRule resolveColumnRule(ExpectedChangeSet changeSet, TableRule tableRule, String tableName,
            String businessKey, String column) {
        if (changeSet != null) {
            ColumnRule scenarioRule = changeSet.findOverride(tableName, businessKey, column);
            if (scenarioRule != null) return scenarioRule;
        }
        ColumnRule tableOverride = tableRule.findColumnRule(column);
        if (tableOverride != null) return tableOverride;
        return defaultColumnRule(tableRule, column);
    }

    private ColumnRule defaultColumnRule(TableRule tableRule, String column) {
        return switch (tableRule.getDefaultRule()) {
            case MUST_CHANGE -> ColumnRule.mustChange(column);
            case IGNORE -> ColumnRule.ignore(column);
            // SYSTEM_CHANGE/INSERT/DELETE/RECREATED have no generic column-level default strategy —
            // callers must supply an explicit ColumnRule override for those; falling back to
            // MUST_NOT_CHANGE here is the safe default (never silently ignores a real column).
            default -> ColumnRule.mustNotChange(column);
        };
    }

    /** Builds a "col1=val1, col2=val2" business-key display string, keyed the same way for lookups. */
    private static Map<String, Map<String, Object>> indexByKey(TableRule tableRule, TableSnapshot snapshot) {
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        if (snapshot == null) return byKey;
        for (Map<String, Object> row : snapshot.getRows()) {
            byKey.put(businessKeyOf(tableRule, row), row);
        }
        return byKey;
    }

    static String businessKeyOf(TableRule tableRule, Map<String, Object> row) {
        List<String> keyColumns = tableRule.getBusinessKeyColumns();
        if (keyColumns == null || keyColumns.isEmpty()) return "(no key)";
        StringBuilder sb = new StringBuilder();
        for (String col : keyColumns) {
            if (sb.length() > 0) sb.append(", ");
            Object raw = DbValueNormalizer.getIgnoreCase(row, col);
            sb.append(col).append('=').append(DbValueNormalizer.displayString(raw));
        }
        return sb.toString();
    }
}
