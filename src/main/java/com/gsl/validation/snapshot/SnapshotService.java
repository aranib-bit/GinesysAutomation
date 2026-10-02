package com.gsl.validation.snapshot;

import com.gsl.database.DatabaseManager;
import com.gsl.utils.Log;
import org.jdbi.v3.core.Jdbi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures a {@link VoucherSnapshot} for one {@link DocumentContext} — plan §1.
 *
 * <p>For each {@link TableRule} active for the context (per {@link VoucherPersistenceMap#activeTables}),
 * runs a generic {@code SELECT * FROM <table> WHERE <fkColumn> = ?} via JDBI's
 * {@code Handle.createQuery(...).mapToMap().list()} — no per-table model classes needed. {@code aud2_*}
 * audit-shadow tables are instead routed to the supplied {@link AuditSnapshotDao} (nullable — when
 * absent, or when a table rule marked {@link TableRule#isAuditTable()} has no dao wired, that table's
 * capture is skipped with a warning rather than failing the whole snapshot).
 */
public class SnapshotService {

    private final Jdbi jdbi;
    private final AuditSnapshotDao auditSnapshotDao;

    /** Uses the shared {@link DatabaseManager#getJdbi()} handle and captures no audit tables
     *  (any {@code aud2_*} table active for a document is skipped — see {@link #captureOneTable}). */
    public SnapshotService() {
        this(DatabaseManager.getJdbi(), null);
    }

    /** Uses the shared {@link DatabaseManager#getJdbi()} handle with the given audit dao. */
    public SnapshotService(AuditSnapshotDao auditSnapshotDao) {
        this(DatabaseManager.getJdbi(), auditSnapshotDao);
    }

    /** Full constructor — explicit {@link Jdbi} handle (e.g. for tests) plus an optional
     *  {@link AuditSnapshotDao} ({@code null} to skip every audit-table capture). */
    public SnapshotService(Jdbi jdbi, AuditSnapshotDao auditSnapshotDao) {
        this.jdbi = jdbi;
        this.auditSnapshotDao = auditSnapshotDao;
    }

    /** Captures a full {@link VoucherSnapshot} — one {@link TableSnapshot} per table active for
     *  {@code context} (per {@link VoucherPersistenceMap#activeTables}), in declared order. */
    public VoucherSnapshot capture(DocumentContext context) {
        Map<String, TableSnapshot> tables = new LinkedHashMap<>();
        for (TableRule rule : context.getPersistenceMap().activeTables(context)) {
            List<Map<String, Object>> rows = captureOneTable(rule, context);
            tables.put(rule.getTableName(), new TableSnapshot(rule.getTableName(), rows));
        }
        return new VoucherSnapshot(tables);
    }

    /** Captures one table's rows: routes {@code aud2_*} tables to {@link #auditSnapshotDao}
     *  (degrading to an empty list, with a warning, if it's absent or throws), and every other
     *  table to the single-column ({@link #captureByCompositeKey} otherwise) generic FK query. */
    private List<Map<String, Object>> captureOneTable(TableRule rule, DocumentContext context) {
        if (rule.isAuditTable()) {
            if (auditSnapshotDao == null) {
                Log.warn("[SnapshotService] Table '{}' is audit-active but no AuditSnapshotDao was "
                        + "supplied — skipping capture (treated as 0 rows).", rule.getTableName());
                return List.of();
            }
            try {
                return auditSnapshotDao.fetch(rule.getTableName(), context);
            } catch (RuntimeException e) {
                Log.warn("[SnapshotService] Audit capture failed for '{}' — degrading to 0 rows: {}",
                        rule.getTableName(), e.getMessage());
                return List.of();
            }
        }

        List<String> fkColumns = rule.getFkColumns();
        if (fkColumns != null && !fkColumns.isEmpty()) {
            return captureByCompositeKey(rule, context, fkColumns);
        }

        String fkColumn = rule.getFkColumn();
        Object fkValue = rule.getFkValueResolver() != null ? rule.getFkValueResolver().apply(context) : null;
        if (fkColumn == null || fkValue == null) {
            Log.warn("[SnapshotService] Table '{}' has no usable fkColumn/fkValueResolver — skipping capture.",
                    rule.getTableName());
            return List.of();
        }

        String sql = "SELECT * FROM " + rule.getTableName() + " WHERE " + fkColumn + " = :fkValue";
        return jdbi.withHandle(handle ->
                handle.createQuery(sql)
                        .bind("fkValue", fkValue)
                        .mapToMap()
                        .list());
    }

    /** Composite-key counterpart of the single-column path above — see {@link TableRule#getFkColumns()}.
     *  Same "skip capture with a warning, don't fail the run" fallback when the resolved values
     *  are missing/incomplete. */
    private List<Map<String, Object>> captureByCompositeKey(TableRule rule, DocumentContext context,
            List<String> fkColumns) {
        List<Object> fkValues = rule.getFkValuesResolver() != null
                ? rule.getFkValuesResolver().apply(context) : null;
        if (fkValues == null || fkValues.size() != fkColumns.size() || fkValues.stream().anyMatch(java.util.Objects::isNull)) {
            Log.warn("[SnapshotService] Table '{}' has no usable fkColumns/fkValuesResolver — skipping capture.",
                    rule.getTableName());
            return List.of();
        }

        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(rule.getTableName()).append(" WHERE ");
        for (int i = 0; i < fkColumns.size(); i++) {
            if (i > 0) sql.append(" AND ");
            sql.append(fkColumns.get(i)).append(" = :fkValue").append(i);
        }

        return jdbi.withHandle(handle -> {
            var query = handle.createQuery(sql.toString());
            for (int i = 0; i < fkValues.size(); i++) {
                query.bind("fkValue" + i, fkValues.get(i));
            }
            return query.mapToMap().list();
        });
    }
}
