package com.gsl.validation.snapshot.audit;

import com.gsl.annotations.AuditTrace;
import com.gsl.database.DatabaseManager;
import com.gsl.utils.Log;
import org.jdbi.v3.core.Jdbi;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves {@code aud2_active_tab_col.exclude_update_of_column} for one base table — the columns
 * whose <em>sole</em> change does not fire that table's update-audit trigger (housekeeping/system
 * columns, e.g. {@code TIME}, {@code DATAVERSION}, {@code LAST_ACCESS_TIME} for {@code FINVCHMAIN}).
 *
 * <p><b>This is an EXCLUDE list, not an include list</b> — confirmed live against {@code idealdb}:
 * {@code FINVCHMAIN} carries 14 excluded columns, {@code FINVCHDET} carries 7 (including, notably,
 * {@code DATAVERSION} itself — see {@link AuditChangeReconstructor}'s Javadoc for why that matters).
 * Never hardcode this list; always resolve it live, per environment, mirroring
 * {@code Aud2AuditStateResolver}'s "query live, degrade gracefully" style exactly.
 *
 * <p>Cached per table name for the life of the JVM — this registry doesn't change during a test
 * run, and repeatedly querying it for every column comparison would be wasteful.
 */
public final class ExcludedAuditColumnsResolver {

    private static final String RESOLVER_SQL =
            "SELECT exclude_update_of_column FROM aud2_active_tab_col WHERE UPPER(table_name) = :tableName";

    private static final Map<String, Set<String>> CACHE = new ConcurrentHashMap<>();

    /**
     * @param tableName base table name, e.g. {@code "finvchdet"} — case-insensitive
     * @return the set of excluded column names, upper-cased, or an empty set if the query fails
     *         against this environment's schema (logged as a warning, never thrown) or the table
     *         has no exclude-list registration at all
     */
    public Set<String> resolveExcludedColumns(String tableName) {
        if (tableName == null || tableName.isBlank()) return Set.of();
        return CACHE.computeIfAbsent(tableName.toUpperCase(), this::queryLive);
    }

    @AuditTrace("resolveExcludedColumns")
    private Set<String> queryLive(String upperTableName) {
        try {
            Jdbi jdbi = DatabaseManager.getJdbi();
            List<String> found = jdbi.withHandle(handle ->
                    handle.createQuery(RESOLVER_SQL)
                            .bind("tableName", upperTableName)
                            .mapTo(String.class)
                            .list());
            return found.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(String::toUpperCase)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        } catch (RuntimeException e) {
            Log.warn("[ExcludedAuditColumnsResolver] aud2_active_tab_col query failed for table '{}' — "
                    + "degrading to 'no columns excluded' rather than failing the test. Cause: {}",
                    upperTableName, e.getMessage());
            return Set.of();
        }
    }

    /** {@code true} if {@code column} is in {@code table}'s exclude-list (case-insensitive). */
    public boolean isExcluded(String tableName, String column) {
        if (column == null) return false;
        return resolveExcludedColumns(tableName).contains(column.toUpperCase());
    }
}
