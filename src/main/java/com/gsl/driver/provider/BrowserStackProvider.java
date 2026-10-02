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
 * {@link ExecutionProvider} for BrowserStack cloud execution, registered against
 * {@code RunMode.BROWSERSTACK} in {@link ExecutionProviderRegistry}.
 * Creates a {@link RemoteWebDriver} against the BrowserStack hub.
 * BrowserStack-specific options are passed via the {@code bs:options} capability map.
 * Credentials are read from {@code browserstack.username} / {@code browserstack.accesskey} in config.properties.
 */
public final class BrowserStackProvider implements ExecutionProvider {

    private static final String HUB_URL = "https://hub.browserstack.com/wd/hub";

    /**
     * Builds a Chrome/Firefox/Edge {@link AbstractDriverOptions} for {@code browser}, attaches
     * BrowserStack's {@code bs:options} capability (credentials, build/session name), and opens
     * a {@link RemoteWebDriver} against the BrowserStack hub.
     *
     * @throws IllegalArgumentException if {@code browser} is not Chrome/Firefox/Edge
     * @throws RuntimeException if the hub URL is malformed
     */
    @Override
    public WebDriver createDriver(Browser browser) {
        try {
            BrowserConfig cfg = ConfigFactory.getBrowserConfig();

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
