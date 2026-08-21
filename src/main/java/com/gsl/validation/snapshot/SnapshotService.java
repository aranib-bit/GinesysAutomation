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

    public SnapshotService() {
        this(DatabaseManager.getJdbi(), null);
    }

    public SnapshotService(AuditSnapshotDao auditSnapshotDao) {
        this(DatabaseManager.getJdbi(), auditSnapshotDao);
    }

    public SnapshotService(Jdbi jdbi, AuditSnapshotDao auditSnapshotDao) {
        this.jdbi = jdbi;
        this.auditSnapshotDao = auditSnapshotDao;
    }

    public VoucherSnapshot capture(DocumentContext context) {
        Map<String, TableSnapshot> tables = new LinkedHashMap<>();
        for (TableRule rule : context.getPersistenceMap().activeTables(context)) {
            List<Map<String, Object>> rows = captureOneTable(rule, context);
            tables.put(rule.getTableName(), new TableSnapshot(rule.getTableName(), rows));
        }
        return new VoucherSnapshot(tables);
    }

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
}
