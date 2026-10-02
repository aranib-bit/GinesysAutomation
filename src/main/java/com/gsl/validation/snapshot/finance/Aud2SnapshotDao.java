package com.gsl.validation.snapshot.finance;

import com.gsl.database.DatabaseManager;
import com.gsl.validation.snapshot.AuditSnapshotDao;
import com.gsl.validation.snapshot.DocumentContext;
import org.jdbi.v3.core.Jdbi;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Fetches one {@code aud2_<table>} audit-shadow table's rows for a General Voucher — plan §2.
 *
 * <p>Table name is interpolated directly into the SQL (JDBI cannot parameter-bind identifiers) —
 * validated against the {@link AuditFk} allowlist (never raw external input) before interpolating,
 * as a defensive guard per the plan's explicit instruction.
 *
 * <p>FK column verified against the live Postgres schema (schema {@code main}): {@code
 * aud2_finvchmain}/{@code aud2_finvchdet} carry their own bigint {@code vchcode} column;
 * {@code aud2_finpost}/{@code aud2_fincosttag} carry a varchar {@code entcode} column (a text-cast
 * FK to {@code finvchmain.vchcode}, mirroring the non-audit {@code finpost}/{@code fincosttag}
 * DAOs). See {@link AuditFk} for the per-table column + bind-type mapping.
 * {@link com.gsl.validation.snapshot.SnapshotService} still wraps every call to this class in a
 * try/catch that degrades to 0 rows with a warning log on any failure — kept as defense-in-depth
 * against genuine DB errors, not because this mapping is still unconfirmed.
 */
public class Aud2SnapshotDao implements AuditSnapshotDao {

    /** Per-table FK column back to the voucher, plus how to resolve/bind its value. */
    private enum AuditFk {
        AUD2_FINVCHMAIN("aud2_finvchmain", "vchcode", DocumentContext::getVchcode),
        AUD2_FINVCHDET("aud2_finvchdet", "vchcode", DocumentContext::getVchcode),
        AUD2_FINPOST("aud2_finpost", "entcode", ctx -> String.valueOf(ctx.getVchcode())),
        AUD2_FINCOSTTAG("aud2_fincosttag", "entcode", ctx -> String.valueOf(ctx.getVchcode()));

        private final String tableName;
        private final String fkColumn;
        private final Function<DocumentContext, Object> fkValueResolver;

        AuditFk(String tableName, String fkColumn, Function<DocumentContext, Object> fkValueResolver) {
            this.tableName = tableName;
            this.fkColumn = fkColumn;
            this.fkValueResolver = fkValueResolver;
        }

        /** Looks up the {@link AuditFk} entry whose {@code tableName} equals {@code tableName}; {@code null} if none. */
        static AuditFk forTable(String tableName) {
            for (AuditFk fk : values()) {
                if (fk.tableName.equals(tableName)) return fk;
            }
            return null;
        }
    }

    /**
     * Fetches every row of {@code auditTableName} scoped to {@code context}'s voucher, via the
     * FK column {@link AuditFk} resolves for that table.
     *
     * @throws IllegalArgumentException if {@code auditTableName} isn't one of the registered
     *                                  {@link AuditFk} tables (defensive allowlist — see class Javadoc)
     */
    @Override
    public List<Map<String, Object>> fetch(String auditTableName, DocumentContext context) {
        AuditFk fk = AuditFk.forTable(auditTableName);
        if (fk == null) {
            throw new IllegalArgumentException("[Aud2SnapshotDao] Refusing to query unrecognized audit table '"
                    + auditTableName + "' — no FK-column mapping registered for it (known tables: "
                    + Arrays.toString(AuditFk.values()) + ").");
        }
        Jdbi jdbi = DatabaseManager.getJdbi();
        String sql = "SELECT * FROM " + fk.tableName + " WHERE " + fk.fkColumn + " = :fkValue";
        return jdbi.withHandle(handle ->
                handle.createQuery(sql)
                        .bind("fkValue", fk.fkValueResolver.apply(context))
                        .mapToMap()
                        .list());
    }
}
