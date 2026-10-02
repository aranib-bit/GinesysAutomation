package com.gsl.utils;

import org.apache.logging.log4j.ThreadContext;

import java.util.UUID;

/**
 * Populates and clears Log4j2's per-thread {@link ThreadContext} (MDC) with test/session metadata
 * — test name/id, browser, environment, UI/API user, session id, execution id, active surface —
 * so every log line emitted while a test runs can be enriched with that context by the logging
 * config's own pattern layout, without each log call site having to pass it explicitly.
 *
 * <p>Thread-scoped by {@code ThreadContext} itself: each key set here is only visible to log
 * statements on the same thread. Callers must invoke {@link #clear()} when a test/thread's work is
 * done (e.g. in an {@code @AfterMethod}) so a pooled/reused thread doesn't leak one test's context
 * into the next test's log lines.
 */
public final class TestContextManager {

    public static final String KEY_TEST_NAME     = "testName";
    public static final String KEY_TEST_ID       = "testId";
    public static final String KEY_TEST_CASE_ID  = "testCaseId";
    public static final String KEY_UI_USER       = "uiUser";
    public static final String KEY_API_USER      = "apiUser";
    public static final String KEY_SESSION_ID    = "sessionId";
    public static final String KEY_EXECUTION_ID  = "executionId";
    public static final String KEY_BROWSER       = "browser";
    public static final String KEY_ENV           = "env";
    public static final String KEY_ACTIVE_SURFACE = "activeSurface";

    private TestContextManager() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Sets the current test's display name (falls back to {@code "UNKNOWN"} if {@code null}). */
    public static void setTestName(String testName) {
        ThreadContext.put(KEY_TEST_NAME, nvl(testName, "UNKNOWN"));
    }

    /** Generates a fresh 8-char correlation ID per test invocation for log traceability. */
    public static void setTestId() {
        ThreadContext.put(KEY_TEST_ID, UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    /** Full UUID — uniquely identifies one test execution across the entire suite lifecycle. */
    public static void setExecutionId() {
        ThreadContext.put(KEY_EXECUTION_ID, UUID.randomUUID().toString());
    }

    public static String getExecutionId() {
        return ThreadContext.get(KEY_EXECUTION_ID);
    }

    /** Sets the current thread's browser name (blank if {@code null}). */
    public static void setBrowser(String browser) { ThreadContext.put(KEY_BROWSER, nvl(browser, "")); }
    /** Sets the current thread's target environment (blank if {@code null}). */
    public static void setEnv(String env)         { ThreadContext.put(KEY_ENV,     nvl(env, "")); }

    /** Sets the current test's own test-case id (blank if {@code null}). */
    public static void setTestCaseId(String id) {
        ThreadContext.put(KEY_TEST_CASE_ID, nvl(id, ""));
    }

    /** Sets the UI-side username driving this thread's browser session (blank if {@code null}). */
    public static void setUiUser(String username)  { ThreadContext.put(KEY_UI_USER,    nvl(username, "")); }
    /** Sets the API-side username driving this thread's API session (blank if {@code null}). */
    public static void setApiUser(String username) { ThreadContext.put(KEY_API_USER,   nvl(username, "")); }
    /** Sets the current thread's remote WebDriver session id (blank if {@code null}). */
    public static void setSessionId(String id)     { ThreadContext.put(KEY_SESSION_ID, nvl(id, "")); }

    /** Multi-context test's currently active surface (WEB/MOBILE/API sub-choice) — set by ContextOrchestrator. */
    public static void setActiveSurface(String surface) { ThreadContext.put(KEY_ACTIVE_SURFACE, nvl(surface, "")); }

    /** Returns the current thread's test name, as last set by {@link #setTestName(String)}. */
    public static String getTestName() {
        return ThreadContext.get(KEY_TEST_NAME);
    }

    /** Remote WebDriver session id stamped by Driver.initDriver — null/blank for local drivers. */
    public static String getSessionId() {
        return ThreadContext.get(KEY_SESSION_ID);
    }

    /** Returns the current thread's test-case id, as last set by {@link #setTestCaseId(String)}. */
    public static String getTestCaseId() {
        return ThreadContext.get(KEY_TEST_CASE_ID);
    }

    /** Removes every key this class manages from the current thread's {@link ThreadContext} —
     *  call at the end of each test/thread's work so a pooled thread starts its next test clean. */
    public static void clear() {
        ThreadContext.remove(KEY_TEST_NAME);
        ThreadContext.remove(KEY_TEST_ID);
        ThreadContext.remove(KEY_TEST_CASE_ID);
        ThreadContext.remove(KEY_UI_USER);
        ThreadContext.remove(KEY_API_USER);
        ThreadContext.remove(KEY_SESSION_ID);
        ThreadContext.remove(KEY_EXECUTION_ID);
        ThreadContext.remove(KEY_BROWSER);
        ThreadContext.remove(KEY_ENV);
        ThreadContext.remove(KEY_ACTIVE_SURFACE);
    }

    private static String nvl(String value, String fallback) {
        return value != null ? value : fallback;
    }
}
