package com.gsl.driver.provider;

import com.gsl.config.ConfigFactory;
import com.gsl.driver.BrowserOptions;
import com.gsl.enums.Browser;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.AbstractDriverOptions;
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
            return new RemoteWebDriver(new URL(getGridUrl()), opts);
        } catch (MalformedURLException e) {
            throw new RuntimeException("[SeleniumGridProvider] Invalid grid URL: " + getGridUrl(), e);
        }
    }

    /** Overridden by DockerGridProvider to use docker.grid.url. */
    protected String getGridUrl() {
        return ConfigFactory.getConfig().gridUrl();
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
