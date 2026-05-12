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

    public static void waitForExtMask() {
        new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(extJsTimeout()))
                .until(ExpectedConditions.invisibilityOfElementLocated(
                        By.cssSelector(".x-mask, .x-mask-msg")
                ));
    }

    public static void waitForExtJsApp() {
        JavascriptExecutorUtils.waitForExtJS();
        waitForExtMask();
        JavascriptExecutorUtils.waitForExtLayout();
    }

    /**
     * Waits for the transient {@code ext-element-*} overlay (and all standard ExtJS masks) to
     * disappear before a click retry.
     *
     * <p>Phase 1 — {@code .x-mask} / {@code .x-mask-msg}: standard ExtJS loading masks that
     * appear during server-side processing.
     *
     * <p>Phase 2 — {@code div[id^='ext-element-']}: dynamic ExtJS modal backdrop elements
     * (e.g. {@code ext-element-6}) that intercept pointer events while a dialog is opening.
     * A JavaScript check is used because CSS attribute selectors with {@code display:block}
     * are unreliable across ExtJS versions; instead we ask the driver whether any such
     * element is currently displayed.
     *
     * <p>Phase 3 — ExtJS AJAX / layout cycle: ensures the JS event queue is empty after
     * the overlay has cleared so that the target element is truly interactive.
     */
    public static void waitForClickOverlay() {
        // Phase 1: standard mask elements
        try {
            new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(extJsTimeout()))
                    .until(ExpectedConditions.invisibilityOfElementLocated(
                            By.cssSelector(".x-mask, .x-mask-msg")
                    ));
        } catch (Exception ignored) { /* not present — proceed */ }

        // Phase 2: ext-element-* backdrop divs (JS check for isDisplayed())
        try {
            new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(extJsTimeout()))
                    .until(driver -> {
                        try {
                            return (Boolean) ((JavascriptExecutor) driver).executeScript(
                                    "var els = document.querySelectorAll('div[id^=\"ext-element-\"]');" +
                                    "for(var i=0;i<els.length;i++){" +
                                    "  var s=els[i].style;" +
                                    "  if(s.display!=='none' && s.visibility!=='hidden' && els[i].offsetParent!==null) return false;" +
                                    "} return true;"
                            );
                        } catch (Exception e) { return true; } // session issue — let the retry proceed
                    });
        } catch (Exception ignored) { /* timeout — best-effort, let click retry proceed */ }

        // Phase 3: ExtJS AJAX + layout cycle
        JavascriptExecutorUtils.waitForExtJS();
    }

    public static void extSafeClick(By locator) {
        JavascriptExecutorUtils.waitForExtJS();
        waitForExtMask();
        WebElement element = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(extJsTimeout()))
                .until(ExpectedConditions.elementToBeClickable(locator));
        element.click();
        waitForExtMask();
        JavascriptExecutorUtils.waitForExtJS();
    }

    private static long extJsTimeout() {
        long configured = ConfigFactory.getConfig().extjsTimeout();
        long min        = ConfigFactory.getConfig().extjsMinTimeout();
        return Math.max(min, configured);
    }
}
