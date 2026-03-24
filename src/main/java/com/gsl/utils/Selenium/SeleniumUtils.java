package com.gsl.utils.Selenium;

import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import com.gsl.reports.ExtentLogger;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class SeleniumUtils {

    private SeleniumUtils() {}

    public static void click(By by, String elementName) {
        WebElement element = waitUntilElementPresent(by);
        element.click();
        ExtentLogger.info(elementName + " is clicked successfully");
    }

    public static void click(By by, WaitType waitType, String elementName) {
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();
        WebElement element;
        if (waitType == WaitType.PRESENCE) {
            element = waitUntilElementPresent(by);
            element.click();
            ExtentLogger.info(elementName + " is clicked successfully");
        } else if (waitType == WaitType.CLICKABLE) {
            element = waitUntilElementClickable(by);
            element.click();
            ExtentLogger.info(elementName + " is clicked successfully");
        } else if (waitType == WaitType.VISIBLE) {
            element = waitUntilElementVisible(by);
            element.click();
            ExtentLogger.info(elementName + " is clicked successfully");
        }
    }

    public static void click(WebElement element, WaitType waitType, String elementName) {
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();
        if (waitType == WaitType.CLICKABLE) {
            WebDriverWait wait = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(ConfigFactory.getConfig().timeout()));
            wait.until(ExpectedConditions.elementToBeClickable(element)).click();
        } else if (waitType == WaitType.VISIBLE) {
            WebDriverWait wait = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(ConfigFactory.getConfig().timeout()));
            wait.until(ExpectedConditions.visibilityOf(element)).click();
        } else {
            element.click();
        }
        ExtentLogger.info(elementName + " is clicked successfully");
    }

    public static void jsClick(By by, String elementName) {
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();
        WebDriverWait wait = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(ConfigFactory.getConfig().timeout()));
        WebElement element = wait.until(ExpectedConditions.elementToBeClickable(by));
        JavascriptExecutorUtils.click(element);
        ExtentLogger.info(elementName + " is clicked successfully");
    }

    public static WebElement findChildElement(By parentLocator, By childLocator) {
        WebDriverWait wait = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(ConfigFactory.getConfig().timeout()));
        return wait.until(ExpectedConditions.presenceOfNestedElementLocatedBy(parentLocator, childLocator));
    }

    public static boolean elementDisappear(By by,String elementName){
        boolean state = waitUntilElementInVisible(by);
        if (state==true){
        ExtentLogger.info(elementName + " is checked that now it is invisible");
        }else{
            ExtentLogger.info(elementName + " is checked that now it is still visible");
        }
        return state;
    }
    public static void sendKeys(By by, WaitType waitType,String value, String elementName) {
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();
        WebElement element;
        if (waitType == WaitType.PRESENCE) {
            element = waitUntilElementPresent(by);
            element.clear();
            element.sendKeys(value);
            ExtentLogger.info("SendKeys is done successfully for element "+elementName + " with value "+value);
        } else if (waitType == WaitType.CLICKABLE) {
            element = waitUntilElementClickable(by);
            element.clear();
            element.sendKeys(value);
            ExtentLogger.info("SendKeys is done successfully for element "+elementName + " with value "+value);
        } else if (waitType == WaitType.VISIBLE) {
            element = waitUntilElementVisible(by);
            element.clear();
            element.sendKeys(value);
            ExtentLogger.info("SendKeys is done successfully for element "+elementName + " with value "+value);
        }
    }

    public static void sendKeys(By by, String value, String elementName) {
        WebElement element = waitUntilElementPresent(by);
        element.clear();
        element.sendKeys(value);
        ExtentLogger.info(value + " is entered sucessfully in " + elementName);
    }

    public static void clear(By by,String elementName){
        WebElement element = waitUntilElementVisible(by);
        element.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        element.sendKeys(Keys.DELETE);
    }
    public static String getAttribute(By by, String attributeName, String elementName) {
        WebElement element = waitUntilElementVisible(by);
        String value = element.getAttribute(attributeName);
        ExtentLogger.info("Attribute '" + attributeName + "' from " + elementName + " is: '" + value + "'");
        return value;
    }

    private static WebElement waitUntilElementPresent(By by) {
        WebDriverWait wait = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(ConfigFactory.getConfig().timeout()));
        return wait.until(ExpectedConditions.presenceOfElementLocated(by));
    }

    private static WebElement waitUntilElementClickable(By by) {
        WebDriverWait wait = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(ConfigFactory.getConfig().timeout()));
        return wait.until(ExpectedConditions.elementToBeClickable(by));
    }

    private static WebElement waitUntilElementClickable(By by,Integer addedtime) {
        WebDriverWait wait = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(ConfigFactory.getConfig().timeout()).plusSeconds(addedtime));
        return wait.until(ExpectedConditions.elementToBeClickable(by));
    }

    private static WebElement waitUntilElementVisible(By by) {
        WebDriverWait wait = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(ConfigFactory.getConfig().timeout()));
        return wait.until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    private static  boolean waitUntilElementInVisible(By by) {
        WebDriverWait wait = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(ConfigFactory.getConfig().timeout()));
        return wait.until(ExpectedConditions.invisibilityOfElementLocated(by));
    }

    public static String getText(By by, String elementName) {
        WebElement element = waitUntilElementVisible(by);
        String text = element.getText();
        ExtentLogger.info("Text from " + elementName + " is: '" + text + "'");
        return text;
    }

    public static void waitForInputReady(By locator) {
        WebDriverWait wait = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(10));
        wait.until(driver -> {
            WebElement el = driver.findElement(locator);
            return el.isDisplayed()
                    && el.isEnabled()
                    && el.getAttribute("readonly") == null;
        });
    }




}
