package com.gsl.validation.snapshot.finance;

import com.gsl.database.DatabaseManager;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.Log;
import org.jdbi.v3.core.Jdbi;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves which of a voucher domain's base tables (e.g. {@code finvchmain}/{@code finvchdet}/
 * {@code finpost}/{@code fincosttag} for GV) are currently audit-active, via {@code
 * aud2_active_table} — plan §2, Context section ("Schema realities").
 *
 * <p><b>Confirmed live against {@code idealdb}</b> (Postgres, 172.22.4.4) — the originally
 * drafted join through {@code aud2_active_module}/{@code aud2_module} was entirely wrong.
 * {@code aud2_active_table} is a flat two-column table ({@code module_shortcode},
 * {@code table_name}); a table is audit-active simply by having a row here, no join needed.
 * {@code table_name} values are stored UPPERCASE — the original lowercase {@code IN (...)}
 * comparison silently matched nothing (a real Postgres case-sensitivity bug, not a schema
 * mismatch — no exception was ever thrown, which is exactly why it went unnoticed until a
 * live query proved the table really is active). Fixed here with {@code UPPER(table_name)}
 * on both sides.
 *
 * <p><b>Now fully common, table-name-parameterized</b> — not hardcoded to GV's 4 tables, so
 * every voucher domain's {@code VoucherPersistenceMapFactory} gets correct audit-active
 * detection for whichever tables its own persistence map actually produces, with zero new
 * code (plan §2's generalization).
 */
public class Aud2AuditStateResolver {

    /**
     * Resolver SQL — see class Javadoc. {@code aud2_active_tab_col} is deliberately not
     * joined here; it's reserved for a Phase 2 enhancement (asserting the audit row's
     * changed-column list), not V1's row-level "did an audit entry get created" check.
     */
    private static final String RESOLVER_SQL =
            "SELECT table_name FROM aud2_active_table WHERE UPPER(table_name) IN (<tables>)";

    /**
     * @param tableNames the base table names to check (e.g. {@code finvchmain}/{@code finvchdet}/
     *                   {@code finpost}/{@code fincosttag}) — case-insensitive
     * @return the subset of {@code tableNames} currently audit-active (lower-cased, matching the
     *         persistence map's own table-name casing), or an empty set if the query itself fails
     *         against this environment's schema (logged as a warning, never thrown, so a schema
     *         surprise degrades the snapshot rather than crashing the whole Edit test)
     */
    public Set<String> resolveAuditActiveTables(Collection<String> tableNames) {
        try {
            List<String> upperTables = tableNames.stream().map(String::toUpperCase).toList();
            Jdbi jdbi = DatabaseManager.getJdbi();
            List<String> found = jdbi.withHandle(handle ->
                    handle.createQuery(RESOLVER_SQL)
                            .bindList("tables", upperTables)
                            .mapTo(String.class)
                            .list());
            Set<String> result = new HashSet<>();
            for (String tableName : found) {
                result.add(tableName.toLowerCase());
            }
            // Item G (Round 9): this query runs well before "Actual Results"' own
            // JdbiSqlLogger.startCapturing()/stopCapturing() window opens (Expected Results runs
            // at Test Data Resolution time, before login) — so without an explicit log line here,
            // this query's execution is invisible everywhere in the report, indistinguishable from
            // "never ran" even though it genuinely did.
            ReportLogger.trace("[Aud2AuditStateResolver] aud2_active_table query for " + tableNames
                    + " -> active: " + result);
            return result;
        } catch (RuntimeException e) {
            Log.warn("[Aud2AuditStateResolver] aud2_active_table query failed against this environment's schema "
                    + "— degrading to 'no tables audit-active' rather than failing the test. Cause: {}",
                    e.getMessage());
            return Set.of();
        }
    }
}
