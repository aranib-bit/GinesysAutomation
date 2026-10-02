package com.gsl.database.dialect;

/**
 * Oracle SQL dialect: {@code FETCH FIRST ... ROWS ONLY} pagination and {@code CAST(... AS DATE)}
 * casting, in place of Postgres's {@code LIMIT}/{@code ::date} syntax. Selected by
 * {@link DbDialectFactory#current()} when the configured database type is {@code "oracle"}.
 */
public class OracleDialect implements DbDialect {

    /** {@inheritDoc} Oracle form: {@code "FETCH FIRST :paramName ROWS ONLY"}. */
    @Override
    public String limitBind(String paramName) {
        return "FETCH FIRST :" + paramName + " ROWS ONLY";
    }

    /** {@inheritDoc} Oracle form: {@code "FETCH FIRST 1 ROWS ONLY"}. */
    @Override
    public String limit1() {
        return "FETCH FIRST 1 ROWS ONLY";
    }

    /** {@inheritDoc} Oracle form: {@code "CAST(columnExpr AS DATE)"}. */
    @Override
    public String castToDate(String columnExpr) {
        return "CAST(" + columnExpr + " AS DATE)";
    }
}
