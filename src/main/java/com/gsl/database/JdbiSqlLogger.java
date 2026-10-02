package com.gsl.database;

import com.gsl.reports.ReportLogger;
import org.jdbi.v3.core.statement.SqlLogger;
import org.jdbi.v3.core.statement.StatementContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    private static final org.apache.logging.log4j.Logger TRACE =
            org.apache.logging.log4j.LogManager.getLogger("MethodTrace");

    private static final Pattern NAMED_PARAM = Pattern.compile(":(\\w+)");

    /** Stores nanoTime at statement start so logAfterExecution can compute elapsed ms. */
    private static final ThreadLocal<Long> START_NS = new ThreadLocal<>();

    /** When true, SQL query lines are silently dropped instead of being logged to the report. */
    private static final ThreadLocal<Boolean> SUPPRESS = ThreadLocal.withInitial(() -> false);

    /** Starts dropping SQL query lines for the current thread instead of logging them to the report. */
    public static void startSuppressing() { SUPPRESS.set(true); }
    /** Stops suppression for the current thread and clears its {@link ThreadLocal} entry. */
    public static void stopSuppressing()  { SUPPRESS.set(false); SUPPRESS.remove(); }

    /**
     * When active, query lines are captured into this list instead of being
     * logged to the report. Activate with {@link #startCapturing()}, read with
     * {@link #getCaptured()}, and clean up with {@link #stopCapturing()}.
     */
    private static final ThreadLocal<List<String>> CAPTURED = new ThreadLocal<>();

    /** Activates capturing on the current thread — must be paired with {@link #stopCapturing()}. */
    public static void startCapturing()        { CAPTURED.set(new ArrayList<>()); }
    /** @return the lines captured so far on the current thread, or an empty list if capturing isn't active. */
    public static List<String> getCaptured()   { var l = CAPTURED.get(); return l != null ? l : Collections.emptyList(); }
    /** Deactivates capturing and clears the current thread's captured list. */
    public static void stopCapturing()         { CAPTURED.remove(); }

    // ── Before execution: resolve SQL and start timing ────────────────────────

    /** Resolves {@code ctx}'s SQL (named + positional params substituted) into {@code __resolvedSql__} and records the start time; swallows all failures so a logging error never breaks the underlying query. */
    @Override
    public void logBeforeExecution(StatementContext ctx) {
        START_NS.set(System.nanoTime());
        try {
            String rawSql = ctx.getRawSql();
            if (rawSql == null || rawSql.isBlank()) return;

            // Prefer the template-RENDERED sql over the raw sql as the base to scan for :name
            // placeholders. bindList()'s IN-clause syntax uses a <name> template token (e.g.
            // "IN (<tables>)"), which JDBI's template engine expands into plain :name0, :name1, ...
            // colon params BEFORE the SQL parser ever sees it — getRenderedSql() reflects that
            // expansion, getRawSql() does not. Previously this always scanned getRawSql(), so a
            // <name> token here never matched the :(\w+) regex below (it's not colon-prefixed) and
            // was left completely unresolved in the logged SQL — not a display nuance: the logged
            // text (e.g. "IN (<tables>)") isn't valid SQL a reader could copy-paste and run, unlike
            // every other resolved query line. getRenderedSql() is a safe universal substitute: for
            // a query with no <name> templates at all, it's identical to getRawSql() in every way
            // that matters here (the template engine is a no-op when there's nothing to render).
            String renderedSql = ctx.getRenderedSql();
            String sqlToScan = (renderedSql != null && !renderedSql.isBlank()) ? renderedSql : rawSql;

            // Step 1: substitute named :paramName placeholders
            var binding = ctx.getBinding();
            Matcher m = NAMED_PARAM.matcher(sqlToScan);
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

        } catch (Throwable e) {
            // catch Throwable (not just Exception) so Errors from Proxy creation
            // or module-access issues don't escape and cause the JDBI query to fail
            try { ctx.define("__resolvedSql__", "(could not resolve: " + e.getMessage() + ")"); }
            catch (Throwable ignored) {}
        }
    }

    // ── After execution: emit with timing ─────────────────────────────────────

    /**
     * Emits the resolved SQL line with elapsed time, routed to whichever sink is active for the
     * current thread — {@link DbQueryBuffer}, {@link #CAPTURED}, {@link com.gsl.reports.ValidationBuffer},
     * or {@link ReportLogger} — and always echoes it into {@code trace.log} unless suppressed.
     */
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

            // Echo the resolved SQL into trace.log at the current AOP call depth.
            // JDBI DAO proxy calls can't be woven by AspectJ, so this is the only
            // way their SQL appears in the method-trace hierarchy.
            if (!Boolean.TRUE.equals(SUPPRESS.get())) {
                int depth = com.gsl.aspects.LoggingAspect.currentDepth();
                String indent = "  ".repeat(depth);
                String ts = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
                String traceId = org.apache.logging.log4j.ThreadContext.get("testId");
                if (traceId == null) traceId = org.apache.logging.log4j.ThreadContext.get("testName");
                if (traceId == null) traceId = "T-" + Thread.currentThread().getId();
                TRACE.info("{}[{}] [TRACE_ID={}] [DB]     {}", indent, ts, traceId, line);
            }

            if (DbQueryBuffer.isActive()) {
                DbQueryBuffer.add(line);
            } else if (CAPTURED.get() != null) {
                CAPTURED.get().add(line);   // capture for HTML display; skip raw log row
            } else if (com.gsl.reports.ValidationBuffer.isActive()) {
                com.gsl.reports.ValidationBuffer.add(line);
            } else if (!Boolean.TRUE.equals(SUPPRESS.get())) {
                ReportLogger.db(line);
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
