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
