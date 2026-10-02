package com.gsl.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks one of the audit framework's key decision methods for structured, greppable diagnostic
 * logging via {@code com.gsl.aspects.AuditLoggingAspect} — DELETE+INSERT reconciliation, the
 * Expected-vs-Actual comparison, the old-value continuity cross-check, and the exclude-column/
 * trigger-state resolvers.
 *
 * <p>Logged on EVERY invocation, pass or fail — not just on exception — because a false positive
 * in this framework looks exactly like a normal passing test from the outside; the decision inputs
 * must be reviewable from the log regardless of outcome (see the plan's "AspectJ Logging for
 * False-Positive / False-Negative Diagnosis" section).
 *
 * <p>Mirrors {@link LogStep}'s shape exactly — a short label identifying which decision point this
 * is, used as the log-line prefix.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditTrace {
    /** Short label identifying which decision point this is; used as the log-line prefix. */
    String value();
}
