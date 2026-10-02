package com.gsl.driver.manager;

import com.gsl.driver.BrowserOptions;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;

/**
 * Single-responsibility manager for Firefox WebDriver creation.
 * Referenced via method reference {@code FirefoxManager::getDriver} in
 * {@code LocalExecutionProvider}'s browser-to-factory map.
 */
public final class FirefoxManager {

    private FirefoxManager() {}

    /** Ensures a matching geckodriver binary via {@link WebDriverManager}, then starts a local {@link FirefoxDriver} using {@link BrowserOptions#firefox()}. */
    public static WebDriver getDriver() {
        WebDriverManager.firefoxdriver().setup();
        return new FirefoxDriver(BrowserOptions.firefox(), BrowserOptions.clientConfig());
    }
}
