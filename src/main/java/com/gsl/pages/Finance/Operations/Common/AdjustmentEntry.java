package com.gsl.pages.Finance.Operations.Common;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import com.gsl.config.ConfigFactory;
import com.gsl.constants.FrameworkConstants;
import com.gsl.driver.DriverManager;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.ActionUtils;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.enums.WaitType;

import java.time.Duration;

public class AdjustmentEntry {

    public AdjustmentEntry(WebDriver driver) {
    }

    // ===========================
    // Locators
    // ===========================

    private static final By ADJUSTED_DOCUMENT_MENU =
            By.xpath("//div[text()='Adjusted Document']//following::*[contains(@class,'mdi mdi-20px mdi-dots-horizontal')]");

    private static final By SELECT_DOCUMENT_OPTION =
            By.xpath("//span[text()='Select Document' and @class='x-btn-inner x-btn-inner-default-toolbar-small']");

    private static final By FIFO_SET_OFF_OPTION =
            By.xpath("//span[text()='FIFO Set-Off' and @class='x-btn-inner x-btn-inner-default-toolbar-small']");

    private static final By REF_SITE_FIFO_OPTION =
            By.xpath("//span[text()='Reference Site wise FIFO set-off' and @class='x-btn-inner x-btn-inner-default-toolbar-small']");

    private static final By OK_BUTTON =
            By.xpath("(//span[normalize-space()='Show Reference Site Summary'])[3]/following::a[contains(normalize-space(),'OK')][1]");

    private static final By CANCEL_BUTTON =
            By.xpath("//span[normalize-space()='Cancel']");

    private static final By SHOW_REF_SITE_SUMMARY =
            By.xpath("//span[normalize-space()='Show Reference Site Summary' and @class='x-btn-inner x-btn-inner-default-toolbar-small']");

    private static final By TRANSACTION_AMOUNT =
            By.xpath("//span[text()='Transaction Amount:']/ancestor::div[contains(@class,'x-form-item')]//input[@role='textbox']");

    private static final By ADJUSTED_AMOUNT =
            By.xpath("//span[text()='Adjusted Amount:']/ancestor::div[contains(@class,'x-form-item')]//input[@role='textbox']");

    private static final By PENDING_TO_ADJUST =
            By.xpath("//span[text()='Pending to Adjust:']/ancestor::div[contains(@class,'x-form-item')]//input[@role='textbox']");

    // Shared row+column prefix: locates the <td> of the 'Adjusted Amount' column
    // in the grid row containing the given document number.
    private static final String ADJUSTED_AMOUNT_CELL_BASE_XPATH =
            "//div[normalize-space()='%s']/ancestor::tr" +
            "//td[count(//div[contains(@class,'x-column-header')]" +
            "[.//span[normalize-space()='Adjusted Amount']]" +
            "/preceding-sibling::div[contains(@class,'x-column-header')]) + 1]";

    private static final String ADJUSTED_AMOUNT_BY_DOC_XPATH =
            ADJUSTED_AMOUNT_CELL_BASE_XPATH + "//div";

    // After a double-click activates the ExtJS inline editor, the editor renders as a
    // floating x-editor overlay — not inside the cell's DOM subtree. Target it globally.
    private static final By ACTIVE_EDITOR_INPUT = By.xpath(
            "//div[contains(@class,'x-editor') and not(contains(@class,'x-hide-display'))]//input"
    );


    // ===========================
    // Actions
    // ===========================

    public AdjustmentEntry openAdjustedDocumentMenu() {
        SeleniumUtils.click(ADJUSTED_DOCUMENT_MENU, WaitType.CLICKABLE, "Adjusted Document Menu");
        return this;
    }

    public SelectDocumentPopup selectDocument() {
        SeleniumUtils.click(SELECT_DOCUMENT_OPTION, WaitType.CLICKABLE, "Select Document Option");
        JavascriptExecutorUtils.waitForExtJS();
        Log.warn("AdjustmentEntry.selectDocument: waitForExtJS done — checking loader mask [{}]",
                FrameworkConstants.getSelectDocGridLoader());
        boolean loaderFoundInitially = !DriverManager.getDriver()
                .findElements(FrameworkConstants.getSelectDocGridLoader()).isEmpty();
        Log.warn("AdjustmentEntry.selectDocument: loader mask present={}", loaderFoundInitially);
        Log.warn("AdjustmentEntry.selectDocument: grid diagnostics BEFORE loader wait — {}",
                JavascriptExecutorUtils.getGridDiagnostics());
        int maxRetries = ConfigFactory.getConfig().selectDocLoaderMaxRetries();
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                new WebDriverWait(DriverManager.getDriver(),
                        Duration.ofSeconds(ConfigFactory.getConfig().extjsTimeout()))
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
        SeleniumUtils.click(FIFO_SET_OFF_OPTION, WaitType.CLICKABLE, "FIFO Set-Off Option");
        return this;
    }

    public AdjustmentEntry referenceSiteWiseFIFO() {
        SeleniumUtils.click(REF_SITE_FIFO_OPTION, WaitType.CLICKABLE, "Reference Site Wise FIFO Option");
        return this;
    }

    public AdjustmentEntry showReferenceSiteSummary() {
        SeleniumUtils.click(SHOW_REF_SITE_SUMMARY, WaitType.CLICKABLE, "Show Reference Site Summary");
        return this;
    }

    public AdjustmentEntry clickOK() {
        SeleniumUtils.click(OK_BUTTON, WaitType.CLICKABLE, "OK Button");
        return this;
    }

    public AdjustmentEntry clickCancel() {
        SeleniumUtils.click(CANCEL_BUTTON, WaitType.CLICKABLE, "Cancel Button");
        return this;
    }

    // ===========================
    // Value Fetchers
    // ===========================

    public String getTransactionAmount() {
        return SeleniumUtils.getAttribute(TRANSACTION_AMOUNT, "value", "Transaction Amount");
    }

    public String getAdjustedAmount() {
        return SeleniumUtils.getAttribute(ADJUSTED_AMOUNT, "value", "Adjusted Amount");
    }

    public String getPendingToAdjust() {
        return SeleniumUtils.getAttribute(PENDING_TO_ADJUST, "value", "Pending To Adjust");
    }

    public String getAdjustedAmountForDocument(String documentNumber) {
        By locator = By.xpath(String.format(ADJUSTED_AMOUNT_BY_DOC_XPATH, documentNumber));
        return SeleniumUtils.getText(locator, "AdjustedAmount_" + documentNumber);
    }

    public AdjustmentEntry setAdjustedAmountForDocument(String documentNumber, String amount) {
        By cellDiv = By.xpath(String.format(ADJUSTED_AMOUNT_BY_DOC_XPATH, documentNumber));
        ActionUtils.doubleClick(cellDiv);
        By cellInput = By.xpath(String.format(ADJUSTED_AMOUNT_CELL_BASE_XPATH + "//input", documentNumber));
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
