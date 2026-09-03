package com.gsl.pages.Finance.Operations.Common;

import com.gsl.pages.enums.CommonButton;
import com.gsl.pages.enums.Finance.Operations.Common.AdjustmentPopups;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherGridColumnHeader;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import com.gsl.config.ConfigFactory;
import com.gsl.constants.FrameworkConstants;
import com.gsl.driver.DriverManager;
import com.gsl.pages.pagecomponent.Finance.Common.AdjustmentEntryParent;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.ActionUtils;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.popup.PopupMonitor;
import com.gsl.utils.Selenium.WaitUtils;
import com.gsl.enums.VoucherDomain;
import com.gsl.enums.WaitType;

import java.time.Duration;

public class AdjustmentEntry {

    private final AdjustmentEntryParent parent;
    private final VoucherDomain voucherDomain;

    public AdjustmentEntry(AdjustmentEntryParent parent) {
        this.parent = parent;
        this.voucherDomain = parent.voucherDomain();
    }

    // ===========================
    // Locators
    // ===========================

    // CANCEL_BUTTON → CommonButton.CANCEL.locator() (plain Cancel, no positional constraint)
    // Adjusted Document menu options → AdjustmentPopups.<X>.menuOptionLocator()
    private static final class Loc {
        static final By ADJUSTED_DOCUMENT_MENU =
                By.xpath("//div[text()='Adjusted Document']//following::*[contains(@class,'mdi mdi-20px mdi-dots-horizontal')]");

        // Scoped OK — the app's own shell is always `x-window-maximized`; any floating dialog
        // (the Adjustment Entry window itself for a plain FIFO Set-Off inline confirmation, or a
        // nested popup such as Reference Site wise FIFO set-off) renders as a non-maximized
        // x-window.
        //
        // @aria-hidden='false' is the load-bearing condition: Ext.Msg is a singleton ExtJS hides
        // (never destroys) between uses, so once any message box has appeared anywhere in the
        // session, its own "OK" button lingers in the DOM as a permanently-hidden match for this
        // xpath — with aria-hidden="true", vs. "false" on the real, currently-shown button.
        // (An earlier version of this locator tried excluding by ancestor CLASS instead —
        // `//div[contains(@class,'x-window') and not(contains(@class,'x-hidden-offsets'))]//a[...]`
        // — but contains(@class,'x-window') substring-matches inner structural wrapper divs like
        // `x-window-body` that don't carry x-hidden-offsets themselves even though their own outer
        // ancestor does, so the exclusion never actually reached the div that mattered. aria-hidden
        // is checked directly on the candidate <a>, so it isn't vulnerable to that same loophole.
        // ancestor:: (not the earlier //div[...]//a[...] structure) is used for the same reason —
        // it walks every real ancestor rather than whichever div an outer predicate binds to first.
        static final By OK_BUTTON = By.xpath(
                "//a[@aria-hidden='false' and contains(normalize-space(),'OK')" +
                " and not(ancestor::div[contains(@class,'x-window-maximized')])]");

        static final By SHOW_REF_SITE_SUMMARY =
                By.xpath("//span[normalize-space()='Show Reference Site Summary' and @class='x-btn-inner x-btn-inner-default-toolbar-small']");

        static final By TRANSACTION_AMOUNT =
                By.xpath("//span[text()='Transaction Amount:']/ancestor::div[contains(@class,'x-form-item')]//input[@role='textbox']");

        static final By ADJUSTED_AMOUNT =
                By.xpath("//span[text()='Adjusted Amount:']/ancestor::div[contains(@class,'x-form-item')]//input[@role='textbox']");

        static final By PENDING_TO_ADJUST =
                By.xpath("//span[text()='Pending to Adjust:']/ancestor::div[contains(@class,'x-form-item')]//input[@role='textbox']");

        // Shared row+column prefix: locates the <td> of the 'Adjusted Amount' column
        // in the grid row containing the given document number.
        static final String ADJUSTED_AMOUNT_CELL_BASE_XPATH =
                "//div[normalize-space()='%s']/ancestor::tr" +
                "//td[count(//div[contains(@class,'x-column-header')]" +
                "[.//span[normalize-space()='" + CommonVoucherGridColumnHeader.ADJUSTED_AMOUNT.headerText() + "']]" +
                "/preceding-sibling::div[contains(@class,'x-column-header')]) + 1]";

        static final String ADJUSTED_AMOUNT_BY_DOC_XPATH = ADJUSTED_AMOUNT_CELL_BASE_XPATH + "//div";

        // After a double-click activates the ExtJS inline editor, the editor renders as a
        // floating x-editor overlay — not inside the cell's DOM subtree. Target it globally.
        static final By ACTIVE_EDITOR_INPUT = By.xpath(
                "//div[contains(@class,'x-editor') and not(contains(@class,'x-hide-display'))]//input"
        );
    }


    // ===========================
    // Actions
    // ===========================

    public AdjustmentEntry openAdjustedDocumentMenu() {
        SeleniumUtils.click(Loc.ADJUSTED_DOCUMENT_MENU, WaitType.CLICKABLE, "Adjusted Document Menu");
        return this;
    }

    public SelectDocumentPopup selectDocument() {
        SeleniumUtils.click(AdjustmentPopups.SELECT_DOCUMENT.menuOptionLocator(),
                WaitType.CLICKABLE, "Select Document Option");
        JavascriptExecutorUtils.waitForExtJS();
        Log.warn("AdjustmentEntry.selectDocument: waitForExtJS done — checking loader mask [{}]",
                FrameworkConstants.getSelectDocGridLoader());
        boolean loaderFoundInitially = !DriverManager.getDriver()
                .findElements(FrameworkConstants.getSelectDocGridLoader()).isEmpty();
        Log.warn("AdjustmentEntry.selectDocument: loader mask present={}", loaderFoundInitially);
        Log.warn("AdjustmentEntry.selectDocument: grid diagnostics BEFORE loader wait — {}",
                JavascriptExecutorUtils.getGridDiagnostics());
        int maxRetries = ConfigFactory.getWaitConfig().selectDocLoaderMaxRetries();
        long loaderWaitStartMs = System.currentTimeMillis();
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                WaitUtils.webDriverWait(ConfigFactory.getFinanceConfig().extjsTimeout())
                    .until(ExpectedConditions.invisibilityOfElementLocated(
                        FrameworkConstants.getSelectDocGridLoader()));
                long elapsedMs = System.currentTimeMillis() - loaderWaitStartMs;
                // Record into the wait buffer regardless: this loop calls WaitUtils.webDriverWait
                // directly, bypassing recordIfEnabled, so its time never reached Wait Analysis.
                com.gsl.reports.WaitDurationBuffer.record(
                        "SelectDoc grid loader (adjustment entry)", elapsedMs,
                        "AdjustmentEntry.selectDocument", attempt);
                if (attempt > 1 || elapsedMs > 1000) {
                    // Only worth a report line when the wait was non-trivial — otherwise this
                    // fires on every fast, healthy call and just adds noise.
                    ReportLogger.warn("[Select-Doc Loader] cleared at attempt " + attempt + "/"
                            + maxRetries + " after " + elapsedMs + "ms total.");
                }
                Log.warn("AdjustmentEntry.selectDocument: loader gone at attempt [{}]", attempt);
                break;
            } catch (TimeoutException te) {
                long elapsedMs = System.currentTimeMillis() - loaderWaitStartMs;
                // ReportLogger, not Log: Log.* is SLF4J-only and never reaches the Extent report,
                // so this loop's retries — worth up to maxRetries × extjsTimeout() (3 × 40s = 120s
                // by default) — were entirely invisible in the HTML report.
                ReportLogger.warn("[Select-Doc Loader Retry " + attempt + "/" + maxRetries
                        + "] loader still visible after " + elapsedMs + "ms total"
                        + (attempt == maxRetries ? " — giving up." : " — retrying."));
                if (attempt == maxRetries) throw te;
                Log.warn("AdjustmentEntry.selectDocument: loader still visible after attempt [{}], retrying...", attempt);
            }
        }
        Log.warn("AdjustmentEntry.selectDocument: grid diagnostics AFTER loader wait — {}",
                JavascriptExecutorUtils.getGridDiagnostics());
        return new SelectDocumentPopup();
    }

    public AdjustmentEntry fifoSetOff() {
        SeleniumUtils.click(AdjustmentPopups.FIFO_SETOFF.menuOptionLocator(),
                WaitType.CLICKABLE, "FIFO Set-Off Option");
        return this;
    }

    public AdjustmentEntry referenceSiteWiseFIFO() {
        SeleniumUtils.click(AdjustmentPopups.REFERENCE_SITEWISE_FIFO_SETOFF.menuOptionLocator(),
                WaitType.CLICKABLE, "Reference Site Wise FIFO Option");
        return this;
    }

    /** Clicks the menu item, waits for the popup window to actually become visible,
     *  then returns a ready-to-use popup object. The visibility wait is load-bearing —
     *  without it, the first native action on the popup grid can race the dialog's
     *  render and fail to find the row-0 cell. */
    public ReferenceSiteWiseFIFOPopup openReferenceSiteWiseFIFOPopup() {
        SeleniumUtils.click(AdjustmentPopups.REFERENCE_SITEWISE_FIFO_SETOFF.menuOptionLocator(),
                WaitType.CLICKABLE, "Reference Site Wise FIFO Option");
        JavascriptExecutorUtils.waitForExtJS();
        WaitUtils.webDriverWait(ConfigFactory.getFinanceConfig().extjsTimeout())
                .until(ExpectedConditions.visibilityOfElementLocated(
                        AdjustmentPopups.REFERENCE_SITEWISE_FIFO_SETOFF.popupWindowLocator(voucherDomain)));
        return new ReferenceSiteWiseFIFOPopup(voucherDomain);
    }

    /** Captures Transaction Amount, Adjusted Amount, Pending to Adjust from the Adjustment Entry footer. */
    public AdjustmentAmountSnapshot captureAmounts() {
        return new AdjustmentAmountSnapshot(
                getTransactionAmount(), getAdjustedAmount(), getPendingToAdjust());
    }

    public AdjustmentEntry showReferenceSiteSummary() {
        WaitUtils.fluentWaitBuilder().until(driver -> {
            driver.findElement(Loc.SHOW_REF_SITE_SUMMARY).click();
            return true;
        });
        ExtentLogger.info("Show Reference Site Summary is clicked successfully");
        WaitUtils.waitForExtMask("Show Reference Site Summary");
        PopupMonitor.checkAfterAction("Show Reference Site Summary");
        return this;
    }

    public AdjustmentEntry clickOK() {
        SeleniumUtils.clickAndNotePopup(Loc.OK_BUTTON, WaitType.CLICKABLE, "OK Button in Adjustment Entry");
        return this;
    }

    /**
     * Clicks OK when Grid Amount > total adjustment entry amount (Grid:GREATER).
     * Returns a DocumentPopup pre-wired with this entry's parent and this instance
     * so the caller can invoke clickProceedAnyway() or clickStay().
     */
    public DocumentPopup clickOKExpectingConfirmation() {
        SeleniumUtils.click(Loc.OK_BUTTON, WaitType.CLICKABLE, "OK Button");
        return new DocumentPopup(parent, this);
    }

    public AdjustmentEntry clickCancel() {
        SeleniumUtils.click(CommonButton.CANCEL.locator(), WaitType.CLICKABLE, "Cancel Button");
        return this;
    }

    // ===========================
    // Value Fetchers
    // ===========================

    public String getTransactionAmount() {
        return SeleniumUtils.getAttribute(Loc.TRANSACTION_AMOUNT, "value", "Transaction Amount");
    }

    public String getAdjustedAmount() {
        return SeleniumUtils.getAttribute(Loc.ADJUSTED_AMOUNT, "value", "Adjusted Amount");
    }

    public String getPendingToAdjust() {
        return SeleniumUtils.getAttribute(Loc.PENDING_TO_ADJUST, "value", "Pending To Adjust");
    }

    public String getAdjustedAmountForDocument(String documentNumber) {
        By locator = By.xpath(String.format(Loc.ADJUSTED_AMOUNT_BY_DOC_XPATH, documentNumber));
        return SeleniumUtils.getText(locator, "AdjustedAmount_" + documentNumber);
    }

    public AdjustmentEntry setAdjustedAmountForDocument(String documentNumber, String amount) {
        By cellDiv = By.xpath(String.format(Loc.ADJUSTED_AMOUNT_BY_DOC_XPATH, documentNumber));
        ActionUtils.doubleClick(cellDiv);
        By cellInput = By.xpath(String.format(Loc.ADJUSTED_AMOUNT_CELL_BASE_XPATH + "//input", documentNumber));
        String editorLabel = "AdjustedAmount_editor_" + documentNumber;
        System.out.println("EDITORLABEL"+editorLabel);
        SeleniumUtils.clear(cellInput, editorLabel);
        SeleniumUtils.sendKeys(cellInput, WaitType.CLICKABLE, amount, editorLabel);
        return this;
    }


    // ===========================
    // Business Flows
    // ===========================

    public AdjustmentEntry performFIFOAdjustment() {
        return openAdjustedDocumentMenu()
                .fifoSetOff()
                .clickOK();
    }

    public AdjustmentEntry performReferenceSiteFIFOAdjustment() {
        return openAdjustedDocumentMenu()
                .referenceSiteWiseFIFO()
                .showReferenceSiteSummary()
                .clickOK();
    }

    public SelectDocumentPopup openSelectDocumentFlow() {
        openAdjustedDocumentMenu();
        return selectDocument();
    }
}
