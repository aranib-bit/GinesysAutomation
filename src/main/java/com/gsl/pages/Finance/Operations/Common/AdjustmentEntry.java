package com.gsl.pages.Finance.Operations.Common;

import com.gsl.pages.enums.CommonButton;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherGridColumnHeader;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import com.gsl.config.ConfigFactory;
import com.gsl.constants.FrameworkConstants;
import com.gsl.driver.DriverManager;
import com.gsl.pages.pagecomponent.Finance.Common.AdjustmentEntryParent;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.ActionUtils;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import com.gsl.enums.WaitType;

import java.time.Duration;

public class AdjustmentEntry {

    private final AdjustmentEntryParent parent;

    public AdjustmentEntry(AdjustmentEntryParent parent) {
        this.parent = parent;
    }

    // ===========================
    // Locators
    // ===========================

    // CANCEL_BUTTON → CommonButton.CANCEL.locator() (plain Cancel, no positional constraint)
    private static final class Loc {
        static final By ADJUSTED_DOCUMENT_MENU =
                By.xpath("//div[text()='Adjusted Document']//following::*[contains(@class,'mdi mdi-20px mdi-dots-horizontal')]");

        static final By SELECT_DOCUMENT_OPTION =
                By.xpath("//span[text()='Select Document' and @class='x-btn-inner x-btn-inner-default-toolbar-small']");

        static final By FIFO_SET_OFF_OPTION =
                By.xpath("//span[text()='FIFO Set-Off' and @class='x-btn-inner x-btn-inner-default-toolbar-small']");

        static final By REF_SITE_FIFO_OPTION =
                By.xpath("//span[text()='Reference Site wise FIFO set-off' and @class='x-btn-inner x-btn-inner-default-toolbar-small']");

        // Scoped OK — positioned relative to Show Reference Site Summary; NOT the generic OK button
        static final By OK_BUTTON =
                By.xpath("(//span[normalize-space()='Show Reference Site Summary'])[3]/following::a[contains(normalize-space(),'OK')][1]");

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
        SeleniumUtils.click(Loc.SELECT_DOCUMENT_OPTION, WaitType.CLICKABLE, "Select Document Option");
        JavascriptExecutorUtils.waitForExtJS();
        Log.warn("AdjustmentEntry.selectDocument: waitForExtJS done — checking loader mask [{}]",
                FrameworkConstants.getSelectDocGridLoader());
        boolean loaderFoundInitially = !DriverManager.getDriver()
                .findElements(FrameworkConstants.getSelectDocGridLoader()).isEmpty();
        Log.warn("AdjustmentEntry.selectDocument: loader mask present={}", loaderFoundInitially);
        Log.warn("AdjustmentEntry.selectDocument: grid diagnostics BEFORE loader wait — {}",
                JavascriptExecutorUtils.getGridDiagnostics());
        int maxRetries = ConfigFactory.getWaitConfig().selectDocLoaderMaxRetries();
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                WaitUtils.webDriverWait(ConfigFactory.getFinanceConfig().extjsTimeout())
                    .until(ExpectedConditions.invisibilityOfElementLocated(
                        FrameworkConstants.getSelectDocGridLoader()));
                Log.warn("AdjustmentEntry.selectDocument: loader gone at attempt [{}]", attempt);
                break;
            } catch (TimeoutException te) {
                if (attempt == maxRetries) throw te;
                Log.warn("AdjustmentEntry.selectDocument: loader still visible after attempt [{}], retrying...", attempt);
            }
        }
        Log.warn("AdjustmentEntry.selectDocument: grid diagnostics AFTER loader wait — {}",
                JavascriptExecutorUtils.getGridDiagnostics());
        return new SelectDocumentPopup();
    }

    public AdjustmentEntry fifoSetOff() {
        SeleniumUtils.click(Loc.FIFO_SET_OFF_OPTION, WaitType.CLICKABLE, "FIFO Set-Off Option");
        return this;
    }

    public AdjustmentEntry referenceSiteWiseFIFO() {
        SeleniumUtils.click(Loc.REF_SITE_FIFO_OPTION, WaitType.CLICKABLE, "Reference Site Wise FIFO Option");
        return this;
    }

    /** Clicks the menu item and returns a ready-to-use popup object. */
    public ReferenceSiteWiseFIFOPopup openReferenceSiteWiseFIFOPopup() {
        SeleniumUtils.click(Loc.REF_SITE_FIFO_OPTION, WaitType.CLICKABLE, "Reference Site Wise FIFO Option");
        JavascriptExecutorUtils.waitForExtJS();
        return new ReferenceSiteWiseFIFOPopup();
    }

    /** Captures Transaction Amount, Adjusted Amount, Pending to Adjust from the Adjustment Entry footer. */
    public AdjustmentAmountSnapshot captureAmounts() {
        return new AdjustmentAmountSnapshot(
                getTransactionAmount(), getAdjustedAmount(), getPendingToAdjust());
    }

    public AdjustmentEntry showReferenceSiteSummary() {
        SeleniumUtils.click(Loc.SHOW_REF_SITE_SUMMARY, WaitType.CLICKABLE, "Show Reference Site Summary");
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
