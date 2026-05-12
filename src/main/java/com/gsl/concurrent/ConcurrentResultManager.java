package com.gsl.concurrent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collects pass/fail counts, per-username outcome lists, and per-session timestamps
 * for SET-2 concurrent test runs. All operations are thread-safe.
 * Call reset() once before each SET-2 test group (done by the first-arriving thread in BaseSetTwoTest).
 *
 * Test methods call:
 *   ConcurrentResultManager.recordSuccess(username, sessionId, System.currentTimeMillis());
 *   ConcurrentResultManager.recordFailure(username, sessionId, "Order already invoiced");
 *
 * @AfterClass calls getSuccessUsers().size() and getFailedUsers().size() to assert exactly-one outcomes.
 */
public final class ConcurrentResultManager {

    private static final AtomicInteger successCount = new AtomicInteger();
    private static final AtomicInteger failureCount = new AtomicInteger();

    private static final List<String> successUsers =
            Collections.synchronizedList(new ArrayList<>());
    private static final List<String> failedUsers =
            Collections.synchronizedList(new ArrayList<>());

    private static final ConcurrentHashMap<String, Long>   saveTimestamps = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> sessionResults = new ConcurrentHashMap<>();

    private ConcurrentResultManager() {}

    public static void recordSuccess(String username, String sessionId, long timestampMs) {
        successCount.incrementAndGet();
        successUsers.add(username);
        saveTimestamps.put(sessionId, timestampMs);
        sessionResults.put(sessionId, "PASS");
    }

    public static void recordFailure(String username, String sessionId, String reason) {
        failureCount.incrementAndGet();
        failedUsers.add(username);
        sessionResults.put(sessionId, "FAIL: " + reason);
    }

    public static int         getSuccessCount()                    { return successCount.get(); }
    public static int         getFailureCount()                    { return failureCount.get(); }
    public static List<String> getSuccessUsers()                   { return successUsers; }
    public static List<String> getFailedUsers()                    { return failedUsers; }
    public static Long        getSaveTimestamp(String sessionId)   { return saveTimestamps.get(sessionId); }
    public static String      getSessionResult(String sessionId)   { return sessionResults.get(sessionId); }

    public static void reset() {
        successCount.set(0);
        failureCount.set(0);
        successUsers.clear();
        failedUsers.clear();
        saveTimestamps.clear();
        sessionResults.clear();
    }
}
