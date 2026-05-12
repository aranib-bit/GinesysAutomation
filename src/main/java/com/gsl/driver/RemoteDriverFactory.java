package com.gsl.driver;

import com.gsl.config.ConfigFactory;
import com.gsl.driver.BrowserOptions;
import com.gsl.enums.Browser;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.AbstractDriverOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.EnumMap;
import java.util.function.Function;

/**
 * Remote driver factory refactored with EnumMap<Browser, Function<WebDriverData, WebDriver>>.
 * Replaces legacy DesiredCapabilities + if/else with typed Selenium 4 options and lazy Functions.
 * New code should prefer SeleniumGridProvider via MasterDriverFactory.
 */
public final class RemoteDriverFactory {

    private RemoteDriverFactory() {}

    private static final EnumMap<Browser, Function<WebDriverData, WebDriver>> REMOTE_FACTORIES =
            new EnumMap<>(Browser.class);

    static {
        REMOTE_FACTORIES.put(Browser.CHROME,  data -> createRemote(BrowserOptions.chrome(),  data.getGridUrl()));
        REMOTE_FACTORIES.put(Browser.FIREFOX, data -> createRemote(BrowserOptions.firefox(), data.getGridUrl()));
        REMOTE_FACTORIES.put(Browser.EDGE,    data -> createRemote(BrowserOptions.edge(),    data.getGridUrl()));
    }

    /** Returns a RemoteWebDriver using typed options from WebDriverData — lazy-evaluated via Function. */
    public static WebDriver getDriver(WebDriverData data) {
        Function<WebDriverData, WebDriver> factory = REMOTE_FACTORIES.get(data.getBrowserType());
        if (factory == null) {
            throw new IllegalArgumentException("[RemoteDriverFactory] Unsupported browser: " + data.getBrowserType());
        }
        return factory.apply(data);
    }

    /** Backward-compatible String overload — builds WebDriverData from config and delegates. */
    public static WebDriver getRemoteDriver(String browser) throws MalformedURLException {
        WebDriverData data = WebDriverData.builder()
                .browserType(Browser.valueOf(browser.trim().toUpperCase()))
                .gridUrl(ConfigFactory.getConfig().gridUrl())
                .headless(ConfigFactory.getConfig().headless())
                .incognito(ConfigFactory.getConfig().incognito())
                .acceptInsecureCerts(ConfigFactory.getConfig().acceptInsecureCerts())
                .build();
        return getDriver(data);
    }

    private static WebDriver createRemote(AbstractDriverOptions<?> opts, String gridUrl) {
        try {
            return new RemoteWebDriver(new URL(gridUrl), opts);
        } catch (MalformedURLException e) {
            throw new RuntimeException("[RemoteDriverFactory] Invalid grid URL: " + gridUrl, e);
        }
    }
}
