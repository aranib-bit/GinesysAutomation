package com.gsl.config;

import org.aeonbits.owner.Config;

/**
 * Suite-level execution controls: thread/concurrency counts, {@code SessionPool} lease and
 * borrow timeouts, suite-abort-on-failure-rate thresholds, and test retry count. Read from
 * {@code execution.properties} and validated at suite start by {@link ConfigValidator#validate()}.
 */
@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({
        "system:properties",
        "file:${user.dir}/src/test/resources/env/${env}/execution.properties",
        "file:${user.dir}/src/test/resources/config/execution.properties"
})
public interface ExecutionConfig extends Config {

    /** Named execution/data-set selector (e.g. SET1) picking which test-data subset a run uses. */
    @Key("execution.type")
    @DefaultValue("SET1")
    String executionType();

    /** Number of TestNG worker threads. */
    @Key("thread.count")
    @DefaultValue("5")
    int threadCount();

    /** Number of concurrent UI/API users the SessionPool is sized for. */
    @Key("concurrent.user.count")
    @DefaultValue("2")
    int concurrentUserCount();

    /** Generic cross-thread synchronization timeout, in seconds. */
    @Key("sync.timeout.seconds")
    @DefaultValue("30")
    long syncTimeoutSeconds();

    /** Time (seconds) a thread can wait on the SessionPool before a warning is logged (short of the hard {@link #poolAcquireTimeoutSeconds()}). */
    @Key("pool.warn.at.seconds")
    @DefaultValue("300")
    int poolWarnAtSeconds();

    /** Max time (seconds) a thread waits to acquire a session from the SessionPool before failing. */
    @Key("pool.acquire.timeout.seconds")
    @DefaultValue("600")
    int poolAcquireTimeoutSeconds();

    // Lease keys are retained as session HYGIENE under the SessionPool model (bound browser
    // memory bloat / server-side session TTL) — no longer anti-hoarding: sessions return to the
    // pool after every row, so a thread can never hoard a permit between rows. Safe to raise.
    /** Max number of data rows a single session may serve before SessionPool recycles it, for browser-memory hygiene. */
    @Key("session.max.lease.rows")
    @DefaultValue("10")
    int sessionMaxLeaseRows();

    /** Max age (seconds) a single session may be leased before SessionPool recycles it, for session-TTL hygiene. */
    @Key("session.max.lease.seconds")
    @DefaultValue("600")
    int sessionMaxLeaseSeconds();

    // Max time one data-provider thread may wait to borrow a logged-in session from SessionPool.
    // Deliberately much larger than pool.acquire.timeout.seconds: with runInParallel=true there
    // can be (methods × data-provider-thread-count) waiters sharing capacity(=UI user count)
    // sessions, and a waiter may legitimately queue behind several multi-minute rows.
    /** Max time (seconds) a data-provider thread waits to borrow a logged-in session from SessionPool. */
    @Key("session.borrow.timeout.seconds")
    @DefaultValue("1800")
    int sessionBorrowTimeoutSeconds();

    /** Master switch for aborting the suite early when the failure rate exceeds {@link #suiteAbortOnFailurePercent()}. */
    @Key("suite.abort.enabled")
    @DefaultValue("false")
    boolean suiteAbortEnabled();

    /** Failure percentage (0-100) that triggers a suite abort when {@link #suiteAbortEnabled()} is true. */
    @Key("suite.abort.on.failure.percent")
    @DefaultValue("30")
    int suiteAbortOnFailurePercent();

    /** Minimum number of completed tests before the abort-on-failure-rate check starts evaluating. */
    @Key("suite.abort.min.tests.before.check")
    @DefaultValue("100")
    int suiteAbortMinTestsBeforeCheck();

    /** Number of tests between successive abort-on-failure-rate checks. */
    @Key("suite.abort.check.interval")
    @DefaultValue("50")
    int suiteAbortCheckInterval();

    /** Max number of automatic retries for a failed test. */
    @Key("retry.max.count")
    @DefaultValue("0")
    int retryMaxCount();

    // Caps SessionPool's concurrent-cold-start throttle (see SessionPool#coldStartGate) when
    // running HEADED (headless=false) — several visible Chrome windows launching at the exact
    // same instant can get clipped/mis-sized by the OS window manager. Defaults to this project's
    // own pre-parallel="tests" maximum concurrency (the largest single <test> block's own
    // thread-count in testng-gv-mandatory.xml) rather than an arbitrary number. Never applied when
    // headless=true — a headless run has no real window to clip, so it keeps full pool capacity.
    /** Max concurrent cold-start browser launches allowed when running headed (see block comment above for why). */
    @Key("session.headed.coldstart.maxconcurrent")
    @DefaultValue("2")
    int headedColdStartMaxConcurrent();

    // Optional caps on concurrent browser sessions (SessionPool capacity), per target OS — see
    // ConcurrencyCap. Strings, not Integer: a blank value means "no cap" and Owner cannot convert "" to a number.
    /** One-off cap for any OS; overrides the per-OS keys when set. Blank = not set. */
    @Key("max.concurrent.sessions")
    @DefaultValue("")
    String maxConcurrentSessions();

    /** Cap when {@code target.os=linux}. Blank = no cap. */
    @Key("max.concurrent.sessions.linux")
    @DefaultValue("")
    String maxConcurrentSessionsLinux();

    /** Cap when {@code target.os=windows} (one Windows VM supports only a few parallel sessions). Blank = no cap. */
    @Key("max.concurrent.sessions.windows")
    @DefaultValue("")
    String maxConcurrentSessionsWindows();

    /** WebDriver command read-timeout, applied to every LOCAL driver (Chrome/Edge/Firefox) — see
     *  {@code com.gsl.driver.BrowserOptions#clientConfig()}. Reverted to Selenium's own built-in
     *  180s default ({@code ClientConfig#defaultReadTimeout}) on 2026-09-28 — a shorter 60s value
     *  (tried alongside the GV suite's parallel="tests" experiment, to free a stuck session's slot
     *  faster under concurrent load) was suspected of cutting off session-creation/commands that
     *  were merely slow under concurrent staging-server load rather than genuinely stuck (run
     *  20260928_134950's "Could not start a new session ... request timed out" skips). Only worth
     *  shortening again alongside a proven-safe concurrent config — see execution.properties'
     *  webdriver.command.readtimeout.seconds for the current rationale. */
    @Key("webdriver.command.readtimeout.seconds")
    @DefaultValue("180")
    int webdriverCommandReadTimeoutSeconds();
}
