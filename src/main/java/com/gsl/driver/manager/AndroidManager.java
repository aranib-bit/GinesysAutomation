package com.gsl.driver.manager;

import com.gsl.driver.MobileDriverData;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import org.openqa.selenium.WebDriver;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Single-responsibility manager for Android AppiumDriver creation.
 * Builds UiAutomator2Options from MobileDriverData and starts an AndroidDriver.
 * Configure MobileDriverData (deviceName, platformVersion, app, appiumUrl) before use.
 */
public final class AndroidManager {

    private AndroidManager() {}

    public static WebDriver getDriver(MobileDriverData data) {
        UiAutomator2Options options = new UiAutomator2Options();
        options.setDeviceName(data.getDeviceName());
        options.setPlatformVersion(data.getPlatformVersion());
        options.setAutomationName(
                data.getAutomationName() != null ? data.getAutomationName() : "UiAutomator2");
        if (data.getApp() != null && !data.getApp().isBlank()) {
            options.setApp(data.getApp());
        }
        try {
            return new AndroidDriver(new URL(data.getAppiumUrl()), options);
        } catch (MalformedURLException e) {
            throw new RuntimeException("[AndroidManager] Invalid Appium URL: " + data.getAppiumUrl(), e);
        }
    }
}
