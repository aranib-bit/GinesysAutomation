package com.gsl.config;

import org.aeonbits.owner.Config;

/**
 * Core framework settings read from {@code config.properties} — application URL, environment
 * name, DB-mode login credentials, global timeouts, and the handful of app-wide behavioral
 * defaults ({@link #testDataSource()}, {@link #gridMultiSortMaxColumns()}) that don't belong in
 * a more specific config interface. Obtained via {@link ConfigFactory#getConfig()}; {@code
 * com.tests.BaseTest} holds one shared instance for all test subclasses.
 */
@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({
        "system:properties",
        "file:${user.dir}/src/test/resources/env/${env}/secrets.local.properties",
        "file:${user.dir}/src/test/resources/env/${env}/config.properties",
        "file:${user.dir}/src/test/resources/config/config.properties"
})
public interface FrameworkConfig extends Config {

    /** Default explicit-wait timeout (seconds) used across {@code WaitUtils}' expected-condition waits. */
    long timeout();

    /** Base URL of the application under test. */
    String url();

    /** Environment name (e.g. local/staging/prod), used for report metadata and environment-specific branching. */
    @DefaultValue("local")
    String env();

    /** Max time (seconds) allowed for an item-scan/lookup operation before timing out. */
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

    /** Maximum number of columns this app's ExtJS grids allow in a multi-column sort at once —
     *  confirmed live (2026-08-28) that clicking a new column beyond this cap changes the
     *  existing sort ordering. Application-wide default; a module whose grid supports a
     *  different limit should pass its own value directly to
     *  {@code GridColumnMenu.sortByPriority(List, int)} rather than expecting an override here —
     *  see that method's Javadoc. */
    @Key("grid.multisort.max.columns")
    @DefaultValue("3")
    int gridMultiSortMaxColumns();
}
