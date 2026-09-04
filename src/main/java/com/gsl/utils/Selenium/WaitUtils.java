package com.gsl.utils.Selenium;

import com.gsl.config.ConfigFactory;
import com.gsl.config.WaitConfig;
import com.gsl.constants.FrameworkConstants;
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
import java.util.List;

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

    /**
     * Waits for every element matching {@code by} to genuinely disappear from the screen —
     * shares the same "genuinely visible" discipline as {@link #isGenuineMaskVisible(WebDriver)},
     * generalized to an arbitrary locator instead of the hardcoded {@code .x-mask}/{@code
     * .x-mask-msg} selector. This is the single choke point behind {@code SeleniumUtils
     * .elementDisappear(By, String)} — used at 30+ call sites across the framework for every
     * "wait for a transient loader/overlay to disappear" scenario (login's splash screen and
     * authenticator loader, every list page's loading mask, GRC/DocumentMessageBox popups, etc.).
     *
     * <p>Before this fix it used Selenium's {@code ExpectedConditions.invisibilityOfElementLocated},
     * which is exactly the naive {@code isDisplayed()}-only check that {@link #isGenuineMaskVisible}
     * was already proven wrong for on {@code .x-mask} — and the exact same false-positive pattern
     * was then independently observed live on two *other* locators sharing this method:
     * {@code LoginPage}'s {@code AUTHENTICATOR_LOADER} wait and {@code
     * FrameworkConstants.getLoadingMask()}'s {@code .x-mask-fixed} wait both timed out with a
     * screenshot showing nothing blocking the screen. Three independent confirmations of the same
     * root cause on three different locators is enough to fix it here once, rather than waiting
     * for every remaining caller to fail separately first.
     */
    /**
     * Round 14 (§27c): {@code untilInvisible} was the only one of this class's five {@code
     * until*} methods with neither a retry NOR {@link PopupMonitor#diagnoseFailure} — a timeout
     * escaped as a bare, undiagnosed Selenium {@code TimeoutException}, despite this being the
     * documented "single choke point behind {@code SeleniumUtils.elementDisappear(By, String)} —
     * used at 30+ call sites". Confirmed live, run 20260904_091356, FIN-GV-ADD-MIXED-01/
     * FIN-GV-ADD-COMBO-01: both died here, in {@code LoginPage.checkAuthenticationLoader}'s
     * post-login loader wait, with no diagnosis and no retry chance.
     *
     * <p>Given the staged-poll shape (mirroring {@code WaitUtils.waitForExtMaskInternal}) already
     * needs the caller-known default {@code timeout()} tier, it's added ONLY to this no-arg
     * overload — the explicit-seconds overload below stays a single attempt (unchanged timing)
     * since its many callers often pass a deliberately SHORT bounded probe (e.g. a 3s optional-
     * popup check); multiplying an intentionally-short wait would be a real regression, not a fix.
     * It still gets {@code diagnoseFailure} for consistent diagnosis.
     */
    public static boolean untilInvisible(By by) {
        ReportLogger.uiAction("waitUntilInvisible {}", by);
        long startMs = System.currentTimeMillis();
        int maxPolls = ConfigFactory.getWaitConfig().maskPollMaxRetries();
        long pollSeconds = ConfigFactory.getWaitConfig().maskPollSeconds();
        long initialSeconds = ConfigFactory.getConfig().timeout();
        int attempts = 1;

        if (untilInvisibleOnce(by, initialSeconds)) {
            return true;
        }
        ReportLogger.warn(String.format(
                "waitUntilInvisible %s: still visible after %ds initial wait — polling up to %d more "
                        + "time(s) at %ds each…",
                by, initialSeconds, maxPolls, pollSeconds));

        for (int poll = 1; poll <= maxPolls; poll++) {
            attempts = 1 + poll;
            if (untilInvisibleOnce(by, pollSeconds)) {
                ReportLogger.info(String.format("waitUntilInvisible %s: cleared on poll %d/%d", by, poll, maxPolls));
                return true;
            }
            ReportLogger.warn(String.format("waitUntilInvisible %s: still visible after poll %d/%d (%ds each)",
                    by, poll, maxPolls, pollSeconds));
        }

        long elapsedMs = System.currentTimeMillis() - startMs;
        throw PopupMonitor.diagnoseFailure(
                new TimeoutException("Element still genuinely visible: " + by),
                "waitUntilInvisible " + by, initialSeconds, elapsedMs, attempts);
    }

    /** Single genuinely-invisible check, capped at {@code seconds} — never throws, returns
     *  {@code false} on timeout so the caller's staged-poll loop can decide what's next. */
    private static boolean untilInvisibleOnce(By by, long seconds) {
        try {
            webDriverWait(seconds).until(driver -> !isGenuinelyVisible(driver, by));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    /**
     * Same "genuinely visible" discipline as {@link #untilInvisible(By)}, at an explicit timeout
     * instead of the default {@code timeout()} — for waits with a structurally different SLA
     * (e.g. {@code TopModuleMenuComponent.navigateToModule()}'s post-click "Loading Package"
     * module-bootstrap wait, which is a heavier one-off step than a routine mask clear).
     * Deliberately single-attempt (see {@link #untilInvisible(By)}'s Javadoc for why) — now routed
     * through {@link PopupMonitor#diagnoseFailure} for consistent diagnosis, matching this
     * class's other {@code until*} methods.
     */
    public static boolean untilInvisible(By by, long seconds) {
        ReportLogger.uiAction("waitUntilInvisible {}s {}", seconds, by);
        try {
            return webDriverWait(seconds).until(driver -> !isGenuinelyVisible(driver, by));
        } catch (TimeoutException e) {
            throw PopupMonitor.diagnoseFailure(e, "waitUntilInvisible " + by, seconds);
        }
    }

    /**
     * Generic counterpart to {@link #isGenuineMaskVisible(WebDriver)} — same bounding-box +
     * on-screen + {@code display}/{@code visibility}/{@code opacity} check, but for any locator
     * instead of the hardcoded ExtJS mask selector. Deliberately skips the {@code
     * elementFromPoint} topmost check that {@code isGenuineMaskVisible} needs (that one exists to
     * confirm a mask is actually *blocking a click* — this one only answers "has this element
     * visually gone away", which doesn't care what else is stacked above/below it).
     *
     * <p>Resolves elements via {@link WebDriver#findElements(By)} (works for any {@code By} type —
     * xpath, css, etc. — unlike re-parsing the locator inside JS) then inspects each one's real
     * rendered box via JS. A {@link StaleElementReferenceException} on any single element means
     * that element has already left the DOM — correctly treated as "not visible", not a failure.
     * Never throws — returns {@code false} on a null driver or any other evaluation error.
     */
    private static boolean isGenuinelyVisible(WebDriver driver, By locator) {
        if (driver == null) return false;
        try {
            List<WebElement> elements = driver.findElements(locator);
            if (elements.isEmpty()) return false;
            for (WebElement el : elements) {
                if (isGenuinelyVisibleElement(driver, el)) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Per-element half of {@link #isGenuinelyVisible(WebDriver, By)} — same bounding-box +
     * on-screen + {@code display}/{@code visibility}/{@code opacity} check, extracted so
     * {@link #untilAnyVisible(By, long)} can reuse it while also returning *which* element it
     * found visible (the {@code By}-only variant only ever needed a boolean). A {@link
     * StaleElementReferenceException} means the element already left the DOM mid-check —
     * correctly treated as "not visible", not a failure.
     */
    private static boolean isGenuinelyVisibleElement(WebDriver driver, WebElement el) {
        try {
            Boolean visible = (Boolean) ((JavascriptExecutor) driver).executeScript(
                    "var el=arguments[0];" +
                    "var r=el.getBoundingClientRect();" +
                    "if(r.width<=0||r.height<=0) return false;" +
                    "if(r.bottom<=0||r.right<=0||r.top>=window.innerHeight||r.left>=window.innerWidth) return false;" +
                    "var s=window.getComputedStyle(el);" +
                    "if(s.display==='none'||s.visibility==='hidden'||parseFloat(s.opacity)===0) return false;" +
                    "return true;", el);
            return Boolean.TRUE.equals(visible);
        } catch (StaleElementReferenceException stale) {
            return false;
        }
    }

    /**
     * Waits for at least one element matching {@code by} to become genuinely visible (same
     * bounding-box/on-screen/opacity discipline as {@link #isGenuinelyVisible(WebDriver, By)})
     * and returns that element. Unlike {@link ExpectedConditions#visibilityOfElementLocated},
     * which only ever inspects the *first* DOM match, this checks every match — for locators
     * that can transiently resolve to more than one element (e.g. a stale/leftover node
     * alongside the live one, only one of which is actually rendered), the naive first-match-only
     * wait can time out even while the real element is visible elsewhere in the matches.
     * Confirmed live on {@code FinanceHomePage}'s dashboard-header locator: the first DOM match
     * was a stale node, and the visible "Finance Dashboard" header was a later match.
     */
    public static WebElement untilAnyVisible(By by, long seconds) {
        ReportLogger.uiAction("waitUntilAnyVisible {}s {}", seconds, by);
        try {
            return webDriverWait(seconds).until(driver -> {
                for (WebElement el : driver.findElements(by)) {
                    if (isGenuinelyVisibleElement(driver, el)) return el;
                }
                return null;
            });
        } catch (TimeoutException e) {
            throw PopupMonitor.diagnoseFailure(e, "waitUntilAnyVisible " + by, seconds);
        }
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
        int[] attempts = {1};
        try {
            waitForExtMaskInternal("waitForExtMask", startMs, attempts);
        } finally {
            recordIfEnabled("waitForExtMask", startMs, "waitForExtMask", attempts[0]);
        }
    }

    public static void waitForExtMask(String contextLabel) {
        long startMs = System.currentTimeMillis();
        int[] attempts = {1};
        try {
            // contextLabel is now FORWARDED into the failure diagnosis, not merely recorded in the
            // wait buffer. It used to be dropped here, so every mask failure in the report read
            // "Action: Loader wait (ExtJS mask)" regardless of which call site produced it —
            // indistinguishable, and the single biggest reason these failures were hard to place.
            waitForExtMaskInternal(contextLabel, startMs, attempts);
        } finally {
            recordIfEnabled(contextLabel, startMs, "waitForExtMask", attempts[0]);
        }
    }

    /**
     * Real answer to "is an ExtJS loading mask actually visible right now?" — checks whether a
     * {@code .x-mask}/{@code .x-mask-msg} candidate is actually the topmost thing rendered on
     * screen, rather than trusting Selenium's {@code isDisplayed()} or a bare size/style check.
     *
     * <p>ExtJS commonly <b>pools and reuses</b> a single mask element instead of destroying it —
     * a "hidden" mask is often left in the DOM still sized and {@code display:block}/
     * {@code visibility:visible}, just moved off-screen, faded to {@code opacity:0}, or stacked
     * behind the newly-rendered content (lower {@code z-index}). A size+display/visibility-only
     * check (the first iteration of this method) still misses all three of those, so it can
     * report a dead leftover mask as "genuinely visible" — confirmed live via
     * {@code RootCauseClassifier}'s "EXTJS MASK BLOCKING — Confidence: HIGH" verdict firing on a
     * failure whose own screenshot showed a fully-rendered, unmasked page.
     *
     * <p>Fix: for each candidate with a non-zero, on-screen, non-transparent bounding rect, use
     * {@code document.elementFromPoint()} at the rect's own center and confirm the mask (or a
     * container of it) is actually the topmost element there — the same signal a human looking at
     * a screenshot would use, so it catches off-screen, opacity-faded, AND z-index-buried
     * leftovers in one check instead of enumerating more CSS hiding tricks individually.
     * Never throws — returns {@code false} on a null driver or any evaluation error (the session
     * may be dead by the time this runs).
     *
     * <p>Single source of truth shared by {@link #isExtMaskPresent(WebDriver)} (and therefore
     * {@code PopupMonitor.diagnoseFailure}, {@code RootCauseClassifier}, and
     * {@code HealthChecker.isLoaderVisible()}), {@link #waitForExtMaskInternal()},
     * {@link #waitForExtMaskBestEffort(String)}, and {@link #waitForPostSaveMask(String)}.
     */
    private static boolean isGenuineMaskVisible(WebDriver driver) {
        if (driver == null) return false;
        try {
            return (Boolean) ((JavascriptExecutor) driver).executeScript(
                    "var m=document.querySelectorAll('.x-mask, .x-mask-msg');" +
                    "for(var i=0;i<m.length;i++){" +
                    "  var el=m[i];" +
                    "  var r=el.getBoundingClientRect();" +
                    "  if(r.width<=0||r.height<=0) continue;" +
                    "  if(r.bottom<=0||r.right<=0||r.top>=window.innerHeight||r.left>=window.innerWidth) continue;" +
                    "  var s=window.getComputedStyle(el);" +
                    "  if(s.display==='none'||s.visibility==='hidden'||parseFloat(s.opacity)===0) continue;" +
                    "  var cx=Math.min(Math.max((r.left+r.right)/2,0),window.innerWidth-1);" +
                    "  var cy=Math.min(Math.max((r.top+r.bottom)/2,0),window.innerHeight-1);" +
                    "  var top=document.elementFromPoint(cx,cy);" +
                    "  if(top && (top===el || el.contains(top) || top.contains(el))) return true;" +
                    "} return false;");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Non-throwing query: is an ExtJS loading mask currently visible?
     *
     * <p>Used by the root-cause classifier at failure time to corroborate a "mask still blocking"
     * verdict. Reuses the same genuine-visibility check as {@link #waitForExtMask()}.
     * Never throws — returns {@code false} on a null driver or any lookup error (the session may be
     * dead by the time this runs).
     */
    public static boolean isExtMaskPresent(WebDriver driver) {
        return isGenuineMaskVisible(driver);
    }

    /**
     * Core mask-wait logic — shared by {@link #waitForExtMask()} and
     * {@link #waitForExtJsApp()} to avoid double-counting in the wait buffer.
     *
     * <p>Round 8: restructured to mirror {@link #waitForSplashScreenClear()}'s already-proven
     * staged shape — an initial {@code extJsTimeout()} wait (unchanged tier, e.g. 40s), then up
     * to {@code mask.poll.max.retries} (default 4) further polls of {@code mask.poll.seconds}
     * (default 15s) each, each logged individually — instead of one single attempt with no retry
     * timeline at all (confirmed absent live: {@code FIN-GV-ADD-COMBO-01} kept failing after
     * exactly one 40.3s attempt across three consecutive runs). Worst case for a THROWING mask
     * wait grows from 40s to 40+4x15=100s; {@link #waitForPostSaveMask} and
     * {@link #waitForExtMaskBestEffort} deliberately stay single-attempt (see their own Javadoc
     * for why) — this is not a blanket threshold increase, only this one throwing path retries.
     *
     * @param actionLabel the CALLER's own context label, forwarded into the failure diagnosis so
     *                    the report names the actual call site instead of a generic constant
     * @param startMs     when the wait began, so the failure can report REAL elapsed time
     * @param attemptsOut single-element out-parameter, updated as attempts happen so the caller's
     *                    {@code finally} block can record the real attempt count in the Wait
     *                    Analysis section even on the throwing path
     */
    private static void waitForExtMaskInternal(String actionLabel, long startMs, int[] attemptsOut) {
        WaitConfig wc = ConfigFactory.getWaitConfig();
        int maxPolls = wc.maskPollMaxRetries();
        long pollSeconds = wc.maskPollSeconds();

        attemptsOut[0] = 1;
        if (waitForMaskClearedOnce(extJsTimeout())) {
            return;
        }

        ReportLogger.warn(String.format(
                "Loader wait [%s]: ExtJS mask still visible after %ds initial wait — polling up to %d "
                        + "more time(s) at %ds each…",
                actionLabel, extJsTimeout(), maxPolls, pollSeconds));

        for (int poll = 1; poll <= maxPolls; poll++) {
            attemptsOut[0] = 1 + poll;
            if (waitForMaskClearedOnce(pollSeconds)) {
                ReportLogger.info(String.format("Loader wait [%s]: mask cleared on poll %d/%d",
                        actionLabel, poll, maxPolls));
                return;
            }
            ReportLogger.warn(String.format("Loader wait [%s]: mask still visible after poll %d/%d (%ds each)",
                    actionLabel, poll, maxPolls, pollSeconds));
        }

        long elapsedMs = System.currentTimeMillis() - startMs;
        boolean dialogBlocking = !DriverManager.getDriver()
                .findElements(By.xpath(
                        "//div[contains(@class,'x-message-box') and not(contains(@class,'x-hide-display'))]"))
                .isEmpty();
        if (dialogBlocking) {
            // Previously a bare `return` — completely silent. A mask wait that burned its full
            // budget and then gave up because a dialog was on screen left NO trace anywhere in
            // the report, so that time simply vanished from the record. Still non-throwing
            // (a blocking dialog is a legitimate reason to stop waiting), but no longer silent.
            ReportLogger.warn("Loader wait [" + actionLabel + "] gave up after " + elapsedMs
                    + "ms (" + attemptsOut[0] + " attempts: " + extJsTimeout() + "s initial + " + maxPolls
                    + "x" + pollSeconds + "s polls) — an x-message-box dialog is on screen, so the mask "
                    + "may never clear until it is dismissed. Not failing the wait.");
            return;
        }
        throw PopupMonitor.diagnoseFailure(
                new TimeoutException("ExtJS mask (.x-mask/.x-mask-msg) still visible after "
                        + attemptsOut[0] + " attempt(s)"),
                actionLabel, extJsTimeout(), elapsedMs, attemptsOut[0]);
    }

    /** Single genuinely-mask-cleared wait, capped at {@code seconds} — never throws, returns
     *  {@code false} on timeout instead so the caller's staged-poll loop can decide what's next. */
    private static boolean waitForMaskClearedOnce(long seconds) {
        try {
            webDriverWait(seconds).until(driver -> !isGenuineMaskVisible(driver));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    /** Back-compat overload for callers with no label/timing of their own. */
    private static void waitForExtMaskInternal() {
        waitForExtMaskInternal("Loader wait (ExtJS mask)", System.currentTimeMillis(), new int[1]);
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
     * <p>Phase 2 — {@code #splashScreen}: app-level splash screen, which disappears via a CSS
     * opacity fade-out rather than {@code display:none}/removal — a naive {@code isDisplayed()}
     * check (as Phase 1's {@code ExpectedConditions.invisibilityOfElementLocated} uses) reports
     * it "displayed" throughout the fade, so this phase uses the same "genuinely visible"
     * bounding-box + computed-style discipline as {@link #untilInvisible(By)} instead, staged and
     * logged via {@link #waitForSplashScreenClear()} rather than one opaque timeout.
     * <p>Phase 3 — {@code div[id^='ext-element-']}: dynamic ExtJS modal backdrop elements.
     * <p>Phase 4 — ExtJS AJAX / layout cycle.
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

            // Phase 2: #splashScreen genuine-visibility fade-out — opacity-based, so Phase 1's
            // isDisplayed()-only check never catches it. Polls in named stages with a log line
            // per stage instead of one opaque timeout — confirmed necessary live in report
            // 20260901_112001, where a fading splashScreen intercepted a click with no
            // diagnostic trail of how long it had already been fading.
            waitForSplashScreenClear();

            // Phase 3: ext-element-* backdrop divs (JS check for isDisplayed())
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

            // Phase 4: ExtJS AJAX + layout cycle
            JavascriptExecutorUtils.waitForExtJS();
        } finally {
            recordIfEnabled("waitForClickOverlay", startMs, "waitForClickOverlay");
        }
    }

    /**
     * Waits for {@code #splashScreen} to genuinely disappear (see {@link #isGenuinelyVisible}),
     * in named stages, logging progress at each stage instead of one opaque timeout: an initial
     * {@code splashscreen.initial.wait.seconds} (default 30s) wait, then up to {@code
     * splashscreen.poll.max.retries} (default 4) further polls of {@code
     * splashscreen.poll.seconds} (default 15s) each. Never throws — this is a best-effort
     * clearance step inside {@link #waitForClickOverlay()}; if the splash screen never clears,
     * the click retry that follows will simply be intercepted again, and {@code
     * SeleniumUtils.clickWithOverlayRetry}'s own retry budget (independently, up to {@code
     * click.overlay.retry.max} times) decides when to fail.
     *
     * <p>Note this phase runs inside that same outer retry loop, so the real worst case for a
     * splash screen that never clears is {@code click.overlay.retry.max} times this method's own
     * total wait — accepted deliberately (see {@code wait.properties}' comment on these keys)
     * in exchange for a full, logged timeline instead of a silent timeout.
     *
     * <p>Round 14 (§27c): made {@code public} — this staged, logged wait already existed but was
     * reachable only from {@link #waitForClickOverlay()}, while {@code LoginPage},
     * {@code IndexPage}, and {@code NavigationUtils} each carried their own BARE, single-attempt
     * 30s wait on the exact same {@code #splashScreen} element via {@code SeleniumUtils
     * .elementDisappear}. Exposed so those three sites call this proven implementation directly
     * instead of duplicating a weaker one.
     */
    public static void waitForSplashScreenClear() {
        WaitConfig wc = ConfigFactory.getWaitConfig();
        long initialSeconds = wc.splashscreenInitialWaitSeconds();
        int maxPolls = wc.splashscreenPollMaxRetries();
        long pollSeconds = wc.splashscreenPollSeconds();

        if (waitForSplashInvisible(initialSeconds)) return;
        ReportLogger.warn(String.format(
                "#splashScreen still visible after %ds — polling up to %d more time(s) at %ds each…",
                initialSeconds, maxPolls, pollSeconds));

        for (int poll = 1; poll <= maxPolls; poll++) {
            if (waitForSplashInvisible(pollSeconds)) {
                ReportLogger.info(String.format("#splashScreen cleared on poll %d/%d", poll, maxPolls));
                return;
            }
            ReportLogger.warn(String.format("#splashScreen still visible after poll %d/%d (%ds each)",
                    poll, maxPolls, pollSeconds));
        }
        ReportLogger.warn(String.format(
                "#splashScreen never cleared after %ds total (%ds initial + %dx%ds polls)",
                initialSeconds + (long) maxPolls * pollSeconds, initialSeconds, maxPolls, pollSeconds));
    }

    /** Single genuinely-invisible wait for {@code #splashScreen}, capped at {@code seconds} — never throws. */
    private static boolean waitForSplashInvisible(long seconds) {
        try {
            webDriverWait(seconds).until(driver -> !isGenuinelyVisible(driver, FrameworkConstants.getSplashScreen()));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Opt-in, best-effort variant of {@link #waitForExtMask()} for use after a {@code fluentClick}.
     *
     * <p>Deliberately kept single-attempt (unlike {@link #waitForExtMaskInternal}'s Round-8
     * staged-poll retry) — this method never fails the test either way, so retrying would only
     * ever silently add minutes across a run for zero benefit; a genuinely-stuck mask here is
     * already tolerated, not diagnosed.
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
                    .until(driver -> !isGenuineMaskVisible(driver));
        } catch (Exception e) {
            // best-effort: a stuck/zero-size mask must never fail the test — but "never fails" is
            // not the same as "never reports". This used to be `catch (Exception ignored) {}`, so
            // a wait that burned the full extJsTimeout() ceiling (40s by default) produced ZERO
            // output anywhere in the HTML report. That silence is a large part of why mask-related
            // slowness looked like it "sometimes" logged and sometimes didn't.
            ReportLogger.warn("Loader wait [" + contextLabel + "] (best-effort) gave up after "
                    + (System.currentTimeMillis() - startMs) + "ms with the mask still visible — "
                    + "continuing without failing. (" + e.getClass().getSimpleName() + ")");
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
     *
     * <p>Deliberately kept single-attempt (unlike {@link #waitForExtMaskInternal}'s Round-8
     * staged-poll retry) — the save it's confirming already succeeded, so this wait only cleans
     * up a lingering mask; a longer retried wait here would only inflate this step's duration for
     * no diagnostic benefit.
     */
    public static void waitForPostSaveMask(String contextLabel) {
        long startMs = System.currentTimeMillis();
        try {
            new FluentWait<WebDriver>(DriverManager.getDriver())
                    .withTimeout(Duration.ofSeconds(10))
                    .pollingEvery(Duration.ofMillis(ConfigFactory.getWaitConfig().pollingMillis()))
                    .ignoring(NoSuchElementException.class)
                    .ignoring(StaleElementReferenceException.class)
                    .until(driver -> !isGenuineMaskVisible(driver));
        } catch (Exception e) {
            // best-effort: timed-out mask must never fail the test — but see
            // waitForExtMaskBestEffort above: silence here hid up to 10s of post-save mask time
            // from the report on exactly the step ("Verify Save Result") where that delay matters
            // most for diagnosing a slow/failed save.
            ReportLogger.warn("Post-save loader wait [" + contextLabel + "] gave up after "
                    + (System.currentTimeMillis() - startMs) + "ms with the mask still visible — "
                    + "continuing without failing. (" + e.getClass().getSimpleName() + ")");
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

    /** Attempts-aware overload — Round 8's mask-retry makes multi-attempt waits real; this is
     *  what actually populates the Wait Analysis section's Attempts column with a value > 1. */
    private static void recordIfEnabled(String label, long startMs, String method, int attempts) {
        if (ConfigFactory.getDiagnosticsConfig().waitAnalysisEnabled()) {
            WaitDurationBuffer.record(label, System.currentTimeMillis() - startMs, method, attempts);
        }
    }

    private static long extJsTimeout() {
        long configured = ConfigFactory.getFinanceConfig().extjsTimeout();
        long min        = ConfigFactory.getFinanceConfig().extjsMinTimeout();
        return Math.max(min, configured);
    }
}
