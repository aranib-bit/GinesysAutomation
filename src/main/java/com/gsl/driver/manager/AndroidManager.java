package com.gsl.driver.manager;

import com.gsl.driver.MobileDriverData;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import org.openqa.selenium.WebDriver;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Single-responsibility manager for Android {@link AppiumDriver} creation, mirroring the
 * {@code ChromeManager}/{@code EdgeManager}/{@code FirefoxManager} pattern for mobile.
 * Builds a {@link UiAutomator2Options} from {@link MobileDriverData} and starts an
 * {@link AndroidDriver}; invoked from {@code AppiumProvider} when the active platform is Android.
 *
 * <p>Configure {@link MobileDriverData} (deviceName, platformVersion, app, appiumUrl) before use.
 */
public final class AndroidManager {

    private AndroidManager() {}

    /**
     * Builds a {@link UiAutomator2Options} from {@code data} and starts an {@link AndroidDriver}
     * against the Appium server at {@code data.getAppiumUrl()}.
     *
     * @param data mobile config (deviceName, platformVersion, automationName, app, appiumUrl)
     * @return a WebDriver backed by a live AndroidDriver session
     * @throws RuntimeException if {@code data.getAppiumUrl()} is not a valid URL
     */
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
