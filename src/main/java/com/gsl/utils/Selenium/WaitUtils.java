package com.gsl.utils.Selenium;

import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import com.gsl.popup.PopupMonitor;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.WaitDurationBuffer;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

import static com.gsl.driver.DriverManager.getDriver;

/**
 * Single home for every explicit Selenium wait in the framework — both the "expected" waits
 * (WebDriverWait + ExpectedConditions) and the "fluent" waits (FluentWait with fast polling).
 * Other utils, page objects, and tests reuse these instead of constructing their own waits.
 *
 * <p>Default expected waits ({@link #webDriverWait()}, {@link #untilClickable(By)} …) use Selenium's
 * default ~500ms polling. The fluent waits ({@link #fluentWaitBuilder()}, {@link #fluentClickable(By)})
 * poll every {@code polling.millis} (default 200ms) and ignore transient ExtJS re-render exceptions.
 */
public final class WaitUtils {

    private WaitUtils() {}

    // ── Centralized wait factories ──────────────────────────────────────────────

    /** WebDriverWait at the global {@code timeout()} with Selenium's default polling (~500ms). */
    public static WebDriverWait webDriverWait() {
        return new WebDriverWait(getDriver(), Duration.ofSeconds(ConfigFactory.getConfig().timeout()));
    }

    /** WebDriverWait at an explicit timeout (seconds) with default polling. */
    public static WebDriverWait webDriverWait(long seconds) {
        return new WebDriverWait(getDriver(), Duration.ofSeconds(seconds));
    }

    /** WebDriverWait at an explicit millisecond timeout — for sub-second waits (e.g. popup detection). */
    public static WebDriverWait webDriverWaitMillis(long millis) {
        return new WebDriverWait(getDriver(), Duration.ofMillis(millis));
    }

    /**
     * FluentWait at {@code timeout()} polling every {@code polling.millis} (default 200ms), ignoring
     * transient NoSuchElement / Stale / NotInteractable exceptions. Opt-in fast path for clicks.
     */
    public static FluentWait<WebDriver> fluentWaitBuilder() {
        return new FluentWait<>(getDriver())
                .withTimeout(Duration.ofSeconds(ConfigFactory.getConfig().timeout()))
                .pollingEvery(Duration.ofMillis(ConfigFactory.getWaitConfig().pollingMillis()))
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class)
                .ignoring(ElementNotInteractableException.class)
                .ignoring(ElementClickInterceptedException.class);
    }

    // ── Expected-condition waits (public, reusable; default polling = no timing change) ──

    public static WebElement untilClickable(By by) {
        ReportLogger.uiAction("waitUntilClickable {}", by);
        try {
            return webDriverWait().until(ExpectedConditions.elementToBeClickable(by));
        } catch (TimeoutException e) {
            throw PopupMonitor.diagnoseFailure(e, "waitUntilClickable " + by, ConfigFactory.getConfig().timeout());
        }
    }

    public static WebElement untilClickable(By by, long seconds) {
        ReportLogger.uiAction("waitUntilClickable {}s {}", seconds, by);
        try {
            return webDriverWait(seconds).until(ExpectedConditions.elementToBeClickable(by));
        } catch (TimeoutException e) {
            throw PopupMonitor.diagnoseFailure(e, "waitUntilClickable " + by, seconds);
        }
    }

    public static WebElement untilPresent(By by) {
        ReportLogger.uiAction("waitUntilPresent {}", by);
        try {
            return webDriverWait().until(ExpectedConditions.presenceOfElementLocated(by));
        } catch (TimeoutException e) {
            throw PopupMonitor.diagnoseFailure(e, "waitUntilPresent " + by, ConfigFactory.getConfig().timeout());
        }
    }

    public static WebElement untilPresent(By by, long seconds) {
        ReportLogger.uiAction("waitUntilPresent {}s {}", seconds, by);
        try {
            return webDriverWait(seconds).until(ExpectedConditions.presenceOfElementLocated(by));
        } catch (TimeoutException e) {
            throw PopupMonitor.diagnoseFailure(e, "waitUntilPresent " + by, seconds);
        }
    }

    public static WebElement untilVisible(By by) {
        ReportLogger.uiAction("waitUntilVisible {}", by);
        try {
            return webDriverWait().until(ExpectedConditions.visibilityOfElementLocated(by));
        } catch (TimeoutException e) {
            throw PopupMonitor.diagnoseFailure(e, "waitUntilVisible " + by, ConfigFactory.getConfig().timeout());
        }
    }

    public static WebElement untilVisible(By by, long seconds) {
        ReportLogger.uiAction("waitUntilVisible {}s {}", seconds, by);
        try {
            return webDriverWait(seconds).until(ExpectedConditions.visibilityOfElementLocated(by));
        } catch (TimeoutException e) {
            throw PopupMonitor.diagnoseFailure(e, "waitUntilVisible " + by, seconds);
        }
    }

    public static boolean untilInvisible(By by) {
        ReportLogger.uiAction("waitUntilInvisible {}", by);
        return webDriverWait().until(ExpectedConditions.invisibilityOfElementLocated(by));
    }

    /** PRESENCE / CLICKABLE / VISIBLE dispatcher (used by {@code SeleniumUtils.click}). */
    public static WebElement until(By by, WaitType waitType) {
        switch (waitType) {
            case CLICKABLE: return untilClickable(by);
            case VISIBLE:   return untilVisible(by);
            case PRESENCE:
            default:        return untilPresent(by);
        }
    }

    /**
     * Opt-in clickable wait using {@code polling.millis} (default 200ms) instead of WebDriverWait's
     * 500ms default, ignoring transient ExtJS re-render exceptions. Same total timeout — only
     * re-checks more often. Lives here so any util/test can reuse it.
     */
    public static WebElement fluentClickable(By by) {
        ReportLogger.uiAction("fluentClickable {}", by);
        return fluentWaitBuilder().until(ExpectedConditions.elementToBeClickable(by));
    }

    /** Effective ExtJS timeout in seconds — {@code max(extjs.timeout, extjs.min.timeout)}. */
    public static long extJsTimeoutSeconds() {
        return extJsTimeout();
    }

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
            WebDriverWait wait = webDriverWait();
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
     * Non-throwing query: is an ExtJS loading mask currently visible?
     *
     * <p>Used by the root-cause classifier at failure time to corroborate a "mask still blocking"
     * verdict. Reuses the same {@code .x-mask, .x-mask-msg} selector as {@link #waitForExtMask()}.
     * Never throws — returns {@code false} on a null driver or any lookup error (the session may be
     * dead by the time this runs).
     */
    public static boolean isExtMaskPresent(WebDriver driver) {
        if (driver == null) return false;
        try {
            return driver.findElements(By.cssSelector(".x-mask, .x-mask-msg"))
                         .stream().anyMatch(WebElement::isDisplayed);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Core mask-wait logic — shared by {@link #waitForExtMask()} and
     * {@link #waitForExtJsApp()} to avoid double-counting in the wait buffer.
     */
    private static void waitForExtMaskInternal() {
        try {
            webDriverWait(extJsTimeout())
                    .until(ExpectedConditions.invisibilityOfElementLocated(
                            By.cssSelector(".x-mask, .x-mask-msg")
                    ));
        } catch (TimeoutException e) {
            boolean dialogBlocking = !DriverManager.getDriver()
                    .findElements(By.xpath(
                            "//div[contains(@class,'x-message-box') and not(contains(@class,'x-hide-display'))]"))
                    .isEmpty();
            if (dialogBlocking) return;
            throw PopupMonitor.diagnoseFailure(e, "Loader wait (ExtJS mask)", extJsTimeout());
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
                webDriverWait(extJsTimeout())
                        .until(ExpectedConditions.invisibilityOfElementLocated(
                                By.cssSelector(".x-mask, .x-mask-msg")
                        ));
            } catch (Exception ignored) { /* not present — proceed */ }

            // Phase 2: ext-element-* backdrop divs (JS check for isDisplayed())
            try {
                webDriverWait(extJsTimeout())
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

    /**
     * Opt-in, best-effort variant of {@link #waitForExtMask()} for use after a {@code fluentClick}.
     *
     * <p>Two differences from {@link #waitForExtMaskInternal()}:
     * <ul>
     *   <li>Only treats a mask as blocking when it is actually <b>visible and has a non-zero
     *       bounding box</b> — ExtJS commonly leaves zero-size {@code .x-mask} leftovers in the DOM
     *       that {@code isDisplayed()} still reports as shown, producing false "mask blocking" timeouts
     *       even though nothing is visible on screen.</li>
     *   <li><b>Never throws</b> — a lingering mask is recorded in the wait buffer but cannot fail a
     *       test. The JS-level {@link JavascriptExecutorUtils#waitForExtJS()} already gates the AJAX
     *       queue.</li>
     * </ul>
     * Polls every {@code polling.millis} (default 200ms) so it returns promptly.
     */
    public static void waitForExtMaskBestEffort(String contextLabel) {
        long startMs = System.currentTimeMillis();
        try {
            new WebDriverWait(DriverManager.getDriver(),
                    Duration.ofSeconds(extJsTimeout()),
                    Duration.ofMillis(ConfigFactory.getWaitConfig().pollingMillis()))
                    .until(driver -> {
                        try {
                            return (Boolean) ((JavascriptExecutor) driver).executeScript(
                                    "var m=document.querySelectorAll('.x-mask, .x-mask-msg');" +
                                    "for(var i=0;i<m.length;i++){" +
                                    "  var r=m[i].getBoundingClientRect();" +
                                    "  var s=window.getComputedStyle(m[i]);" +
                                    "  if(r.width>0 && r.height>0 && s.display!=='none' && s.visibility!=='hidden')" +
                                    "    return false;" +
                                    "} return true;");
                        } catch (Exception e) {
                            return true;   // cannot evaluate → do not block
                        }
                    });
        } catch (Exception ignored) {
            // best-effort: a stuck/zero-size mask must never fail the test
        } finally {
            recordIfEnabled(contextLabel, startMs, "waitForExtMaskBestEffort");
        }
    }

    /**
     * Post-save specific FluentWait for {@code .x-mask} / {@code .x-mask-msg} clearance.
     *
     * <p>Uses a 10-second ceiling instead of the global {@code extJsTimeout()} (40 s) because the
     * save is already confirmed by the post-save dialog before this is called — a long timeout is
     * unnecessary and inflates "Verify Save Result" step duration when the mask lingers briefly.
     * Returns as soon as the mask clears; never throws (best-effort).
     */
    public static void waitForPostSaveMask(String contextLabel) {
        long startMs = System.currentTimeMillis();
        try {
            new FluentWait<WebDriver>(DriverManager.getDriver())
                    .withTimeout(Duration.ofSeconds(10))
                    .pollingEvery(Duration.ofMillis(ConfigFactory.getWaitConfig().pollingMillis()))
                    .ignoring(NoSuchElementException.class)
                    .ignoring(StaleElementReferenceException.class)
                    .until(driver -> {
                        try {
                            return (Boolean) ((JavascriptExecutor) driver).executeScript(
                                    "var m=document.querySelectorAll('.x-mask,.x-mask-msg');" +
                                    "for(var i=0;i<m.length;i++){" +
                                    "  var r=m[i].getBoundingClientRect();" +
                                    "  var s=window.getComputedStyle(m[i]);" +
                                    "  if(r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden')" +
                                    "    return false;" +
                                    "} return true;");
                        } catch (Exception e) {
                            return true; // cannot evaluate → treat as clear
                        }
                    });
        } catch (Exception ignored) {
            // best-effort: timed-out mask must never fail the test
        } finally {
            recordIfEnabled(contextLabel, startMs, "waitForPostSaveMask");
        }
    }

    public static void extSafeClick(By locator) {
        // Composite method — individual wait calls inside are already instrumented;
        // do NOT add a WaitDurationBuffer entry here to avoid ambiguous double entries.
        JavascriptExecutorUtils.waitForExtJS();
        waitForExtMaskInternal();
        WebElement element = webDriverWait(extJsTimeout())
                .until(ExpectedConditions.elementToBeClickable(locator));
        element.click();
        waitForExtMaskInternal();
        JavascriptExecutorUtils.waitForExtJS();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void recordIfEnabled(String label, long startMs, String method) {
        if (ConfigFactory.getDiagnosticsConfig().waitAnalysisEnabled()) {
            WaitDurationBuffer.record(label, System.currentTimeMillis() - startMs, method);
        }
    }

    private static long extJsTimeout() {
        long configured = ConfigFactory.getFinanceConfig().extjsTimeout();
        long min        = ConfigFactory.getFinanceConfig().extjsMinTimeout();
        return Math.max(min, configured);
    }
}
