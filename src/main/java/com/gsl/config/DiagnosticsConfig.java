package com.gsl.config;

import org.aeonbits.owner.Config;

/**
 * Master on/off switches and tuning knobs for the {@code com.gsl.diagnostics} package's
 * failure-forensics capture — HTML snapshots, server/DB utilization sampling, action trails,
 * step timelines, wait analysis, flaky-test scoring, custom browser metrics, and frontend
 * diagnostics. Every feature here is designed to be cheap when disabled and rich when a test
 * fails, so most flags default to capturing on failure only (see each key's
 * {@code *CaptureMode}) rather than on every test.
 */
@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({
        "system:properties",
        "file:${user.dir}/src/test/resources/env/${env}/diagnostics.properties",
        "file:${user.dir}/src/test/resources/config/diagnostics.properties"
})
public interface DiagnosticsConfig extends Config {

    // ─── HTML capture ─────────────────────────────────────────────────────────

    /** Whether to capture the page's HTML source when a single element interaction fails. */
    @Key("diagnostics.html.capture.on.element.failure")
    @DefaultValue("true")
    boolean diagnosticsHtmlCaptureOnElementFailure();

    // ─── Page-state snapshot at a failing step ────────────────────────────────

    /** Whether a failing step also records a compact summary of the LIVE page state (visible masks,
     *  windows/dialogs, open dropdown lists, cell editors, login error text, in-flight Ajax). This is
     *  how an intermittent failure (login that never completes, a picker that returns nothing, an
     *  editor that never opens) is explained — instead of lengthening any timeout. */
    @Key("diagnostics.page.state.enabled")
    @DefaultValue("true")
    boolean diagnosticsPageStateEnabled();

    /** Max rows listed per kind of element in the page-state summary (masks, windows, list items...). */
    @Key("diagnostics.page.state.max.items")
    @DefaultValue("8")
    int diagnosticsPageStateMaxItems();

    /** Max characters of text shown per element in the page-state summary. */
    @Key("diagnostics.page.state.max.text.chars")
    @DefaultValue("160")
    int diagnosticsPageStateMaxTextChars();

    // ─── App server utilization ───────────────────────────────────────────────

    /** Whether to sample the app server's CPU/memory utilization on failure. */
    @Key("diagnostics.app.server.util.enabled")
    @DefaultValue("false")
    boolean diagnosticsAppServerUtilEnabled();

    /** OS type of the app server, selecting which utilization-sampling command is run. */
    @Key("diagnostics.app.server.type")
    @DefaultValue("WINDOWS")
    String diagnosticsAppServerType();

    /** Hostname of the app server to sample utilization from. */
    @Key("diagnostics.app.server.host")
    @DefaultValue("localhost")
    String diagnosticsAppServerHost();

    // ─── DB server utilization ────────────────────────────────────────────────

    /** Whether to sample the DB server's CPU/memory utilization on failure. */
    @Key("diagnostics.db.server.util.enabled")
    @DefaultValue("false")
    boolean diagnosticsDbServerUtilEnabled();

    /** OS type of the DB server, selecting which utilization-sampling command is run. */
    @Key("diagnostics.db.server.type")
    @DefaultValue("WINDOWS")
    String diagnosticsDbServerType();

    /** Hostname of the DB server to sample utilization from. */
    @Key("diagnostics.db.server.host")
    @DefaultValue("localhost")
    String diagnosticsDbServerHost();

    // ─── Event log ────────────────────────────────────────────────────────────

    /** Whether to capture recent OS event-log entries on failure. */
    @Key("diagnostics.event.log.enabled")
    @DefaultValue("false")
    boolean diagnosticsEventLogEnabled();

    /** Number of trailing event-log lines to capture. */
    @Key("diagnostics.event.log.lines")
    @DefaultValue("30")
    int diagnosticsEventLogLines();

    // ─── Driver capabilities ──────────────────────────────────────────────────

    /** Whether to log the WebDriver session's negotiated capabilities. */
    @Key("diagnostics.driver.capabilities.enabled")
    @DefaultValue("true")
    boolean diagnosticsDriverCapabilitiesEnabled();

    // ─── Action Trail ─────────────────────────────────────────────────────────

    /** ALL/FAIL/etc. — when the per-test action trail (chronological UI-action log) is captured. */
    @Key("action.trail.capture.mode")
    @DefaultValue("ALL")
    String actionTrailCaptureMode();

    /** Max number of actions retained per test in the in-memory action-trail buffer. */
    @Key("action.trail.buffer.size")
    @DefaultValue("200")
    int actionTrailBufferSize();

    /** Max number of action-trail entries shown in the report. */
    @Key("action.trail.display.limit")
    @DefaultValue("50")
    int actionTrailDisplayLimit();

    // ─── UI Components ────────────────────────────────────────────────────────

    /** ALL/FAIL/etc. — when UI-component diagnostics are captured. */
    @Key("ui.components.capture.mode")
    @DefaultValue("ALL")
    String uiComponentsCaptureMode();

    /** Master switch for UI-component diagnostics capture. */
    @Key("ui.diagnostics.enabled")
    @DefaultValue("true")
    boolean uiDiagnosticsEnabled();

    // ─── Active popup ─────────────────────────────────────────────────────────

    /** Whether to detect and report the currently-active popup/dialog on failure. */
    @Key("diagnostics.active.popup.enabled")
    @DefaultValue("true")
    boolean diagnosticsActivePopupEnabled();

    /** CSS selector used to locate the active popup/dialog body for diagnostics capture. */
    @Key("diagnostics.popup.selector")
    @DefaultValue(".x-window-body")
    String diagnosticsPopupSelector();

    // ─── DB state snapshot ────────────────────────────────────────────────────

    /** Whether to run {@link #diagnosticsDbStateQuery()} and attach its result on failure. */
    @Key("diagnostics.db.state.enabled")
    @DefaultValue("false")
    boolean diagnosticsDbStateEnabled();

    /** SQL query run to snapshot recent DB state when a failure is diagnosed. */
    @Key("diagnostics.db.state.query")
    @DefaultValue("SELECT vchcode,vchdate,slcode,amount FROM finvchmain ORDER BY creation_dt DESC LIMIT 5")
    String diagnosticsDbStateQuery();

    // ─── Thread dump ──────────────────────────────────────────────────────────

    /** Whether to capture a JVM thread dump on failure. */
    @Key("diagnostics.thread.dump.enabled")
    @DefaultValue("false")
    boolean diagnosticsThreadDumpEnabled();

    // ─── Step Timeline ────────────────────────────────────────────────────────

    /** Master switch for the per-test step timeline (chronological duration breakdown). */
    @Key("step.timeline.enabled")
    @DefaultValue("true")
    boolean stepTimelineEnabled();

    /** ALL/FAIL/etc. — when the step timeline is captured. */
    @Key("step.timeline.capture.mode")
    @DefaultValue("ALL")
    String stepTimelineCaptureMode();

    /** Duration (ms) above which a single step is flagged as slow in the timeline. */
    @Key("step.timeline.warn.ms")
    @DefaultValue("3000")
    long stepTimelineWarnMs();

    // ─── Wait Analysis ────────────────────────────────────────────────────────

    /** Master switch for wait-analysis capture (aggregated explicit-wait durations per test). */
    @Key("wait.analysis.enabled")
    @DefaultValue("true")
    boolean waitAnalysisEnabled();

    /** ALL/FAIL/etc. — when wait analysis is captured. */
    @Key("wait.analysis.capture.mode")
    @DefaultValue("ALL")
    String waitAnalysisCaptureMode();

    /** Duration (ms) above which a single wait is flagged as slow. */
    @Key("wait.analysis.slow.threshold.ms")
    @DefaultValue("5000")
    long waitAnalysisSlowThresholdMs();

    /** Minimum test duration (seconds) before wait analysis is considered meaningful enough to report. */
    @Key("wait.analysis.min.test.duration.seconds")
    @DefaultValue("0")
    int waitAnalysisMinTestDurationSeconds();

    // ─── Flaky Analytics ──────────────────────────────────────────────────────

    /** Whether to compute and display the flaky-test summary/score. */
    @Key("flaky.summary.enabled")
    @DefaultValue("true")
    boolean flakySummaryEnabled();

    /** Flaky score above which a test is flagged with a warning in the report. */
    @Key("flaky.score.warn.threshold")
    @DefaultValue("50")
    int flakyScoreWarnThreshold();

    // ─── Custom Metrics ───────────────────────────────────────────────────────

    /** Master switch for browser custom-metrics capture (resource timing, user timing, long tasks). */
    @Key("custom.metrics.enabled")
    @DefaultValue("false")
    boolean customMetricsEnabled();

    /** ALL/FAIL/etc. — when custom metrics are captured. */
    @Key("custom.metrics.capture.mode")
    @DefaultValue("FAIL")
    String customMetricsCaptureMode();

    /** Resource-load duration (ms) above which a browser resource is flagged as slow. */
    @Key("custom.metrics.slow.resource.threshold.ms")
    @DefaultValue("2000")
    long customMetricsSlowResourceThresholdMs();

    /** Browser User Timing API mark/measure duration (ms) above which it's flagged as slow. */
    @Key("custom.metrics.user.timing.warn.ms")
    @DefaultValue("3000")
    long customMetricsUserTimingWarnMs();

    /** Number of long tasks (main-thread blocking &gt;50ms) tolerated before the test is failed; 0 = never fail on this. */
    @Key("custom.metrics.fail.on.long.task.count")
    @DefaultValue("0")
    int customMetricsFailOnLongTaskCount();

    /** Whether to capture browser Long Animation Frame entries. */
    @Key("custom.metrics.long.animation.frame.enabled")
    @DefaultValue("true")
    boolean customMetricsLongAnimationFrameEnabled();

    /** Whether to capture the Server-Timing response header when present. */
    @Key("custom.metrics.server.timing.enabled")
    @DefaultValue("true")
    boolean customMetricsServerTimingEnabled();

    // ─── Frontend Diagnostics ─────────────────────────────────────────────────

    /** Master switch for frontend (DOM/memory/interaction/grid/reflow) diagnostics capture. */
    @Key("frontend.diagnostics.enabled")
    @DefaultValue("false")
    boolean frontendDiagnosticsEnabled();

    /** ALL/FAIL/etc. — when frontend diagnostics are captured. */
    @Key("frontend.diagnostics.capture.mode")
    @DefaultValue("FAIL")
    String frontendDiagnosticsCaptureMode();

    /** Component render duration (ms) above which it's flagged as slow. */
    @Key("frontend.diagnostics.component.render.warn.ms")
    @DefaultValue("200")
    long frontendDiagnosticsComponentRenderWarnMs();

    /** Whether to track DOM node-count growth during a test. */
    @Key("frontend.diagnostics.dom.tracking.enabled")
    @DefaultValue("true")
    boolean frontendDomTrackingEnabled();

    /** Whether to track JS heap memory usage during a test. */
    @Key("frontend.diagnostics.memory.tracking.enabled")
    @DefaultValue("true")
    boolean frontendMemoryTrackingEnabled();

    /** Whether to track user-interaction (click/input) responsiveness during a test. */
    @Key("frontend.diagnostics.interaction.tracking.enabled")
    @DefaultValue("true")
    boolean frontendInteractionTrackingEnabled();

    /** Whether to track ExtJS grid render timing during a test. */
    @Key("frontend.diagnostics.grid.render.tracking.enabled")
    @DefaultValue("true")
    boolean frontendGridRenderTrackingEnabled();

    /** Whether to detect layout-thrashing/reflow during a test. */
    @Key("frontend.diagnostics.reflow.detection.enabled")
    @DefaultValue("true")
    boolean frontendReflowDetectionEnabled();
}
