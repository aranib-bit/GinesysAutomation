package com.gsl.database.dialect;

import com.gsl.config.ConfigFactory;

/**
 * Single resolution point for the active {@link DbDialect}.
 * Call {@code DbDialectFactory.current()} wherever SQL fragments must
 * vary between Postgres and Oracle.
 */
public final class DbDialectFactory {

    private DbDialectFactory() {}

    /**
     * Resolves the dialect from {@link ConfigFactory}'s configured database type — {@code
     * "oracle"} (case-insensitive) yields {@link OracleDialect}; anything else, including unset,
     * defaults to {@link PostgresDialect}.
     */
    public static DbDialect current() {
        String type = ConfigFactory.getDataBaseConfig().databaseType();
        return "oracle".equalsIgnoreCase(type)
                ? new OracleDialect()
                : new PostgresDialect();   // default to Postgres
    }
}
