package com.gsl.driver;

import com.gsl.browser.AdvancedJsLogger;
import com.gsl.config.BrowserConfig;
import com.gsl.config.ConfigFactory;
import com.gsl.driver.provider.ExecutionProviderRegistry;
import com.gsl.enums.Browser;
import com.gsl.enums.RunMode;
import com.gsl.network.NetworkCapture;
import com.gsl.network.NetworkCaptureManager;
import com.gsl.network.NetworkServiceFactory;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.Log;
import com.gsl.utils.TestContextManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Owns the low-level lifecycle of a single thread's WebDriver: creation (via
 * {@link MasterDriverFactory}), viewport enforcement, initial navigation to the app URL, and
 * network-capture start/stop. {@link SessionPool#createSession} and {@link ContextOrchestrator}
 * are the callers that decide WHEN a driver is needed; this class only knows HOW to stand one up
 * or tear it down for the calling thread.
 *
 * <p>Both methods are idempotent no-ops when there is nothing to do: {@link #initDriver()} skips
 * creation entirely if {@link DriverManager#getDriver()} already returns a driver for the current
 * thread's active context, and {@link #quitDriver()} is a no-op when none exists.
 */
public final class Driver {

    private Driver() {}

    /**
     * Creates (if not already present) a WebDriver for the current thread's active context using
     * the configured {@code RunMode}/{@code Browser}, instruments it via {@link DriverManager},
     * enforces the configured viewport via {@link ViewportEnforcer} (before navigation — ExtJS
     * makes layout decisions at boot that a later resize does not fully undo), navigates to the
     * configured app URL, and starts network capture.
     */
    public static void initDriver() {
        // Typed enum resolution via Owner Converter — no inline valueOf needed
        RunMode  mode    = ConfigFactory.getBrowserConfig().runMode();
        Browser  browser = com.gsl.config.BrowserResolver.current();

        if (DriverManager.getDriver() == null) {
            WebDriver driver = MasterDriverFactory.create(mode, browser);
            DriverManager.setDriver(driver);

            String sessionId = "";
            if (driver instanceof RemoteWebDriver rwd) {
                sessionId = rwd.getSessionId().toString();
                TestContextManager.setSessionId(sessionId);
            }
            // Record browser/OS/driver versions now, while the session's capabilities are known —
            // reports show them so Linux-vs-Windows differences can be tied to exact builds.
            com.gsl.reports.BrowserFingerprint.register(sessionId, driver);
            BrowserConfig browserCfg = ConfigFactory.getBrowserConfig();
            Log.info("Driver init — thread={}, provider={}, browser={}, sessionId={}, "
                            + "runmode={}, headless={}, requestedViewport={}x{}",
                    Thread.currentThread().getId(),
                    ExecutionProviderRegistry.get(mode).getProviderName(),
                    browser,
                    sessionId,
                    mode,
                    browserCfg.headless(),
                    browserCfg.windowWidth(),
                    browserCfg.windowHeight());
        }

        // Enforced (setSize + CDP device-metrics override + fail-fast assert) BEFORE navigation:
        // --window-size and manage().window().setSize() are both silently ignored on a Docker
        // node with no window manager (observed: browser stays at its 800x600/800x513 default
        // regardless of what's requested), and ExtJS makes layout decisions at boot that a later
        // resize does not fully undo. See ViewportEnforcer for the full mechanism.
        //
        // Round 21: the 3 blocks below are individually timed via ReportLogger.framework (not
        // StepExecutor — ExtentManager.hasTest() is normally false this early, at @BeforeMethod-time
        // session creation, so a StepExecutor node would rarely attach to anything) after a run
        // (20260926_201332) showed a bare java.util.concurrent.TimeoutException killing session
        // creation with no indication which of these 3 WebDriver commands actually stalled — only
        // "WebDriver initialized" (before this block) and "WebDriver unloaded" (createSession()'s own
        // failure unwind) bracketing a ~98s gap. If a command throws, its own "Starting" line with no
        // matching "completed" line already pinpoints it without needing a next run's exception detail.
        long viewportStartMs = System.currentTimeMillis();
        ReportLogger.framework("[Driver.initDriver] Starting ViewportEnforcer.enforce()...");
        ViewportEnforcer.enforce(DriverManager.getDriver());
        ReportLogger.framework("[Driver.initDriver] ViewportEnforcer.enforce() completed in {}ms",
                System.currentTimeMillis() - viewportStartMs);

        String appUrl = ConfigFactory.getConfig().url();
        long navStartMs = System.currentTimeMillis();
        ReportLogger.framework("[Driver.initDriver] Starting navigation to {}...", appUrl);
        navigateToApp(appUrl);
        ReportLogger.framework("[Driver.initDriver] Navigation completed in {}ms",
                System.currentTimeMillis() - navStartMs);

        if (ConfigFactory.getNetworkConfig().browserLogsEnabled()) {
            AdvancedJsLogger.inject();
        }

        long networkStartMs = System.currentTimeMillis();
        ReportLogger.framework("[Driver.initDriver] Starting network capture setup...");
        NetworkCapture capture = NetworkServiceFactory.create(DriverManager.getDriver());
        capture.start();
        ReportLogger.framework("[Driver.initDriver] Network capture started in {}ms",
                System.currentTimeMillis() - networkStartMs);
        NetworkCaptureManager.set(capture);
    }

    /**
     * Opens the application, bounded by {@code pageload.timeout.seconds} (wait.properties) - a value that was never
     * applied to the driver. Without it, a first load whose page never finished (run 20261009_134343: "Starting
     * navigation" at 13:47:44, no "completed") blocked {@code get()} until the WebDriver HTTP read timeout (180 s)
     * and failed with a bare {@code java.util.concurrent.TimeoutException}, killing the new session and skipping its
     * test. Now the load stops at the configured limit, the page state is logged, and the app is loaded once more in
     * the same browser; a second timeout is thrown as before. The browser's previous page-load timeout is restored
     * afterwards, so no other navigation changes behaviour.
     */
    private static void navigateToApp(String appUrl) {
        WebDriver driver = DriverManager.getDriver();
        java.time.Duration previous = null;
        long limit = ConfigFactory.getWaitConfig().pageLoadTimeoutSeconds();
        try {
            previous = driver.manage().timeouts().getPageLoadTimeout();
            driver.manage().timeouts().pageLoadTimeout(java.time.Duration.ofSeconds(limit));
        } catch (RuntimeException e) {
            Log.warn("[Driver.initDriver] Could not apply the page-load timeout: {}", e.getMessage());
        }
        try {
            try {
                driver.get(appUrl);
            } catch (org.openqa.selenium.TimeoutException first) {
                ReportLogger.frameworkWarn("[Driver.initDriver] App did not finish loading within {}s ({}) - loading it once more",
                        limit, pageState(driver));
                driver.get(appUrl);
                ReportLogger.framework("[Driver.initDriver] App loaded on the second attempt ({})", pageState(driver));
            }
            // A loaded page is not a rendered app: run 20261009_164937 (FIN-AP-ADD-DOC-04) got readyState=complete
            // after the reload, but the login form never appeared (hung renderer) and the test died at login. Check
            // it here, within the same bound, so SessionPool's cold-start path replaces the browser instead.
            try {
                new org.openqa.selenium.support.ui.WebDriverWait(driver, java.time.Duration.ofSeconds(limit))
                        .until(org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated(
                                com.gsl.pages.LoginPage.usernameField()));
            } catch (org.openqa.selenium.TimeoutException notRendered) {
                throw new org.openqa.selenium.TimeoutException("[Driver.initDriver] App page loaded but the login form"
                        + " did not appear within " + limit + "s (" + pageState(driver) + ")", notRendered);
            }
        } finally {
            if (previous != null) {
                try {
                    driver.manage().timeouts().pageLoadTimeout(previous);
                } catch (RuntimeException ignored) {
                    // restoring is best effort
                }
            }
        }
    }

    /** document.readyState and the number of resources still loading - diagnostics only, never throws. */
    private static String pageState(WebDriver driver) {
        try {
            Object r = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                    "var p=performance.getEntriesByType('resource').filter(function(e){return !e.responseEnd;}).length;"
                            + "return 'readyState=' + document.readyState + ', resources still loading=' + p;");
            return String.valueOf(r);
        } catch (RuntimeException e) {
            return "page state unreadable: " + e.getClass().getSimpleName();
        }
    }

    /** Stops network capture and quits/unloads the current thread's WebDriver, if one exists. No-op otherwise. */
    public static void quitDriver() {
        if (DriverManager.getDriver() != null) {
            NetworkCaptureManager.get().stop();
            NetworkCaptureManager.remove();
            DriverManager.getDriver().quit();
            DriverManager.unloadDriver();
            Log.info("Driver quit — thread={}", Thread.currentThread().getId());
        }
    }
}
