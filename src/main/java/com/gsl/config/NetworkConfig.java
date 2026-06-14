package com.gsl.config;

import org.aeonbits.owner.Config;

@Config.Sources("file:${user.dir}/src/test/resources/config/network.properties")
public interface NetworkConfig extends Config {

    @Key("enable.network.logs")
    @DefaultValue("false")
    boolean enableNetworkLogs();

    @Key("network.capture.failed.only")
    @DefaultValue("false")
    boolean networkCaptureFailedOnly();

    @Key("network.timing.log.on")
    @DefaultValue("always")
    String networkTimingLogOn();

    @Key("network.cookies.log.on")
    @DefaultValue("failure")
    String networkCookiesLogOn();

    @Key("network.callstack.log.on")
    @DefaultValue("failure")
    String networkCallstackLogOn();

    @Key("network.capture.max.snapshots")
    @DefaultValue("150")
    int networkCaptureMaxSnapshots();

    @Key("network.capture.headers")
    @DefaultValue("true")
    boolean networkCaptureHeaders();

    @Key("network.capture.body")
    @DefaultValue("false")
    boolean networkCaptureBody();

    @Key("network.capture.url.filter")
    @DefaultValue("")
    String networkCaptureUrlFilter();

    @Key("network.capture.pass.show.all")
    @DefaultValue("false")
    boolean networkCapturePassShowAll();

    @Key("browser.logs.enabled")
    @DefaultValue("false")
    boolean browserLogsEnabled();

    @Key("browser.logs.capture.on.failure")
    @DefaultValue("true")
    boolean browserLogsCaptureOnFailure();

    @Key("browser.logs.fail.on.js.error")
    @DefaultValue("false")
    boolean browserLogsFailOnJsError();

    @Key("browser.logs.fail.on.critical.js.error")
    @DefaultValue("true")
    boolean browserLogsFailOnCriticalJsError();

    @Key("browser.logs.capture.only")
    @DefaultValue("false")
    boolean browserLogsCaptureOnly();

    @Key("browser.logs.warning.patterns")
    @DefaultValue("")
    String browserLogsWarningPatterns();

    @Key("browser.logs.ignore.patterns")
    @DefaultValue("")
    String browserLogsIgnorePatterns();

    @Key("browser.logs.level")
    @DefaultValue("SEVERE")
    String browserLogsLevel();
}
