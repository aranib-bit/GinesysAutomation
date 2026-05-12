package com.gsl.driver.manager;

import com.gsl.driver.BrowserOptions;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Single-responsibility manager for Chrome WebDriver creation.
 * Referenced via method reference ChromeManager::getDriver in LocalExecutionProvider.
 */
public final class ChromeManager {

    private ChromeManager() {}

    public static WebDriver getDriver() {
        WebDriverManager.chromedriver().setup();
        return new ChromeDriver(BrowserOptions.chrome());
    }
}
