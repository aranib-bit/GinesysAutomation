package com.gsl.driver.manager;

import com.gsl.driver.MobileDriverData;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;
import org.openqa.selenium.WebDriver;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Single-responsibility manager for iOS AppiumDriver creation.
 * Builds XCUITestOptions from MobileDriverData and starts an IOSDriver.
 * Configure MobileDriverData (deviceName, platformVersion, bundleId/app, appiumUrl) before use.
 */
public final class IOSManager {

    private IOSManager() {}

    public static WebDriver getDriver(MobileDriverData data) {
        XCUITestOptions options = new XCUITestOptions();
        options.setDeviceName(data.getDeviceName());
        options.setPlatformVersion(data.getPlatformVersion());
        options.setAutomationName(
                data.getAutomationName() != null ? data.getAutomationName() : "XCUITest");
        if (data.getBundleId() != null && !data.getBundleId().isBlank()) {
            options.setBundleId(data.getBundleId());
        } else if (data.getApp() != null && !data.getApp().isBlank()) {
            options.setApp(data.getApp());
        }
        try {
            return new IOSDriver(new URL(data.getAppiumUrl()), options);
        } catch (MalformedURLException e) {
            throw new RuntimeException("[IOSManager] Invalid Appium URL: " + data.getAppiumUrl(), e);
        }
    }
}
