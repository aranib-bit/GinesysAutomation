package com.gsl.config;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Key;
import org.aeonbits.owner.Config.DefaultValue;

@Config.Sources({"file:${user.dir}/src/test/resources/config/app-log-paths.properties"})
public interface AppLogPathsConfig extends Config {
    @Key("finance.api.error")    @DefaultValue("") String financeApiError();
    @Key("finance.api.info")     @DefaultValue("") String financeApiInfo();
    @Key("finance.api.eflogger") @DefaultValue("") String financeApiEfLogger();
    @Key("webapi.error")         @DefaultValue("") String webapiError();
    @Key("webapi.info")          @DefaultValue("") String webapiInfo();
    @Key("webapi.debug")         @DefaultValue("") String webapiDebug();
    @Key("webapi.nhibernate")    @DefaultValue("") String webapiNhibernate();
    @Key("webui.trace")          @DefaultValue("") String webuiTrace();
}
