package com.gsl.driver.provider;

import com.gsl.enums.Browser;
import org.openqa.selenium.WebDriver;

/**
 * Appium driver provider for mobile execution.
 * Configure UiAutomator2Options / XCUITestOptions per platform before enabling.
 */
public final class AppiumProvider implements ExecutionProvider {

    @Override
    public WebDriver createDriver(Browser browser) {
        throw new UnsupportedOperationException(
                "[AppiumProvider] Configure Appium capabilities (appium.url, platform, deviceName) " +
                "in config.properties and implement UiAutomator2Options / XCUITestOptions before use.");
    }

    @Override
    public String getProviderName() {
        return "AppiumProvider";
    }
}
