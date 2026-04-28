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

    /** Buffer sleep (ms) after resetGridScrollToTop() before the loader wait. */
    @Key("selectdoc.grid.load.buffer.ms")
    @DefaultValue("200")
    int selectDocGridLoadBufferMs();

    /** Render wait (ms) after jumpGridToDocument() scrolls to the target row. */
    @Key("selectdoc.jump.render.ms")
    @DefaultValue("500")
    int selectDocJumpRenderMs();

    /** Max proportional scroll attempts in the scroll-poll loop. */
    @Key("selectdoc.scroll.max.attempts")
    @DefaultValue("20")
    int selectDocScrollMaxAttempts();

    /** Minimum allowed scroll attempts — effective attempts = max(min, configured). */
    @Key("selectdoc.scroll.min.attempts")
    @DefaultValue("5")
    int selectDocScrollMinAttempts();

    /** Sleep (ms) between each proportional scroll attempt. */
    @Key("selectdoc.scroll.step.wait.ms")
    @DefaultValue("400")
    int selectDocScrollStepWaitMs();

    /** Render wait (ms) after focusRow() — needs to be longer for large (7000+) grids. */
    @Key("selectdoc.focusrow.render.ms")
    @DefaultValue("1500")
    int selectDocFocusRowRenderMs();

    /** Buffer sleep (ms) before the checkbox click after the row becomes visible. */
    @Key("selectdoc.click.buffer.ms")
    @DefaultValue("300")
    int selectDocClickBufferMs();

    /** Max retry attempts for the loader-disappear wait in AdjustmentEntry.selectDocument().
     *  Each attempt uses extjsTimeout() seconds; default 3 gives up to 3x40=120s total. */
    @Key("selectdoc.loader.max.retries")
    @DefaultValue("3")
    int selectDocLoaderMaxRetries();
}
