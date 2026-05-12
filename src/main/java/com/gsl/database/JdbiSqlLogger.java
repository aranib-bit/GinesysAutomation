package com.gsl.database;

import com.gsl.reports.ReportLogger;
import org.jdbi.v3.core.statement.SqlLogger;
import org.jdbi.v3.core.statement.StatementContext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JDBI {@link SqlLogger} that logs every executed statement as effective SQL —
 * i.e., the {@code @SqlQuery} template with {@code :paramName} placeholders
 * replaced by the actual bound values.
 *
 * <p>Registered once in {@link DatabaseManager}; fires automatically for every
 * DAO call.  No manual {@code DbQueryLogger.log()} calls needed in validators.
 */
public class JdbiSqlLogger implements SqlLogger {

    private static final Pattern NAMED_PARAM = Pattern.compile(":(\\w+)");

    @Override
    public void logBeforeExecution(StatementContext ctx) {
        try {
            String rawSql = ctx.getRawSql();
            if (rawSql == null || rawSql.isBlank()) return;

            var binding = ctx.getBinding();
            Matcher m = NAMED_PARAM.matcher(rawSql);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String name  = m.group(1);
                String value = binding.findForName(name,ctx)
                        .map(arg -> LogCapturePreparedStatement.capture(arg, ctx))
                        .orElse("?");
                m.appendReplacement(sb, Matcher.quoteReplacement(value));
            }
            m.appendTail(sb);
            ReportLogger.info("[DB Query] " + sb);
        } catch (Exception e) {
            ReportLogger.info("[DB Query] (could not resolve: " + e.getMessage() + ")");
        }
    }
}
