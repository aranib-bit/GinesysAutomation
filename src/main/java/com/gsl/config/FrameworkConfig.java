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
