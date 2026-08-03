package com.gsl.driver;

import com.gsl.config.BrowserConfig;
import com.gsl.config.ConfigFactory;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds typed browser options from FrameworkConfig flags (headless, incognito, ssl bypass).
 * Used by LocalExecutionProvider, SeleniumGridProvider, BrowserStackProvider, etc.
 */
public final class BrowserOptions {

    private BrowserOptions() {}

    /**
     * Chrome download preferences pointed at this thread's {@link SessionDownloadDirectory} —
     * unique per pooled session so two sessions exporting a file concurrently (e.g. Account
     * Class's Export-to-Excel test) never collide on the same downloaded filename.
     */
    private static Map<String, Object> downloadPrefs() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory", SessionDownloadDirectory.current().toAbsolutePath().toString());
        prefs.put("download.prompt_for_download", false);
        prefs.put("download.directory_upgrade", true);
        prefs.put("safebrowsing.enabled", true);
        return prefs;
    }

    public static ChromeOptions chrome() {
        BrowserConfig cfg = ConfigFactory.getBrowserConfig();
        ChromeOptions opts = new ChromeOptions();
        opts.addArguments(
                "--disable-notifications",
                "--disable-infobars",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--window-size=" + cfg.windowWidth() + "," + cfg.windowHeight()
        );
        if (cfg.headless())           opts.addArguments("--headless=new");
        if (cfg.incognito())          opts.addArguments("--incognito");
        if (cfg.acceptInsecureCerts()) opts.setAcceptInsecureCerts(true);
        opts.setExperimentalOption("prefs", downloadPrefs());
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
        opts.addArguments("-width", String.valueOf(cfg.windowWidth()), "-height", String.valueOf(cfg.windowHeight()));

        // Firefox has no Chromium-style "prefs" map — each key is set individually, and unlike
        // Chrome it shows an Open/Save picker for unrecognised MIME types unless the type is
        // pre-allowed via browser.helperApps.neverAsk.saveToDisk.
        String downloadDir = SessionDownloadDirectory.current().toAbsolutePath().toString();
        opts.addPreference("browser.download.folderList", 2); // 2 = use browser.download.dir
        opts.addPreference("browser.download.dir", downloadDir);
        opts.addPreference("browser.download.useDownloadDir", true);
        opts.addPreference("browser.helperApps.neverAsk.saveToDisk",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,"
                + "application/vnd.ms-excel,application/octet-stream");
        opts.addPreference("browser.download.manager.showWhenStarting", false);
        return opts;
    }

    public static EdgeOptions edge() {
        BrowserConfig cfg = ConfigFactory.getBrowserConfig();
        EdgeOptions opts = new EdgeOptions();
        opts.addArguments("--disable-notifications");
        opts.addArguments("--window-size=" + cfg.windowWidth() + "," + cfg.windowHeight());
        if (cfg.headless())           opts.addArguments("--headless=new");
        if (cfg.incognito())          opts.addArguments("--inprivate");
        if (cfg.acceptInsecureCerts()) opts.setAcceptInsecureCerts(true);
        opts.setExperimentalOption("prefs", downloadPrefs());
        return opts;
    }
}
