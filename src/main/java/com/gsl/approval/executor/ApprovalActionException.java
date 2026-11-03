package com.gsl.approval.executor;

/** An approval action did not go through, with a {@link Kind} the level service decides on (retry with a fresh session, reconcile, or fail). */
public class ApprovalActionException extends RuntimeException {

    /** What went wrong. */
    public enum Kind {
        /** HTTP 401: the session used is no longer valid (a reused session whose owner logged out). */
        STALE_SESSION,
        /** No answer, or a server error: the action may or may not have been applied - reconcile before any retry. */
        AMBIGUOUS,
        /** The application answered and refused ({@code result:false}, 4xx, {@code success:false}). */
        REJECTED
    }

    private final Kind kind;

    public ApprovalActionException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public ApprovalActionException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
