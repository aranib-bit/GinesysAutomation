package com.gsl.database.dialect;

import com.gsl.config.ConfigFactory;

/**
 * Single resolution point for the active {@link DbDialect}.
 * Call {@code DbDialectFactory.current()} wherever SQL fragments must
 * vary between Postgres and Oracle.
 */
public final class DbDialectFactory {

    private DbDialectFactory() {}

    public static DbDialect current() {
        String type = ConfigFactory.getDataBaseConfig().databaseType();
        return "oracle".equalsIgnoreCase(type)
                ? new OracleDialect()
                : new PostgresDialect();   // default to Postgres
    }
}
