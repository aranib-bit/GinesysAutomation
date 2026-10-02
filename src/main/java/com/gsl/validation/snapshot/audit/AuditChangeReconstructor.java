package com.gsl.validation.snapshot.audit;

import com.gsl.annotations.AuditTrace;
import com.gsl.utils.Log;
import com.gsl.validation.snapshot.DbValueNormalizer;
import com.gsl.validation.snapshot.TableRule;
import com.gsl.validation.snapshot.TableSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The <b>AuditNormalizer</b> — reconstructs field-level {@link AuditChange}s from a BEFORE/AFTER
 * pair of {@code aud2_<table>} row captures, the "Actual Audit" side of the doc §7 Expected-vs-
 * Actual comparison.
 *
 * <p>{@code aud2_<table>} rows are whole-row snapshots, not old/new pairs — an {@code UPDATE} audit
 * row captures only the <b>new</b> state (confirmed live: {@code aud2_finvchmain}'s {@code UPDATE}
 * row for a released voucher carried the same {@code glcode}/{@code damount}/{@code camount} as its
 * preceding {@code INSERT} row; the real change — {@code release_status}/{@code release_time} — was
 * elsewhere in the row). Per the user's confirmed design decision, the <b>old</b> value for an
 * in-place {@code UPDATE} comes from the test's own live BEFORE data-table snapshot (ground truth),
 * not from chasing prior audit rows — {@code beforeDataRows} is passed in for exactly this lookup.
 *
 * <h3>DELETE+INSERT reconciliation</h3>
 * Some tables physically delete-and-reinsert a row under a new surrogate key for what is, from the
 * business perspective, a single {@code UPDATE} (doc §5; confirmed live in {@code aud2_finvchdn}:
 * same {@code finvchmain_code}, a {@code DELETE} at {@code code=1} immediately followed by an
 * {@code INSERT} at {@code code=21}, differing only in {@code finjrnmain_code}). New rows are
 * grouped by {@link TableRule#getIdentityColumnsOrDefault()} — a coarser key than the full business
 * key, excluding the surrogate column — and a DELETE+INSERT pair sharing that identity collapses
 * into one {@link AuditOperation#UPDATE} per differing (non-excluded) column, using the DELETE row's
 * captured value as old and the INSERT row's as new (no {@code beforeDataRows} lookup needed here —
 * the DELETE row already <em>is</em> the pre-change state).
 */
public class AuditChangeReconstructor {

    /**
     * @param dataTableRule    the base (non-audit) table's {@link TableRule} — supplies business-key
     *                         and identity-column configuration
     * @param beforeAuditRows  {@code aud2_<table>} rows captured before the operation
     * @param afterAuditRows   {@code aud2_<table>} rows captured after the operation
     * @param beforeDataRows   the base table's own BEFORE snapshot — ground truth for an in-place
     *                         UPDATE's old value (nullable; in-place UPDATE columns are skipped with
     *                         a warning if absent)
     * @param excludedColumnsUpper column names (upper-case) excluded from being reported as
     *                             "changed" per {@code aud2_active_tab_col.exclude_update_of_column}
     *                             — still present in the row, just not audit-significant
     * @return every reconstructed {@link AuditChange}, empty if no new audit row appeared
     */
    @AuditTrace("reconstruct")
    public List<AuditChange> reconstruct(TableRule dataTableRule, TableSnapshot beforeAuditRows,
            TableSnapshot afterAuditRows, TableSnapshot beforeDataRows, Set<String> excludedColumnsUpper) {

        List<Map<String, Object>> before = rowsOf(beforeAuditRows);
        List<Map<String, Object>> after = rowsOf(afterAuditRows);
        Set<String> excluded = excludedColumnsUpper == null ? Set.of() : excludedColumnsUpper;

        Set<String> beforeRowIds = new LinkedHashSet<>();
        for (Map<String, Object> row : before) beforeRowIds.add(rowIdentity(dataTableRule, row));

        List<Map<String, Object>> newRows = new ArrayList<>();
        for (Map<String, Object> row : after) {
            if (!beforeRowIds.contains(rowIdentity(dataTableRule, row))) newRows.add(row);
        }
        if (newRows.isEmpty()) return List.of();

        List<String> identityColumns = dataTableRule.getIdentityColumnsOrDefault();

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : newRows) {
            grouped.computeIfAbsent(keyOf(identityColumns, row), k -> new ArrayList<>()).add(row);
        }

        List<AuditChange> changes = new ArrayList<>();
        for (List<Map<String, Object>> group : grouped.values()) {
            changes.addAll(reconcileGroup(dataTableRule, group, beforeDataRows, excluded));
        }
        return changes;
    }

    /** Splits one identity-group's new audit rows by their {@code action}, pairs up DELETE+INSERT
     *  rows into reconciled UPDATEs (see class Javadoc), and diffs any genuine UPDATE rows against
     *  {@code beforeDataRows} — the per-group body behind {@link #reconstruct}. */
    @AuditTrace("reconcileGroup")
    private List<AuditChange> reconcileGroup(TableRule dataTableRule, List<Map<String, Object>> group,
            TableSnapshot beforeDataRows, Set<String> excluded) {

        List<Map<String, Object>> deletes = new ArrayList<>();
        List<Map<String, Object>> inserts = new ArrayList<>();
        List<Map<String, Object>> updates = new ArrayList<>();
        for (Map<String, Object> row : group) {
            String action = str(DbValueNormalizer.getIgnoreCase(row, "action"));
            if ("DELETE".equalsIgnoreCase(action)) deletes.add(row);
            else if ("INSERT".equalsIgnoreCase(action)) inserts.add(row);
            else if ("UPDATE".equalsIgnoreCase(action)) updates.add(row);
            else Log.warn("[AuditChangeReconstructor] Unrecognized aud2_{} action='{}' — row ignored.",
                    dataTableRule.getTableName(), action);
        }

        List<AuditChange> changes = new ArrayList<>();

        // Reconcile DELETE+INSERT pairs sharing this identity group into a single business UPDATE —
        // 1:1 in encounter order (append-only audit log preserves chronological order within a group).
        int paired = Math.min(deletes.size(), inserts.size());
        for (int i = 0; i < paired; i++) {
            changes.addAll(diffReconciledUpdate(dataTableRule, deletes.get(i), inserts.get(i), excluded));
        }
        for (int i = paired; i < deletes.size(); i++) {
            changes.add(wholeRowChange(dataTableRule, deletes.get(i), AuditOperation.DELETE));
        }
        for (int i = paired; i < inserts.size(); i++) {
            changes.add(wholeRowChange(dataTableRule, inserts.get(i), AuditOperation.INSERT));
        }
        for (Map<String, Object> row : updates) {
            changes.addAll(diffInPlaceUpdate(dataTableRule, row, beforeDataRows, excluded));
        }
        return changes;
    }

    /** DELETE row already IS the pre-change state — no {@code beforeDataRows} lookup needed.
     *
     * <p>The generic {@code exclude_update_of_column} list ({@code excluded}) is deliberately NOT
     * applied to {@code dataTableRule}'s own business-key column(s) here — that list exists to
     * suppress noise columns for an ordinary in-place UPDATE (where the business key never changes),
     * but for a reconciled DELETE+INSERT pair the business key changing under a new surrogate value
     * (e.g. {@code fincosttag.code}) IS the change: it's the only column that ever differs between
     * the DELETE and INSERT rows, and it's exactly what identifies this as a reconciled business
     * UPDATE rather than two unrelated row-level events (see class Javadoc). Excluding it here would
     * make every reconciled pair diff to nothing, silently discarding the one audit-significant
     * signal this method exists to surface (confirmed live: {@code aud2_active_tab_col} lists
     * {@code CODE} under {@code fincosttag}'s exclude list — correct for in-place updates, wrong for
     * this path). */
    private List<AuditChange> diffReconciledUpdate(TableRule dataTableRule, Map<String, Object> deleteRow,
            Map<String, Object> insertRow, Set<String> excluded) {
        List<AuditChange> changes = new ArrayList<>();
        String recordId = businessKeyOf(dataTableRule, insertRow);
        List<String> businessKeyColumns = dataTableRule.getBusinessKeyColumns();
        for (String column : commonColumns(deleteRow, insertRow)) {
            boolean isBusinessKeyColumn = businessKeyColumns != null
                    && businessKeyColumns.stream().anyMatch(col -> col.equalsIgnoreCase(column));
            if (!isBusinessKeyColumn && excluded.contains(column.toUpperCase())) continue;
            Object oldValue = DbValueNormalizer.getIgnoreCase(deleteRow, column);
            Object newValue = DbValueNormalizer.getIgnoreCase(insertRow, column);
            if (!DbValueNormalizer.equalsNormalized(oldValue, newValue)) {
                changes.add(new AuditChange(dataTableRule.getTableName(), recordId,
                        AuditOperation.UPDATE, column.toLowerCase(), oldValue, newValue));
            }
        }
        return changes;
    }

    /** In-place UPDATE row — old value comes from the live BEFORE data-table snapshot. */
    private List<AuditChange> diffInPlaceUpdate(TableRule dataTableRule, Map<String, Object> auditRow,
            TableSnapshot beforeDataRows, Set<String> excluded) {
        String recordId = businessKeyOf(dataTableRule, auditRow);
        Map<String, Object> beforeDataRow = findByBusinessKey(dataTableRule, beforeDataRows, recordId);
        if (beforeDataRow == null) {
            Log.warn("[AuditChangeReconstructor] No BEFORE data-table row found for {} {} — skipping "
                    + "in-place UPDATE column comparison for this audit row (old value unknown).",
                    dataTableRule.getTableName(), recordId);
            return List.of();
        }

        List<AuditChange> changes = new ArrayList<>();
        for (String column : auditRow.keySet()) {
            if (isMetaColumn(column) || excluded.contains(column.toUpperCase())) continue;
            Object oldValue = DbValueNormalizer.getIgnoreCase(beforeDataRow, column);
            Object newValue = DbValueNormalizer.getIgnoreCase(auditRow, column);
            if (!DbValueNormalizer.equalsNormalized(oldValue, newValue)) {
                changes.add(new AuditChange(dataTableRule.getTableName(), recordId,
                        AuditOperation.UPDATE, column.toLowerCase(), oldValue, newValue));
            }
        }
        return changes;
    }

    /** Builds a row-level (no single {@code column}) {@link AuditChange} for an unpaired DELETE or
     *  INSERT row — see {@link #reconcileGroup}. */
    private AuditChange wholeRowChange(TableRule dataTableRule, Map<String, Object> row, AuditOperation operation) {
        return new AuditChange(dataTableRule.getTableName(), businessKeyOf(dataTableRule, row),
                operation, null, null, null);
    }

    private static List<Map<String, Object>> rowsOf(TableSnapshot snapshot) {
        return snapshot == null ? List.of() : snapshot.getRows();
    }

    /** First row in {@code snapshot} whose business key equals {@code businessKey}; {@code null} if none. */
    private static Map<String, Object> findByBusinessKey(TableRule tableRule, TableSnapshot snapshot, String businessKey) {
        for (Map<String, Object> row : rowsOf(snapshot)) {
            if (businessKeyOf(tableRule, row).equals(businessKey)) return row;
        }
        return null;
    }

    /** Row identity for new-row detection: full business key + {@code transaction_id} — a table can
     *  legitimately produce several new audit rows (one per changed business-key row) under the
     *  SAME transaction_id in one Save, so transaction_id alone doesn't disambiguate them. */
    private static String rowIdentity(TableRule dataTableRule, Map<String, Object> row) {
        String txnId = str(DbValueNormalizer.getIgnoreCase(row, "transaction_id"));
        return txnId + "|" + businessKeyOf(dataTableRule, row);
    }

    /** Same "col1=val1, col2=val2" display format as {@code DiffEngine.businessKeyOf} — lets an
     *  {@link AuditChange#recordId()} correlate directly with a {@code ColumnCheckResult.businessKey()}. */
    private static String businessKeyOf(TableRule dataTableRule, Map<String, Object> row) {
        return keyOf(dataTableRule.getBusinessKeyColumns(), row);
    }

    /** Renders {@code row}'s values for {@code columns} as a {@code "col1=val1, col2=val2"} display
     *  string — shared by {@link #businessKeyOf} and {@link #rowIdentity}'s grouping key. */
    private static String keyOf(List<String> columns, Map<String, Object> row) {
        if (columns == null || columns.isEmpty()) return "(no key)";
        StringBuilder sb = new StringBuilder();
        for (String col : columns) {
            if (sb.length() > 0) sb.append(", ");
            Object raw = DbValueNormalizer.getIgnoreCase(row, col);
            sb.append(col).append('=').append(DbValueNormalizer.displayString(raw));
        }
        return sb.toString();
    }

    /** Union of {@code a}'s and {@code b}'s column names, excluding meta columns — the column set
     *  {@link #diffReconciledUpdate} compares between a DELETE and its paired INSERT row. */
    private static Set<String> commonColumns(Map<String, Object> a, Map<String, Object> b) {
        Set<String> columns = new LinkedHashSet<>();
        for (String c : a.keySet()) if (!isMetaColumn(c)) columns.add(c);
        for (String c : b.keySet()) if (!isMetaColumn(c)) columns.add(c);
        return columns;
    }

    /** Audit-shadow-table bookkeeping columns that are never themselves reportable "changes". */
    private static boolean isMetaColumn(String column) {
        return column != null && (column.equalsIgnoreCase("action") || column.equalsIgnoreCase("action_subtype")
                || column.equalsIgnoreCase("transaction_id"));
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
