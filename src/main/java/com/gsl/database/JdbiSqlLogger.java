package com.gsl.database;

import com.gsl.reports.ReportLogger;
import org.jdbi.v3.core.statement.SqlLogger;
import org.jdbi.v3.core.statement.StatementContext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JDBI {@link SqlLogger} that logs every executed statement as effective SQL —
 * i.e., the {@code @SqlQuery} template with {@code :paramName} placeholders
 * replaced by the actual bound values, including {@code bindList} IN-list expansion.
 *
 * <p>Registered once in {@link DatabaseManager}; fires automatically for every
 * DAO call. No manual {@code DbQueryLogger.log()} calls needed in validators.
 *
 * <p>During the data-loading phase ({@link DbQueryBuffer#isActive()}), resolved
 * query lines are buffered via {@link DbQueryBuffer#add} instead of being written
 * directly to the Extent Report — they are flushed into the
 * "🗄 Database Data Gathering" child node at the end of
 * {@code DbApVoucherDataSource.loadFreshForScenario()}.
 *
 * <p>Outside the data-loading phase (e.g., in validators) lines go directly to
 * {@link ReportLogger} as before.
 */
public class JdbiSqlLogger implements SqlLogger {

    private static final Pattern NAMED_PARAM = Pattern.compile(":(\\w+)");

    /** Stores nanoTime at statement start so logAfterExecution can compute elapsed ms. */
    private static final ThreadLocal<Long> START_NS = new ThreadLocal<>();

    // ── Before execution: resolve SQL and start timing ────────────────────────

    @Override
    public void logBeforeExecution(StatementContext ctx) {
        START_NS.set(System.nanoTime());
        try {
            String rawSql = ctx.getRawSql();
            if (rawSql == null || rawSql.isBlank()) return;

            // Step 1: substitute named :paramName placeholders
            var binding = ctx.getBinding();
            Matcher m = NAMED_PARAM.matcher(rawSql);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String name  = m.group(1);
                @SuppressWarnings("deprecation")
                String value = binding.findForName(name, ctx)
                        .map(arg -> LogCapturePreparedStatement.capture(arg, ctx))
                        .orElse("?");
                m.appendReplacement(sb, Matcher.quoteReplacement(value));
            }
            m.appendTail(sb);
            String partialSql = sb.toString();

            // Step 2: if '?' remains (from bindList expansion), use getRenderedSql()
            // and substitute positional params — fixes IN () for bindList queries
            if (partialSql.contains("?")) {
                String rendered = ctx.getRenderedSql();
                if (rendered != null && !rendered.isBlank()) {
                    partialSql = substitutePositional(rendered, ctx);
                }
            }

            // Store resolved SQL for logAfterExecution to append timing
            ctx.define("__resolvedSql__", partialSql);

        } catch (Exception e) {
            ctx.define("__resolvedSql__", "(could not resolve: " + e.getMessage() + ")");
        }
    }

    // ── After execution: emit with timing ─────────────────────────────────────

    @Override
    public void logAfterExecution(StatementContext ctx) {
        try {
            long elapsedMs = START_NS.get() != null
                    ? (System.nanoTime() - START_NS.get()) / 1_000_000L
                    : -1;
            START_NS.remove();

            // Prefer the JDBC driver's toString() — PostgreSQL's PgPreparedStatement
            // returns fully-rendered SQL with all values substituted (copy-paste ready).
            // Falls back to the JDBI-resolved SQL stored in logBeforeExecution.
            String resolvedSql = resolveFromStatement(ctx);
            if (resolvedSql == null) {
                resolvedSql = (String) ctx.getAttribute("__resolvedSql__");
            }
            if (resolvedSql == null) return;

            String line = elapsedMs >= 0
                    ? "[DB Query ⏱ " + elapsedMs + "ms] " + resolvedSql
                    : "[DB Query] " + resolvedSql;

            if (DbQueryBuffer.isActive()) {
                DbQueryBuffer.add(line);
            } else {
                ReportLogger.info(line);
            }
        } catch (Exception ignored) {
            START_NS.remove();
        }
    }

    /**
     * Asks the JDBC driver to render the SQL with bound values.
     * PostgreSQL's {@code PgPreparedStatement.toString()} returns the query as sent
     * to the server — parameters already substituted, copy-paste ready.
     * Returns {@code null} if the statement is not available or the driver doesn't
     * support this (non-PG drivers typically return the class name instead).
     */
    private static String resolveFromStatement(StatementContext ctx) {
        try {
            java.sql.Statement stmt = ctx.getStatement();
            if (stmt == null) return null;
            String s = stmt.toString();
            // Accept only if it looks like real SQL (starts with a keyword, not a class name)
            if (s != null && s.length() > 6) {
                String upper = s.stripLeading().substring(0, 6).toUpperCase();
                if (upper.startsWith("SELECT") || upper.startsWith("INSERT")
                        || upper.startsWith("UPDATE") || upper.startsWith("DELETE")
                        || upper.startsWith("WITH")) {
                    return s.strip();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Substitutes positional {@code ?} placeholders in the rendered SQL with their
     * actual bound values. Handles bindList queries where JDBI expands
     * {@code <paramName>} to one {@code ?} per list element.
     */
    private static String substitutePositional(String sql, StatementContext ctx) {
        StringBuilder out = new StringBuilder(sql.length() + 64);
        int paramIdx = 1;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?') {
                final int pos = paramIdx++;
                @SuppressWarnings("deprecation")
                String val = ctx.getBinding()
                        .findForPosition(pos)
                        .map(a -> LogCapturePreparedStatement.capture(a, ctx))
                        .orElse("?");
                out.append(val);
            } else {
                out.append(sql.charAt(i));
            }
        }
        return out.toString();
    }
}
