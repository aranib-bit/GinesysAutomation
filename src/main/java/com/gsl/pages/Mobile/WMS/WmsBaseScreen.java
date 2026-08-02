package com.gsl.pages.Mobile.WMS;

import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Common base for WMS mobile screens — structure only, per direction: real screens/locators
 * come in a follow-up once the app is shared. Establishes the pattern other WMS screens follow.
 */
public abstract class WmsBaseScreen {

    protected final AppiumDriver driver;
    protected final WebDriverWait wait;

    protected WmsBaseScreen(AppiumDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    protected void waitForVisible(org.openqa.selenium.By locator) {
        wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }
}
