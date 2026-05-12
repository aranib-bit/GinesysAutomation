package com.gsl.driver.manager;

import com.gsl.driver.BrowserOptions;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;

/**
 * Single-responsibility manager for Firefox WebDriver creation.
 * Referenced via method reference FirefoxManager::getDriver in LocalExecutionProvider.
 */
public final class FirefoxManager {

    private FirefoxManager() {}

    public static WebDriver getDriver() {
        WebDriverManager.firefoxdriver().setup();
        return new FirefoxDriver(BrowserOptions.firefox());
    }
}
