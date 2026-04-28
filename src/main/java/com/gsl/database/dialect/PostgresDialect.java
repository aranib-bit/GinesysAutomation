package com.gsl.database.dialect;

public class PostgresDialect implements DbDialect {

    @Override
    public String limitBind(String paramName) {
        return "LIMIT :" + paramName;
    }

    @Override
    public String limit1() {
        return "LIMIT 1";
    }

    @Override
    public String castToDate(String columnExpr) {
        return columnExpr + "::date";
    }
}
