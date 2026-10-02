package com.gsl.database.dialect;

/**
 * Postgres SQL dialect: {@code LIMIT}/{@code ::date} syntax, in place of Oracle's
 * {@code FETCH FIRST ... ROWS ONLY}/{@code CAST(... AS DATE)}. Selected by
 * {@link DbDialectFactory#current()} whenever the configured database type isn't {@code "oracle"}
 * — the framework's default.
 */
public class PostgresDialect implements DbDialect {

    /** {@inheritDoc} Postgres form: {@code "LIMIT :paramName"}. */
    @Override
    public String limitBind(String paramName) {
        return "LIMIT :" + paramName;
    }

    /** {@inheritDoc} Postgres form: {@code "LIMIT 1"}. */
    @Override
    public String limit1() {
        return "LIMIT 1";
    }

    /** {@inheritDoc} Postgres form: {@code "columnExpr::date"}. */
    @Override
    public String castToDate(String columnExpr) {
        return columnExpr + "::date";
    }
}
