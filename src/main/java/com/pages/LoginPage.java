package com.pages;

import com.enums.WaitType;
import org.openqa.selenium.By;

import static com.utils.Selenium.SeleniumUtils.click;
import static com.utils.Selenium.SeleniumUtils.sendKeys;

public class LoginPage {

    private static final By TXTBOX_USERNAME = By.name("username");
    private static final By TXTBOX_PASSWORD = By.name("password");
    private static final By BTN_LOGIN = By.xpath("//*[@type='submit']");


    private LoginPage setUserName(String userName) {
        sendKeys(TXTBOX_USERNAME, userName, "Username");
        return this;
    }

    private LoginPage setPassword(String password) {
        sendKeys(TXTBOX_PASSWORD, password, "Password");
        return this;
    }

    private HomePage setLogin() {
        click(BTN_LOGIN, WaitType.CLICKABLE, "Login Button");
        return new HomePage();
    }

    public HomePage loginToApplication(String username, String password) {
        return setUserName(username).setPassword(password).setLogin();
    }
}