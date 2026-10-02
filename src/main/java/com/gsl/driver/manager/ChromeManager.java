package com.gsl.driver.manager;

import com.gsl.driver.BrowserOptions;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Single-responsibility manager for Chrome WebDriver creation.
 * Referenced via method reference {@code ChromeManager::getDriver} in
 * {@code LocalExecutionProvider}'s browser-to-factory map.
 */
public final class ChromeManager {

    private ChromeManager() {}

    /** Ensures a matching chromedriver binary via {@link WebDriverManager}, then starts a local {@link ChromeDriver} using {@link BrowserOptions#chrome()}. */
    public static WebDriver getDriver() {
        WebDriverManager.chromedriver().setup();
        return new ChromeDriver(BrowserOptions.chrome(), BrowserOptions.clientConfig());
    }
}
