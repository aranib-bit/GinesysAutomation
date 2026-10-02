package com.gsl.driver.manager;

import com.gsl.driver.MobileDriverData;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;
import org.openqa.selenium.WebDriver;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Single-responsibility manager for iOS AppiumDriver creation, the iOS counterpart of
 * {@link AndroidManager}. Builds an {@link XCUITestOptions} from {@link MobileDriverData} and
 * starts an {@link IOSDriver}; invoked from {@code AppiumProvider} when the active platform is iOS.
 *
 * <p>Configure {@link MobileDriverData} (deviceName, platformVersion, bundleId/app, appiumUrl)
 * before use.
 */
public final class IOSManager {

    private IOSManager() {}

    /**
     * Builds an {@link XCUITestOptions} from {@code data} and starts an {@link IOSDriver} against
     * the Appium server at {@code data.getAppiumUrl()}. Prefers {@code bundleId} over {@code app}
     * when both are set.
     *
     * @param data mobile config (deviceName, platformVersion, automationName, bundleId/app, appiumUrl)
     * @return a WebDriver backed by a live IOSDriver session
     * @throws RuntimeException if {@code data.getAppiumUrl()} is not a valid URL
     */
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
