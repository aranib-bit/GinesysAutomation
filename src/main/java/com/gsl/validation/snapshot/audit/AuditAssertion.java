package com.gsl.validation.snapshot.audit;

import com.gsl.annotations.AuditTrace;
import com.gsl.validation.snapshot.AuditSnapshotDao;
import com.gsl.validation.snapshot.DbValueNormalizer;
import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.TableRule;
import org.testng.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static convenience assertions for call sites that don't run the full before/after
 * {@code SnapshotService} pipeline — Delete (the row is gone, there's no "after" business-table
 * state to diff) and Revert/Release (a single targeted field, e.g. {@code release_status}). Each
 * builds a minimal, one-table audit check under the hood, so these get the same
 * exclude-column-aware comparison as Edit, not a separate hand-rolled path.
 */
public final class AuditAssertion {

    private static final ExcludedAuditColumnsResolver EXCLUDED_COLUMNS = new ExcludedAuditColumnsResolver();

    private AuditAssertion() {}

    /**
     * Asserts {@code aud2_<table>} recorded a {@code DELETE} row for {@code beforeRow}'s business
     * key whose captured (non-excluded) column values match the record's state immediately before
     * it was deleted.
     */
    @AuditTrace("assertAuditDeleted")
    public static void assertAuditDeleted(DocumentContext context, AuditSnapshotDao auditDao,
            TableRule tableRule, Map<String, Object> beforeRow) {
        String tableName = tableRule.getTableName();
        String auditTableName = "aud2_" + tableName;
        String businessKey = businessKeyOf(tableRule, beforeRow);
        Set<String> excludedColumns = EXCLUDED_COLUMNS.resolveExcludedColumns(tableName);

        List<Map<String, Object>> auditRows = new AuditRepository(auditDao).fetchAuditRows(auditTableName, context);
        Map<String, Object> deleteRow = mostRecentMatching(tableRule, auditRows, businessKey, "DELETE");

        Assert.assertNotNull(deleteRow, "[AuditAssertion] Expected a DELETE row in " + auditTableName + " for "
                + businessKey + " after deleting the record, but none was found — the audit trail did not record "
                + "this delete (check whether the audit trigger is enabled for " + tableName + " in this environment).");

        List<String> mismatches = compareColumns(beforeRow, deleteRow, excludedColumns);
        Assert.assertTrue(mismatches.isEmpty(), "[AuditAssertion] " + auditTableName + " DELETE row for " + businessKey
                + " does not match the pre-delete state: " + String.join("; ", mismatches));
    }

    /**
     * Asserts {@code aud2_<table>} recorded an {@code UPDATE} (or a reconciled DELETE+INSERT — both
     * decoded identically by {@link AuditRepository}'s raw fetch here) row reflecting {@code column}
     * transitioning from {@code expectedOld} to {@code expectedNew} for {@code beforeRow}'s business key.
     */
    @AuditTrace("assertAuditUpdated")
    public static void assertAuditUpdated(DocumentContext context, AuditSnapshotDao auditDao, TableRule tableRule,
            Map<String, Object> beforeRow, String column, Object expectedOld, Object expectedNew) {
        String tableName = tableRule.getTableName();
        String auditTableName = "aud2_" + tableName;
        String businessKey = businessKeyOf(tableRule, beforeRow);

        List<Map<String, Object>> auditRows = new AuditRepository(auditDao).fetchAuditRows(auditTableName, context);
        Map<String, Object> updateRow = mostRecentMatching(tableRule, auditRows, businessKey, "UPDATE");

        Assert.assertNotNull(updateRow, "[AuditAssertion] Expected an UPDATE row in " + auditTableName + " for "
                + businessKey + " reflecting " + column + "=" + expectedOld + "→" + expectedNew
                + ", but no UPDATE audit row was found (check whether the audit trigger is enabled for "
                + tableName + " in this environment).");

        Object auditNewValue = DbValueNormalizer.getIgnoreCase(updateRow, column);
        Object liveOldValue = DbValueNormalizer.getIgnoreCase(beforeRow, column);

        Assert.assertTrue(DbValueNormalizer.equalsNormalized(expectedNew, auditNewValue),
                "[AuditAssertion] " + auditTableName + " UPDATE row for " + businessKey + " column '" + column
                        + "' — expected new value '" + expectedNew + "' but audit trail shows '"
                        + DbValueNormalizer.displayString(auditNewValue) + "'.");
        Assert.assertTrue(DbValueNormalizer.equalsNormalized(expectedOld, liveOldValue),
                "[AuditAssertion] Pre-condition mismatch — expected the live BEFORE value of '" + column
                        + "' to be '" + expectedOld + "' but it was '" + DbValueNormalizer.displayString(liveOldValue)
                        + "'; the UPDATE audit comparison above is against a stale expectation.");
    }

    /** Asserts {@code aud2_<table>} recorded an {@code INSERT} row for {@code afterRow}'s business key. */
    @AuditTrace("assertAuditInserted")
    public static void assertAuditInserted(DocumentContext context, AuditSnapshotDao auditDao,
            TableRule tableRule, Map<String, Object> afterRow) {
        String tableName = tableRule.getTableName();
        String auditTableName = "aud2_" + tableName;
        String businessKey = businessKeyOf(tableRule, afterRow);

        List<Map<String, Object>> auditRows = new AuditRepository(auditDao).fetchAuditRows(auditTableName, context);
        Map<String, Object> insertRow = mostRecentMatching(tableRule, auditRows, businessKey, "INSERT");

        Assert.assertNotNull(insertRow, "[AuditAssertion] Expected an INSERT row in " + auditTableName + " for "
                + businessKey + " but none was found — the audit trail did not record this insert (check whether "
                + "the audit trigger is enabled for " + tableName + " in this environment).");
    }

    /** Asserts NO new audit row exists for {@code recordId} beyond {@code knownTransactionIds} — the
     *  "no business change must not produce a business audit change" no-op-save case (doc §9/§14.2). */
    @AuditTrace("assertNoAudit")
    public static void assertNoAudit(DocumentContext context, AuditSnapshotDao auditDao, TableRule tableRule,
            String recordId, Set<String> knownTransactionIds) {
        String tableName = tableRule.getTableName();
        String auditTableName = "aud2_" + tableName;

        List<Map<String, Object>> auditRows = new AuditRepository(auditDao).fetchAuditRows(auditTableName, context);
        List<String> newTransactionIds = new ArrayList<>();
        for (Map<String, Object> row : auditRows) {
            if (!businessKeyOf(tableRule, row).equals(recordId)) continue;
            String txnId = String.valueOf(DbValueNormalizer.getIgnoreCase(row, "transaction_id"));
            if (!knownTransactionIds.contains(txnId)) newTransactionIds.add(txnId);
        }
        Assert.assertTrue(newTransactionIds.isEmpty(), "[AuditAssertion] Expected NO new audit rows in "
                + auditTableName + " for " + recordId + " but found new transaction_id(s): " + newTransactionIds
                + " — a no-op save must not produce a business audit change.");
    }

    private static Map<String, Object> mostRecentMatching(TableRule tableRule, List<Map<String, Object>> rows,
            String businessKey, String action) {
        Map<String, Object> best = null;
        for (Map<String, Object> row : rows) {
            if (!action.equalsIgnoreCase(String.valueOf(DbValueNormalizer.getIgnoreCase(row, "action")))) continue;
            if (!businessKeyOf(tableRule, row).equals(businessKey)) continue;
            if (best == null || isLater(row, best)) best = row;
        }
        return best;
    }

    private static boolean isLater(Map<String, Object> candidate, Map<String, Object> current) {
        Object candidateId = DbValueNormalizer.getIgnoreCase(candidate, "transaction_id");
        Object currentId = DbValueNormalizer.getIgnoreCase(current, "transaction_id");
        try {
            return Long.parseLong(String.valueOf(candidateId)) > Long.parseLong(String.valueOf(currentId));
        } catch (NumberFormatException e) {
            return String.valueOf(candidateId).compareTo(String.valueOf(currentId)) > 0;
        }
    }

    private static List<String> compareColumns(Map<String, Object> expectedRow, Map<String, Object> actualRow,
            Set<String> excludedColumns) {
        List<String> mismatches = new ArrayList<>();
        for (String column : expectedRow.keySet()) {
            if (isMetaColumn(column) || excludedColumns.contains(column.toUpperCase())) continue;
            Object expected = DbValueNormalizer.getIgnoreCase(expectedRow, column);
            Object actual = DbValueNormalizer.getIgnoreCase(actualRow, column);
            if (!DbValueNormalizer.equalsNormalized(expected, actual)) {
                mismatches.add(column + ": expected='" + DbValueNormalizer.displayString(expected)
                        + "' audit='" + DbValueNormalizer.displayString(actual) + "'");
            }
        }
        return mismatches;
    }

    private static boolean isMetaColumn(String column) {
        return column != null && (column.equalsIgnoreCase("action") || column.equalsIgnoreCase("action_subtype")
                || column.equalsIgnoreCase("transaction_id"));
    }

    private static String businessKeyOf(TableRule tableRule, Map<String, Object> row) {
        List<String> columns = tableRule.getBusinessKeyColumns();
        if (columns == null || columns.isEmpty()) return "(no key)";
        StringBuilder sb = new StringBuilder();
        for (String col : columns) {
            if (sb.length() > 0) sb.append(", ");
            Object raw = DbValueNormalizer.getIgnoreCase(row, col);
            sb.append(col).append('=').append(DbValueNormalizer.displayString(raw));
        }
        return sb.toString();
    }
}
