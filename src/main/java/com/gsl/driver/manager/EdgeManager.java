package com.gsl.driver.manager;

import com.gsl.driver.BrowserOptions;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver;

/**
 * Single-responsibility manager for Edge WebDriver creation.
 * Referenced via method reference EdgeManager::getDriver in LocalExecutionProvider.
 */
public final class EdgeManager {

    private EdgeManager() {}

    public static WebDriver getDriver() {
        WebDriverManager.edgedriver().setup();
        return new EdgeDriver(BrowserOptions.edge());
    }
}
