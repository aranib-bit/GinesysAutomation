package com.gsl.utils.Selenium;


import com.gsl.driver.DriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class SeleniumActions {

    /**
     * Performs a right-click (context click) on the element located by the provided By locator.
     *
     * @param by The Selenium By by for the element
     */

    private WebDriver driver;
    private WebDriverWait wait;
    private Actions actions;

    public static void rightClick(By by) {
        WebDriver driver = DriverManager.getDriver();
        WebElement element = driver.findElement(by);
        Actions actions = new Actions(driver);
        actions.contextClick(element).perform();
    }

    public static void scrollToElement(By by) {
        WebDriver driver = DriverManager.getDriver();
        WebElement element = driver.findElement(by);
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});", element);
    }

    public static void moveToElementAndClick(By by) {
        WebDriver driver = DriverManager.getDriver();
        WebElement element = driver.findElement(by);
        Actions actions = new Actions(driver);
        actions.moveToElement(element).click().build().perform();
    }

    public static void moveToElementAndSendKeys(By by, String text) {
        WebDriver driver = DriverManager.getDriver();
        WebElement element = driver.findElement(by);
        Actions actions = new Actions(driver);
        element.clear();
        actions.moveToElement(element).sendKeys(text).build().perform();
    }

    public static void sendKeysToActiveElement(Keys key) {
        WebDriver driver = DriverManager.getDriver();
        Actions actions = new Actions(driver);
        actions.sendKeys(key).build().perform();
    }

    public static void sendKeyToElement(By by, Keys key) {
        WebDriver driver = DriverManager.getDriver();
        WebElement element = driver.findElement(by);
        Actions actions = new Actions(driver);
        actions.moveToElement(element).sendKeys(key).build().perform();
    }

    public static String getText(By by) {
        WebDriver driver = DriverManager.getDriver();
        WebElement element = driver.findElement(by);
        String text = element.getAttribute("textContent");
        if (text == null || text.isBlank()) {
            text = (String) ((JavascriptExecutor) driver).executeScript("return arguments[0].textContent;", element);
        }
        System.out.println("Extracted text: " + text);
        return text == null ? "" : text.trim();

    }



    // 🔹 New method: hover on one element, then click another
    public static void moveToElementAndClick(By hoverTarget, By clickTarget) {
        WebDriver driver = DriverManager.getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        Actions actions = new Actions(driver);

        int attempts = 0;
        boolean success = false;

        while (attempts < 3 && !success) {
            try {
                WebElement hoverElement = wait.until(ExpectedConditions.visibilityOfElementLocated(hoverTarget));
                actions.moveToElement(hoverElement).perform();
                WebElement clickElement = wait.until(ExpectedConditions.elementToBeClickable(clickTarget));
                clickElement.click();

                success = true;
            } catch (StaleElementReferenceException | TimeoutException e) {
                System.out.println("Retry hoverAndClick due to: " + e.getMessage());
                attempts++;
            }
        }

        if (!success) {
            throw new RuntimeException("Failed to perform moveToElementAndClick on: " + clickTarget);
        }
    }

    public static void clickAndHoldThenClick(By sourceLocator, By targetLocator) {
        WebDriver driver = DriverManager.getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        Actions actions = new Actions(driver);

        try {
            // Locate source element
            WebElement source = wait.until(ExpectedConditions.elementToBeClickable(sourceLocator));

            // Click and hold on source
            actions.clickAndHold(source).build().perform();

            // Wait for target to become visible
            WebElement target = wait.until(ExpectedConditions.elementToBeClickable(targetLocator));

            // Release hold and click target
            actions.release(source).moveToElement(target).click().build().perform();

        } catch (TimeoutException e) {
            throw new RuntimeException("Target element not visible after holding source element: " + targetLocator, e);
        }
    }


//    public static String getAttribute(By by, String attribute) {
//        WebDriver driver = DriverManager.getDriver();
//        return driver.findElement(by).getAttribute(attribute);
//    }
}
