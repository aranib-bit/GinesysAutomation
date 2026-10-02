package com.gsl.reports;

import com.aventstack.extentreports.ExtentTest;

/**
 * Thread-local home for the {@link ExtentTest} node(s) the current test thread is logging
 * against, plus the active {@link ShardKey} for that thread. Every other logger in this
 * package ({@code ReportLogger} and its {@code *Logger}/{@code *DescriptionLogger} facades)
 * reads the current node from here rather than being handed one directly, so a step logged
 * from deep inside a page object still lands on the right node(s) under
 * {@code parallel="methods"} execution.
 *
 * <p>Two parallel node slots are tracked per thread: the main {@link #getExtentTest() test node}
 * (the single combined report) and an optional {@link #getShardTest() shard node} — the same
 * test's node inside its own per-module/sub-module/operation {@code ExtentReports} instance
 * (see {@code ExtentReport}'s {@code SHARD_EXTENTS}), which produces the smaller per-area report
 * files. {@link #getCurrentShardKey()} identifies which {@link ShardKey} that shard node belongs
 * to, so {@code SummaryReportBuilder} can accumulate pass/fail stats per shard. Call
 * {@link #clearAll()} at the end of each test to avoid a thread leaking stale state into
 * whatever test runs on it next.
 */
public final class ExtentManager {

    private ExtentManager() {
    }

    private static final ThreadLocal<ExtentTest> threadLocal      = new ThreadLocal<>();
    private static final ThreadLocal<ExtentTest> shardThreadLocal = new ThreadLocal<>();

    /** The current thread's active {@link ExtentTest} node, or {@code null} if none is set. */
    public static ExtentTest getExtentTest() {
        return threadLocal.get();
    }

    /** Sets the current thread's active {@link ExtentTest} node. */
    public static void setExtentTest(ExtentTest test) {
        threadLocal.set(test);
    }

    /** {@code true} if the current thread has an active test node. */
    public static boolean hasTest() {
        return threadLocal.get() != null;
    }

    // R-02: shard test node (may be null when no shard is registered)
    static void setShardTest(ExtentTest test) {
        shardThreadLocal.set(test);
    }

    /** The current thread's active shard node, or {@code null} when no shard is registered. */
    public static ExtentTest getShardTest() {
        return shardThreadLocal.get();
    }

    // R-02: tracks the active ShardKey so SummaryReportBuilder can accumulate stats
    private static final ThreadLocal<ShardKey> shardKeyLocal = new ThreadLocal<>();

    static void setCurrentShardKey(ShardKey key) {
        shardKeyLocal.set(key);
    }

    /** The current thread's active {@link ShardKey}, or {@code null} when no shard is registered. */
    public static ShardKey getCurrentShardKey() {
        return shardKeyLocal.get();
    }

    /** Clears this thread's test node, shard node, and shard key — call at the end of each test. */
    public static void clearAll() {
        threadLocal.remove();
        shardThreadLocal.remove();
        shardKeyLocal.remove();
    }
}
