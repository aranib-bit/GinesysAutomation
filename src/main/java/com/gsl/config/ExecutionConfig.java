package com.gsl.config;

import org.aeonbits.owner.Config;

@Config.Sources("file:${user.dir}/src/test/resources/config/execution.properties")
public interface ExecutionConfig extends Config {

    @Key("execution.type")
    @DefaultValue("SET1")
    String executionType();

    @Key("thread.count")
    @DefaultValue("5")
    int threadCount();

    @Key("concurrent.user.count")
    @DefaultValue("2")
    int concurrentUserCount();

    @Key("sync.timeout.seconds")
    @DefaultValue("30")
    long syncTimeoutSeconds();

    @Key("pool.warn.at.seconds")
    @DefaultValue("300")
    int poolWarnAtSeconds();

    @Key("pool.acquire.timeout.seconds")
    @DefaultValue("600")
    int poolAcquireTimeoutSeconds();

    // Lease keys are retained as session HYGIENE under the SessionPool model (bound browser
    // memory bloat / server-side session TTL) — no longer anti-hoarding: sessions return to the
    // pool after every row, so a thread can never hoard a permit between rows. Safe to raise.
    @Key("session.max.lease.rows")
    @DefaultValue("10")
    int sessionMaxLeaseRows();

    @Key("session.max.lease.seconds")
    @DefaultValue("600")
    int sessionMaxLeaseSeconds();

    // Max time one data-provider thread may wait to borrow a logged-in session from SessionPool.
    // Deliberately much larger than pool.acquire.timeout.seconds: with runInParallel=true there
    // can be (methods × data-provider-thread-count) waiters sharing capacity(=UI user count)
    // sessions, and a waiter may legitimately queue behind several multi-minute rows.
    @Key("session.borrow.timeout.seconds")
    @DefaultValue("1800")
    int sessionBorrowTimeoutSeconds();

    @Key("suite.abort.enabled")
    @DefaultValue("false")
    boolean suiteAbortEnabled();

    @Key("suite.abort.on.failure.percent")
    @DefaultValue("30")
    int suiteAbortOnFailurePercent();

    @Key("suite.abort.min.tests.before.check")
    @DefaultValue("100")
    int suiteAbortMinTestsBeforeCheck();

    @Key("suite.abort.check.interval")
    @DefaultValue("50")
    int suiteAbortCheckInterval();

    @Key("retry.max.count")
    @DefaultValue("0")
    int retryMaxCount();
}
