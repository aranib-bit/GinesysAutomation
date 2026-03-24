package com.gsl.database;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

public class JdbiFactory {
    private static final Jdbi jdbi;

    static {
        jdbi = Jdbi.create("jdbc:postgresql://localhost:5432/yourdb", "user", "password");
        jdbi.installPlugin(new SqlObjectPlugin());
    }

    public static <T> T onDemand(Class<T> daoClass) {
        return jdbi.onDemand(daoClass);
    }
}
