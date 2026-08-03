package com.gsl.driver.provider;

import com.gsl.config.ConfigFactory;
import com.gsl.driver.BrowserOptions;
import com.gsl.enums.Browser;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.AbstractDriverOptions;
import org.openqa.selenium.remote.Augmenter;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Creates a RemoteWebDriver against a Selenium Grid 4 hub.
 * Uses typed Selenium 4 options (ChromeOptions / FirefoxOptions / EdgeOptions) instead of
 * the legacy DesiredCapabilities, enabling full capability inheritance from BrowserOptions.
 * Grid URL is read from config key: grid.url
 */
public class SeleniumGridProvider implements ExecutionProvider {

    @Override
    public WebDriver createDriver(Browser browser) {
        try {
            AbstractDriverOptions<?> opts = buildOptions(browser);
            RemoteWebDriver raw = new RemoteWebDriver(new URL(getGridUrl()), opts);
            // Plain RemoteWebDriver does NOT implement HasDevTools — only local driver classes
            // (ChromeDriver, EdgeDriver) do that directly. Augmenter inspects the session's
            // returned capabilities (the grid advertises "se:cdp": "ws://...") and wraps the
            // driver in a dynamic subclass that adds HasDevTools support wired to that endpoint.
            // Without this, every CDP-dependent feature against a remote/grid session — this
            // includes ViewportEnforcer's device-metrics override AND CdpNetworkCapture's request
            // logging — silently no-ops via its own `driver instanceof HasDevTools` check.
            return new Augmenter().augment(raw);
        } catch (MalformedURLException e) {
            throw new RuntimeException("[SeleniumGridProvider] Invalid grid URL: " + getGridUrl(), e);
        }
    }

    /** Overridden by DockerGridProvider to use docker.grid.url. */
    protected String getGridUrl() {
        return ConfigFactory.getBrowserConfig().gridUrl();
    }

    protected AbstractDriverOptions<?> buildOptions(Browser browser) {
        return switch (browser) {
            case CHROME  -> BrowserOptions.chrome();
            case FIREFOX -> BrowserOptions.firefox();
            case EDGE    -> BrowserOptions.edge();
            default      -> throw new IllegalArgumentException(
                    "[SeleniumGridProvider] Unsupported browser: " + browser);
        };
    }
}
