package com.gsl.config;


import org.aeonbits.owner.Config;

@Config.Sources({
        "file:${user.dir}/src/test/resources/config/databaseconfig.properties"
})

public interface DataBaseConfig extends Config{
    @Key("databasetype")
    String databaseType();
    @Key("databasename")
    String databasename();
    @Key("databasehost")
    String databaseHost();
    @Key("databaseusername")
    String databaseUsername();
    @Key("databasepassword")
    String databasePassword();
    @Key("databaseport")
    String databasePort();

    @Key("MaximumPoolSize")
    Integer MaximumPoolSize();
    @Key("MinimumIdle")
    Integer MinimumIdle();
    @Key("IdleTimeout")
    Integer IdleTimeout();
    @Key("ConnectionTimeout")
    Integer ConnectionTimeout();
    @Key("MaxLifetime")
    Integer MaxLifetime();
    @Key("LeakDetectionThreashold")
    Integer LeakDetectionThreashold();
    @Key("PoolName")
    String PoolName();


}
