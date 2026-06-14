package com.gsl.reports;

import com.aventstack.extentreports.ExtentTest;

public final class ExtentManager {

    private ExtentManager() {
    }

    private static final ThreadLocal<ExtentTest> threadLocal      = new ThreadLocal<>();
    private static final ThreadLocal<ExtentTest> shardThreadLocal = new ThreadLocal<>();

    public static ExtentTest getExtentTest() {
        return threadLocal.get();
    }

    public static void setExtentTest(ExtentTest test) {
        threadLocal.set(test);
    }

    public static boolean hasTest() {
        return threadLocal.get() != null;
    }

    // R-02: shard test node (may be null when no shard is registered)
    static void setShardTest(ExtentTest test) {
        shardThreadLocal.set(test);
    }

    public static ExtentTest getShardTest() {
        return shardThreadLocal.get();
    }

    // R-02: tracks the active ShardKey so SummaryReportBuilder can accumulate stats
    private static final ThreadLocal<ShardKey> shardKeyLocal = new ThreadLocal<>();

    static void setCurrentShardKey(ShardKey key) {
        shardKeyLocal.set(key);
    }

    public static ShardKey getCurrentShardKey() {
        return shardKeyLocal.get();
    }
}
