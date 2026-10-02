package com.gsl.database;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

/**
 * Minimal {@link Jdbi} provider that opens an on-demand DAO proxy directly from a
 * hardcoded local JDBC URL ({@code jdbc:postgresql://localhost:5432/yourdb}, placeholder
 * {@code user}/{@code password} credentials), independent of
 * {@link com.gsl.config.ConfigFactory#getDataBaseConfig() the framework's configured DB connection}.
 *
 * <p>TODO-confirm: a repo-wide search found no callers of this class, and the hardcoded
 * placeholder credentials suggest it was a scratch/sample setup rather than a
 * production path — {@link DatabaseManager} is the provider actually used by DAOs/queries
 * across {@code com.gsl.database}. Left as-is; not touched further since
 * removing/consolidating it is outside the scope of this pass.
 */
public class JdbiFactory {
    private static final Jdbi jdbi;

    static {
        jdbi = Jdbi.create("jdbc:postgresql://localhost:5432/yourdb", "user", "password");
        jdbi.installPlugin(new SqlObjectPlugin());
    }

    /** Returns an on-demand JDBI SQL Object proxy for {@code daoClass}, backed by this class's own {@link Jdbi} instance. */
    public static <T> T onDemand(Class<T> daoClass) {
        return jdbi.onDemand(daoClass);
    }
}
