package com.gsl.validation.snapshot.audit;

import com.gsl.database.DatabaseManager;
import com.gsl.utils.Log;
import com.gsl.validation.snapshot.AuditSnapshotDao;
import com.gsl.validation.snapshot.DocumentContext;
import org.jdbi.v3.core.Jdbi;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Retrieval-only access to the audit trail — no assertions, no comparison logic (that's
 * {@link AuditComparator}'s job). Wraps the existing {@link AuditSnapshotDao} (per-document,
 * per-audit-table row fetch, already used by {@code SnapshotService}) plus new queries against
 * {@code aud2_transaction}/{@code aud2_transaction_table} for whole-transaction correlation
 * (doc §12/§25/§26).
 *
 * <p>Deliberately scoped by {@code header_key_value} (the document's own key, e.g. {@code vchcode}
 * as text) plus a transaction-time window — never a bare {@code SELECT * FROM aud2_transaction} —
 * so retrieval stays correct and cheap under parallel test execution (doc §26: two threads editing
 * different documents under the same user must never cross-contaminate each other's audit read).
 */
public class AuditRepository {

    private static final String TRANSACTION_SQL =
            "SELECT transaction_id, transaction_date, login_user, module_type, module_shortcode, program " +
            "FROM aud2_transaction " +
            "WHERE header_key_value = :headerKeyValue " +
            "AND transaction_date BETWEEN :windowStart AND :windowEnd " +
            "ORDER BY transaction_date";

    private static final String TRANSACTION_TABLES_SQL =
            "SELECT table_name FROM aud2_transaction_table WHERE transaction_id = :transactionId";

    private final AuditSnapshotDao auditSnapshotDao;

    public AuditRepository(AuditSnapshotDao auditSnapshotDao) {
        this.auditSnapshotDao = auditSnapshotDao;
    }

    /** Delegates to the existing {@link AuditSnapshotDao#fetch} — one {@code aud2_<table>}'s rows
     *  for the document in {@code context}. Exposed here so {@link AuditComparator} has a single
     *  retrieval-layer entry point rather than reaching into {@code SnapshotService} internals. */
    public List<Map<String, Object>> fetchAuditRows(String auditTableName, DocumentContext context) {
        if (auditSnapshotDao == null) return List.of();
        try {
            return auditSnapshotDao.fetch(auditTableName, context);
        } catch (RuntimeException e) {
            Log.warn("[AuditRepository] fetchAuditRows failed for '{}': {}", auditTableName, e.getMessage());
            return List.of();
        }
    }

    /**
     * Retrieves every {@code aud2_transaction} row for this document within
     * {@code [windowStart, windowEnd]} (the test's own before/after wall-clock capture times),
     * with the table fan-out from {@code aud2_transaction_table} attached, as {@link AuditEvent}s.
     * {@code changes} is left empty here — populating field-level {@link AuditChange}s per
     * transaction is {@link AuditChangeReconstructor}'s job against the actual before/after
     * snapshot pair, not this class's; this method gives the transaction-level "who/when/module/
     * which tables" metadata (doc §12/§21's event envelope).
     *
     * @param context     resolved document context (supplies {@code vchcode}/document type)
     * @param windowStart start of the transaction-time window (inclusive)
     * @param windowEnd   end of the transaction-time window (inclusive)
     */
    public List<AuditEvent> getAuditEvents(DocumentContext context, LocalDateTime windowStart, LocalDateTime windowEnd) {
        String headerKeyValue = String.valueOf(context.getVchcode());
        try {
            Jdbi jdbi = DatabaseManager.getJdbi();
            List<Map<String, Object>> rows = jdbi.withHandle(handle ->
                    handle.createQuery(TRANSACTION_SQL)
                            .bind("headerKeyValue", headerKeyValue)
                            .bind("windowStart", windowStart)
                            .bind("windowEnd", windowEnd)
                            .mapToMap()
                            .list());

            List<AuditEvent> events = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                String transactionId = String.valueOf(row.get("transaction_id"));
                events.add(new AuditEvent(
                        transactionId,
                        String.valueOf(row.get("login_user")),
                        String.valueOf(row.get("module_shortcode")),
                        context.getDocumentType() != null ? context.getDocumentType().name() : null,
                        context.getVoucherNo(),
                        toLocalDateTime(row.get("transaction_date")),
                        List.of()));
            }
            return events;
        } catch (RuntimeException e) {
            Log.warn("[AuditRepository] getAuditEvents failed for header_key_value='{}': {}",
                    headerKeyValue, e.getMessage());
            return List.of();
        }
    }

    /** Which base tables {@code transactionId} touched, per {@code aud2_transaction_table}. */
    public List<String> getTablesForTransaction(String transactionId) {
        try {
            Jdbi jdbi = DatabaseManager.getJdbi();
            return jdbi.withHandle(handle ->
                    handle.createQuery(TRANSACTION_TABLES_SQL)
                            .bind("transactionId", transactionId)
                            .mapTo(String.class)
                            .list());
        } catch (RuntimeException e) {
            Log.warn("[AuditRepository] getTablesForTransaction failed for transaction_id='{}': {}",
                    transactionId, e.getMessage());
            return List.of();
        }
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        return null;
    }
}
