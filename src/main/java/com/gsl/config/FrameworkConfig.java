package com.gsl.config;

import org.aeonbits.owner.Config;

@Config.Sources(value = "file:${user.dir}/src/test/resources/config/config.properties")
public interface FrameworkConfig extends Config {

    long timeout();

    String url();

    @DefaultValue("local")
    String env();

    /** Raw browser name string — used by TestContextManager and report metadata. */
    String browser();

    long itemscanmaxtimeout();

    /** EXCEL | DB — selects which DataSource implementation loads test data. */
    @Key("testdata.source")
    @DefaultValue("EXCEL")
    String testDataSource();

    /** App login username used in DB mode (must match hrdemp.abbre in the database). */
    @Key("appusername")
    String username();

    /** App login password used in DB mode. */
    @Key("apppassword")
    String password();
}
