package com.gsl.config;


import org.aeonbits.owner.Config;

/**
 * Connection details and HikariCP pool tuning for the framework's test-data/validation database,
 * read from {@code databaseconfig.properties}. Backs {@code SessionPool}/JDBI wiring — see the
 * {@code com.gsl.database} package for where these values are consumed to build the pooled
 * datasource.
 */
@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({
        "system:properties",
        "file:${user.dir}/src/test/resources/env/${env}/secrets.local.properties",
        "file:${user.dir}/src/test/resources/env/${env}/databaseconfig.properties",
        "file:${user.dir}/src/test/resources/config/databaseconfig.properties"
})
public interface DataBaseConfig extends Config{
    /** JDBC vendor discriminator (e.g. postgresql/oracle/sqlserver) used to pick the driver/URL format. */
    @Key("databasetype")
    String databaseType();
    /** Database/schema name to connect to. */
    @Key("databasename")
    String databasename();
    /** Database server hostname. */
    @Key("databasehost")
    String databaseHost();
    /** Database login username. */
    @Key("databaseusername")
    String databaseUsername();
    /** Database login password. */
    @Key("databasepassword")
    String databasePassword();
    /** Database server port. */
    @Key("databaseport")
    String databasePort();

    /** HikariCP maximum pool size. */
    @Key("MaximumPoolSize")
    Integer MaximumPoolSize();
    /** HikariCP minimum idle connection count. */
    @Key("MinimumIdle")
    Integer MinimumIdle();
    /** HikariCP idle timeout, in milliseconds. */
    @Key("IdleTimeout")
    Integer IdleTimeout();
    /** HikariCP connection-acquisition timeout, in milliseconds. */
    @Key("ConnectionTimeout")
    Integer ConnectionTimeout();
    /** HikariCP max connection lifetime, in milliseconds. */
    @Key("MaxLifetime")
    Integer MaxLifetime();
    /** HikariCP leak-detection threshold, in milliseconds (0 disables leak detection). */
    @Key("LeakDetectionThreashold")
    Integer LeakDetectionThreashold();
    /** HikariCP pool name, used to distinguish this pool in logs/JMX. */
    @Key("PoolName")
    String PoolName();


}
