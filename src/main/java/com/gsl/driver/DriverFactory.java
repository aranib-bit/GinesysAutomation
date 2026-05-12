package com.gsl.driver;

import com.gsl.enums.Browser;
import com.gsl.enums.RunMode;
import org.openqa.selenium.WebDriver;

/**
 * Backward-compatible adapter — parses legacy String runmode/browser args and
 * delegates to MasterDriverFactory. New code should call MasterDriverFactory directly.
 */
public final class DriverFactory {

    private DriverFactory() {}

    public static WebDriver getDriver(String runmode, String browserName) {
        RunMode  mode    = RunMode.valueOf(runmode.toUpperCase());
        Browser  browser = Browser.valueOf(browserName.toUpperCase());
        return MasterDriverFactory.create(mode, browser);
    }
}
