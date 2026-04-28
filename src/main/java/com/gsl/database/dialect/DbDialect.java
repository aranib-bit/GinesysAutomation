package com.gsl.database.dialect;

/**
 * Abstracts SQL syntax differences between Postgres and Oracle.
 * Add methods here when a new dialect-sensitive construct is needed;
 * implement in {@link PostgresDialect} and {@link OracleDialect}.
 * Callers resolve the correct instance via {@link DbDialectFactory#current()}.
 */
public interface DbDialect {

    /** "LIMIT :n" (Postgres) vs "FETCH FIRST :n ROWS ONLY" (Oracle) */
    String limitBind(String paramName);

    /** "LIMIT 1" (Postgres) vs "FETCH FIRST 1 ROWS ONLY" (Oracle) */
    String limit1();

    /** column::date (Postgres) vs CAST(column AS DATE) (Oracle) */
    String castToDate(String columnExpr);

    /** CURRENT_DATE — same for both for now; override in OracleDialect if it diverges */
    default String currentDate() {
        return "CURRENT_DATE";
    }
}
