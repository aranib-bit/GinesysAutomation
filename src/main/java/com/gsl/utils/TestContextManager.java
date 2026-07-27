package com.gsl.utils;

import org.apache.logging.log4j.ThreadContext;

import java.util.UUID;

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

    private TestContextManager() {
        throw new UnsupportedOperationException("Utility class");
    }

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

    public static void setBrowser(String browser) { ThreadContext.put(KEY_BROWSER, nvl(browser, "")); }
    public static void setEnv(String env)         { ThreadContext.put(KEY_ENV,     nvl(env, "")); }

    public static void setTestCaseId(String id) {
        ThreadContext.put(KEY_TEST_CASE_ID, nvl(id, ""));
    }

    public static void setUiUser(String username)  { ThreadContext.put(KEY_UI_USER,    nvl(username, "")); }
    public static void setApiUser(String username) { ThreadContext.put(KEY_API_USER,   nvl(username, "")); }
    public static void setSessionId(String id)     { ThreadContext.put(KEY_SESSION_ID, nvl(id, "")); }

    public static String getTestName() {
        return ThreadContext.get(KEY_TEST_NAME);
    }

    /** Remote WebDriver session id stamped by Driver.initDriver — null/blank for local drivers. */
    public static String getSessionId() {
        return ThreadContext.get(KEY_SESSION_ID);
    }

    public static String getTestCaseId() {
        return ThreadContext.get(KEY_TEST_CASE_ID);
    }

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
    }

    private static String nvl(String value, String fallback) {
        return value != null ? value : fallback;
    }
}
