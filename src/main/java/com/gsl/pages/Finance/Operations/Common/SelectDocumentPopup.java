package com.gsl.pages.Finance.Operations.Common;

import com.google.common.util.concurrent.Uninterruptibles;
import com.gsl.config.ConfigFactory;
import com.gsl.constants.FrameworkConstants;
import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import com.gsl.pages.enums.CommonButton;
import com.gsl.utils.Selenium.ActionUtils;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.gsl.utils.Log;

public class SelectDocumentPopup {

    private static final class Loc {
        static final String CHECKBOX_EXTJS_XPATH =
                "//div[normalize-space()='%s']/ancestor::tr//td[contains(@class,'x-grid-cell-first')]//div[contains(@class,'x-grid-checkcolumn')]";
    }

    public SelectDocumentPopup selectDocument(String documentNumber) {
        WebDriver driver = DriverManager.getDriver();
        By checkboxBy = By.xpath(String.format(Loc.CHECKBOX_EXTJS_XPATH, documentNumber));

        // Reset to top before every search so that:
        //   1st document  → already at top, no visible effect.
        //   2nd+ document → grid was left at the previous document's scroll
        //                   position; resetting ensures the search always
        //                   starts from row 0, not from a mid-list position.
        JavascriptExecutorUtils.resetGridScrollToTop();
        Uninterruptibles.sleepUninterruptibly(
                ConfigFactory.getWaitConfig().selectDocGridLoadBufferMs(), TimeUnit.MILLISECONDS);

        WebElement checkbox;
        Log.debug("SelectDocumentPopup: Attempting direct find of checkbox for document [{}]", documentNumber);
        try {
            checkbox = driver.findElement(checkboxBy);
            Log.debug("SelectDocumentPopup: Checkbox found directly in viewport for document [{}]", documentNumber);
        } catch (NoSuchElementException e) {
            Log.warn("SelectDocumentPopup: Checkbox not in viewport for [{}] — attempting direct jump", documentNumber);
            checkbox = null;

            // ── Strategy 1: direct jump (works for any record count when all
            //    records are loaded in the store — regular store, 500 or 60 000+)
            long jumpedTo = JavascriptExecutorUtils.jumpGridToDocument(documentNumber);
            Log.warn("SelectDocumentPopup: jumpGridToDocument scrollTop=[{}] for [{}]", jumpedTo, documentNumber);
            if (jumpedTo >= 0) {
                Uninterruptibles.sleepUninterruptibly(
                        ConfigFactory.getWaitConfig().selectDocJumpRenderMs(), TimeUnit.MILLISECONDS);
                try {
                    checkbox = driver.findElement(checkboxBy);
                    Log.warn("SelectDocumentPopup: Checkbox found via direct jump for [{}]", documentNumber);
                } catch (NoSuchElementException ex) {
                    Log.warn("SelectDocumentPopup: Direct jump landed but checkbox still absent for [{}] — falling back to proportional scroll", documentNumber);
                }
            }

            // ── Strategy 2: mouse-wheel scroll loop — fallback for BufferedStore
            //    (target page not yet loaded in memory). Uses a TRUSTED W3C
            //    wheel event so ExtJS's renderer always reacts (a synthetic
            //    scrollTop= write can be ignored by the buffered renderer).
            //    Terminates on content-based end-of-list, not a fixed attempt
            //    count: at-bottom + store-not-loading + no progress in
            //    scrollTop/storeCount/top-doc/bottom-doc for 2 iterations.
            //    The safety cap is a runaway-loop guard, not a tuning knob.
            if (checkbox == null) {
                WebElement gridView = JavascriptExecutorUtils.getActiveGridViewElement();
                Map<String, Object> prev = JavascriptExecutorUtils.getGridScrollProgress();
                long prevScrollTop   = readLong(prev, "scrollTop", 0L);
                long scrollHeight    = readLong(prev, "scrollHeight", 0L);
                long clientHeight    = readLong(prev, "clientHeight", 360L);
                long prevStoreCount  = readLong(prev, "storeCount", -1L);
                String prevTopDoc    = readString(prev, "topDocNumber");
                String prevBottomDoc = readString(prev, "bottomDocNumber");

                // One viewport per tick. Smaller than scrollHeight/15 so the
                // BufferedRenderer's leading buffer zone overlaps consecutive
                // ticks — every row is rendered into the DOM at least once.
                int deltaY      = (int) Math.max(200L, clientHeight);
                int safetyCap   = ConfigFactory.getWaitConfig().selectDocScrollSafetyCap();
                long stepWaitMs = ConfigFactory.getWaitConfig().selectDocScrollStepWaitMs();
                int noProgressStreak = 0;

                Log.warn("SelectDocumentPopup: Starting mouse-wheel scroll loop for [{}] — clientHeight=[{}] scrollHeight=[{}] deltaY=[{}] diagnostics: {}",
                        documentNumber, clientHeight, scrollHeight, deltaY, JavascriptExecutorUtils.getGridDiagnostics());

                for (int attempt = 1; attempt <= safetyCap && checkbox == null; attempt++) {
                    if (gridView != null) {
                        ActionUtils.scrollByWheel(gridView, deltaY);
                    } else {
                        // No origin element → fall back to JS scroll for this tick.
                        JavascriptExecutorUtils.scrollSelectDocumentGridDown();
                    }
                    Uninterruptibles.sleepUninterruptibly(stepWaitMs, TimeUnit.MILLISECONDS);

                    Map<String, Object> curr = JavascriptExecutorUtils.getGridScrollProgress();
                    long currScrollTop   = readLong(curr, "scrollTop", prevScrollTop);
                    long currStoreCount  = readLong(curr, "storeCount", prevStoreCount);
                    String currTopDoc    = readString(curr, "topDocNumber");
                    String currBottomDoc = readString(curr, "bottomDocNumber");
                    boolean atBottom     = readBool(curr, "atBottom", false);
                    boolean storeLoading = readBool(curr, "storeLoading", false);

                    Log.warn("SelectDocumentPopup: wheel attempt [{}] target=[{}] viewport top=[{}] bottom=[{}] scrollTop=[{}] storeCount=[{}]",
                            attempt, documentNumber, currTopDoc, currBottomDoc, currScrollTop, currStoreCount);

                    try {
                        checkbox = driver.findElement(checkboxBy);
                        Log.warn("SelectDocumentPopup: Checkbox found at wheel attempt [{}] for [{}]", attempt, documentNumber);
                        break;
                    } catch (NoSuchElementException ex) {
                        // row not rendered yet — evaluate progress and continue
                    }

                    boolean progressed =
                            currScrollTop > prevScrollTop ||
                            currStoreCount > prevStoreCount ||
                            !Objects.equals(currTopDoc, prevTopDoc) ||
                            !Objects.equals(currBottomDoc, prevBottomDoc);
                    if (atBottom && !storeLoading && !progressed) {
                        noProgressStreak++;
                    } else {
                        noProgressStreak = 0;
                    }
                    if (noProgressStreak >= 2) {
                        Log.warn("SelectDocumentPopup: End-of-list reached at attempt [{}] for [{}] (atBottom & no progress for 2 iterations)",
                                attempt, documentNumber);
                        break;
                    }
                    prevScrollTop  = currScrollTop;
                    prevStoreCount = currStoreCount;
                    prevTopDoc     = currTopDoc;
                    prevBottomDoc  = currBottomDoc;
                }
            }

            // ── Strategy 3: ExtJS focusRow — last resort when manual scroll
            //    math or proportional steps miss the target row.
            if (checkbox == null) {
                Log.warn("SelectDocumentPopup: Starting focusRow fallback for [{}]", documentNumber);
                long focusedIdx = JavascriptExecutorUtils.focusGridRowByDocument(documentNumber);
                Log.warn("SelectDocumentPopup: focusGridRowByDocument returned index=[{}] for [{}]", focusedIdx, documentNumber);
                if (focusedIdx >= 0) {
                    Uninterruptibles.sleepUninterruptibly(
                            ConfigFactory.getWaitConfig().selectDocFocusRowRenderMs(), TimeUnit.MILLISECONDS);
                    try {
                        checkbox = driver.findElement(checkboxBy);
                        Log.warn("SelectDocumentPopup: Checkbox found via focusRow fallback for [{}]", documentNumber);
                    } catch (NoSuchElementException ex) {
                        Log.warn("SelectDocumentPopup: focusRow landed but checkbox still absent for [{}]", documentNumber);
                    }
                }
            }

            if (checkbox == null) {
                throw new NoSuchElementException(
                    "SelectDocumentPopup: Checkbox for [" + documentNumber + "] not found after direct jump + mouse-wheel scroll to end-of-list + focusRow fallback. Final diagnostics: "
                            + JavascriptExecutorUtils.getGridDiagnostics());
            }
        }

        Log.debug("SelectDocumentPopup: Waiting for checkbox visibility for document [{}]", documentNumber);
        WaitUtils.webDriverWait(ConfigFactory.getFinanceConfig().extjsMinTimeout())
                .until(ExpectedConditions.visibilityOf(checkbox));
        Log.debug("SelectDocumentPopup: Checkbox is visible for document [{}]", documentNumber);
        Uninterruptibles.sleepUninterruptibly(
                ConfigFactory.getWaitConfig().selectDocClickBufferMs(), TimeUnit.MILLISECONDS);

        Log.debug("SelectDocumentPopup: Attempting click on checkbox for document [{}]", documentNumber);
        try {
            SeleniumUtils.clickWithOverlayRetry(checkbox, "SELECT_DOC_CHECKBOX_" + documentNumber);
            Log.debug("SelectDocumentPopup: Mouse click succeeded for document [{}]", documentNumber);
        } catch (Exception e) {
            Log.warn("SelectDocumentPopup: Mouse click failed for [{}] - {}, retrying with JS click", documentNumber, e.getMessage());
            JavascriptExecutorUtils.click(checkbox);
            Log.debug("SelectDocumentPopup: JS click executed for document [{}]", documentNumber);
        }

        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    public void clickSelect() {
        // waitForExtJS() alone isn't sufficient here — this window's loading indicator isn't an
        // ExtJS cmp.mask()-tracked component (same gap AdjustmentEntry.selectDocument() already
        // had to work around for this same popup when it first opens). Checking documents can
        // re-trigger this loader (to recompute the selected total / validate before enabling
        // "Select"), so wait for it to clear before clicking, not just for isMasked=true to drop.
        waitForSelectDocGridLoaderToClear();
        SeleniumUtils.click(CommonButton.SELECT.locator(), WaitType.CLICKABLE, "SELECT_BUTTON");
        JavascriptExecutorUtils.waitForExtJS();
    }

    private void waitForSelectDocGridLoaderToClear() {
        int maxRetries = ConfigFactory.getWaitConfig().selectDocLoaderMaxRetries();
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                WaitUtils.webDriverWait(ConfigFactory.getFinanceConfig().extjsTimeout())
                        .until(ExpectedConditions.invisibilityOfElementLocated(
                                FrameworkConstants.getSelectDocGridLoader()));
                return;
            } catch (TimeoutException te) {
                if (attempt == maxRetries) throw te;
                Log.warn("SelectDocumentPopup: loader still visible before Select click, attempt [{}], retrying...", attempt);
            }
        }
    }

    // Tiny readers that coerce values out of the JS-returned progress Map.
    // Selenium returns JS numbers as Long or Double depending on the value,
    // so we normalise both here.
    private static long readLong(Map<String, Object> m, String key, long fallback) {
        if (m == null) return fallback;
        Object v = m.get(key);
        return v instanceof Number ? ((Number) v).longValue() : fallback;
    }

    private static boolean readBool(Map<String, Object> m, String key, boolean fallback) {
        if (m == null) return fallback;
        Object v = m.get(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    private static String readString(Map<String, Object> m, String key) {
        if (m == null) return null;
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }
}
