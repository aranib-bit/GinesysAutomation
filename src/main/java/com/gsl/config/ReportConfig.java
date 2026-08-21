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

    /** BCP-47 language tag controlling amount digit-grouping in reports — resolved to a
     *  {@link java.util.Locale} by {@code VoucherReportUtils.fmtAmount} (deliberately NOT a
     *  default method here: Owner's dynamic proxy routes every call, default methods included,
     *  through its own property-resolution InvocationHandler rather than running the real
     *  default-method body, resolving an unmapped method name to {@code null} — see
     *  {@code VoucherReportUtils.fmtAmount}'s Javadoc). Default {@code en-IN} (Indian 2-3
     *  grouping, e.g. 12,34,567.89); set to {@code en-US} for Western 3-3-3 grouping
     *  (12,345,678.90), {@code ar-AE} for Dubai/UAE-style, or any other valid tag. */
    @Key("report.amount.numbering.locale")
    @DefaultValue("en-IN")
    String amountNumberingLocaleTag();

    /** Plan §11 — gates the "Full Validation Details" full-column XLSX export/link.
     *  {@code false} by default: opt-in, not opt-out, since this is a new feature and the
     *  concern raised was specifically about unconstrained file-count growth across a large
     *  suite run, not per-file size (a single export is only tens of KB — see
     *  {@code XlsxWriter}'s Javadoc). Set {@code report.export.fullValidationXlsx.enabled=true}
     *  in this same properties file to opt in. */
    @Key("report.export.fullValidationXlsx.enabled")
    @DefaultValue("false")
    boolean fullValidationXlsxEnabled();
}
