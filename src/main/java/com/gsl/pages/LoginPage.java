package com.gsl.pages;

import com.gsl.annotations.LogStep;
import com.gsl.enums.WaitType;
import org.openqa.selenium.By;

import static com.gsl.utils.Selenium.SeleniumUtils.*;

public class LoginPage {

    private static final By TXTBOX_USERNAME = By.name("username");
    private static final By TXTBOX_PASSWORD = By.name("password");
    private static final By BTN_LOGIN = By.xpath("//span[text()='Login']");
    private static final By SPLASHSCREEN=By.id("splashScreen");
    private static final By AUTHENTICATOR_LOADER=By.xpath("//*[@class='x-window vport x-layer x-window-default x-border-box x-window-maximized']");
    //For time being addeing OK
    private static final By OK_BUTTON=By.xpath("//span[text()='OK']");
    private static final By BTN_CONCURRENT_SESSION_YES =
            By.xpath("//div[contains(@class,'x-message-box')]//span[text()='Yes']");

    private LoginPage clickOk(){
        click(OK_BUTTON,WaitType.CLICKABLE,"OK_BUTTON");
        return this;
    }


    private LoginPage setUserName(String userName) {
        sendKeys(TXTBOX_USERNAME, userName, "Username");
        return this;
    }

    private LoginPage setPassword(String password) {
        sendKeys(TXTBOX_PASSWORD, password, "Password");
        return this;
    }

    private LoginPage setLogin() {
        click(BTN_LOGIN, WaitType.CLICKABLE, "Login Button");
        return this;
    }

    private LoginPage handleConcurrentSessionPopup() {
        if (isElementPresent(BTN_CONCURRENT_SESSION_YES, 3)) {
            click(BTN_CONCURRENT_SESSION_YES, WaitType.CLICKABLE, "Concurrent Session Yes Button");
        }
        return this;
    }

    private HomePage checkAuthenticationLoader(){
        elementDisappear(AUTHENTICATOR_LOADER,"Authentication Loader");
        return new HomePage();
    }

    private LoginPage checkSplashScreenLoader(){
        elementDisappear(SPLASHSCREEN,"Application Loader");
        return this;
    }


    @LogStep("Login to Application")
    public HomePage loginToApplication(String username, String password) {
        return checkSplashScreenLoader()
                .setUserName(username)
                .setPassword(password)
                .setLogin()
                .handleConcurrentSessionPopup()
                .checkAuthenticationLoader();
    }
}