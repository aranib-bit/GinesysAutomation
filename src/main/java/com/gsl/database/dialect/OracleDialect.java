package com.gsl.database.dialect;

public class OracleDialect implements DbDialect {

    @Override
    public String limitBind(String paramName) {
        return "FETCH FIRST :" + paramName + " ROWS ONLY";
    }

    @Override
    public String limit1() {
        return "FETCH FIRST 1 ROWS ONLY";
    }

    @Override
    public String castToDate(String columnExpr) {
        return "CAST(" + columnExpr + " AS DATE)";
    }
}
