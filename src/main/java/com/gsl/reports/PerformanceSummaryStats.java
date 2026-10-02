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

    /** Tallies one new session creation and records {@code username} in the suite-wide assigned-users set. */
    public static void recordSessionCreated(String username) {
        SESSIONS_CREATED.incrementAndGet();
        if (username != null) USERS_ASSIGNED.add(username);
    }

    /** Tallies one recovery across the suite. */
    public static void recordRecovery() {
        RECOVERIES.incrementAndGet();
    }

    /** Tallies one skipped (already-loaded) navigation across the suite. */
    public static void recordNavigationSkipped() {
        NAVIGATION_SKIPPED.incrementAndGet();
    }

    /** Tallies one performed (actually navigated) navigation across the suite. */
    public static void recordNavigationPerformed() {
        NAVIGATION_PERFORMED.incrementAndGet();
    }

    /** Total sessions created across the suite so far. */
    public static int sessionsCreated()    { return SESSIONS_CREATED.get(); }
    /** Total recoveries across the suite so far. */
    public static int recoveries()         { return RECOVERIES.get(); }
    /** Total skipped navigations across the suite so far. */
    public static int navigationSkipped()  { return NAVIGATION_SKIPPED.get(); }
    /** Total performed navigations across the suite so far. */
    public static int navigationPerformed() { return NAVIGATION_PERFORMED.get(); }
    /** Unmodifiable view of every distinct username assigned a session so far. */
    public static Set<String> usersAssigned() { return Collections.unmodifiableSet(USERS_ASSIGNED); }
}
