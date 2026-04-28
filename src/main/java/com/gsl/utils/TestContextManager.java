package com.gsl.utils;

import org.apache.logging.log4j.ThreadContext;

import java.util.UUID;

public final class TestContextManager {

    public static final String KEY_TEST_NAME    = "testName";
    public static final String KEY_TEST_ID      = "testId";
    public static final String KEY_TEST_CASE_ID = "testCaseId";

    private TestContextManager() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void setTestName(String testName) {
        ThreadContext.put(KEY_TEST_NAME, testName != null ? testName : "UNKNOWN");
    }

    /** Generates a fresh 8-char correlation ID per test invocation for log traceability. */
    public static void setTestId() {
        ThreadContext.put(KEY_TEST_ID, UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    public static void setTestCaseId(String id) {
        ThreadContext.put(KEY_TEST_CASE_ID, id != null ? id : "");
    }

    public static void clear() {
        ThreadContext.remove(KEY_TEST_NAME);
        ThreadContext.remove(KEY_TEST_ID);
        ThreadContext.remove(KEY_TEST_CASE_ID);
    }
}
