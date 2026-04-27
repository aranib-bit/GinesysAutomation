package com.gsl.config;

import org.aeonbits.owner.Config;

@Config.Sources(value = "file:${user.dir}/src/test/resources/config/config.properties")
public interface FrameworkConfig extends Config {
    long timeout();

    String url();

    String browser();

    String runmode();

    String remoteurl();

    long itemscanmaxtimeout();

    /** EXCEL | DB — selects which DataSource implementation loads AR Voucher test data. */
    @Key("testdata.source")
    @DefaultValue("EXCEL")
    String testDataSource();

    /** App login username used in DB mode (must match hrdemp.abbre in the database). */
    @Key("appusername")
    String username();

    /** App login password used in DB mode. */
    @Key("apppassword")
    String password();

    /** Tolerance in seconds for finvchmain.creation_dt vs UI save time. */
    @Key("db.creation.tolerance")
    @DefaultValue("60")
    long dbCreationTolerance();

    /** Max wait for ExtJS synchronization (seconds). Reduce for fast networks. */
    @Key("extjs.timeout")
    @DefaultValue("40")
    long extjsTimeout();

    /** Floor for ExtJS timeout — effective timeout is always max(extjs.min.timeout, extjs.timeout). */
    @Key("extjs.min.timeout")
    @DefaultValue("10")
    long extjsMinTimeout();

    /** Max retries for the non-critical warning-message text read in verifyWarningMessage().
     *  Each attempt uses a 3-second timeout; default is 2. Configurable in config.properties. */
    @Key("warning.msg.max.retries")
    @DefaultValue("2")
    int warningMsgMaxRetries();
}
