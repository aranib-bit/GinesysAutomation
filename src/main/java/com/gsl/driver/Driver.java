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
import com.gsl.utils.Log;
import com.gsl.utils.TestContextManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;

public final class Driver {

    private Driver() {}

    public static void initDriver() {
        // Typed enum resolution via Owner Converter — no inline valueOf needed
        RunMode  mode    = ConfigFactory.getBrowserConfig().runMode();
        Browser  browser = ConfigFactory.getBrowserConfig().browserType();

        if (DriverManager.getDriver() == null) {
            WebDriver driver = MasterDriverFactory.create(mode, browser);
            DriverManager.setDriver(driver);

            String sessionId = "";
            if (driver instanceof RemoteWebDriver rwd) {
                sessionId = rwd.getSessionId().toString();
                TestContextManager.setSessionId(sessionId);
            }
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
        ViewportEnforcer.enforce(DriverManager.getDriver());

        DriverManager.getDriver().get(ConfigFactory.getConfig().url());

        if (ConfigFactory.getNetworkConfig().browserLogsEnabled()) {
            AdvancedJsLogger.inject();
        }

        NetworkCapture capture = NetworkServiceFactory.create(DriverManager.getDriver());
        capture.start();
        NetworkCaptureManager.set(capture);
    }

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
