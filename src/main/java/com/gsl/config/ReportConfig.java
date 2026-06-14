package com.gsl.config;

import org.aeonbits.owner.Config;

@Config.Sources("file:${user.dir}/src/test/resources/config/report.properties")
public interface ReportConfig extends Config {

    @Key("report.flush.batch.enabled")
    @DefaultValue("true")
    boolean flushBatchEnabled();

    @Key("report.flush.batch.size")
    @DefaultValue("100")
    int flushBatchSize();

    @Key("performance.timing.enabled")
    @DefaultValue("true")
    boolean performanceTimingEnabled();

    @Key("js.error.json.export.enabled")
    @DefaultValue("true")
    boolean jsErrorJsonExportEnabled();

    @Key("screenshot.retention.days")
    @DefaultValue("3")
    int screenshotRetentionDays();

    @Key("screenshot.max.per.test")
    @DefaultValue("3")
    int screenshotMaxPerTest();
}
