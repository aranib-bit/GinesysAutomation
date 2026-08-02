package com.gsl.reports;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Per-test-class attribution of shared-resource cost — how long each class's threads held a
 * {@code UserPool} credential, and how much of that was unavoidable per-thread cold-start
 * login/navigation (see {@code ExecutionContext.isColdStartInProgress()}) versus later reuse.
 *
 * <p>Exists specifically to answer "which class is consuming disproportionate shared-pool time"
 * without manually reading the Extent report line by line — the exact question a real incident
 * (AccountGroupLifecycle starving AP/AR Voucher of pool users) took a long manual investigation to
 * answer. {@link SummaryReportBuilder} renders this once, at suite end.</p>
 */
public final class PoolAttributionStats {

    private PoolAttributionStats() {}

    private static final ConcurrentHashMap<String, LongAdder> HOLD_SECONDS_BY_CLASS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> COLD_START_MS_BY_CLASS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> COLD_START_COUNT_BY_CLASS = new ConcurrentHashMap<>();

    /** Called from {@code UserPool.releaseSet1User} with the class-qualified holder label. */
    public static void recordPoolHold(String qualifiedLabel, long heldSeconds) {
        String className = classNameOf(qualifiedLabel);
        HOLD_SECONDS_BY_CLASS.computeIfAbsent(className, k -> new LongAdder()).add(heldSeconds);
    }

    /** Called once per thread's first-ever login+navigation (see ExecutionContext/WebTest/SessionManager). */
    public static void recordColdStartWarmup(String qualifiedLabel, long elapsedMs) {
        String className = classNameOf(qualifiedLabel);
        COLD_START_MS_BY_CLASS.computeIfAbsent(className, k -> new LongAdder()).add(elapsedMs);
        COLD_START_COUNT_BY_CLASS.computeIfAbsent(className, k -> new LongAdder()).increment();
    }

    /** @return class simple name → cumulative pool-hold seconds, sorted by hold time descending is left to the caller. */
    public static Map<String, Long> holdSecondsByClass() {
        Map<String, Long> out = new TreeMap<>();
        HOLD_SECONDS_BY_CLASS.forEach((k, v) -> out.put(k, v.sum()));
        return Collections.unmodifiableMap(out);
    }

    public static Map<String, Long> coldStartMsByClass() {
        Map<String, Long> out = new TreeMap<>();
        COLD_START_MS_BY_CLASS.forEach((k, v) -> out.put(k, v.sum()));
        return Collections.unmodifiableMap(out);
    }

    public static Map<String, Long> coldStartCountByClass() {
        Map<String, Long> out = new TreeMap<>();
        COLD_START_COUNT_BY_CLASS.forEach((k, v) -> out.put(k, v.sum()));
        return Collections.unmodifiableMap(out);
    }

    /** Labels are "SimpleClassName.methodName" (see WebTest.setUp()); falls back to the whole label if unqualified. */
    private static String classNameOf(String qualifiedLabel) {
        if (qualifiedLabel == null) return "<unknown>";
        int dot = qualifiedLabel.indexOf('.');
        return dot > 0 ? qualifiedLabel.substring(0, dot) : qualifiedLabel;
    }
}
