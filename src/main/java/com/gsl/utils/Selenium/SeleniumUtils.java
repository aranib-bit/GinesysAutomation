package com.gsl.utils.Selenium;

import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import com.gsl.popup.PopupMonitor;
import com.gsl.reports.ExtentLogger;
import com.gsl.utils.ElementMeta;
import com.gsl.utils.Log;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class SeleniumUtils {

    private SeleniumUtils() {}

    public static void click(By by, String elementName) {
        WebElement element = waitUntilElementPresent(by);
        clickWithOverlayRetry(element, elementName);
        ExtentLogger.info(elementName + " is clicked successfully");
    }

    public static void click(By by, WaitType waitType, String elementName) {
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();
        WebElement element;
        if (waitType == WaitType.PRESENCE) {
            element = waitUntilElementPresent(by);
            clickWithOverlayRetry(element, elementName);
            ExtentLogger.info(elementName + " is clicked successfully");
        } else if (waitType == WaitType.CLICKABLE) {
            element = waitUntilElementClickable(by);
            clickWithOverlayRetry(element, elementName);
            ExtentLogger.info(elementName + " is clicked successfully");
        } else if (waitType == WaitType.VISIBLE) {
            element = waitUntilElementVisible(by);
            clickWithOverlayRetry(element, elementName);
            ExtentLogger.info(elementName + " is clicked successfully");
        }
    }

    public static void click(WebElement element, WaitType waitType, String elementName) {
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();
        WebElement ready;
        WebDriverWait wait = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(ConfigFactory.getConfig().timeout()));
        if (waitType == WaitType.CLICKABLE) {
            ready = wait.until(ExpectedConditions.elementToBeClickable(element));
        } else if (waitType == WaitType.VISIBLE) {
            ready = wait.until(ExpectedConditions.visibilityOf(element));
        } else {
            ready = element;
        }
        clickWithOverlayRetry(ready, elementName);
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

    public static boolean elementDisappear(By by, String elementName) {
        boolean state = waitUntilElementInVisible(by);
        if (state) {
            Log.debug("{} is now invisible", elementName);
        } else {
            Log.debug("{} is still visible", elementName);
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

    public static void pressTab(By by, String elementName) {
        WebElement element = waitUntilElementPresent(by);
        element.sendKeys(Keys.TAB);
        ExtentLogger.info("Tab key pressed on element: " + elementName);
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

    public static String getDomProperty(By by, String propertyName, String elementName) {
        WebElement element = waitUntilElementVisible(by);
        String value = element.getDomProperty(propertyName);
        ExtentLogger.info("Property '" + propertyName + "' from " + elementName + " is: '" + value + "'");
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

    public static boolean isElementPresent(By by, int timeoutInSeconds) {
        try {
            new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(timeoutInSeconds))
                    .until(ExpectedConditions.visibilityOfElementLocated(by));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static  boolean waitUntilElementInVisible(By by) {
        WebDriverWait wait = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(ConfigFactory.getConfig().timeout()));
        return wait.until(ExpectedConditions.invisibilityOfElementLocated(by));
    }

    public static String getText(By by, String elementName) {
        WebElement element = waitUntilElementVisible(by);
        String text = element.getAttribute("innerText");
        ExtentLogger.info("Text from " + elementName + " is: '" + text + "'");
        return text;
    }

    // --- ElementMeta overloads (delegate to existing String elementName methods) ---

    public static void click(By by, ElementMeta meta) {
        click(by, meta.getName());
    }

    public static void click(By by, WaitType waitType, ElementMeta meta) {
        click(by, waitType, meta.getName());
    }

    public static void click(WebElement element, WaitType waitType, ElementMeta meta) {
        click(element, waitType, meta.getName());
    }

    public static void sendKeys(By by, String value, ElementMeta meta) {
        sendKeys(by, value, meta.getName());
    }

    public static void sendKeys(By by, WaitType waitType, String value, ElementMeta meta) {
        sendKeys(by, waitType, value, meta.getName());
    }

    // -------------------------------------------------------------------------

    /**
     * Clicks a business-triggering element and immediately checks for unexpected popups.
     * Use for Save, Submit, Delete, Post, Approve, and other backend-invoking actions.
     * Do NOT use for field focus, typing, checkbox ticks, or scroll actions.
     *
     * @param by         locator of the button/element to click
     * @param actionName human-readable description used in failure messages and reports
     * @throws com.gsl.exceptions.UnexpectedPopupException if the action triggers an unexpected popup
     */
    public static void clickAndCheckPopup(By by, String actionName) {
        click(by, WaitType.CLICKABLE, actionName);
        WaitUtils.waitForExtMask();
        PopupMonitor.checkAfterAction(actionName);
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

    // ── Overlay-retry click ───────────────────────────────────────────────────

    /**
     * Attempts to click {@code element}, retrying up to {@code click.overlay.retry.max} times
     * (default 3) whenever an {@link ElementClickInterceptedException} is thrown.
     *
     * <p>The most common cause is the transient {@code ext-element-*} modal backdrop that
     * ExtJS renders while a dialog is opening.  On each interception the method:
     * <ol>
     *   <li>Logs a WARN to Log4j and a WARNING entry on the current ExtentReport node so
     *       testers can see that a retry occurred and what overlay caused it.</li>
     *   <li>Calls {@link WaitUtils#waitForClickOverlay()} to wait for ExtJS masks,
     *       {@code ext-element-*} backdrops, and the AJAX queue to clear.</li>
     *   <li>Re-attempts the click.</li>
     * </ol>
     * After exhausting all retries the original exception is re-thrown.
     *
     * <p><b>Note:</b> The {@code info("… is clicked successfully")} log is written by the
     * caller, not here, so it only appears once on actual success.
     */
    private static void clickWithOverlayRetry(WebElement element, String elementName) {
        int maxRetries = ConfigFactory.getConfig().clickOverlayRetryMax();

        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                element.click();
                return; // clicked successfully
            } catch (ElementClickInterceptedException e) {
                if (attempt > maxRetries) {
                    Log.error("[OVERLAY-RETRY] '{}' still intercepted after {} retr{}. Giving up.",
                            elementName, maxRetries, maxRetries == 1 ? "y" : "ies");
                    throw e;
                }
                String overlayTag = detectInterceptingOverlay(e.getMessage());
                Log.warn("[OVERLAY-RETRY] Click on '{}' intercepted{} — attempt {}/{}. Waiting for overlay to clear…",
                        elementName, overlayTag, attempt, maxRetries);
                ExtentLogger.warning(String.format(
                        "[Overlay Retry %d/%d] Click on '%s' was intercepted%s — waiting for overlay to clear",
                        attempt, maxRetries, elementName, overlayTag));
                WaitUtils.waitForClickOverlay();
            }
        }
    }

    /**
     * Extracts a short human-readable overlay label from the {@link ElementClickInterceptedException}
     * message for use in log and report entries.
     *
     * <p>The message from ChromeDriver / GeckoDriver typically contains a fragment like:
     * {@code "Other element would receive the click: <div id="ext-element-6" …>"}
     * from which we pull the ID token.
     */
    private static String detectInterceptingOverlay(String msg) {
        if (msg == null || msg.isBlank()) return "";
        // Match ext-element-* IDs (e.g. ext-element-6)
        if (msg.contains("ext-element")) {
            int idx = msg.indexOf("ext-element");
            String token = msg.substring(idx).split("[\\s\"'<>]")[0]; // first word-boundary character
            return " (by #" + token + ")";
        }
        if (msg.contains("x-mask"))        return " (by .x-mask)";
        if (msg.contains("x-mask-msg"))    return " (by .x-mask-msg)";
        if (msg.contains("modal"))         return " (by modal overlay)";
        return "";
    }
}
