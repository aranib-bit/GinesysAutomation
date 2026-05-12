package com.gsl.diagnostics;

import com.gsl.browser.AdvancedLogFetcher;
import com.gsl.browser.BrowserLogRecord;
import com.gsl.config.ConfigFactory;
import com.gsl.diagnostics.section.*;
import com.gsl.driver.DriverManager;
import com.gsl.network.NetworkCaptureManager;
import com.gsl.remote.RemoteLogCollector;
import com.gsl.remote.model.CaptureMode;
import com.gsl.remote.model.LogFetchResponse;
import com.gsl.utils.Log;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.testng.ITestResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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

        // Pre-collect remote logs concurrently (APP + DB in parallel) before builder chain
        Map<ServerRole, LogFetchResponse> remoteLogs = collectRemoteLogs(start, end);

        return new DiagnosticReport()
                // ── Browser & frontend ──────────────────────────────────────
                .add(browserLogSection(result, driver))
                .add(new NetworkLogSection(
                        NetworkCaptureManager.get().getLogs(),
                        NetworkCaptureManager.get().protocolName()))
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
    }

    /**
     * Assembles a lighter evidence bundle for successful tests (ALWAYS capture mode).
     */
    public static DiagnosticReport onSuccess(ITestResult result) {
        WebDriver driver = DriverManager.getDriver();
        Instant   start  = Instant.ofEpochMilli(result.getStartMillis());
        Instant   end    = Instant.now();

        Map<ServerRole, LogFetchResponse> remoteLogs = collectRemoteLogs(start, end);

        return new DiagnosticReport()
                .add(browserLogSection(result, driver))
                .add(new NetworkLogSection(
                        NetworkCaptureManager.get().getLogs(),
                        NetworkCaptureManager.get().protocolName()))
                .add(remoteLogSection(ServerRole.APP, remoteLogs))
                .add(remoteLogSection(ServerRole.DB,  remoteLogs));
    }

    // ── section builders ──────────────────────────────────────────────────────

    private static DiagnosticSection browserLogSection(ITestResult result, WebDriver driver) {
        if (!ConfigFactory.getConfig().browserLogsEnabled()) return null;
        if (driver == null) return null;
        try {
            List<BrowserLogRecord> logs = AdvancedLogFetcher.fetchLogs();
            return (logs != null) ? new BrowserLogSection(logs, result.getMethod().getDescription()) : null;
        } catch (Exception e) {
            Log.warn("[FailureBundleOrchestrator] Browser log fetch failed: {}", e.getMessage());
            return null;
        }
    }

    private static DiagnosticSection htmlCaptureSection(Throwable t, WebDriver driver, ITestResult result) {
        if (!ConfigFactory.getConfig().diagnosticsHtmlCaptureOnElementFailure()) return null;
        if (driver == null) return null;
        if (!(t instanceof NoSuchElementException) && !(t instanceof TimeoutException)) return null;
        return new HtmlCaptureSection(driver, result.getMethod().getMethodName());
    }

    private static DiagnosticSection driverCapsSection(WebDriver driver) {
        if (driver == null) return null;
        if (!ConfigFactory.getConfig().diagnosticsDriverCapabilitiesEnabled()) return null;
        return new DriverCapabilitiesSection(driver);
    }

    private static DiagnosticSection activePopupSection(WebDriver driver) {
        if (driver == null) return null;
        if (!ConfigFactory.getConfig().diagnosticsActivePopupEnabled()) return null;
        return new ActivePopupSection(driver);
    }

    private static DiagnosticSection dbStateSection() {
        if (!ConfigFactory.getConfig().diagnosticsDbStateEnabled()) return null;
        return new DbStateSection();
    }

    private static DiagnosticSection threadDumpSection(Throwable t) {
        if (!ConfigFactory.getConfig().diagnosticsThreadDumpEnabled()) return null;
        if (!(t instanceof TimeoutException)) return null;
        return new ThreadDumpSection();
    }

    // ── remote log helpers ────────────────────────────────────────────────────

    /**
     * Pre-collects remote logs concurrently for APP and DB servers.
     * Returns an empty map when capture mode is NEVER or on any initialisation error.
     */
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

    /**
     * Resolves the effective host for a server role.
     * {@code remote.*.server.host} takes priority; falls back to
     * {@code diagnostics.*.server.host} for backward compat (excluding localhost).
     */
    private static String resolveHost(ServerRole role) {
        boolean isApp = role == ServerRole.APP;

        String newHost = isApp
                ? ConfigFactory.getConfig().remoteAppServerHost()
                : ConfigFactory.getConfig().remoteDbServerHost();

        if (newHost != null && !newHost.isBlank()) return newHost;

        String legacyHost = isApp
                ? ConfigFactory.getConfig().diagnosticsAppServerHost()
                : ConfigFactory.getConfig().diagnosticsDbServerHost();

        if (legacyHost != null && !legacyHost.isBlank()
                && !"localhost".equalsIgnoreCase(legacyHost)) return legacyHost;

        return null;
    }

    private static RemoteLogSection remoteLogSection(
            ServerRole role, Map<ServerRole, LogFetchResponse> results) {
        LogFetchResponse resp = results.get(role);
        if (resp == null) return null;
        String host  = resolveHost(role);
        String label = role.label + (host != null ? " (" + host + ")" : "");
        return new RemoteLogSection(resp, label);
    }

    private static CaptureMode resolveCaptureMode() {
        try {
            return CaptureMode.valueOf(
                    ConfigFactory.getConfig().remoteLogsCaptureMode().toUpperCase());
        } catch (Exception e) {
            return CaptureMode.NEVER;
        }
    }
}
