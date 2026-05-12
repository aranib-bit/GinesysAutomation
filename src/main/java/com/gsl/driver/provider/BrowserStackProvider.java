package com.gsl.driver.provider;

import com.gsl.config.ConfigFactory;
import com.gsl.config.FrameworkConfig;
import com.gsl.driver.BrowserOptions;
import com.gsl.enums.Browser;
import com.gsl.utils.TestContextManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.AbstractDriverOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

/**
 * Creates a RemoteWebDriver against BrowserStack hub.
 * BrowserStack-specific options are passed via the bs:options capability map.
 * Credentials are read from browserstack.username / browserstack.accesskey in config.properties.
 */
public final class BrowserStackProvider implements ExecutionProvider {

    private static final String HUB_URL = "https://hub.browserstack.com/wd/hub";

    @Override
    public WebDriver createDriver(Browser browser) {
        try {
            FrameworkConfig cfg = ConfigFactory.getConfig();

            HashMap<String, Object> bsOptions = new HashMap<>();
            bsOptions.put("userName",    cfg.browserStackUsername());
            bsOptions.put("accessKey",   cfg.browserStackAccessKey());
            bsOptions.put("buildName",   cfg.browserStackBuildName());
            bsOptions.put("sessionName", TestContextManager.getTestName());

            AbstractDriverOptions<?> opts = switch (browser) {
                case CHROME  -> BrowserOptions.chrome();
                case FIREFOX -> BrowserOptions.firefox();
                case EDGE    -> BrowserOptions.edge();
                default      -> throw new IllegalArgumentException(
                        "[BrowserStackProvider] Unsupported browser: " + browser);
            };
            opts.setCapability("bs:options", bsOptions);

            return new RemoteWebDriver(new URL(HUB_URL), opts);
        } catch (MalformedURLException e) {
            throw new RuntimeException("[BrowserStackProvider] Invalid hub URL: " + HUB_URL, e);
        }
    }
}
