package com.gsl.driver.manager;

import com.gsl.driver.BrowserOptions;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver;

/**
 * Single-responsibility manager for Edge WebDriver creation.
 * Referenced via method reference {@code EdgeManager::getDriver} in
 * {@code LocalExecutionProvider}'s browser-to-factory map.
 */
public final class EdgeManager {

    private EdgeManager() {}

    /** Ensures a matching msedgedriver binary via {@link WebDriverManager}, then starts a local {@link EdgeDriver} using {@link BrowserOptions#edge()}. */
    public static WebDriver getDriver() {
        WebDriverManager.edgedriver().setup();
        return new EdgeDriver(BrowserOptions.edge(), BrowserOptions.clientConfig());
    }
}
