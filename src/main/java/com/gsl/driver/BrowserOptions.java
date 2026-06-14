package com.gsl.driver;

import com.gsl.config.BrowserConfig;
import com.gsl.config.ConfigFactory;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;

/**
 * Builds typed browser options from FrameworkConfig flags (headless, incognito, ssl bypass).
 * Used by LocalExecutionProvider, SeleniumGridProvider, BrowserStackProvider, etc.
 */
public final class BrowserOptions {

    private BrowserOptions() {}

    public static ChromeOptions chrome() {
        BrowserConfig cfg = ConfigFactory.getBrowserConfig();
        ChromeOptions opts = new ChromeOptions();
        opts.addArguments(
                "--disable-notifications",
                "--disable-infobars",
                "--no-sandbox",
                "--disable-dev-shm-usage"
        );
        if (cfg.headless())           opts.addArguments("--headless=new");
        if (cfg.incognito())          opts.addArguments("--incognito");
        if (cfg.acceptInsecureCerts()) opts.setAcceptInsecureCerts(true);
        return opts;
    }

    public static FirefoxOptions firefox() {
        BrowserConfig cfg = ConfigFactory.getBrowserConfig();
        FirefoxOptions opts = new FirefoxOptions();
        if (cfg.headless())            opts.addArguments("-headless");
        if (cfg.incognito())           opts.addArguments("-private");
        if (cfg.acceptInsecureCerts()) opts.setAcceptInsecureCerts(true);
        opts.addPreference("dom.webnotifications.enabled", false);
        opts.setCapability("webSocketUrl", true);
        return opts;
    }

    public static EdgeOptions edge() {
        BrowserConfig cfg = ConfigFactory.getBrowserConfig();
        EdgeOptions opts = new EdgeOptions();
        opts.addArguments("--disable-notifications");
        if (cfg.headless())           opts.addArguments("--headless=new");
        if (cfg.incognito())          opts.addArguments("--inprivate");
        if (cfg.acceptInsecureCerts()) opts.setAcceptInsecureCerts(true);
        return opts;
    }
}
