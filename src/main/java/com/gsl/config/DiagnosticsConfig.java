package com.gsl.config;

import org.aeonbits.owner.Config;

@Config.Sources("file:${user.dir}/src/test/resources/config/diagnostics.properties")
public interface DiagnosticsConfig extends Config {

    // ─── HTML capture ─────────────────────────────────────────────────────────

    @Key("diagnostics.html.capture.on.element.failure")
    @DefaultValue("true")
    boolean diagnosticsHtmlCaptureOnElementFailure();

    // ─── App server utilization ───────────────────────────────────────────────

    @Key("diagnostics.app.server.util.enabled")
    @DefaultValue("false")
    boolean diagnosticsAppServerUtilEnabled();

    @Key("diagnostics.app.server.type")
    @DefaultValue("WINDOWS")
    String diagnosticsAppServerType();

    @Key("diagnostics.app.server.host")
    @DefaultValue("localhost")
    String diagnosticsAppServerHost();

    // ─── DB server utilization ────────────────────────────────────────────────

    @Key("diagnostics.db.server.util.enabled")
    @DefaultValue("false")
    boolean diagnosticsDbServerUtilEnabled();

    @Key("diagnostics.db.server.type")
    @DefaultValue("WINDOWS")
    String diagnosticsDbServerType();

    @Key("diagnostics.db.server.host")
    @DefaultValue("localhost")
    String diagnosticsDbServerHost();

    // ─── Event log ────────────────────────────────────────────────────────────

    @Key("diagnostics.event.log.enabled")
    @DefaultValue("false")
    boolean diagnosticsEventLogEnabled();

    @Key("diagnostics.event.log.lines")
    @DefaultValue("30")
    int diagnosticsEventLogLines();

    // ─── Driver capabilities ──────────────────────────────────────────────────

    @Key("diagnostics.driver.capabilities.enabled")
    @DefaultValue("true")
    boolean diagnosticsDriverCapabilitiesEnabled();

    // ─── Action Trail ─────────────────────────────────────────────────────────

    @Key("action.trail.capture.mode")
    @DefaultValue("ALL")
    String actionTrailCaptureMode();

    @Key("action.trail.buffer.size")
    @DefaultValue("200")
    int actionTrailBufferSize();

    @Key("action.trail.display.limit")
    @DefaultValue("50")
    int actionTrailDisplayLimit();

    // ─── UI Components ────────────────────────────────────────────────────────

    @Key("ui.components.capture.mode")
    @DefaultValue("ALL")
    String uiComponentsCaptureMode();

    @Key("ui.diagnostics.enabled")
    @DefaultValue("true")
    boolean uiDiagnosticsEnabled();

    // ─── Active popup ─────────────────────────────────────────────────────────

    @Key("diagnostics.active.popup.enabled")
    @DefaultValue("true")
    boolean diagnosticsActivePopupEnabled();

    @Key("diagnostics.popup.selector")
    @DefaultValue(".x-window-body")
    String diagnosticsPopupSelector();

    // ─── DB state snapshot ────────────────────────────────────────────────────

    @Key("diagnostics.db.state.enabled")
    @DefaultValue("false")
    boolean diagnosticsDbStateEnabled();

    @Key("diagnostics.db.state.query")
    @DefaultValue("SELECT vchcode,vchdate,slcode,amount FROM finvchmain ORDER BY creation_dt DESC LIMIT 5")
    String diagnosticsDbStateQuery();

    // ─── Thread dump ──────────────────────────────────────────────────────────

    @Key("diagnostics.thread.dump.enabled")
    @DefaultValue("false")
    boolean diagnosticsThreadDumpEnabled();

    // ─── Step Timeline ────────────────────────────────────────────────────────

    @Key("step.timeline.enabled")
    @DefaultValue("true")
    boolean stepTimelineEnabled();

    @Key("step.timeline.capture.mode")
    @DefaultValue("ALL")
    String stepTimelineCaptureMode();

    @Key("step.timeline.warn.ms")
    @DefaultValue("3000")
    long stepTimelineWarnMs();

    // ─── Wait Analysis ────────────────────────────────────────────────────────

    @Key("wait.analysis.enabled")
    @DefaultValue("true")
    boolean waitAnalysisEnabled();

    @Key("wait.analysis.capture.mode")
    @DefaultValue("ALL")
    String waitAnalysisCaptureMode();

    @Key("wait.analysis.slow.threshold.ms")
    @DefaultValue("5000")
    long waitAnalysisSlowThresholdMs();

    @Key("wait.analysis.min.test.duration.seconds")
    @DefaultValue("0")
    int waitAnalysisMinTestDurationSeconds();

    // ─── Flaky Analytics ──────────────────────────────────────────────────────

    @Key("flaky.summary.enabled")
    @DefaultValue("true")
    boolean flakySummaryEnabled();

    @Key("flaky.score.warn.threshold")
    @DefaultValue("50")
    int flakyScoreWarnThreshold();

    // ─── Custom Metrics ───────────────────────────────────────────────────────

    @Key("custom.metrics.enabled")
    @DefaultValue("false")
    boolean customMetricsEnabled();

    @Key("custom.metrics.capture.mode")
    @DefaultValue("FAIL")
    String customMetricsCaptureMode();

    @Key("custom.metrics.slow.resource.threshold.ms")
    @DefaultValue("2000")
    long customMetricsSlowResourceThresholdMs();

    @Key("custom.metrics.user.timing.warn.ms")
    @DefaultValue("3000")
    long customMetricsUserTimingWarnMs();

    @Key("custom.metrics.fail.on.long.task.count")
    @DefaultValue("0")
    int customMetricsFailOnLongTaskCount();

    @Key("custom.metrics.long.animation.frame.enabled")
    @DefaultValue("true")
    boolean customMetricsLongAnimationFrameEnabled();

    @Key("custom.metrics.server.timing.enabled")
    @DefaultValue("true")
    boolean customMetricsServerTimingEnabled();

    // ─── Frontend Diagnostics ─────────────────────────────────────────────────

    @Key("frontend.diagnostics.enabled")
    @DefaultValue("false")
    boolean frontendDiagnosticsEnabled();

    @Key("frontend.diagnostics.capture.mode")
    @DefaultValue("FAIL")
    String frontendDiagnosticsCaptureMode();

    @Key("frontend.diagnostics.component.render.warn.ms")
    @DefaultValue("200")
    long frontendDiagnosticsComponentRenderWarnMs();

    @Key("frontend.diagnostics.dom.tracking.enabled")
    @DefaultValue("true")
    boolean frontendDomTrackingEnabled();

    @Key("frontend.diagnostics.memory.tracking.enabled")
    @DefaultValue("true")
    boolean frontendMemoryTrackingEnabled();

    @Key("frontend.diagnostics.interaction.tracking.enabled")
    @DefaultValue("true")
    boolean frontendInteractionTrackingEnabled();

    @Key("frontend.diagnostics.grid.render.tracking.enabled")
    @DefaultValue("true")
    boolean frontendGridRenderTrackingEnabled();

    @Key("frontend.diagnostics.reflow.detection.enabled")
    @DefaultValue("true")
    boolean frontendReflowDetectionEnabled();
}
