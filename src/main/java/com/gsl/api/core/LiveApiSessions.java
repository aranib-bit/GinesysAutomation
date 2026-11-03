package com.gsl.api.core;

import io.restassured.specification.RequestSpecification;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The authenticated specs of the WEBAPI sessions this JVM currently holds, keyed by user id. {@code ApiSessionFactory} registers a session
 * when it logs one in and {@code ApiSessionBundle.logout()} removes it, so another flow (the approval workflow acting as an approver) can
 * reuse a user who is already logged in instead of logging them in again - which would end the first session (code 012 / KillUser). Held in
 * memory only; the specs carry tokens, so nothing here is ever logged. A reused spec can still go stale (its owner logs out): callers treat
 * a 401 as "ask again".
 */
public final class LiveApiSessions {

    private static final Map<String, RequestSpecification> BY_USER_ID = new ConcurrentHashMap<>();

    private LiveApiSessions() {}

    static void register(String userId, RequestSpecification spec) {
        if (userId != null && spec != null) {
            BY_USER_ID.put(userId, spec);
        }
    }

    static void unregister(String userId) {
        if (userId != null) {
            BY_USER_ID.remove(userId);
        }
    }

    /** The live session spec of {@code userId}, or {@code null} when this JVM holds none. */
    public static RequestSpecification find(String userId) {
        return userId == null ? null : BY_USER_ID.get(userId);
    }
}
