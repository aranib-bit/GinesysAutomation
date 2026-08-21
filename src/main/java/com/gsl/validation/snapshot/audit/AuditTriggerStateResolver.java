package com.gsl.validation.snapshot.audit;

import com.gsl.annotations.AuditTrace;
import com.gsl.database.DatabaseManager;
import com.gsl.utils.Log;
import org.jdbi.v3.core.Jdbi;

import java.util.List;
import java.util.Optional;

/**
 * Checks whether a table's {@code t_<table>_aud2} audit trigger is physically enabled on this
 * environment — the exact {@code pg_trigger}/{@code pg_class}/{@code pg_namespace} query supplied
 * for this purpose, made reusable per table.
 *
 * <p>This exists because {@code aud2_module.ismandatory} / {@code aud2_active_table} registration
 * does NOT guarantee the underlying trigger is actually enabled on a given staging instance (the
 * user's explicit caution) — a module can be registered as audit-active while its trigger has been
 * disabled for maintenance, a migration, or simply never re-enabled after one. Checking both is
 * the only way to tell "audit not configured for this table" apart from "audit configured but
 * silently not firing right now."
 */
public class AuditTriggerStateResolver {

    /** Enabled/disabled state of one table's audit trigger. {@link #UNKNOWN} covers both a query
     *  failure and a trigger that simply doesn't exist by the expected {@code t_<table>_aud2} name —
     *  both cases mean "don't assume either way," never "assume enabled." */
    public enum TriggerStatus { ENABLED, DISABLED, UNKNOWN }

    private static final String TRIGGER_SQL =
            "SELECT t.tgenabled " +
            "FROM pg_trigger t " +
            "JOIN pg_class c ON c.oid = t.tgrelid " +
            "JOIN pg_namespace n ON n.oid = c.relnamespace " +
            "WHERE c.relname = :tableName AND t.tgname = :triggerName AND NOT t.tgisinternal";

    /**
     * @param tableName base table name, e.g. {@code "finvchmain"} — lower-case, matching
     *                  {@code pg_class.relname}
     * @return this table's {@code t_<tableName>_aud2} trigger status; {@link TriggerStatus#UNKNOWN}
     *         if the trigger can't be found or the query fails (never throws)
     */
    @AuditTrace("resolveTriggerStatus")
    public TriggerStatus resolveTriggerStatus(String tableName) {
        if (tableName == null || tableName.isBlank()) return TriggerStatus.UNKNOWN;
        String lower = tableName.toLowerCase();
        String triggerName = "t_" + lower + "_aud2";
        try {
            Jdbi jdbi = DatabaseManager.getJdbi();
            List<String> rows = jdbi.withHandle(handle ->
                    handle.createQuery(TRIGGER_SQL)
                            .bind("tableName", lower)
                            .bind("triggerName", triggerName)
                            .mapTo(String.class)
                            .list());
            Optional<String> tgenabled = rows.stream().findFirst();
            if (tgenabled.isEmpty()) {
                Log.warn("[AuditTriggerStateResolver] Trigger '{}' not found on table '{}' — "
                        + "treating as UNKNOWN (not ENABLED, not DISABLED).", triggerName, lower);
                return TriggerStatus.UNKNOWN;
            }
            return switch (tgenabled.get()) {
                case "O", "A", "R" -> TriggerStatus.ENABLED; // origin / always / replica-enabled
                case "D" -> TriggerStatus.DISABLED;
                default -> TriggerStatus.UNKNOWN;
            };
        } catch (RuntimeException e) {
            Log.warn("[AuditTriggerStateResolver] pg_trigger query failed for table '{}' — degrading to "
                    + "UNKNOWN rather than failing the test. Cause: {}", lower, e.getMessage());
            return TriggerStatus.UNKNOWN;
        }
    }
}
