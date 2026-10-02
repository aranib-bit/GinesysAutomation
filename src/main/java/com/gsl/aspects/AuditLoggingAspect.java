package com.gsl.aspects;

import com.gsl.annotations.AuditTrace;
import com.gsl.validation.snapshot.audit.AuditTriggerStateResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Structured, greppable diagnostic logging for the audit framework's key decision points —
 * DELETE+INSERT reconciliation, the Expected-vs-Actual comparison, the old-value continuity
 * cross-check, and the exclude-column/trigger-state resolvers (every method carrying
 * {@link AuditTrace}). Routed into {@code logs/db-validation.log} via the existing {@code
 * DBValidation} named Log4j2 logger — no new log file, same audience as {@code ReportLogger.db(...)}
 * already targets.
 *
 * <p><b>Logs on every invocation, pass or fail</b> — never gated on failure. A false positive in
 * this framework looks exactly like a normal passing test from the outside; the whole point of
 * this aspect is that the Expected/Actual/reconciliation/continuity inputs are reviewable from the
 * log regardless of the test's own outcome (see the plan's "AspectJ Logging for False-Positive /
 * False-Negative Diagnosis" section).
 *
 * <p>Sibling to {@link LoggingAspect} rather than folded into it — kept separate so the audit
 * framework's semantically-tagged decision log doesn't get lost inside that class's much broader,
 * generic method-trace advice. Covered by the same {@code aop.xml} {@code <include
 * within="com.gsl..*"/>} weaving config already in place — no weaver config change needed.
 */
@Aspect
public class AuditLoggingAspect {

    private static final Logger DB_VALIDATION_LOG = LogManager.getLogger("DBValidation");
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * Wraps any {@link AuditTrace}-annotated method: proceeds unconditionally, then logs one line to
     * {@code DBValidation} tagged via {@link #tagFor(String, Object)} with the method's args, its
     * result (or the thrown exception), and elapsed time. Rethrows any exception from the wrapped
     * method after logging it as {@code AUDIT-ERROR}.
     */
    @Around("@annotation(auditTrace)")
    public Object logAuditDecision(ProceedingJoinPoint pjp, AuditTrace auditTrace) throws Throwable {
        String decision = auditTrace.value();
        String className = pjp.getTarget() != null
                ? pjp.getTarget().getClass().getSimpleName()
                : pjp.getSignature().getDeclaringType().getSimpleName();

        long startNanos = System.nanoTime();
        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            logLine("AUDIT-ERROR", decision, className, pjp.getArgs(),
                    "threw " + t.getClass().getSimpleName() + ": " + t.getMessage(), durationMs);
            throw t;
        }
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        logLine(tagFor(decision, result), decision, className, pjp.getArgs(), result, durationMs);
        return result;
    }

    /** Maps each known {@link AuditTrace} decision label to its greppable log tag. Falls back to a
     *  generic tag for any future {@code @AuditTrace}-annotated method not yet in this list. */
    private String tagFor(String decision, Object result) {
        return switch (decision) {
            case "reconstruct" -> "AUDIT-ACTUAL";
            case "reconcileGroup" -> "AUDIT-RECONCILE";
            case "compare" -> "AUDIT-VERDICT";
            case "deriveExpected" -> "AUDIT-EXPECTED";
            case "runContinuityCheck" -> "AUDIT-CHAIN-CHECK";
            // Logged every time (ENABLED included), not only when skipping — but tagged AUDIT-SKIP
            // specifically when the result is what actually causes a table's audit checks to be
            // skipped, so `grep AUDIT-SKIP` finds exactly the environments/tables where it happened.
            case "resolveTriggerStatus" -> result == AuditTriggerStateResolver.TriggerStatus.DISABLED
                    ? "AUDIT-SKIP" : "AUDIT-TRIGGER-STATE";
            case "resolveExcludedColumns" -> "AUDIT-EXCLUDE-RESOLVE";
            case "assertAuditDeleted", "assertAuditUpdated", "assertAuditInserted", "assertNoAudit" -> "AUDIT-ASSERT";
            default -> "AUDIT-TRACE";
        };
    }

    /** Writes one greppable {@code DBValidation} log line for an audited decision; failures here are swallowed, never propagated. */
    private void logLine(String tag, String decision, String className, Object[] args, Object result, long durationMs) {
        try {
            DB_VALIDATION_LOG.info("[{}] [{}] {}.{}(...) params={} -> result={} [{}ms]",
                    tag, LocalDateTime.now().format(TS_FMT), className, decision,
                    summarizeArgs(args), summarize(result), durationMs);
        } catch (Exception ignored) {
            // Never let diagnostic logging break the actual audit decision.
        }
    }

    /** Renders a method's argument array as a bracketed, {@link #summarize(Object)}-per-element string. */
    private String summarizeArgs(Object[] args) {
        if (args == null || args.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(summarize(args[i]));
        }
        return sb.append("]").toString();
    }

    /** Best-effort, size-bounded rendering — records/enums/collections get their natural {@code
     *  toString()} (informative for this framework's model types); anything else is truncated
     *  rather than risking a runaway log line. */
    private String summarize(Object value) {
        if (value == null) return "null";
        if (value instanceof Collection<?> c) {
            String preview = c.stream().limit(10).map(String::valueOf).collect(Collectors.joining("; "));
            return "[" + c.size() + " item(s)" + (c.isEmpty() ? "" : ": " + preview) + "]";
        }
        String s = String.valueOf(value);
        return s.length() > 500 ? s.substring(0, 500) + "…(truncated)" : s;
    }
}
