package com.gsl.utils.Selenium;

import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

import static com.gsl.driver.DriverManager.getDriver;

public final class WaitUtils {

    private WaitUtils() {}

    public static WebElement fluentWait(By by) {
        Wait<WebDriver> wait = new FluentWait<>(getDriver())
                .withTimeout(Duration.ofMillis(ConfigFactory.getConfig().itemscanmaxtimeout()))
                .pollingEvery(Duration.ofSeconds(2))
                .ignoring(NoSuchElementException.class)
                .ignoring(ElementClickInterceptedException.class)
                .ignoring(ElementNotInteractableException.class);

        return wait.until(ExpectedConditions.elementToBeClickable(by));
    }

    public static WebElement waitUntilAjaxAndElementClickable(By by) {
        WebDriverWait wait = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(ConfigFactory.getConfig().timeout()));

        // Wait for ExtJS AJAX requests to complete
        wait.until(driver -> (Boolean) ((JavascriptExecutor) driver).executeScript(
                "return (window.Ext && window.Ext.Ajax && !window.Ext.Ajax.isLoading()) || (!window.Ext || !window.Ext.Ajax);"
        ));

        // Wait for the element to be clickable
        return wait.until(ExpectedConditions.elementToBeClickable(by));
    }
}
