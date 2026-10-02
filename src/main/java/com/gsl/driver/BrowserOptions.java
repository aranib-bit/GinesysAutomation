package com.gsl.driver;

import com.gsl.config.BrowserConfig;
import com.gsl.config.ConfigFactory;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.http.ClientConfig;

import java.time.Duration;
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

    /** Chrome options with framework-standard stability flags, the configured window size, and (when enabled) headless/incognito/insecure-certs flags plus per-session download prefs. */
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

    /** Firefox options mirroring {@link #chrome()}'s flags/download behavior, but set via individual preferences since Firefox has no Chromium-style "prefs" map. */
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

    /**
     * Round 19 — shared WebDriver command read-timeout, applied to every LOCAL driver
     * (Chrome/Edge/Firefox). Back to Selenium's own built-in 180s default as of 2026-09-28 (see
     * {@code ExecutionConfig#webdriverCommandReadTimeoutSeconds()} for why a shorter 60s value was
     * tried and reverted). Value is fully config-driven via that method (`execution.properties`)
     * — no literal timeout value here.
     */
    public static ClientConfig clientConfig() {
        int seconds = ConfigFactory.getExecutionConfig().webdriverCommandReadTimeoutSeconds();
        return ClientConfig.defaultConfig().readTimeout(Duration.ofSeconds(seconds));
    }

    /** Edge options mirroring {@link #chrome()}'s flags, window size, and per-session download prefs (Edge is Chromium-based, so it shares the same "prefs" map shape). */
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
