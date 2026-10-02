package com.gsl.utils.Selenium;


import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Static Actions-API helpers (right-click, hover, drag, scroll-into-view, hover+click) used
 * directly by page objects that need a raw {@link Actions} interaction rather than the
 * wait/retry/report-integrated helpers in {@code SeleniumUtils}/{@code ActionUtils}.
 */
public class SeleniumActions {

    private WebDriver driver;
    private WebDriverWait wait;
    private Actions actions;

    /** Right-clicks (context-clicks) {@code by}'s element. */
    public static void rightClick(By by) {
        WebDriver driver = DriverManager.getDriver();
        WebElement element = driver.findElement(by);
        Actions actions = new Actions(driver);
        actions.contextClick(element).perform();
    }

    /** Scrolls {@code by}'s element into view via JS {@code scrollIntoView} (smooth, centered). */
    public static void scrollToElement(By by) {
        WebDriver driver = DriverManager.getDriver();
        WebElement element = driver.findElement(by);
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});", element);
    }

    public static void moveToElementAndClick(By by) {
        WebDriver driver = DriverManager.getDriver();
        // untilClickable rather than a bare findElement: the caller frequently needs to click
        // something that only appears after a prior action renders it (e.g. a dropdown item that
        // shows up after clicking its trigger). A local Windows Chrome session is fast enough that
        // the element is usually already there; a loaded Docker/grid session is not, and a bare
        // findElement() throws NoSuchElementException on that timing gap.
        WebElement element = WaitUtils.untilClickable(by);
        Actions actions = new Actions(driver);
        actions.moveToElement(element).click().build().perform();
    }

    /** Clears {@code by}'s element then sends {@code text} to it. */
    public static void moveToElementAndSendKeys(By by, String text) {
        WebDriver driver = DriverManager.getDriver();
        WebElement element = driver.findElement(by);
        Actions actions = new Actions(driver);
        element.clear();
        actions.moveToElement(element).sendKeys(text).build().perform();
    }

    /** Sends {@code key} to whichever element currently has focus (no explicit target locator). */
    public static void sendKeysToActiveElement(Keys key) {
        WebDriver driver = DriverManager.getDriver();
        Actions actions = new Actions(driver);
        actions.sendKeys(key).build().perform();
    }

    /** Moves to {@code by}'s element and sends {@code key} to it. */
    public static void sendKeyToElement(By by, Keys key) {
        WebDriver driver = DriverManager.getDriver();
        WebElement element = driver.findElement(by);
        Actions actions = new Actions(driver);
        actions.moveToElement(element).sendKeys(key).build().perform();
    }

    /** Returns {@code by}'s element's {@code textContent}, falling back to a JS read if the attribute comes back blank. */
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
    /**
     * Hovers over {@code hoverTarget} then clicks {@code clickTarget}, retrying up to 3 times on
     * {@link StaleElementReferenceException}/{@link TimeoutException}.
     */
    public static void moveToElementAndClick(By hoverTarget, By clickTarget) {
        WebDriver driver = DriverManager.getDriver();
        WebDriverWait wait = WaitUtils.webDriverWait(ConfigFactory.getWaitConfig().interactionWaitSeconds());
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

    /**
     * Clicks and holds {@code sourceLocator}'s element, waits for {@code targetLocator} to become
     * clickable, then releases onto it — a drag-like press-wait-release interaction.
     *
     * @throws RuntimeException if {@code targetLocator} never becomes clickable
     */
    public static void clickAndHoldThenClick(By sourceLocator, By targetLocator) {
        WebDriver driver = DriverManager.getDriver();
        WebDriverWait wait = WaitUtils.webDriverWait(ConfigFactory.getWaitConfig().interactionWaitSeconds());
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
