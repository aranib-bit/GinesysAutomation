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
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.function.Supplier;

public class SeleniumUtils {

    private SeleniumUtils() {}

    /** Caps {@link #fillAndVerifyCommit}'s retry loop — an intermittent ExtJS commit race (not a
     *  deterministic bug) needs a bounded retry, not a longer single wait; see that method's
     *  Javadoc. Promoted here (from its original home in {@code VoucherDocumentInformationBase})
     *  so any field-fill method across the Finance page-object layer can reuse the same
     *  retry-until-verified mechanism, not just the Reference/Remarks fields it was first fixed
     *  for. */
    public static final int MAX_COMMIT_ATTEMPTS = 3;

    /**
     * Runs {@code fillAndCommitAction} (whatever combination of type/click/Tab a given field
     * needs to both enter and commit a value), then reads the field back via {@code readback} to
     * confirm the commit actually landed — retrying the whole cycle up to
     * {@link #MAX_COMMIT_ATTEMPTS} times before failing loudly.
     *
     * <p>Fixes a confirmed-live, intermittent ExtJS commit race where a field visibly shows the
     * typed/selected value but what's actually submitted on Save silently stays stale — first
     * caught on the Reference field, then Remarks (both in {@code VoucherDocumentInformationBase});
     * generalized here so any other field (General Ledger, Sub Ledger, Amount, Is Reversal,
     * Ledger Narration, Cash/Bank, ...) can get the same protection via a caller-supplied
     * {@code readback} instead of assuming a plain text input's {@code "value"} attribute — a
     * combo/picker field's committed display text may live in a different DOM location entirely.
     */
    public static void fillAndVerifyCommit(String fieldLabel, String value,
            Runnable fillAndCommitAction, Supplier<String> readback) {
        for (int attempt = 1; attempt <= MAX_COMMIT_ATTEMPTS; attempt++) {
            fillAndCommitAction.run();
            String actual = readback.get();
            if (value.equals(actual)) return;
            Log.warn("[SeleniumUtils] {} field shows '{}' after fill+commit attempt {}/{}, expected "
                    + "'{}' — retrying.", fieldLabel, actual, attempt, MAX_COMMIT_ATTEMPTS, value);
        }
        throw new AssertionError("[SeleniumUtils] " + fieldLabel + " field failed to commit '" + value
                + "' after " + MAX_COMMIT_ATTEMPTS + " attempts — UI fill/commit likely failed.");
    }

    /**
     * {@link BigDecimal}-aware overload of {@link #fillAndVerifyCommit(String, String, Runnable,
     * Supplier)} for amount fields — compares via {@link BigDecimal#compareTo} rather than string
     * equality, since a committed cell's formatted text (e.g. trailing/absent decimal places) need
     * not match the input's own {@code toPlainString()} rendering even when the two are numerically
     * identical. Same retry-until-verified mechanism and attempt cap otherwise.
     */
    public static void fillAndVerifyCommit(String fieldLabel, java.math.BigDecimal value,
            Runnable fillAndCommitAction, Supplier<java.math.BigDecimal> readback) {
        for (int attempt = 1; attempt <= MAX_COMMIT_ATTEMPTS; attempt++) {
            fillAndCommitAction.run();
            java.math.BigDecimal actual = readback.get();
            if (actual != null && value.compareTo(actual) == 0) return;
            Log.warn("[SeleniumUtils] {} field shows '{}' after fill+commit attempt {}/{}, expected "
                    + "'{}' — retrying.", fieldLabel, actual, attempt, MAX_COMMIT_ATTEMPTS, value);
        }
        throw new AssertionError("[SeleniumUtils] " + fieldLabel + " field failed to commit '" + value
                + "' after " + MAX_COMMIT_ATTEMPTS + " attempts — UI fill/commit likely failed.");
    }

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

    // ── Stale-element retry (By-based only) ───────────────────────────────────
    //
    // click(By, ...)/sendKeys(By, ...) resolve their WebElement once, then act on that single
    // reference — clickWithOverlayRetry only retries ElementClickInterceptedException/
    // ElementNotInteractableException (transient overlay blocks), never StaleElementReferenceException.
    // If the target re-renders between "element resolved" and "element acted on" (an ExtJS grid
    // reload is the common trigger — see AllocateSitesPopup's own Javadoc), the resolved reference
    // goes stale and the action throws uncaught. These wrappers exist for call sites exposed to
    // that race; they re-invoke the same By-based method (which re-resolves fresh from the locator
    // every call) on StaleElementReferenceException, bounded to a small retry count — purely
    // additive, no existing click(...)/sendKeys(...) call site elsewhere is touched.
    private static final int STALE_RETRY_MAX_ATTEMPTS = 2;

    /** {@link #click(By, WaitType, String)}, retrying up to {@value #STALE_RETRY_MAX_ATTEMPTS} times on {@link StaleElementReferenceException}. */
    public static void clickWithStaleRetry(By by, WaitType waitType, String elementName) {
        for (int attempt = 1; attempt <= STALE_RETRY_MAX_ATTEMPTS; attempt++) {
            try {
                click(by, waitType, elementName);
                return;
            } catch (StaleElementReferenceException e) {
                if (attempt >= STALE_RETRY_MAX_ATTEMPTS) {
                    Log.error("[STALE-RETRY] '{}' still stale after {} attempts. Giving up.", elementName, attempt);
                    throw e;
                }
                Log.warn("[STALE-RETRY] Click on '{}' hit a stale element — attempt {}/{}. Re-resolving and retrying…",
                        elementName, attempt, STALE_RETRY_MAX_ATTEMPTS);
                ExtentLogger.warning(String.format(
                        "[Stale-Element Retry %d/%d] Click on '%s' hit a stale element (likely an ExtJS re-render) — re-resolving",
                        attempt, STALE_RETRY_MAX_ATTEMPTS, elementName));
                JavascriptExecutorUtils.waitForExtJS();
            }
        }
    }

    /** {@link #sendKeys(By, WaitType, String, String)}, retrying up to {@value #STALE_RETRY_MAX_ATTEMPTS} times on {@link StaleElementReferenceException}. */
    public static void sendKeysWithStaleRetry(By by, WaitType waitType, String value, String elementName) {
        for (int attempt = 1; attempt <= STALE_RETRY_MAX_ATTEMPTS; attempt++) {
            try {
                sendKeys(by, waitType, value, elementName);
                return;
            } catch (StaleElementReferenceException | ElementNotInteractableException e) {
                // Widened (2026-08-31) beyond StaleElementReferenceException to also retry
                // ElementNotInteractableException — confirmed live (report 20260831_163724,
                // GridColumnMenu.applyFilter's hover-flyout auto-collapse race) that a flyout
                // input can go not-interactable (hidden/collapsed) rather than genuinely stale
                // when ExtJS auto-collapses it mid-interaction; same re-resolve-and-retry
                // response applies to both.
                String exceptionKind = e instanceof StaleElementReferenceException ? "a stale element" : "a not-interactable element";
                if (attempt >= STALE_RETRY_MAX_ATTEMPTS) {
                    Log.error("[STALE-RETRY] '{}' still hitting {} after {} attempts. Giving up.", elementName, exceptionKind, attempt);
                    throw e;
                }
                Log.warn("[STALE-RETRY] SendKeys on '{}' hit {} — attempt {}/{}. Re-resolving and retrying…",
                        elementName, exceptionKind, attempt, STALE_RETRY_MAX_ATTEMPTS);
                ExtentLogger.warning(String.format(
                        "[Stale-Element Retry %d/%d] SendKeys on '%s' hit %s (likely an ExtJS re-render/flyout collapse) — re-resolving",
                        attempt, STALE_RETRY_MAX_ATTEMPTS, elementName, exceptionKind));
                JavascriptExecutorUtils.waitForExtJS();
            }
        }
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

    /**
     * {@link #elementDisappear(By, String)} with an explicit timeout — for waits with a
     * structurally different SLA than the standard 30s default (e.g. the post-module-click
     * "Loading Package" bootstrap wait). Every other call site is unaffected.
     */
    public static boolean elementDisappear(By by, String elementName, long seconds) {
        boolean state = waitUntilElementInVisible(by, seconds);
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

    /**
     * Sends ENTER to {@code by} — the commit-in-place counterpart to {@link #pressTab}.
     *
     * <p>For an ExtJS grid cell editor, ENTER completes the edit and closes the editor WITHOUT
     * advancing to the next editable column ({@code Ext.grid.CellEditor} defaults
     * {@code completeOnEnter: true}), whereas TAB commits AND auto-advances. Prefer this wherever
     * the auto-advance would leave an unwanted editor open on the following column — see
     * {@code GeneralVoucherExpenseIncomeDetail.setIsReversal}, whose own entry state was made
     * ambiguous by exactly that side effect from the preceding field's TAB commit.
     */
    public static void pressEnter(By by, String elementName) {
        WebElement element = waitUntilElementPresent(by);
        element.sendKeys(Keys.ENTER);
        ExtentLogger.info("Enter key pressed on element: " + elementName);
    }

    /**
     * Clicks {@code option} inside a paged ExtJS grid-style boundlist combo, paging FORWARD
     * through the picker's own Next-Page control when {@code option} isn't on the currently
     * rendered page — instead of a single wait-and-click that can only ever see page 1.
     *
     * <p><b>Why this exists.</b> Several combo pickers across GV/AR/AP (General Ledger and Sub
     * Ledger selection) share one ExtJS widget: a live-typed search that performs a
     * CONTAINS-style match and returns every matching row, paginated via a real
     * First/Previous/Next/Last toolbar embedded inside the floating boundlist — confirmed live
     * (run 20260903_100559, FIN-GV-EDIT-DETAIL-02): the open combo's picker showed "Page 1 of 4"
     * with Next Page enabled while the framework's old single-page lookup timed out after 30s,
     * because the exact match happened to be on a later page. Every one of these call sites
     * previously drove the exact same bare
     * {@code fluentWaitBuilder().until(driver -> {driver.findElement(option).click(); ...})} loop
     * — one page, no paging — so this is one shared fix for GV's own picker (formerly
     * {@code gridBoundlistOption}) and AR's/AP's {@code subLedgerPickerRow}, not three separate
     * ones (generic mechanics belong in one utility, per this repo's own layering convention;
     * see docs/conventions/utility-layering.md).
     *
     * <p>Generic on purpose: takes the boundlist's own root XPath as a plain {@code String} (each
     * caller already has one — GV's {@code BOUNDLIST_ROOT}, AR/AP's inline
     * {@code x-boundlist-floating} root) and the target option as a plain {@link By}, so the Next
     * Page button below is scoped inside THAT specific boundlist and can never be confused with
     * the main list-page grid's own, structurally-identical footer (both can coexist in the DOM
     * at once).
     *
     * <p>Stops and fails clearly only once Next Page itself is confirmed absent/disabled — i.e.
     * the last page was reached without a match — rather than on a fixed timeout that can't tell
     * "still searching further pages" from "genuinely not present." {@code maxPages} is a bound
     * on page COUNT (a sanity ceiling against a pathological infinite-paging state), not a
     * disguised wait-longer timeout.
     *
     * @param boundlistRootXpath the open boundlist's own root XPath (e.g.
     *                           {@code "//div[contains(@class,'x-boundlist-floating') and ...]"})
     * @param option             the target option's locator, scoped inside that same boundlist
     * @param logTag             label used in step logging and the eventual failure message
     */
    public static void clickBoundlistOptionAcrossPages(String boundlistRootXpath, By option, String logTag) {
        By nextPageEnabled = By.xpath(boundlistRootXpath
                + "//span[contains(@class,'x-tbar-page-next')]/ancestor::a[1][@aria-disabled='false']");
        By nextPageDisabled = By.xpath(boundlistRootXpath
                + "//span[contains(@class,'x-tbar-page-next')]/ancestor::a[1][@aria-disabled='true']");

        int perPageProbeSeconds = 2;
        int maxPages = 50;
        for (int page = 1; page <= maxPages; page++) {
            if (isElementPresent(option, perPageProbeSeconds)) {
                click(option, WaitType.CLICKABLE, logTag);
                return;
            }
            if (isElementPresent(nextPageEnabled, perPageProbeSeconds)) {
                click(nextPageEnabled, WaitType.CLICKABLE, logTag + "_NEXT_PAGE");
                JavascriptExecutorUtils.waitForExtJS();
                continue;
            }
            boolean confirmedLastPage = isElementPresent(nextPageDisabled, 1);
            throw new NoSuchElementException("[SeleniumUtils] " + logTag + ": option not found on page "
                    + page + ", and Next Page is not enabled — "
                    + (confirmedLastPage
                        ? "reached the last page without a match."
                        : "the boundlist/paging toolbar did not render as expected.")
                    + " Locator: " + option);
        }
        throw new NoSuchElementException("[SeleniumUtils] " + logTag + ": exceeded " + maxPages
                + " pages without finding " + option);
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

    private static boolean waitUntilElementInVisible(By by, long seconds) {
        return WaitUtils.untilInvisible(by, seconds);
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

                // Retrying only pays off when the blocker is something that goes away on its own.
                // A stable layout element (a form label, a panel, a grid cell) never will, and each
                // retry costs two full componentready timeouts inside waitForClickOverlay(). Run
                // 20260807_095328 burned ~123s per test that way: a leftover Add-form label
                // (#combo-1216-labelTextEl) sat over the side menu, and all 3 retries were futile
                // by construction. Fail immediately instead, naming the blocker.
                if (!isTransientOverlayBlock(e.getMessage())) {
                    String blocker = describeBlocker(e.getMessage());
                    Log.error("[OVERLAY-RETRY] Click on '{}' is blocked by {} — a stable layout element, "
                            + "not a transient overlay. Skipping {} retr{} that cannot succeed.",
                            elementName, blocker, maxRetries, maxRetries == 1 ? "y" : "ies");
                    ExtentLogger.fail(String.format(
                            "Click on '%s' is blocked by %s. That is part of the page layout, not a "
                            + "loading mask — retrying cannot clear it. Most likely a previous step left "
                            + "a form or dialog open over this element.", elementName, blocker));
                    throw e;
                }

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
        // App-level splash screen mid CSS opacity fade-out — genuinely transient, not a stable
        // layout element. Report 20260901_112001 misclassified <div id="splashScreen"
        // style="opacity: 0.0296854;"> as "part of the page layout" and failed fast instead of
        // retrying, because none of the branches here matched it.
        if (msg.contains("splashScreen"))  return " (by #splashScreen)";
        if (msg.contains("x-mask"))        return " (by .x-mask)";
        if (msg.contains("x-mask-msg"))    return " (by .x-mask-msg)";
        if (msg.contains("modal"))         return " (by modal overlay)";
        return "";
    }

    /** The ChromeDriver fragment that names what actually swallowed the click. */
    private static final String INTERCEPTOR_FRAGMENT = "other element would receive the click";

    /**
     * Whether the thing blocking a click is expected to disappear by itself, and so is worth
     * waiting and retrying for.
     *
     * <p>Three cases, and the distinction matters because each retry costs two
     * {@code componentready.timeout.seconds} windows inside {@link WaitUtils#waitForClickOverlay()}:
     * <ul>
     *   <li>No interceptor named at all (a plain "element not interactable" — mid-animation, not yet
     *       scrolled into view): <b>transient</b>, retry. This is the case the retry loop was built for.</li>
     *   <li>An interceptor named, and it is a loading mask or ExtJS backdrop
     *       ({@code ext-element-*}, {@code .x-mask}, {@code .x-mask-msg}, a modal overlay):
     *       <b>transient</b>, retry.</li>
     *   <li>An interceptor named, and it is anything else — a form label, panel, grid cell:
     *       <b>not transient</b>. It is where the layout put it and no amount of mask-waiting moves
     *       it. Fail fast so the report names the real problem instead of a timeout.</li>
     * </ul>
     *
     * <p>Classifies the <b>interceptor's</b> markup only, never the whole message. The driver
     * message names two elements — the click target and the thing that swallowed it — and ExtJS
     * hands out {@code id="ext-element-NN"} freely to ordinary nodes (the captured DOM has one on a
     * boundlist {@code <td>}). Scanning the full message would let an {@code ext-element} id on the
     * <i>target</i> mark a genuine layout obstruction as a transient backdrop, restoring exactly the
     * futile retry loop this guard exists to prevent.
     */
    private static boolean isTransientOverlayBlock(String msg) {
        if (msg == null || msg.isBlank()) return true;
        int idx = msg.toLowerCase().indexOf(INTERCEPTOR_FRAGMENT);
        if (idx < 0) {
            return true; // nothing named as the interceptor — not a layout obstruction
        }
        String interceptor = msg.substring(idx + INTERCEPTOR_FRAGMENT.length());
        return !detectInterceptingOverlay(interceptor).isEmpty();
    }

    /**
     * Human-readable description of the intercepting element, pulled from the driver message's
     * "Other element would receive the click: &lt;…&gt;" fragment — e.g.
     * {@code <span id="combo-1216-labelTextEl" class="x-form-item-label-text">}. Falls back to a
     * generic label if the message is shaped unexpectedly.
     */
    private static String describeBlocker(String msg) {
        if (msg == null || msg.isBlank()) return "another element";
        int idx = msg.toLowerCase().indexOf(INTERCEPTOR_FRAGMENT);
        if (idx < 0) return "another element";
        String tail = msg.substring(idx + INTERCEPTOR_FRAGMENT.length()).trim();
        if (tail.startsWith(":")) tail = tail.substring(1).trim();
        // Keep it to the opening tag — the message can carry a long trailing stack/context.
        int end = tail.indexOf('>');
        String blocker = (end > 0 ? tail.substring(0, end + 1) : tail).trim();
        return blocker.isEmpty() ? "another element" : blocker;
    }
}
