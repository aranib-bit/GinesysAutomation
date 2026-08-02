package com.gsl.pages.Mobile.WMS;

import com.gsl.testdata.UserCredential;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;

/**
 * WMS app login screen — one example screen demonstrating the pattern. Locators below are
 * placeholders; replace with real ones once the WMS app's screens are shared (out of scope
 * for this build beyond establishing the shape other screens follow).
 */
public class WmsLoginScreen extends WmsBaseScreen {

    private static final By USERNAME_FIELD = By.id("wms.login.username");
    private static final By PASSWORD_FIELD = By.id("wms.login.password");
    private static final By LOGIN_BUTTON   = By.id("wms.login.submit");

    public WmsLoginScreen(AppiumDriver driver) {
        super(driver);
    }

    public void login(UserCredential credential) {
        waitForVisible(USERNAME_FIELD);
        driver.findElement(USERNAME_FIELD).sendKeys(credential.username());
        driver.findElement(PASSWORD_FIELD).sendKeys(credential.password());
        driver.findElement(LOGIN_BUTTON).click();
    }
}
