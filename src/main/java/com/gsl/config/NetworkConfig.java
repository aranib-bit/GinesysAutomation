package com.gsl.config;

import org.aeonbits.owner.Config;

/**
 * Controls for CDP network-traffic capture and browser (JS console) log capture, read from
 * {@code network.properties}. Consumed by the network/browser-log diagnostics under
 * {@code com.gsl.diagnostics} and {@code com.gsl.driver} to decide what to record and how much
 * of it to keep, without recompiling — most capture is opt-in or failure-only by default to
 * keep normal runs lightweight.
 */
@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({
        "system:properties",
        "file:${user.dir}/src/test/resources/env/${env}/network.properties",
        "file:${user.dir}/src/test/resources/config/network.properties"
})
public interface NetworkConfig extends Config {

    /** Master switch for CDP network-log capture. */
    @Key("enable.network.logs")
    @DefaultValue("false")
    boolean enableNetworkLogs();

    /** When true, only failed (non-2xx/redirect) network requests are retained. */
    @Key("network.capture.failed.only")
    @DefaultValue("false")
    boolean networkCaptureFailedOnly();

    /** always/failure/never — when per-request timing is logged. */
    @Key("network.timing.log.on")
    @DefaultValue("always")
    String networkTimingLogOn();

    /** always/failure/never — when request cookies are logged. */
    @Key("network.cookies.log.on")
    @DefaultValue("failure")
    String networkCookiesLogOn();

    /** always/failure/never — when the initiating call stack is logged for a request. */
    @Key("network.callstack.log.on")
    @DefaultValue("failure")
    String networkCallstackLogOn();

    /** Max number of network request/response snapshots retained per test. */
    @Key("network.capture.max.snapshots")
    @DefaultValue("150")
    int networkCaptureMaxSnapshots();

    /** Whether request/response headers are captured. */
    @Key("network.capture.headers")
    @DefaultValue("true")
    boolean networkCaptureHeaders();

    /** Whether request/response bodies are captured. */
    @Key("network.capture.body")
    @DefaultValue("false")
    boolean networkCaptureBody();

    /** Substring/regex filter restricting which request URLs are captured; blank = capture all. */
    @Key("network.capture.url.filter")
    @DefaultValue("")
    String networkCaptureUrlFilter();

    /** Whether a passing test's report still shows the full captured network list (not just failures). */
    @Key("network.capture.pass.show.all")
    @DefaultValue("false")
    boolean networkCapturePassShowAll();

    /** Master switch for browser (JS console) log capture. */
    @Key("browser.logs.enabled")
    @DefaultValue("false")
    boolean browserLogsEnabled();

    /** Whether browser logs are captured only when the test fails. */
    @Key("browser.logs.capture.on.failure")
    @DefaultValue("true")
    boolean browserLogsCaptureOnFailure();

    /** Whether any JS error in the browser console fails the test. */
    @Key("browser.logs.fail.on.js.error")
    @DefaultValue("false")
    boolean browserLogsFailOnJsError();

    /** Whether a JS error matching a "critical" pattern fails the test even if {@link #browserLogsFailOnJsError()} is off. */
    @Key("browser.logs.fail.on.critical.js.error")
    @DefaultValue("true")
    boolean browserLogsFailOnCriticalJsError();

    /** When true, browser logs are only captured (not used to fail the test), overriding the fail-on-error flags. */
    @Key("browser.logs.capture.only")
    @DefaultValue("false")
    boolean browserLogsCaptureOnly();

    /** Comma-separated patterns that downgrade a matching console entry to a warning instead of a failure. */
    @Key("browser.logs.warning.patterns")
    @DefaultValue("")
    String browserLogsWarningPatterns();

    /** Comma-separated patterns for console entries to ignore entirely. */
    @Key("browser.logs.ignore.patterns")
    @DefaultValue("")
    String browserLogsIgnorePatterns();

    /** Minimum browser console log level captured (e.g. SEVERE, WARNING). */
    @Key("browser.logs.level")
    @DefaultValue("SEVERE")
    String browserLogsLevel();
}
