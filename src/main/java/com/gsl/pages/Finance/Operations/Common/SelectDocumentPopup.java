package com.gsl.pages.Finance.Operations.Common;

import com.google.common.util.concurrent.Uninterruptibles;
import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.gsl.utils.Log;

public class SelectDocumentPopup {

    private static final String CHECKBOX_EXTJS_XPATH =
            "//div[normalize-space()='%s']/ancestor::tr//td[contains(@class,'x-grid-cell-first')]//div[contains(@class,'x-grid-checkcolumn')]";
    private static final By SELECT_BUTTON = By.xpath("//span[normalize-space()='Select']");

    public SelectDocumentPopup selectDocument(String documentNumber) {
        WebDriver driver = DriverManager.getDriver();
        By checkboxBy = By.xpath(String.format(CHECKBOX_EXTJS_XPATH, documentNumber));

        // Reset to top before every search so that:
        //   1st document  → already at top, no visible effect.
        //   2nd+ document → grid was left at the previous document's scroll
        //                   position; resetting ensures the search always
        //                   starts from row 0, not from a mid-list position.
        JavascriptExecutorUtils.resetGridScrollToTop();
        Uninterruptibles.sleepUninterruptibly(
                ConfigFactory.getConfig().selectDocGridLoadBufferMs(), TimeUnit.MILLISECONDS);

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
                        ConfigFactory.getConfig().selectDocJumpRenderMs(), TimeUnit.MILLISECONDS);
                try {
                    checkbox = driver.findElement(checkboxBy);
                    Log.warn("SelectDocumentPopup: Checkbox found via direct jump for [{}]", documentNumber);
                } catch (NoSuchElementException ex) {
                    Log.warn("SelectDocumentPopup: Direct jump landed but checkbox still absent for [{}] — falling back to proportional scroll", documentNumber);
                }
            }

            // ── Strategy 2: proportional scroll loop — fallback for BufferedStore
            //    (target page not yet loaded in memory).
            //    Step = scrollHeight/15, so ≤ 15 steps covers the full grid
            //    regardless of whether it has 500 or 60 000 rows.
            if (checkbox == null) {
                Log.warn("SelectDocumentPopup: Starting proportional scroll-poll loop for [{}] — grid diagnostics: {}",
                        documentNumber, JavascriptExecutorUtils.getGridDiagnostics());
                int maxAttempts = Math.max(
                        ConfigFactory.getConfig().selectDocScrollMinAttempts(),
                        ConfigFactory.getConfig().selectDocScrollMaxAttempts());
                for (int attempt = 1; attempt <= maxAttempts && checkbox == null; attempt++) {
                    long newScrollTop = JavascriptExecutorUtils.scrollSelectDocumentGridDown();
                    Log.warn("SelectDocumentPopup: Proportional scroll attempt [{}], scrollTop=[{}] for [{}]", attempt, newScrollTop, documentNumber);
                    Uninterruptibles.sleepUninterruptibly(
                            ConfigFactory.getConfig().selectDocScrollStepWaitMs(), TimeUnit.MILLISECONDS);
                    try {
                        checkbox = driver.findElement(checkboxBy);
                        Log.warn("SelectDocumentPopup: Checkbox found at proportional scroll attempt [{}] for [{}]", attempt, documentNumber);
                    } catch (NoSuchElementException ex) {
                        // row not rendered yet — continue
                    }
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
                            ConfigFactory.getConfig().selectDocFocusRowRenderMs(), TimeUnit.MILLISECONDS);
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
                    "SelectDocumentPopup: Checkbox for [" + documentNumber + "] not found after direct jump + 20 proportional scroll attempts + focusRow fallback");
            }
        }

        Log.debug("SelectDocumentPopup: Waiting for checkbox visibility for document [{}]", documentNumber);
        new WebDriverWait(driver, Duration.ofSeconds(ConfigFactory.getConfig().extjsMinTimeout()))
                .until(ExpectedConditions.visibilityOf(checkbox));
        Log.debug("SelectDocumentPopup: Checkbox is visible for document [{}]", documentNumber);
        Uninterruptibles.sleepUninterruptibly(
                ConfigFactory.getConfig().selectDocClickBufferMs(), TimeUnit.MILLISECONDS);

        Log.debug("SelectDocumentPopup: Attempting click on checkbox for document [{}]", documentNumber);
        try {
            checkbox.click();
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
        SeleniumUtils.click(SELECT_BUTTON, WaitType.CLICKABLE, "SELECT_BUTTON");
        JavascriptExecutorUtils.waitForExtJS();
    }
}
