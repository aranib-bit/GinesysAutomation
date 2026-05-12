package com.gsl.driver;

import com.gsl.browser.AdvancedJsLogger;
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
        RunMode  mode    = ConfigFactory.getConfig().runMode();
        Browser  browser = ConfigFactory.getConfig().browserType();

        if (DriverManager.getDriver() == null) {
            WebDriver driver = MasterDriverFactory.create(mode, browser);
            DriverManager.setDriver(driver);

            String sessionId = "";
            if (driver instanceof RemoteWebDriver rwd) {
                sessionId = rwd.getSessionId().toString();
                TestContextManager.setSessionId(sessionId);
            }
            Log.info("Driver init — thread={}, provider={}, browser={}, sessionId={}",
                    Thread.currentThread().getId(),
                    ExecutionProviderRegistry.get(mode).getProviderName(),
                    browser,
                    sessionId);
        }

        DriverManager.getDriver().get(ConfigFactory.getConfig().url());
        DriverManager.getDriver().manage().window().maximize();

        if (ConfigFactory.getConfig().browserLogsEnabled()) {
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
