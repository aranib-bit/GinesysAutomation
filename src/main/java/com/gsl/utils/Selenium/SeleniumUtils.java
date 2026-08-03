package com.gsl.utils.Selenium;

import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.enums.LogCategory;
import com.gsl.enums.WaitType;
import com.gsl.popup.PopupMonitor;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.ElementMeta;
import com.gsl.utils.Log;
import com.gsl.utils.SensitiveDataMasker;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class SeleniumUtils {

    private SeleniumUtils() {}

    public static void click(By by, String elementName) {
        click(by, elementName, LogCategory.UI_ACTION);
    }

    public static void click(By by, String elementName, LogCategory category) {
        WebElement element = waitUntilElementPresent(by);
        clickWithOverlayRetry(element, elementName);
        ReportLogger.info(category, elementName + " is clicked successfully");
        WaitUtils.waitForExtMask(elementName);
        PopupMonitor.checkAfterAction(elementName);
    }

    public static void click(By by, WaitType waitType, String elementName) {
        click(by, waitType, elementName, LogCategory.UI_ACTION);
    }

    public static void click(By by, WaitType waitType, String elementName, LogCategory category) {
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();
        WebElement element;
        if (waitType == WaitType.PRESENCE) {
            element = waitUntilElementPresent(by);
        } else if (waitType == WaitType.CLICKABLE) {
            element = waitUntilElementClickable(by);
        } else {
            element = waitUntilElementVisible(by);
        }
        clickWithOverlayRetry(element, elementName);
        ReportLogger.info(category, elementName + " is clicked successfully");
        WaitUtils.waitForExtMask(elementName);
        PopupMonitor.checkAfterAction(elementName);
    }

    public static void click(WebElement element, WaitType waitType, String elementName) {
        click(element, waitType, elementName, LogCategory.UI_ACTION);
    }

    public static void click(WebElement element, WaitType waitType, String elementName, LogCategory category) {
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();
        WebElement ready;
        WebDriverWait wait = WaitUtils.webDriverWait();
        if (waitType == WaitType.CLICKABLE) {
            ready = wait.until(ExpectedConditions.elementToBeClickable(element));
        } else if (waitType == WaitType.VISIBLE) {
            ready = wait.until(ExpectedConditions.visibilityOf(element));
        } else {
            ready = element;
        }
        clickWithOverlayRetry(ready, elementName);
        ReportLogger.info(category, elementName + " is clicked successfully");
        WaitUtils.waitForExtMask(elementName);
        PopupMonitor.checkAfterAction(elementName);
    }

    public static void jsClick(By by, String elementName) {
        jsClick(by, elementName, LogCategory.UI_ACTION);
    }

    public static void jsClick(By by, String elementName, LogCategory category) {
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();
        WebElement element = WaitUtils.untilClickable(by);
        JavascriptExecutorUtils.click(element);
        ReportLogger.info(category, elementName + " is clicked successfully");
    }

    public static WebElement findChildElement(By parentLocator, By childLocator) {
        return WaitUtils.webDriverWait()
                .until(ExpectedConditions.presenceOfNestedElementLocatedBy(parentLocator, childLocator));
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
        String display = SensitiveDataMasker.isSensitiveByName(elementName)
                || SensitiveDataMasker.isSensitive(null, by)
                ? SensitiveDataMasker.mask(value) : value;
        WebElement element;
        if (waitType == WaitType.PRESENCE) {
            element = waitUntilElementPresent(by);
            element.clear();
            element.sendKeys(value);
            ExtentLogger.info("SendKeys is done successfully for element " + elementName + " with value " + display);
        } else if (waitType == WaitType.CLICKABLE) {
            element = waitUntilElementClickable(by);
            element.clear();
            element.sendKeys(value);
            ExtentLogger.info("SendKeys is done successfully for element " + elementName + " with value " + display);
        } else if (waitType == WaitType.VISIBLE) {
            element = waitUntilElementVisible(by);
            element.clear();
            element.sendKeys(value);
            ExtentLogger.info("SendKeys is done successfully for element " + elementName + " with value " + display);
        }
    }

    public static void sendKeys(By by, String value, String elementName) {
        String display = SensitiveDataMasker.isSensitiveByName(elementName)
                || SensitiveDataMasker.isSensitive(null, by)
                ? SensitiveDataMasker.mask(value) : value;
        WebElement element = waitUntilElementPresent(by);
        element.clear();
        element.sendKeys(value);
        ExtentLogger.info(display + " is entered sucessfully in " + elementName);
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

    // Wait wrappers delegate to the centralized WaitUtils (single home for all explicit waits).

    private static WebElement waitUntilElementPresent(By by) {
        return WaitUtils.untilPresent(by);
    }

    private static WebElement waitUntilElementClickable(By by) {
        return WaitUtils.untilClickable(by);
    }

    private static WebElement waitUntilElementClickable(By by, Integer addedtime) {
        return WaitUtils.untilClickable(by, ConfigFactory.getConfig().timeout() + addedtime);
    }

    private static WebElement waitUntilElementVisible(By by) {
        return WaitUtils.untilVisible(by);
    }

    public static boolean isElementPresent(By by, int timeoutInSeconds) {
        try {
            WaitUtils.untilVisible(by, timeoutInSeconds);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean waitUntilElementInVisible(By by) {
        return WaitUtils.untilInvisible(by);
    }

    public static String getText(By by, String elementName) {
        return getText(by, elementName, LogCategory.UI_ACTION);
    }

    public static String getText(By by, String elementName, LogCategory category) {
        WebElement element = waitUntilElementVisible(by);
        String text = element.getAttribute("innerText");
        ReportLogger.info(category, "Text from " + elementName + " is: '" + text + "'");
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

    /**
     * Clicks an element and notes any resulting popup in the report without failing the test.
     * Use when a popup is a known, handled outcome of this action.
     */
    public static void clickAndNotePopup(By by, WaitType waitType, String elementName) {
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();
        WebElement element;
        if (waitType == WaitType.PRESENCE)       element = waitUntilElementPresent(by);
        else if (waitType == WaitType.CLICKABLE)  element = waitUntilElementClickable(by);
        else                                      element = waitUntilElementVisible(by);
        clickWithOverlayRetry(element, elementName);
        ExtentLogger.info(elementName + " is clicked successfully");
        WaitUtils.waitForExtMask(elementName);
        PopupMonitor.noteAfterAction(elementName);
    }

    // ── Opt-in FluentWait click (snappy polling; non-disruptive) ──────────────
    //
    // These do NOT replace the existing click()/waitUntil* methods — migrate slow
    // call sites (e.g. On-Account No→Yes toggle, voucher Save) to fluentClick() to
    // remove the click gap. The gap on standard click() comes from WebDriverWait's
    // 500ms default polling stacked across waitForPageLoad + waitForExtJS + clickable
    // + post-click mask; these helpers poll every polling.millis (default 200ms).

    /**
     * Opt-in, snappier counterpart to {@link #click(By, WaitType, String)}: gates the prior AJAX
     * via {@link JavascriptExecutorUtils#waitForExtJS()}, waits for the element with
     * {@link WaitUtils#fluentClickable(By)} (fast {@code polling.millis} polling), clicks with the
     * same overlay-retry as the standard path, then uses {@link WaitUtils#waitForExtMaskBestEffort(String)}
     * — which never fails on a lingering zero-size ExtJS mask. Drop-in for call sites where the
     * standard click idles too long.
     */
    public static void fluentClick(By by, String elementName) {
        JavascriptExecutorUtils.waitForExtJS();
        WebElement element = WaitUtils.fluentClickable(by);
        clickWithOverlayRetry(element, elementName);
        ExtentLogger.info(elementName + " is clicked successfully");
        WaitUtils.waitForExtMaskBestEffort(elementName);
        PopupMonitor.checkAfterAction(elementName);
    }

    /** {@link ElementMeta} overload of {@link #fluentClick(By, String)}. */
    public static void fluentClick(By by, ElementMeta meta) {
        fluentClick(by, meta.getName());
    }

    public static void waitForInputReady(By locator) {
        WebDriverWait wait = WaitUtils.webDriverWait(ConfigFactory.getWaitConfig().interactionWaitSeconds());
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
    public static void clickWithOverlayRetry(WebElement element, String elementName) {
        int maxRetries = ConfigFactory.getWaitConfig().clickOverlayRetryMax();

        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                element.click();
                return; // clicked successfully
            } catch (ElementNotInteractableException e) {
                if (attempt > maxRetries) {
                    Log.error("[OVERLAY-RETRY] '{}' still intercepted after {} retr{}. Giving up.",
                            elementName, maxRetries, maxRetries == 1 ? "y" : "ies");
                    throw e;
                }
                String overlayTag = detectInterceptingOverlay(e.getMessage());
                Log.warn("[OVERLAY-RETRY] Click on '{}' intercepted{} — attempt {}/{}. Scrolling into view and waiting for overlay to clear…",
                        elementName, overlayTag, attempt, maxRetries);
                ExtentLogger.warning(String.format(
                        "[Overlay Retry %d/%d] Click on '%s' was intercepted%s — scrolling into view and waiting for overlay to clear",
                        attempt, maxRetries, elementName, overlayTag));
                JavascriptExecutorUtils.scrollToElement(element);
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
