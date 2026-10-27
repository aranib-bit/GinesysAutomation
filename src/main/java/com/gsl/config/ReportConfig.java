package com.gsl.config;

import org.aeonbits.owner.Config;

/**
 * Extent report/output behavior — flush batching, screenshot retention, performance-timing and
 * JS-error export toggles, amount-formatting locale, and the "Full Validation Details"/debug-log
 * visibility gates. Read from {@code report.properties}, merged with {@code system:properties}
 * so any key can be overridden per run with {@code -Dkey=value}. Consumed by {@code
 * com.gsl.reports} and {@code com.gsl.listeners.TestListener}.
 */
@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({
        "system:properties",
        "file:${user.dir}/src/test/resources/env/${env}/report.properties",
        "file:${user.dir}/src/test/resources/config/report.properties"
})
public interface ReportConfig extends Config {

    /** Whether Extent report writes are batched (flushed in groups) rather than flushed after every log call. */
    @Key("report.flush.batch.enabled")
    @DefaultValue("false")
    boolean flushBatchEnabled();

    /** Number of log entries accumulated before a batched flush, when {@link #flushBatchEnabled()} is true. */
    @Key("report.flush.batch.size")
    @DefaultValue("100")
    int flushBatchSize();

    /** Whether browser performance-timing metrics are captured and reported. */
    @Key("performance.timing.enabled")
    @DefaultValue("true")
    boolean performanceTimingEnabled();

    /** Whether captured JS errors are also exported as a JSON file alongside the report. */
    @Key("js.error.json.export.enabled")
    @DefaultValue("true")
    boolean jsErrorJsonExportEnabled();

    /** Number of days screenshots are retained before cleanup. */
    @Key("screenshot.retention.days")
    @DefaultValue("3")
    int screenshotRetentionDays();

    /** Max number of failure screenshots ATTACHED to the report per test when
     *  {@link #screenshotsMode()} is {@code ALL} (the most recent N are kept; 0 = unlimited).
     *  Ignored in {@code LAST} mode, which always attaches exactly one. */
    @Key("screenshot.max.per.test")
    @DefaultValue("3")
    int screenshotMaxPerTest();

    /** Which failure screenshots reach the Extent report: {@code LAST} (default) attaches only the
     *  final one per test — a multi-failure test no longer embeds a screenshot per failing step,
     *  which shrinks the report significantly — while {@code ALL} attaches every one (capped by
     *  {@link #screenshotMaxPerTest()}). Every screenshot is still captured while the test runs;
     *  those not attached are deleted from disk at test end. Per-run override:
     *  {@code -Dreport.screenshots.mode=ALL}. */
    @Key("report.screenshots.mode")
    @DefaultValue("LAST")
    String screenshotsMode();

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

    /** Gates visibility of "debug" {@link com.gsl.enums.LogCategory} entries (see
     *  {@link com.gsl.enums.LogCategory#isDebugCategory()}) in the Extent HTML report only — file
     *  logging via {@code ReportLogger#loggerFor(LogCategory)} is never affected by this flag.
     *  {@code false} by default: a business-user-facing report shows only business-centric
     *  categories (BUSINESS/VALIDATION/MODULE/SUB_MODULE/MENU/SUB_MENU/NAVIGATION/FRAMEWORK/
     *  TEST_STEP_STATUS — "did this succeed or fail", not "which Selenium call did that") until
     *  {@code report.debugLogs.extentVisible=true} is set for a debugging run. */
    @Key("report.debugLogs.extentVisible")
    @DefaultValue("false")
    boolean debugLogsExtentVisible();

    /** Gates whether {@code SnapshotValidationReporter}'s "Full Validation Details" table
     *  suppresses genuinely-unchanged {@code MUST_NOT_CHANGE} columns (Before == After — zero
     *  diagnostic value in the normal case). {@code true} by default: preserves the existing
     *  noise-reduction behavior people already rely on (opt-out, not opt-in). Set
     *  {@code report.snapshotValidation.suppressUnchanged.enabled=false} for a debugging run that
     *  wants to see literally every column checked, including no-op ones.
     *  <p>Override for a single run without editing this file (this interface now merges
     *  {@code system:properties} ahead of the file source):
     *  {@code mvn clean test -Dreport.snapshotValidation.suppressUnchanged.enabled=false} */
    @Key("report.snapshotValidation.suppressUnchanged.enabled")
    @DefaultValue("true")
    boolean suppressUnchangedColumnsEnabled();

    /** yes | no — full UI validation detail in the report; no = only failures and known issues (validation still runs). */
    @Key("report.validation.ui.enabled")
    @DefaultValue("yes")
    String validationUiDetail();

    /** yes | no — full API validation detail in the report; no = only failures. */
    @Key("report.validation.api.enabled")
    @DefaultValue("yes")
    String validationApiDetail();

    /** yes | no — full DB validation detail in the report; no = only failures and known issues. */
    @Key("report.validation.db.enabled")
    @DefaultValue("yes")
    String validationDbDetail();
}
