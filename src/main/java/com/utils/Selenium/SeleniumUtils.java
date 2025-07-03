package com.utils.Selenium;

import com.config.ConfigFactory;
import com.driver.DriverManager;
import com.enums.WaitType;
import com.reports.ExtentLogger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SeleniumUtils {

    public static void click(By by,String elementName){
        WebElement element=waitUntilElementPresent(by);
        element.click();
        ExtentLogger.info(elementName + " is clicked successfully");
    }

    public static void click(By by, WaitType waitType,String elementName){
        WebElement element;
        if (waitType ==WaitType.PRESENCE) {
            element = waitUntilElementPresent(by);
            element.click();
            ExtentLogger.info(elementName + " is clicked successfully");
        } else if (waitType ==WaitType.CLICKABLE) {
            element = waitUntilElementClickable(by);
            element.click();
            ExtentLogger.info(elementName + " is clicked successfully");
        } else if (waitType==WaitType.VISIBLE) {
            element = waitUntilElementVisible(by);
            element.click();
            ExtentLogger.info(elementName + " is clicked successfully");
        }
    }

    public static  void sendKeys(By by,String value,String elementName){
        WebElement element=waitUntilElementPresent(by);
        element.sendKeys(value);
        ExtentLogger.info(value + " is entered sucessfully in "+ elementName);
    }

    private static WebElement waitUntilElementPresent(By by) {
        WebDriverWait wait=new WebDriverWait(DriverManager.getDriver(), ConfigFactory.getConfig().timeout());
        return wait.until(ExpectedConditions.presenceOfElementLocated(by));
    }

    private static WebElement waitUntilElementClickable(By by) {
        WebDriverWait wait=new WebDriverWait(DriverManager.getDriver(), ConfigFactory.getConfig().timeout());
        return wait.until(ExpectedConditions.elementToBeClickable(by));
    }

    private static WebElement waitUntilElementVisible(By by) {
        WebDriverWait wait=new WebDriverWait(DriverManager.getDriver(), ConfigFactory.getConfig().timeout());
        return wait.until(ExpectedConditions.visibilityOfElementLocated(by));
    }


}
