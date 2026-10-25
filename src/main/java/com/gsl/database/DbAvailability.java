package com.gsl.database;

import com.gsl.config.ConfigFactory;

import java.util.Locale;

/**
 * The single gate every database-touching class consults. Driven by the explicit per-environment
 * setting {@code db.available=yes|no} (default {@code yes}, which keeps today's behaviour) — it is
 * deliberately <em>not</em> probed at runtime: a {@code yes} environment whose database is down
 * fails fast, and a {@code no} environment never makes a database call at all.
 *
 * <p>When {@code db.available=no}, {@code validation.nodb.mode} decides what replaces the DB-derived
 * validation: {@link NoDbValidation#API} (default — API/audit equivalents, nothing skipped) or
 * {@link NoDbValidation#SKIP} (DB-derived checks reported SKIPPED).
 *
 * <p>{@link DatabaseManager} enforces the gate, so any call site that was missed fails loudly with
 * {@link DbDisabledException} instead of silently trying to connect.
 */
public final class DbAvailability {

    /** What happens to DB-derived validation when {@code db.available=no}. */
    public enum NoDbValidation { API, SKIP }

    private DbAvailability() {}

    /** True when the database may be used in this run ({@code db.available=yes}). */
    public static boolean isAvailable() {
        return parseFlag(ConfigFactory.getConfig().dbAvailable());
    }

    /** The configured replacement for DB validation when the DB is not available. */
    public static NoDbValidation noDbValidation() {
        return parseNoDbMode(ConfigFactory.getConfig().validationNoDbMode());
    }

    /** Throws {@link DbDisabledException} naming {@code what} when {@code db.available=no}. */
    public static void requireAvailable(String what) {
        if (!isAvailable()) {
            throw new DbDisabledException("DB disabled by config (db.available=no): " + what
                    + " tried to use the database. Serve it through the API or gate it with DbAvailability.isAvailable().");
        }
    }

    /** Accepts yes/no, true/false, y/n, 1/0 (case-insensitive); anything else is a config error. */
    public static boolean parseFlag(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "yes", "true", "y", "1" -> true;
            case "no", "false", "n", "0" -> false;
            default -> throw new IllegalArgumentException(
                    "db.available='" + raw + "' is invalid — expected yes or no");
        };
    }

    /** Parses {@code validation.nodb.mode}; blank means the default {@link NoDbValidation#API}. */
    public static NoDbValidation parseNoDbMode(String raw) {
        String v = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (v.isEmpty()) return NoDbValidation.API;
        try {
            return NoDbValidation.valueOf(v);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "validation.nodb.mode='" + raw + "' is invalid — expected API or SKIP");
        }
    }

    /** A database call was attempted while {@code db.available=no}. */
    public static final class DbDisabledException extends IllegalStateException {
        public DbDisabledException(String message) {
            super(message);
        }
    }
}
