package com.gsl.utils.Selenium;

import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.reports.WaitDurationBuffer;
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
        long startMs = System.currentTimeMillis();
        try {
            Wait<WebDriver> wait = new FluentWait<>(getDriver())
                    .withTimeout(Duration.ofMillis(ConfigFactory.getConfig().itemscanmaxtimeout()))
                    .pollingEvery(Duration.ofSeconds(2))
                    .ignoring(NoSuchElementException.class)
                    .ignoring(ElementClickInterceptedException.class)
                    .ignoring(ElementNotInteractableException.class);
            return wait.until(ExpectedConditions.elementToBeClickable(by));
        } finally {
            recordIfEnabled("fluentWait(" + by + ")", startMs, "fluentWait");
        }
    }

    public static WebElement waitUntilAjaxAndElementClickable(By by) {
        long startMs = System.currentTimeMillis();
        try {
            WebDriverWait wait = new WebDriverWait(DriverManager.getDriver(),
                    Duration.ofSeconds(ConfigFactory.getConfig().timeout()));
            wait.until(driver -> (Boolean) ((JavascriptExecutor) driver).executeScript(
                    "return (window.Ext && window.Ext.Ajax && !window.Ext.Ajax.isLoading()) || (!window.Ext || !window.Ext.Ajax);"
            ));
            return wait.until(ExpectedConditions.elementToBeClickable(by));
        } finally {
            recordIfEnabled("waitUntilAjaxAndClickable(" + by + ")", startMs, "waitUntilAjaxAndElementClickable");
        }
    }

    /**
     * Waits for ExtJS loading masks to disappear.
     * Records elapsed time to {@link WaitDurationBuffer} when wait analysis is enabled.
     * Internally delegates to {@link #waitForExtMaskInternal()} so that
     * {@link #waitForExtJsApp()} can call the inner logic without creating a double entry.
     */
    public static void waitForExtMask() {
        long startMs = System.currentTimeMillis();
        try {
            waitForExtMaskInternal();
        } finally {
            recordIfEnabled("waitForExtMask", startMs, "waitForExtMask");
        }
    }

    public static void waitForExtMask(String contextLabel) {
        long startMs = System.currentTimeMillis();
        try {
            waitForExtMaskInternal();
        } finally {
            recordIfEnabled(contextLabel, startMs, "waitForExtMask");
        }
    }

    /**
     * Core mask-wait logic — shared by {@link #waitForExtMask()} and
     * {@link #waitForExtJsApp()} to avoid double-counting in the wait buffer.
     */
    private static void waitForExtMaskInternal() {
        try {
            new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(extJsTimeout()))
                    .until(ExpectedConditions.invisibilityOfElementLocated(
                            By.cssSelector(".x-mask, .x-mask-msg")
                    ));
        } catch (TimeoutException e) {
            boolean dialogBlocking = !DriverManager.getDriver()
                    .findElements(By.xpath(
                            "//div[contains(@class,'x-message-box') and not(contains(@class,'x-hide-display'))]"))
                    .isEmpty();
            if (!dialogBlocking) throw e;
        }
    }

    public static void waitForExtJsApp() {
        long startMs = System.currentTimeMillis();
        try {
            JavascriptExecutorUtils.waitForExtJS();
            waitForExtMaskInternal();   // internal — no separate buffer entry
            JavascriptExecutorUtils.waitForExtLayout();
        } finally {
            recordIfEnabled("waitForExtJsApp", startMs, "waitForExtJsApp");
        }
    }

    /**
     * Waits for the transient {@code ext-element-*} overlay (and all standard ExtJS masks) to
     * disappear before a click retry.
     *
     * <p>Phase 1 — {@code .x-mask} / {@code .x-mask-msg}: standard ExtJS loading masks.
     * <p>Phase 2 — {@code div[id^='ext-element-']}: dynamic ExtJS modal backdrop elements.
     * <p>Phase 3 — ExtJS AJAX / layout cycle.
     */
    public static void waitForClickOverlay() {
        long startMs = System.currentTimeMillis();
        try {
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
                            } catch (Exception e) { return true; }
                        });
            } catch (Exception ignored) { /* timeout — best-effort, let click retry proceed */ }

            // Phase 3: ExtJS AJAX + layout cycle
            JavascriptExecutorUtils.waitForExtJS();
        } finally {
            recordIfEnabled("waitForClickOverlay", startMs, "waitForClickOverlay");
        }
    }

    public static void extSafeClick(By locator) {
        // Composite method — individual wait calls inside are already instrumented;
        // do NOT add a WaitDurationBuffer entry here to avoid ambiguous double entries.
        JavascriptExecutorUtils.waitForExtJS();
        waitForExtMaskInternal();
        WebElement element = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(extJsTimeout()))
                .until(ExpectedConditions.elementToBeClickable(locator));
        element.click();
        waitForExtMaskInternal();
        JavascriptExecutorUtils.waitForExtJS();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void recordIfEnabled(String label, long startMs, String method) {
        if (ConfigFactory.getConfig().waitAnalysisEnabled()) {
            WaitDurationBuffer.record(label, System.currentTimeMillis() - startMs, method);
        }
    }

    private static long extJsTimeout() {
        long configured = ConfigFactory.getConfig().extjsTimeout();
        long min        = ConfigFactory.getConfig().extjsMinTimeout();
        return Math.max(min, configured);
    }
}
