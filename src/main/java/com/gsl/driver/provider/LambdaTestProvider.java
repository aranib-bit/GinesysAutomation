package com.gsl.driver.provider;

import com.gsl.config.BrowserConfig;
import com.gsl.config.ConfigFactory;
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
 * Creates a RemoteWebDriver against LambdaTest hub.
 * LambdaTest-specific options are passed via the lt:options capability map.
 * Credentials are read from lambdatest.username / lambdatest.accesskey in config.properties.
 */
public final class LambdaTestProvider implements ExecutionProvider {

    private static final String HUB_URL = "https://hub.lambdatest.com/wd/hub";

    @Override
    public WebDriver createDriver(Browser browser) {
        try {
            BrowserConfig cfg = ConfigFactory.getBrowserConfig();

            HashMap<String, Object> ltOptions = new HashMap<>();
            ltOptions.put("username",    cfg.lambdaTestUsername());
            ltOptions.put("accessKey",   cfg.lambdaTestAccessKey());
            ltOptions.put("sessionName", TestContextManager.getTestName());
            ltOptions.put("build",       cfg.browserStackBuildName());

            AbstractDriverOptions<?> opts = switch (browser) {
                case CHROME  -> BrowserOptions.chrome();
                case FIREFOX -> BrowserOptions.firefox();
                case EDGE    -> BrowserOptions.edge();
                default      -> throw new IllegalArgumentException(
                        "[LambdaTestProvider] Unsupported browser: " + browser);
            };
            opts.setCapability("lt:options", ltOptions);

            return new RemoteWebDriver(new URL(HUB_URL), opts);
        } catch (MalformedURLException e) {
            throw new RuntimeException("[LambdaTestProvider] Invalid hub URL: " + HUB_URL, e);
        }
    }
}
