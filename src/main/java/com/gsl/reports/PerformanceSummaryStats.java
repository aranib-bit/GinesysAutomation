package com.gsl.reports;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Suite-wide counters for the Performance Report's summary section (Sessions Created,
 * Recoveries, Navigation Skipped, Users Assigned, Time Saved) — accumulated across the whole
 * run by {@code SessionManager}/{@code RecoveryManager}/{@code NavigationLogger} and rendered
 * once by {@link SummaryReportBuilder}.
 */
public final class PerformanceSummaryStats {

    private PerformanceSummaryStats() {}

    private static final AtomicInteger SESSIONS_CREATED   = new AtomicInteger();
    private static final AtomicInteger RECOVERIES         = new AtomicInteger();
    private static final AtomicInteger NAVIGATION_SKIPPED  = new AtomicInteger();
    private static final AtomicInteger NAVIGATION_PERFORMED = new AtomicInteger();
    private static final Set<String> USERS_ASSIGNED = ConcurrentHashMap.newKeySet();

    public static void recordSessionCreated(String username) {
        SESSIONS_CREATED.incrementAndGet();
        if (username != null) USERS_ASSIGNED.add(username);
    }

    public static void recordRecovery() {
        RECOVERIES.incrementAndGet();
    }

    public static void recordNavigationSkipped() {
        NAVIGATION_SKIPPED.incrementAndGet();
    }

    public static void recordNavigationPerformed() {
        NAVIGATION_PERFORMED.incrementAndGet();
    }

    public static int sessionsCreated()    { return SESSIONS_CREATED.get(); }
    public static int recoveries()         { return RECOVERIES.get(); }
    public static int navigationSkipped()  { return NAVIGATION_SKIPPED.get(); }
    public static int navigationPerformed() { return NAVIGATION_PERFORMED.get(); }
    public static Set<String> usersAssigned() { return Collections.unmodifiableSet(USERS_ASSIGNED); }
}
