package com.gsl.approval.auth;

import com.gsl.approval.model.ApproverIdentity;
import io.restassured.specification.RequestSpecification;

/**
 * An authenticated WEBAPI session acting as one approver. {@code source} says where it came from, so the report can show "token reused" or
 * "logged in". The spec carries the tokens: it is never logged, printed or put in a report ({@link #toString()} shows neither).
 */
public final class ApproverSession {

    /** Where the session came from. */
    public enum Source { BROWSER_TOKENS, LIVE_API_SESSION, NEW_LOGIN }

    private final ApproverIdentity identity;
    private final RequestSpecification spec;
    private final Source source;
    private final Runnable closer;

    public ApproverSession(ApproverIdentity identity, RequestSpecification spec, Source source, Runnable closer) {
        this.identity = identity;
        this.spec = spec;
        this.source = source;
        this.closer = closer;
    }

    public ApproverIdentity identity() {
        return identity;
    }

    public RequestSpecification spec() {
        return spec;
    }

    public Source source() {
        return source;
    }

    /** {@code true} when an already-live session was reused (no login happened). */
    public boolean reused() {
        return source != Source.NEW_LOGIN;
    }

    /** Ends the session only when this object logged it in; a reused session belongs to its owner and is left alone. */
    public void close() {
        if (source == Source.NEW_LOGIN && closer != null) {
            closer.run();
        }
    }

    @Override
    public String toString() {
        return "ApproverSession[user=" + identity.getUsername() + ", source=" + source + ", tokens=***]";
    }
}
