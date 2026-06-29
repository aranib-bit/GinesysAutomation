package com.gsl.diagnostics;

import com.gsl.annotations.ApiFlow;
import com.gsl.browser.AdvancedLogFetcher;
import com.gsl.browser.BrowserLogRecord;
import com.gsl.config.ConfigFactory;
import com.gsl.config.DiagnosticsConfig;
import com.gsl.config.ExecutionConfig;
import com.gsl.config.NetworkConfig;
import com.gsl.config.RemoteLogConfig;
import com.gsl.config.ReportConfig;
import com.gsl.diagnostics.section.*;
import com.gsl.diagnostics.section.frontend.FrontendPerformanceSection;
import com.gsl.diagnostics.section.frontend.FrontendPerformanceSectionFactory;
import com.gsl.reports.RetryHistoryBuffer;
import com.gsl.reports.StepDurationBuffer;
import com.gsl.reports.WaitDurationBuffer;
import com.gsl.reports.ExtentLogger;
import com.gsl.driver.DriverManager;
import com.gsl.network.NetworkCaptureManager;
import com.gsl.network.NetworkJsErrorCorrelator;
import com.gsl.network.NetworkLogRecord;
import com.gsl.network.pattern.ApiPattern;
import com.gsl.remote.RemoteLogCollector;
import com.gsl.remote.model.CaptureMode;
import com.gsl.remote.model.LogFetchResponse;
import com.gsl.reports.ActionTrailBuffer;
import com.gsl.utils.Log;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.testng.ITestResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single entry point for assembling all failure (or success) evidence into a
 * {@link DiagnosticReport} that is then attached to the current ExtentTest node.
 *
 * <p>Remote log collection (APP + DB) is pre-computed concurrently via
 * {@link RemoteLogCollector} before the {@link DiagnosticReport} builder chain starts,
 * so all futures are fully resolved before rendering.
 */
public final class FailureBundleOrchestrator {

    private FailureBundleOrchestrator() {}

    /**
     * Assembles the full failure evidence bundle for the given test result.
     */
    public static DiagnosticReport onFailure(ITestResult result) {
        Throwable t      = result.getThrowable();
        WebDriver driver = DriverManager.getDriver();
        Instant   start  = Instant.ofEpochMilli(result.getStartMillis());
        Instant   end    = Instant.now();
        long testDurationSeconds = (end.toEpochMilli() - result.getStartMillis()) / 1000;

        // Fetch browser logs and network logs first — needed by correlator + exporter
        List<BrowserLogRecord> jsLogs  = fetchBrowserLogs(driver);
        List<NetworkLogRecord>  netLogs = NetworkCaptureManager.get().getLogs();
        String                  proto   = NetworkCaptureManager.get().protocolName();

        // Correlate failed network requests to CRITICAL JS errors by timestamp proximity
        Set<String> correlatedUrls = NetworkJsErrorCorrelator.correlatedUrls(netLogs, jsLogs);

        // Write structured JSON artifact when CRITICAL JS errors are present
        JsErrorJsonExporter.export(result, jsLogs, netLogs, correlatedUrls, driver);

        // Pre-collect remote logs concurrently (APP + DB in parallel) before builder chain
        Map<ServerRole, LogFetchResponse> remoteLogs = collectRemoteLogs(start, end);

        // Collect Custom Metrics once — reused for both the report and threshold check
        CustomMetricsSection customMetrics = buildCustomMetricsSection(driver, true);

        // Collect Frontend Diagnostics once — reused for report and RootCauseAttributionSection
        FrontendPerformanceSection frontendPerf = buildFrontendPerfSection(driver, true);

        DiagnosticReport report = new DiagnosticReport()
                // ── Browser & frontend ──────────────────────────────────────
                .add(new BrowserLogSection(jsLogs, result.getMethod().getDescription()))
                .add(new NetworkLogSection(netLogs, proto, correlatedUrls))
                .add(networkTimingSection(true))
                // ── Action trail ──────────────────────────────────────────────
                .add(new ActionTrailSection(ActionTrailBuffer.getForDisplay(), ActionTrailBuffer.totalCaptured()))
                // ── Phase 2: Step timeline, wait analysis, flaky summary ───────
                .add(stepTimelineSection(true))
                .add(waitAnalysisSection(true, testDurationSeconds))
                .add(flakySummarySection())
                // ── Performance timing ────────────────────────────────────────
                .add(performanceTimingSection(driver))
                // ── Custom Metrics (web.dev Performance APIs) ─────────────────
                .add(customMetrics)
                // ── Frontend framework diagnostics (ExtJS / React / Angular) ──
                .add(frontendPerf)
                // ── Root Cause Attribution ────────────────────────────────────
                .add(rootCauseSection(customMetrics, frontendPerf))
                // ── UI component state (ExtJS / React / Angular) ─────────────
                .add(uiComponentSection(driver))
                // ── HTML capture ──────────────────────────────────────────────
                .add(htmlCaptureSection(t, driver, result))
                // ── Driver metadata ──────────────────────────────────────────
                .add(driverCapsSection(driver))
                // ── Active popups ────────────────────────────────────────────
                .add(activePopupSection(driver))
                // ── Server-side ──────────────────────────────────────────────
                .add(new ServerUtilSection(ServerRole.APP))
                .add(new ServerUtilSection(ServerRole.DB))
                .add(new EventLogSection())
                // ── Remote logs (SSH / WinRM — OS auto-detected) ─────────────
                .add(remoteLogSection(ServerRole.APP, remoteLogs))
                .add(remoteLogSection(ServerRole.DB,  remoteLogs))
                // ── DB state snapshot ────────────────────────────────────────
                .add(dbStateSection())
                // ── Thread dump on timeout ───────────────────────────────────
                .add(threadDumpSection(t));

        // Threshold check uses the same instance — no second JS round-trip
        enforceCustomMetricsThreshold(customMetrics);

        return report;
    }

    /**
     * Assembles a lighter evidence bundle for successful tests (ALWAYS capture mode).
     */
    public static DiagnosticReport onSuccess(ITestResult result) {
        WebDriver driver = DriverManager.getDriver();
        Instant   start  = Instant.ofEpochMilli(result.getStartMillis());
        Instant   end    = Instant.now();
        long testDurationSeconds = (end.toEpochMilli() - result.getStartMillis()) / 1000;

        List<BrowserLogRecord> jsLogs  = fetchBrowserLogs(driver);

        Map<ServerRole, LogFetchResponse> remoteLogs = collectRemoteLogs(start, end);

        CustomMetricsSection passCustomMetrics = buildCustomMetricsSection(driver, false);
        FrontendPerformanceSection passFrontendPerf = buildFrontendPerfSection(driver, false);

        return new DiagnosticReport()
                .add(new BrowserLogSection(jsLogs, result.getMethod().getDescription()))
                .add(networkLogSectionForPass(result))
                .add(networkTimingSection(false))
                .add(actionTrailSectionForPass())
                // ── Phase 2: Step timeline, wait analysis, flaky summary ───────
                .add(stepTimelineSection(false))
                .add(waitAnalysisSection(false, testDurationSeconds))
                .add(flakySummarySection())
                .add(performanceTimingSection(driver))
                // Custom Metrics on pass only when capture.mode=ALL
                .add(passCustomMetrics)
                // Frontend diagnostics on pass only when capture.mode=ALL
                .add(passFrontendPerf)
                .add(rootCauseSection(passCustomMetrics, passFrontendPerf))
                .add(uiComponentSectionForPass(driver))
                .add(driverCapsSection(driver))
                .add(activePopupSection(driver));
        // Application logs are collected only for failed tests (see onFailure).
    }

    private static DiagnosticSection networkTimingSection(boolean isFailure) {
        String logOn = ConfigFactory.getNetworkConfig().networkTimingLogOn();
        if (!"always".equalsIgnoreCase(logOn) && !isFailure) return null;
        NetworkTimingSection section = new NetworkTimingSection(isFailure);
        return section.hasData() ? section : null;
    }

    // ── pass-specific network section ────────────────────────────────────────

    private static DiagnosticSection networkLogSectionForPass(ITestResult result) {
        List<NetworkLogRecord> logs     = NetworkCaptureManager.get().getLogs();
        String                 protocol = NetworkCaptureManager.get().protocolName();

        if (ConfigFactory.getNetworkConfig().networkCapturePassShowAll()) {
            return new NetworkLogSection(logs, protocol);
        }
        ApiPattern pattern = resolveApiPattern(result);
        return new NetworkLogSection(logs, protocol, pattern);
    }

    private static ApiPattern resolveApiPattern(ITestResult result) {
        Class<?> cls = result.getInstance().getClass();
        while (cls != null && cls != Object.class) {
            ApiFlow ann = cls.getAnnotation(ApiFlow.class);
            if (ann != null) return ann.value();
            cls = cls.getSuperclass();
        }
        return null;
    }

    // ── section builders ──────────────────────────────────────────────────────

    private static List<BrowserLogRecord> fetchBrowserLogs(WebDriver driver) {
        if (!ConfigFactory.getNetworkConfig().browserLogsEnabled() || driver == null) return List.of();
        try {
            List<BrowserLogRecord> logs = AdvancedLogFetcher.fetchLogs();
            return logs != null ? logs : List.of();
        } catch (Exception e) {
            Log.warn("[FailureBundleOrchestrator] Browser log fetch failed: {}", e.getMessage());
            return List.of();
        }
    }

    private static DiagnosticSection actionTrailSectionForPass() {
        String mode = ConfigFactory.getDiagnosticsConfig().actionTrailCaptureMode();
        if (!"ALL".equalsIgnoreCase(mode)) return null;
        return new ActionTrailSection(ActionTrailBuffer.getForDisplay(), ActionTrailBuffer.totalCaptured());
    }

    /**
     * Builds the FrontendPerformanceSection when frontend diagnostics are enabled and the
     * capture mode matches the call site (isFailure=true → FAIL or ALL; false → ALL only).
     * Returns null (silently skipped) when disabled, not configured, or no data collected.
     */
    private static FrontendPerformanceSection buildFrontendPerfSection(WebDriver driver,
                                                                        boolean isFailure) {
        DiagnosticsConfig cfg = ConfigFactory.getDiagnosticsConfig();
        if (!cfg.frontendDiagnosticsEnabled()) {
            Log.debug("[FrontendDiag] Skipped — frontend.diagnostics.enabled=false");
            return null;
        }
        if (driver == null) {
            Log.debug("[FrontendDiag] Skipped — driver is null");
            return null;
        }
        String mode = cfg.frontendDiagnosticsCaptureMode();
        if ("NEVER".equalsIgnoreCase(mode)) {
            Log.debug("[FrontendDiag] Skipped — capture.mode=NEVER");
            return null;
        }
        if (!isFailure && !"ALL".equalsIgnoreCase(mode)) {
            Log.debug("[FrontendDiag] Skipped — capture.mode={} and test did not fail", mode);
            return null;
        }
        FrontendPerformanceSection section = FrontendPerformanceSectionFactory.create(driver);
        if (!section.hasData()) {
            Log.debug("[FrontendDiag] Skipped — window.__gslExtDiag present={} but all arrays empty",
                    section.isCaptured());
        }
        return section.hasData() ? section : null;
    }

    /**
     * Builds the RootCauseAttributionSection from the already-collected CustomMetrics and
     * FrontendPerformance sections.  Returns null when either input is null (no double
     * JS round-trip needed — both sections are reused).
     */
    private static DiagnosticSection rootCauseSection(CustomMetricsSection customMetrics,
                                                       FrontendPerformanceSection frontendPerf) {
        if (customMetrics == null || frontendPerf == null) return null;
        RootCauseAttributionSection section =
                new RootCauseAttributionSection(customMetrics, frontendPerf);
        return section.hasData() ? section : null;
    }

    private static DiagnosticSection uiComponentSectionForPass(WebDriver driver) {
        String mode = ConfigFactory.getDiagnosticsConfig().uiComponentsCaptureMode();
        if (!"ALL".equalsIgnoreCase(mode)) return null;
        return uiComponentSection(driver);
    }

    private static DiagnosticSection performanceTimingSection(WebDriver driver) {
        if (!ConfigFactory.getReportConfig().performanceTimingEnabled() || driver == null) return null;
        PerformanceTimingSection section = new PerformanceTimingSection(driver);
        return section.hasData() ? section : null;
    }

    /**
     * Collects Custom Metrics for the given driver when the feature is enabled and the
     * capture mode matches the call site (isFailure=true → FAIL or ALL; false → ALL only).
     * Returns the live section instance so it can be added to the report AND inspected
     * for threshold checks without a second JS round-trip.
     */
    private static CustomMetricsSection buildCustomMetricsSection(WebDriver driver, boolean isFailure) {
        DiagnosticsConfig cfg = ConfigFactory.getDiagnosticsConfig();
        if (!cfg.customMetricsEnabled() || driver == null) return null;
        String mode = cfg.customMetricsCaptureMode();
        if ("NEVER".equalsIgnoreCase(mode)) return null;
        if (!isFailure && !"ALL".equalsIgnoreCase(mode)) return null;
        CustomMetricsSection section = new CustomMetricsSection(driver, cfg);
        return section.hasData() ? section : null;
    }

    /**
     * Fails the test when the long task count in the already-collected section exceeds
     * the configured threshold. Reuses the existing instance — no second JS round-trip.
     */
    private static void enforceCustomMetricsThreshold(CustomMetricsSection section) {
        if (section == null) return;
        int threshold = ConfigFactory.getDiagnosticsConfig().customMetricsFailOnLongTaskCount();
        if (threshold <= 0) return;
        int count = section.longTaskCount();
        if (count > threshold) {
            String msg = "[CustomMetrics] Long task threshold breached: " + count
                    + " long tasks detected (limit=" + threshold + ").";
            ExtentLogger.fail(msg);
            throw new AssertionError(msg);
        }
    }

    private static DiagnosticSection uiComponentSection(WebDriver driver) {
        if (!ConfigFactory.getDiagnosticsConfig().uiDiagnosticsEnabled() || driver == null) return null;
        try {
            UiComponentSection section = new UiComponentSection(driver);
            return section.hasData() ? section : null;
        } catch (Exception e) {
            Log.warn("[FailureBundleOrchestrator] UI component section failed: {}", e.getMessage());
            return null;
        }
    }

    private static DiagnosticSection htmlCaptureSection(Throwable t, WebDriver driver, ITestResult result) {
        if (!ConfigFactory.getDiagnosticsConfig().diagnosticsHtmlCaptureOnElementFailure()) return null;
        if (driver == null) return null;
        if (!(t instanceof NoSuchElementException) && !(t instanceof TimeoutException)) return null;
        return new HtmlCaptureSection(driver, result.getMethod().getMethodName());
    }

    private static DiagnosticSection driverCapsSection(WebDriver driver) {
        if (driver == null) return null;
        if (!ConfigFactory.getDiagnosticsConfig().diagnosticsDriverCapabilitiesEnabled()) return null;
        return new DriverCapabilitiesSection(driver);
    }

    private static DiagnosticSection activePopupSection(WebDriver driver) {
        if (driver == null) return null;
        if (!ConfigFactory.getDiagnosticsConfig().diagnosticsActivePopupEnabled()) return null;
        return new ActivePopupSection(driver);
    }

    private static DiagnosticSection dbStateSection() {
        if (!ConfigFactory.getDiagnosticsConfig().diagnosticsDbStateEnabled()) return null;
        return new DbStateSection();
    }

    private static DiagnosticSection threadDumpSection(Throwable t) {
        if (!ConfigFactory.getDiagnosticsConfig().diagnosticsThreadDumpEnabled()) return null;
        if (!(t instanceof TimeoutException)) return null;
        return new ThreadDumpSection();
    }

    // ── remote log helpers ────────────────────────────────────────────────────

    private static Map<ServerRole, LogFetchResponse> collectRemoteLogs(Instant start, Instant end) {
        try {
            if (resolveCaptureMode() == CaptureMode.NEVER) return Map.of();

            String appHost = resolveHost(ServerRole.APP);
            String dbHost  = resolveHost(ServerRole.DB);

            return new RemoteLogCollector().collect(appHost, dbHost, start, end);
        } catch (Exception e) {
            Log.warn("[FailureBundleOrchestrator] Remote log collection failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private static String resolveHost(ServerRole role) {
        boolean isApp = role == ServerRole.APP;

        String newHost = isApp
                ? ConfigFactory.getRemoteLogConfig().remoteAppServerHost()
                : ConfigFactory.getRemoteLogConfig().remoteDbServerHost();

        if (newHost != null && !newHost.isBlank()) return newHost;

        String legacyHost = isApp
                ? ConfigFactory.getDiagnosticsConfig().diagnosticsAppServerHost()
                : ConfigFactory.getDiagnosticsConfig().diagnosticsDbServerHost();

        if (legacyHost != null && !legacyHost.isBlank()
                && !"localhost".equalsIgnoreCase(legacyHost)) return legacyHost;

        return null;
    }

    private static RemoteLogSection remoteLogSection(
            ServerRole role, Map<ServerRole, LogFetchResponse> results) {
        String displayName = (role == ServerRole.APP) ? "Application Logs" : "DB Server Logs";
        String roleKey     = (role == ServerRole.APP) ? "app" : "db";
        String host  = resolveHost(role);
        String label = displayName + (host != null ? " — " + host : "");

        if (!results.containsKey(role)) {
            String reason = displayName + ": capture disabled — set remote.logs.capture.mode=ALWAYS"
                    + " or ON_FAILURE in remote-log.properties";
            return new RemoteLogSection(label, reason);
        }

        LogFetchResponse resp = results.get(role);
        if (resp == null) {
            String reason = (host == null)
                    ? displayName + ": host not configured — set remote."
                            + roleKey + ".server.host in remote-log.properties"
                    : displayName + ": log collection error — check console/log4j output for details";
            return new RemoteLogSection(label, reason);
        }

        if (resp.getSkipReason() != null) {
            String reason;
            switch (resp.getSkipReason()) {
                case CREDS_MISSING_WINRM:
                    reason = displayName + ": credentials not configured — set "
                            + "REMOTE_LOG_WINRM_USER/PASS env vars or remote."
                            + roleKey + ".server.winrm.username/password in remote-log.properties";
                    break;
                case CREDS_MISSING_SSH:
                    reason = displayName + ": credentials not configured — set "
                            + "REMOTE_LOG_SSH_USER/PASS env vars or remote."
                            + roleKey + ".server.ssh.username/password in remote-log.properties";
                    break;
                case FETCH_FAILED:
                    reason = displayName + ": collection failed — "
                            + (resp.getStderr() != null && !resp.getStderr().isBlank()
                                    ? resp.getStderr()
                                    : "see test logs for details");
                    break;
                default:
                    reason = displayName + ": skipped";
            }
            return new RemoteLogSection(label, reason);
        }

        return new RemoteLogSection(resp, label);
    }

    private static CaptureMode resolveCaptureMode() {
        try {
            return CaptureMode.valueOf(
                    ConfigFactory.getRemoteLogConfig().remoteLogsCaptureMode().toUpperCase());
        } catch (Exception e) {
            return CaptureMode.NEVER;
        }
    }

    // ── Phase 2: Step Timeline, Wait Analysis, Flaky Summary ─────────────────

    private static DiagnosticSection stepTimelineSection(boolean isFailure) {
        DiagnosticsConfig cfg = ConfigFactory.getDiagnosticsConfig();
        if (!cfg.stepTimelineEnabled()) return null;
        if (!isFailure && !"ALL".equalsIgnoreCase(cfg.stepTimelineCaptureMode())) return null;
        java.util.List<StepDurationBuffer.StepRecord> records = StepDurationBuffer.getAll();
        if (records.isEmpty()) return null;
        return new StepTimelineSection(records, cfg.stepTimelineWarnMs());
    }

    private static DiagnosticSection waitAnalysisSection(boolean isFailure, long testDurationSeconds) {
        DiagnosticsConfig cfg = ConfigFactory.getDiagnosticsConfig();
        if (!cfg.waitAnalysisEnabled()) return null;
        if (!isFailure && !"ALL".equalsIgnoreCase(cfg.waitAnalysisCaptureMode())) return null;
        int minSec = cfg.waitAnalysisMinTestDurationSeconds();
        if (minSec > 0 && testDurationSeconds < minSec) return null;
        java.util.List<WaitDurationBuffer.WaitRecord> records = WaitDurationBuffer.getAll();
        if (records.isEmpty()) return null;
        return new WaitAnalysisSection(records, cfg.waitAnalysisSlowThresholdMs());
    }

    private static DiagnosticSection flakySummarySection() {
        DiagnosticsConfig diagCfg = ConfigFactory.getDiagnosticsConfig();
        ExecutionConfig   execCfg = ConfigFactory.getExecutionConfig();
        if (!diagCfg.flakySummaryEnabled()) return null;
        if (execCfg.retryMaxCount() <= 0) return null;
        java.util.List<RetryHistoryBuffer.RetryRecord> records = RetryHistoryBuffer.getAll();
        if (records.isEmpty()) return null;
        return new FlakySummarySection(records, execCfg.retryMaxCount(), diagCfg.flakyScoreWarnThreshold());
    }
}
