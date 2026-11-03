package com.gsl.approval.evaluator;

/**
 * A rule cannot be evaluated: it uses a variable or operator this framework does not support, a value it cannot read, or the caller did not
 * supply a fact it needs. Reported as a configuration error naming the cause; the framework never guesses a result.
 */
public class ApprovalEvaluationException extends RuntimeException {

    /** What could not be evaluated. */
    public enum Kind { UNKNOWN_VARIABLE, BASIS_MISMATCH, MISSING_FACT, UNSUPPORTED_OPERATOR, BAD_VALUE }

    private final Kind kind;

    public ApprovalEvaluationException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public ApprovalEvaluationException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
