package com.gsl.api.core;

import com.gsl.enums.ApiPlatform;
import com.gsl.reports.ReportLogger;

import java.util.EnumMap;

/**
 * Thread-safe API session manager — structurally identical to {@code DriverManager}'s
 * EnumMap-of-ThreadLocal pattern, so a developer who understands one immediately understands
 * the other. Keeps one {@link ApiSessionBundle} per thread per {@link ApiPlatform}.
 *
 * <p>{@code K6BULK} keeps a map slot for symmetry but is never populated — {@code
 * ContextOrchestrator} never switches into it; only {@code DataSourceRouter}/{@code
 * DataAvailabilityGuard} select it, routing straight to {@code K6ProcessBulkDataProvisioner}.
 */
public final class ApiSessionManager {

    private static final EnumMap<ApiPlatform, ThreadLocal<ApiSessionBundle>> CONTEXT_SESSIONS = buildMap();

    private static final ThreadLocal<ApiPlatform> activeApiContext =
            ThreadLocal.withInitial(() -> ApiPlatform.WEBAPI);

    private ApiSessionManager() {}

    private static EnumMap<ApiPlatform, ThreadLocal<ApiSessionBundle>> buildMap() {
        EnumMap<ApiPlatform, ThreadLocal<ApiSessionBundle>> map = new EnumMap<>(ApiPlatform.class);
        for (ApiPlatform p : ApiPlatform.values()) {
            map.put(p, new ThreadLocal<>());
        }
        return map;
    }

    /** Returns this thread's session for the currently active {@link ApiPlatform} — null if none has been set yet. */
    public static ApiSessionBundle getSession() {
        return CONTEXT_SESSIONS.get(activeApiContext.get()).get();
    }

    /** Stores {@code bundle} as this thread's session for the currently active {@link ApiPlatform}. */
    public static void setSession(ApiSessionBundle bundle) {
        CONTEXT_SESSIONS.get(activeApiContext.get()).set(bundle);
        ReportLogger.framework("API session initialized — context={}", activeApiContext.get());
    }

    /** Logs out (WEBAPI only, no-op otherwise) and removes the session for the active context. */
    public static void unloadSession() {
        ApiSessionBundle bundle = CONTEXT_SESSIONS.get(activeApiContext.get()).get();
        if (bundle != null) {
            bundle.logout();
        }
        CONTEXT_SESSIONS.get(activeApiContext.get()).remove();
    }

    /** Logs out and removes every context's session for this thread — call in tearDown. */
    public static void unloadAllContexts() {
        for (ThreadLocal<ApiSessionBundle> tl : CONTEXT_SESSIONS.values()) {
            ApiSessionBundle bundle = tl.get();
            if (bundle != null) {
                bundle.logout();
            }
            tl.remove();
        }
        activeApiContext.remove();
    }

    /** Switches this thread's active {@link ApiPlatform} — subsequent {@link #getSession()}/{@link #setSession} calls target it. */
    public static void switchContext(ApiPlatform platform) {
        activeApiContext.set(platform);
    }

    /** The {@link ApiPlatform} this thread is currently pointed at (defaults to {@code WEBAPI}). */
    public static ApiPlatform getActiveContext() {
        return activeApiContext.get();
    }
}
