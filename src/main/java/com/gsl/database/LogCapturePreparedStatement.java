package com.gsl.database;

import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.statement.StatementContext;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;

/**
 * Extracts the string representation of a bound JDBI {@link Argument} without
 * executing a real SQL statement.  Used by {@link JdbiSqlLogger} to reconstruct
 * effective SQL for logging.
 */
public final class LogCapturePreparedStatement {

    private LogCapturePreparedStatement() {}

    /**
     * Applies {@code arg} to a no-op {@link PreparedStatement} proxy and returns
     * the captured value as a SQL-ready string.
     */
    public static String capture(Argument arg, StatementContext ctx) {
        try {
            Object[] box      = {null};
            boolean[] wasNull = {false};

            // Use the application classloader — PreparedStatement.class.getClassLoader() can
            // return null (bootstrap) on Java 17+ module-aware JVMs, causing Proxy.newProxyInstance
            // to throw NullPointerException or IllegalAccessError.
            ClassLoader cl = LogCapturePreparedStatement.class.getClassLoader();
            PreparedStatement stub = (PreparedStatement) Proxy.newProxyInstance(
                    cl,
                    new Class[]{PreparedStatement.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("setNull".equals(name)) {
                            wasNull[0] = true;
                            return null;
                        }
                        if (name.startsWith("set") && args != null && args.length >= 2) {
                            box[0] = args[1];   // args[0] = position index, args[1] = value
                            return null;
                        }
                        Class<?> ret = method.getReturnType();
                        if (ret == boolean.class) return false;
                        if (ret == int.class || ret == short.class || ret == byte.class) return 0;
                        if (ret == long.class) return 0L;
                        if (ret == float.class) return 0.0f;
                        if (ret == double.class) return 0.0d;
                        return null;
                    });

            try {
                arg.apply(1, stub, ctx);
            } catch (Exception ignored) {}

            if (wasNull[0] || box[0] == null) return "NULL";
            Object value = box[0];
            // SQL-literal quoting: string/date/time/UUID values need single-quote wrapping (with
            // embedded-quote escaping) to be valid, re-runnable SQL — numeric/boolean values are
            // correct unquoted as-is. Previously every type went through a bare toString(), so a
            // reconstructed query like "WHERE slname = AUTO_SL_..." couldn't be copy-pasted back
            // into a SQL client without a manual fix-up.
            if (value instanceof CharSequence || value instanceof java.time.temporal.Temporal
                    || value instanceof java.util.Date || value instanceof java.util.UUID) {
                return "'" + value.toString().replace("'", "''") + "'";
            }
            return value.toString();
        } catch (Throwable t) {
            return "?";
        }
    }
}
