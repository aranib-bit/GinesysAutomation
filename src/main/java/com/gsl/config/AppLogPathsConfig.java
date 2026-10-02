package com.gsl.config;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Key;
import org.aeonbits.owner.Config.DefaultValue;

/**
 * File-path lookup for the app-server log files that {@code com.gsl.diagnostics}/remote-log
 * tooling reads to correlate a UI/API failure with server-side output. Each accessor resolves
 * one log file's path from {@code app-log-paths.properties}; blank ({@code ""}) means that log
 * is not configured for the current environment and callers should skip it rather than fail.
 */
@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({
        "system:properties",
        "file:${user.dir}/src/test/resources/env/${env}/app-log-paths.properties",
        "file:${user.dir}/src/test/resources/config/app-log-paths.properties"
})
public interface AppLogPathsConfig extends Config {
    /** Path to the Finance API's error log. */
    @Key("finance.api.error")    @DefaultValue("") String financeApiError();
    /** Path to the Finance API's info log. */
    @Key("finance.api.info")     @DefaultValue("") String financeApiInfo();
    /** Path to the Finance API's Entity Framework logger output. */
    @Key("finance.api.eflogger") @DefaultValue("") String financeApiEfLogger();
    /** Path to the WEBAPI error log. */
    @Key("webapi.error")         @DefaultValue("") String webapiError();
    /** Path to the WEBAPI info log. */
    @Key("webapi.info")          @DefaultValue("") String webapiInfo();
    /** Path to the WEBAPI debug log. */
    @Key("webapi.debug")         @DefaultValue("") String webapiDebug();
    /** Path to the WEBAPI's NHibernate (SQL/ORM) log. */
    @Key("webapi.nhibernate")    @DefaultValue("") String webapiNhibernate();
    /** Path to the web UI's trace log. */
    @Key("webui.trace")          @DefaultValue("") String webuiTrace();
}
