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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Primary interaction layer for page objects — click, sendKeys, getText/getAttribute, and their
 * wait-integrated, retry-hardened, report-logging variants. Every wait blocks through
 * {@link WaitUtils}; every click routes through {@link #clickWithOverlayRetry} so a transient
 * ExtJS mask/backdrop is retried automatically instead of failing the test outright.
 *
 * <p>Prefer the {@code (By, WaitType, String)} overloads from new page-object code — they make
 * the wait strategy explicit at the call site instead of relying on a method-specific default.
 * {@link #fluentClick(By, String)} and {@link #clickBoundlistOptionAcrossPages} are opt-in
 * variants for specific known-slow or paginated widgets.
 */
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

    /**
     * Round 12 (§25): generalizes the "act, then confirm the result is STABLE — not just true
     * once, but true again after a real gap — retrying the whole action if not" idiom this
     * codebase had begun re-implementing separately at multiple call sites (the checkbox
     * selection race fixed in {@code VoucherMainGridBase.clickCheckbox}, the boundlist-option
     * absence conclusion in {@link #clickBoundlistOptionAcrossPages}, and the site-commit
     * verification in {@code AllocateSitesPopup.searchSite}) — all three are the same shape: an
     * ExtJS component can silently reload/re-render shortly after an action or a quick read, so a
     * single instantaneous check cannot tell a genuinely-settled state from one about to be
     * overwritten a moment later. One real implementation instead of several copies that could
     * quietly drift to different retry counts/delays.
     *
     * @param action           what to (re)do each attempt — a no-op ({@code () -> {}}) is valid
     *                         when there is nothing to redo and only the check itself needs
     *                         re-polling (e.g. confirming a genuine absence)
     * @param check            does the desired state currently hold?
     * @param stabilityDelayMs real gap before re-checking — long enough for an async reload this
     *                         action might have triggered to land and be observed, if it's going to
     * @param maxAttempts      total attempts (each: run action, check, wait, re-check) before
     *                         giving up
     * @param logTag           identifies this call site in the retry warning
     * @return {@code true} once {@code check} holds on two consecutive reads separated by
     *         {@code stabilityDelayMs}; {@code false} after {@code maxAttempts} attempts — never
     *         throws itself, so the caller decides what a final {@code false} means (throw, fall
     *         through to another strategy, etc.)
     */
    public static boolean actAndConfirmStable(Runnable action, java.util.function.BooleanSupplier check,
            long stabilityDelayMs, int maxAttempts, String logTag) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            action.run();
            if (check.getAsBoolean()) {
                com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly(
                        stabilityDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (check.getAsBoolean()) {
                    return true;
                }
            }
            if (attempt < maxAttempts) {
                Log.warn("[SeleniumUtils] {}: not yet stable on attempt {}/{} — retrying",
                        logTag, attempt, maxAttempts);
            }
        }
        return false;
    }

    /**
     * Round 14 (§26): {@code true} once the row containing {@code rowInnerLocator} (the clicked
     * cell/div INSIDE the row, not the row itself) is genuinely selected in its grid's own
     * selection model — the same signal already proven reliable for the main voucher grid
     * ({@code VoucherMainGridBase.isChecked}): {@code aria-selected} on the row's ancestor
     * {@code <tr>}, falling back to {@code x-grid-item-selected} on its ancestor {@code <table>}.
     *
     * <p>Added after measuring the root cause of {@code AllocateSitesPopup.searchSite}'s
     * stable-wrong-value failures (FIN-GV-ADD-COMBO-05/-06): the picker's row-visibility wait can
     * be satisfied against the FULL unfiltered list before the picker's own async search-reload
     * lands, so a click that lands pre-reload gets silently discarded when the reload replaces
     * the grid — the field-value readback afterward reads a stable, non-converging wrong value
     * because the click itself, not just the readback, needs to be verified. A stale/not-yet-
     * rendered element reads as "not selected" rather than throwing, so a caller polling this via
     * {@link #actAndConfirmStable} can simply retry — the identical widget is also used by
     * {@code CashBankPopup.selectCashBank}, latently exposed to the same race.
     */
    public static boolean isRowSelected(By rowInnerLocator) {
        try {
            WebElement inner = DriverManager.getDriver().findElement(rowInnerLocator);
            WebElement tr = inner.findElement(By.xpath("./ancestor::tr[1]"));
            if ("true".equals(tr.getAttribute("aria-selected"))) {
                return true;
            }
            WebElement table = inner.findElement(By.xpath("./ancestor::table[1]"));
            String cls = table.getAttribute("class");
            return cls != null && cls.contains("x-grid-item-selected");
        } catch (Exception e) {
            return false;
        }
    }

    /** {@link #click(By, String, LogCategory)} with the default {@code UI_ACTION} category. */
    public static void click(By by, String elementName) {
        click(by, elementName, LogCategory.UI_ACTION);
    }

    /**
     * Waits for {@code by}'s element to be present, clicks it (with overlay retry), then waits
     * for the ExtJS mask to clear and checks for a resulting popup.
     */
    public static void click(By by, String elementName, LogCategory category) {
        WebElement element = waitUntilElementPresent(by);
        clickWithOverlayRetry(element, elementName);
        ReportLogger.info(category, elementName + " is clicked successfully");
        WaitUtils.waitForExtMask(elementName);
        PopupMonitor.checkAfterAction(elementName);
    }

    /** {@link #click(By, WaitType, String, LogCategory)} with the default {@code UI_ACTION} category. */
    public static void click(By by, WaitType waitType, String elementName) {
        click(by, waitType, elementName, LogCategory.UI_ACTION);
    }

    /**
     * Waits for {@code by}'s element per {@code waitType} (present/clickable/visible), clicks it
     * (with overlay retry), then waits for the ExtJS mask to clear and checks for a resulting
     * popup.
     */
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

    /** {@link #click(WebElement, WaitType, String, LogCategory)} with the default {@code UI_ACTION} category. */
    public static void click(WebElement element, WaitType waitType, String elementName) {
        click(element, waitType, elementName, LogCategory.UI_ACTION);
    }

    /**
     * Same as {@link #click(By, WaitType, String, LogCategory)} but for an already-located
     * {@code element} rather than a fresh {@code By} lookup.
     */
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

    /** {@link #jsClick(By, String, LogCategory)} with the default {@code UI_ACTION} category. */
    public static void jsClick(By by, String elementName) {
        jsClick(by, elementName, LogCategory.UI_ACTION);
    }

    /**
     * Clicks {@code by}'s element via JavaScript ({@link JavascriptExecutorUtils#click}) instead
     * of a native Selenium click — bypasses overlay-interception entirely.
     */
    public static void jsClick(By by, String elementName, LogCategory category) {
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();
        WebElement element = WaitUtils.untilClickable(by);
        JavascriptExecutorUtils.click(element);
        ReportLogger.info(category, elementName + " is clicked successfully");
    }

    /** Waits for and returns {@code childLocator}'s element nested inside {@code parentLocator}'s element. */
    public static WebElement findChildElement(By parentLocator, By childLocator) {
        return WaitUtils.webDriverWait()
                .until(ExpectedConditions.presenceOfNestedElementLocatedBy(parentLocator, childLocator));
    }

    /** Waits (default timeout) for {@code by}'s element to become genuinely invisible; logs and returns whether it did rather than throwing. */
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
    /**
     * Waits for {@code by}'s element per {@code waitType}, clears it, and sends {@code value} —
     * masking the logged value for sensitive fields (see {@link SensitiveDataMasker}).
     */
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
            ReportLogger.fieldEntry(elementName + " filled with input value: '" + display + "'");
        } else if (waitType == WaitType.CLICKABLE) {
            element = waitUntilElementClickable(by);
            element.clear();
            element.sendKeys(value);
            ReportLogger.fieldEntry(elementName + " filled with input value: '" + display + "'");
        } else if (waitType == WaitType.VISIBLE) {
            element = waitUntilElementVisible(by);
            element.clear();
            element.sendKeys(value);
            ReportLogger.fieldEntry(elementName + " filled with input value: '" + display + "'");
        }
    }

    /** {@link #sendKeys(By, WaitType, String, String)} using {@link WaitType#PRESENCE}. */
    public static void sendKeys(By by, String value, String elementName) {
        String display = SensitiveDataMasker.isSensitiveByName(elementName)
                || SensitiveDataMasker.isSensitive(null, by)
                ? SensitiveDataMasker.mask(value) : value;
        WebElement element = waitUntilElementPresent(by);
        element.clear();
        element.sendKeys(value);
        ReportLogger.fieldEntry(elementName + " filled with input value: '" + display + "'");
    }

    /** Sends TAB to {@code by}'s element — commits the current field and (for a grid cell editor) advances to the next column. */
    public static void pressTab(By by, String elementName) {
        WebElement element = waitUntilElementPresent(by);
        element.sendKeys(Keys.TAB);
        // Item AD (Round 5): debug-gated UI_ACTION, not a plain uncategorized log — this is
        // routine key-press noise, same treatment as click()/jsClick() already get.
        ReportLogger.uiAction("Tab key pressed on element: " + elementName);
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
        // Item AD (Round 5): debug-gated UI_ACTION, same as pressTab above.
        ReportLogger.uiAction("Enter key pressed on element: " + elementName);
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
            // Round 12 (§23): a single ~2s miss isn't proof the option is genuinely absent on this
            // page — confirmed live that this combo's own debounced live-search request can still
            // be rendering its filtered result well after JavascriptExecutorUtils.waitForExtJS()
            // (which only checks Ext.Ajax.isLoading()/masked components, not this specific
            // combo's search debounce) has already returned. Re-confirm with the shared
            // act-and-confirm-stable idiom (no action to redo here — only the check itself is
            // re-polled) before concluding the option isn't on this page at all.
            if (actAndConfirmStable(() -> {}, () -> isElementPresent(option, perPageProbeSeconds),
                    perPageProbeSeconds * 1000L, 2, logTag)) {
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

    /**
     * Clears a field via Ctrl+A then DELETE, and logs the outcome.
     *
     * <p>The log line is not cosmetic: {@code elementName} used to be accepted and silently
     * ignored, which made this method's effect completely unobservable in the HTML report. That
     * cost a full investigation round — a caller passed {@code "AllocateSites_SiteSearch_PreClear"}
     * expecting to see it in the report, and its absence was initially read as "the clear never
     * ran" when in fact it ran every time and simply never logged.
     *
     * <p>Also logs the value that was actually there beforehand, because Ctrl+A+DELETE is a
     * <b>no-op on a read-only input</b> (e.g. an ExtJS picklist field whose value only the picker
     * may write) — in that case this method succeeds, changes nothing, and only the before/after
     * values reveal it.
     */
    public static void clear(By by, String elementName) {
        WebElement element = waitUntilElementVisible(by);
        String before = element.getAttribute("value");
        element.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        element.sendKeys(Keys.DELETE);
        String after = element.getAttribute("value");
        ReportLogger.fieldEntry("Cleared " + elementName + " — value before: '" + before + "', after: '"
                + after + "'"
                + (after != null && !after.isEmpty() && after.equals(before)
                    ? " (UNCHANGED — the field is likely read-only; Ctrl+A+DELETE had no effect)"
                    : ""));
    }
    /** Reads {@code attributeName} from {@code by}'s element and logs it as a field-validation entry. */
    public static String getAttribute(By by, String attributeName, String elementName) {
        return getAttribute(waitUntilElementVisible(by), attributeName, elementName);
    }

    /** {@link #getAttribute(By, String, String)} against an already-resolved element — for callers
     *  that must disambiguate among multiple DOM matches themselves (e.g. via
     *  {@link WaitUtils#untilAnyVisible(By, long)} when a locator can resolve to more than one
     *  element and only one is genuinely visible/rendered) before reading the attribute. */
    public static String getAttribute(WebElement element, String attributeName, String elementName) {
        String value = element.getAttribute(attributeName);
        // Item P: FIELD_VALIDATION (read-back), not FIELD_ENTRY (write) — visually distinct badge.
        ReportLogger.fieldValidation(elementName + " validated from UI after entry: '" + value + "'");
        return value;
    }

    /** Reads DOM property {@code propertyName} from {@code by}'s element and logs it. */
    public static String getDomProperty(By by, String propertyName, String elementName) {
        WebElement element = waitUntilElementVisible(by);
        String value = element.getDomProperty(propertyName);
        ExtentLogger.info("Property '" + propertyName + "' from " + elementName + " is: '" + value + "'");
        return value;
    }

    /** Reads {@code by}'s element's live computed {@code font-size}/{@code font-family} via
     *  {@code window.getComputedStyle} — same JS mechanism already used by
     *  {@code WaitUtils#isGenuinelyVisibleElement}/{@code isGenuineMaskVisible} for their own
     *  bounding-box checks, applied here to a page-styling regression check instead. Returns
     *  {@code "<size>|<family>"}, e.g. {@code "13px|Segoe UI Light, Open Sans"}. */
    public static String getComputedFont(By by, String elementName) {
        return getComputedFont(waitUntilElementVisible(by), elementName);
    }

    /** {@link #getComputedFont(By, String)} against an already-resolved element — same use case as
     *  {@link #getAttribute(WebElement, String, String)}: a locator that can resolve to more than
     *  one DOM match, where the caller has already picked the genuinely-visible one (e.g. via
     *  {@link WaitUtils#untilAnyVisible(By, long)}). */
    public static String getComputedFont(WebElement element, String elementName) {
        Map<String, String> style = getComputedStyle(element, elementName, "fontSize", "fontFamily");
        return style.get("fontSize") + "|" + style.get("fontFamily");
    }

    /** Reads one or more live {@code window.getComputedStyle} properties off {@code by}'s element —
     *  the general-purpose sibling {@link #getComputedFont(By, String)} is a thin wrapper over.
     *  Property names are JS camelCase computed-style keys (e.g. {@code "color"},
     *  {@code "backgroundColor"}, {@code "borderColor"}, {@code "fontWeight"}, {@code "width"},
     *  {@code "height"}, {@code "padding"}), not CSS kebab-case. Added for the UI-contract
     *  validation framework ({@code com.gsl.validation.ui}), which needs color/border/dimension
     *  properties {@link #getComputedFont} alone can't provide. */
    public static Map<String, String> getComputedStyle(By by, String elementName, String... properties) {
        return getComputedStyle(waitUntilElementVisible(by), elementName, properties);
    }

    /** {@link #getComputedStyle(By, String, String...)} against an already-resolved element — same
     *  use case as {@link #getAttribute(WebElement, String, String)}.
     *
     *  <p>{@code properties} are inlined directly into the JS source (as {@code s.<property>}
     *  reads) rather than passed as an {@code executeScript} array argument — this codebase has no
     *  existing precedent for passing array args through {@link JavascriptExecutorUtils#executeScript},
     *  and every caller here only ever passes fixed internal literal property names (never
     *  test/user data), so string-building the script is both simpler and safe. */
    public static Map<String, String> getComputedStyle(WebElement element, String elementName, String... properties) {
        StringBuilder script = new StringBuilder("var s=window.getComputedStyle(arguments[0]); return ");
        for (int i = 0; i < properties.length; i++) {
            if (i > 0) script.append(" + '|' + ");
            script.append("s.").append(properties[i]);
        }
        script.append(";");
        Object result = JavascriptExecutorUtils.executeScript(script.toString(), element);
        // Chrome's getComputedStyle serializes non-generic font-family names wrapped in double
        // quotes (e.g. "Segoe UI Light", "Open Sans") — a CSSOM serialization detail, not
        // meaningful content, so it's stripped here to keep comparisons against plain expected
        // strings (e.g. PageStylingSpec/FieldStyleSpec) accurate.
        String[] values = String.valueOf(result).replace("\"", "").split("\\|", -1);
        Map<String, String> style = new LinkedHashMap<>();
        for (int i = 0; i < properties.length; i++) {
            style.put(properties[i], i < values.length ? values[i] : null);
        }
        ReportLogger.fieldValidation(elementName + " computed style " + style);
        return style;
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

    /** Whether {@code by}'s element becomes visible within {@code timeoutInSeconds} — never throws. */
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

    /** {@link #getText(By, String, LogCategory)} with the default {@code UI_ACTION} category. */
    public static String getText(By by, String elementName) {
        return getText(by, elementName, LogCategory.UI_ACTION);
    }

    /** Reads and logs {@code by}'s element's {@code innerText}. */
    public static String getText(By by, String elementName, LogCategory category) {
        WebElement element = waitUntilElementVisible(by);
        String text = element.getAttribute("innerText");
        ReportLogger.info(category, "Text from " + elementName + " is: '" + text + "'");
        return text;
    }

    // --- ElementMeta overloads (delegate to existing String elementName methods) ---

    /** {@link ElementMeta} overload of {@link #click(By, String)}. */
    public static void click(By by, ElementMeta meta) {
        click(by, meta.getName());
    }

    /** {@link ElementMeta} overload of {@link #click(By, WaitType, String)}. */
    public static void click(By by, WaitType waitType, ElementMeta meta) {
        click(by, waitType, meta.getName());
    }

    /** {@link ElementMeta} overload of {@link #click(WebElement, WaitType, String)}. */
    public static void click(WebElement element, WaitType waitType, ElementMeta meta) {
        click(element, waitType, meta.getName());
    }

    /** {@link ElementMeta} overload of {@link #sendKeys(By, String, String)}. */
    public static void sendKeys(By by, String value, ElementMeta meta) {
        sendKeys(by, value, meta.getName());
    }

    /** {@link ElementMeta} overload of {@link #sendKeys(By, WaitType, String, String)}. */
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

    /** Waits until {@code locator}'s element is displayed, enabled, and not read-only. */
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
     *   <li>Calls {@link WaitUtils#waitForClickOverlay(String)} (passing {@code elementName} as
     *       the log context label) to wait for ExtJS masks, {@code ext-element-*} backdrops, and
     *       the AJAX queue to clear.</li>
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
                // * Note: ElementClickInterceptedException (the exception ChromeDriver actually
                // throws for "another element would receive the click", e.g. an x-mask overlay)
                // is itself a subclass of ElementNotInteractableException in this Selenium
                // version, so this one catch already covers both — confirmed by the compiler
                // when a redundant explicit multi-catch was tried here.
                if (attempt > maxRetries) {
                    Log.error("[OVERLAY-RETRY] '{}' still intercepted after {} retr{}. Giving up.",
                            elementName, maxRetries, maxRetries == 1 ? "y" : "ies");
                    throw e;
                }
                String overlayTag = detectInterceptingOverlay(e.getMessage());

                // Retrying only pays off when the blocker is something that goes away on its own.
                // A stable layout element (a form label, a panel, a grid cell) never will, and each
                // retry now costs up to ~100s in waitForClickOverlay()'s Phase 1 alone (hardened,
                // genuinely-visible staged-poll — see WaitUtils#waitForGenuineMaskClear), plus its
                // other phases — a materially higher cost than before that hardening, since Phase 1
                // used to give up after one ~40s isDisplayed()-only check. Run 20260807_095328
                // burned ~123s per test on the OLD, cheaper timing that way: a leftover Add-form
                // label (#combo-1216-labelTextEl) sat over the side menu, and all 3 retries were
                // futile by construction. Fail immediately instead, naming the blocker.
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
                WaitUtils.waitForClickOverlay(elementName);
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
        // ExtJS toolbar-layout filler (e.g. <div class="x-toolbar-spacer ..." role="presentation"
        // id="tbspacer-1260">) — a genuinely transient layout artifact, not a real UI element:
        // role="presentation" is the ARIA marker for "this element carries no semantic meaning and
        // is never meant to receive interaction," so ANY element carrying it that intercepts a
        // click is, by definition, a temporary layout-reflow overlap (e.g. a toolbar spacer
        // momentarily sliding over an adjacent button while the toolbar recalculates widths) —
        // never a real stable blocker a user would need to route around. Confirmed live: run
        // 20260909_094016, a "reopen" click on GV's Add button (issued after a page-identity
        // verification race — see VerifiablePage#waitUntilVerified) was intercepted by exactly such
        // a spacer belonging to the Add form that had, by then, already opened.
        if (msg.contains("role=\"presentation\"") || msg.contains("role='presentation'")) {
            return " (by role=presentation layout artifact)";
        }
        return "";
    }

    /** The ChromeDriver fragment that names what actually swallowed the click. */
    private static final String INTERCEPTOR_FRAGMENT = "other element would receive the click";

    /**
     * Whether the thing blocking a click is expected to disappear by itself, and so is worth
     * waiting and retrying for.
     *
     * <p>Three cases, and the distinction matters because each retry now costs up to ~100s in
     * {@link WaitUtils#waitForClickOverlay(String)}'s hardened Phase 1 mask-wait alone (see
     * {@code WaitUtils#waitForGenuineMaskClear}), plus its other phases:
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
