package com.gsl.pages.pagecomponent.Finance.Operations.Common;

import com.gsl.driver.DriverManager;
import com.gsl.enums.VoucherDomain;
import com.gsl.enums.WaitType;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankParent;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankPopup;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherDocumentHeader;
import com.gsl.reports.ExtentLogger;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Common XPaths and fill logic shared by AR, AP, General, and Contra Voucher
 * document-information page components.
 *
 * All field locators are built in the constructor from the VoucherDomain enum,
 * which implements VoucherDocumentLabels. Each domain constant overrides only
 * the label methods that differ from the application-wide defaults. To change a
 * label for a specific voucher type, override the relevant method in VoucherDomain.
 *
 * Subclasses call fillCommonFields() and may interleave voucher-specific steps
 * (e.g. On Account radio for AP) using the protected setters exposed here.
 */
public abstract class VoucherDocumentInformationBase {

    protected static final String SCHEME_DOC_SELECTION = "//span[text()='%s']";

    private static final DateTimeFormatter UI_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── Locators built from VoucherDomain labels ──────────────────────────────

    private final By docScheme;
    private final By documentDate;
    private final By reference;
    private final By cashBankSearch;
    private final By cashBankDisplay;
    private final By remarks;

    private static final By ownerSiteDisplay = By.xpath(
            "//span[contains(@class,'x-form-item-label-text') and normalize-space()='Owner Site:']" +
            "/ancestor::div[contains(@class,'x-form-item')]" +
            "//input[contains(@id,'-inputEl')]");

    private static final By NARRATION_POPUP_HEADER = By.xpath(
            "//div[contains(@class,'x-boundlist-floating')]" +
            "[.//div[@class='headerBoundList'][.//b[normalize-space(text())='Narration Text']]]");

    private final CashBankParent parentPage;

    protected VoucherDocumentInformationBase(CashBankParent parentPage, VoucherDomain domain) {
        this.parentPage     = parentPage;
        this.docScheme      = byLabel(domain.getLabelVoucherNo(),
                                 "//input[contains(@id,'inputEl') and contains(@id,'combo')]");
        this.documentDate   = byLabel(domain.getLabelDate(),
                                 "//input[contains(@id,'" + domain.getDateFieldId() + "')" +
                                 " and contains(@data-componentid,'datefield-')]");
        this.reference      = byLabel(domain.getLabelReference(),
                                 "//input[contains(@id,'textfield-')" +
                                 " and contains(@data-componentid,'textfield-')" +
                                 " and contains(@aria-disabled,'false')]");
        this.cashBankSearch = byLabel(domain.getLabelCashBank(),
                                 " //div[contains(@id,'gslpicklistfieldextended-')" +
                                 " and contains(@id,'-trigger-picker')]");
        this.cashBankDisplay = byLabel(domain.getLabelCashBank(),
                                 "//input[contains(@id,'gslpicklistfieldextended-')" +
                                 " and contains(@id,'-inputEl')]");
        this.remarks        = byLabel(domain.getLabelRemarks(),
                                 "//input[contains(@id,'narration')" +
                                 " and contains(@data-componentid,'combo-')]");
    }

    private static By byLabel(String label, String widgetXPath) {
        return By.xpath("//span[text()='" + label + "']/ancestor::label" +
                        "/following-sibling::div" + widgetXPath);
    }

    // ── Template method — subclasses call this for the common section ─────────

    protected final void fillCommonFields(VoucherDocumentHeader docInfo) {
        setDocScheme(docInfo.getVoucherno());
        setDocumentDate(formatDate(docInfo.getDocumentdate()));
        setReference(docInfo.getReference());
        // T-03 negative scenario NEG_BLANK_CASHBANK leaves Cash/Bank deliberately empty so Save
        // raises "Cash/Bank is required". Skip the picker when no value is supplied; positive
        // scenarios always pass a non-blank Cash/Bank, so this is a no-op for them.
        String cashbank = docInfo.getCashbank();
        if (cashbank != null && !cashbank.isBlank()) {
            clickCashBankSearch();
            selectCashBank(cashbank);
        }
    }

    // ── Protected setters — available to subclasses for custom ordering ───────

    protected void setDocScheme(String docscheme) {
        JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.clear(docScheme, "DOC_SCHEME");
        SeleniumUtils.sendKeys(docScheme, WaitType.CLICKABLE, docscheme, "DOC_SCHEME");
        JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.click(By.xpath(String.format(SCHEME_DOC_SELECTION, docscheme)),
                WaitType.CLICKABLE, "SCHEME_DOC_SELECTION");
    }

    protected void setDocumentDate(String date) {
        String current = SeleniumUtils.getAttribute(documentDate, "value", "DOCUMENT_DATE");
        if (current != null && current.equalsIgnoreCase(date)) {
            Log.info("DOCUMENT_DATE is already '{}' — skipping update", date);
            return;
        }
        Log.info("DOCUMENT_DATE current='{}', setting to '{}'", current, date);
        SeleniumUtils.clear(documentDate, "DOCUMENT_DATE");
        SeleniumUtils.sendKeys(documentDate, WaitType.CLICKABLE, date, "DOCUMENT_DATE");
    }

    protected void setReference(String referenceValue) {
        if (referenceValue == null || referenceValue.isBlank()) return;
        SeleniumUtils.sendKeys(reference, WaitType.CLICKABLE, referenceValue, "REFERENCE");
    }

    protected void setRemarks(String remarksValue) {
        if (remarksValue == null || remarksValue.isBlank()) return;
        SeleniumUtils.sendKeys(remarks, WaitType.CLICKABLE, remarksValue, "REMARKS");
        handleNarrationTextPopup(remarksValue);
    }

    /**
     * After typing in the Remarks (narration) combo, the app shows a "Narration Text"
     * picker grid with previously saved narrations.
     *
     * <ul>
     *   <li>If a row matching the typed text exists — click it to select the narration.</li>
     *   <li>Otherwise — press Tab to commit the typed text as a new entry and dismiss.</li>
     * </ul>
     *
     * After either branch the method waits for the popup to be fully invisible before
     * returning. This prevents the lingering overlay from interfering with subsequent
     * DOM interactions (e.g. native Sub-Ledger combo editor cell click), which causes
     * ExtJS alignTo() to compute zero geometry and throw a layout exception.
     */
    private void handleNarrationTextPopup(String remarksValue) {
        try {
            JavascriptExecutorUtils.waitForExtJS();
            WaitUtils.untilVisible(NARRATION_POPUP_HEADER, 3);
        } catch (TimeoutException | NoSuchElementException e) {
            ExtentLogger.info("[REMARKS] Narration Text popup did not appear — keeping typed value as-is");
            return;
        }

        boolean hasMatch = !DriverManager.getDriver()
                .findElements(matchedNarrationRow(remarksValue)).isEmpty();

        if (hasMatch) {
            SeleniumUtils.click(matchedNarrationRow(remarksValue), WaitType.CLICKABLE, "NARRATION_ROW");
            ExtentLogger.info("[REMARKS] Narration Text popup — clicked matched row for '" + remarksValue + "'");
        } else {
            WaitUtils.untilVisible(remarks).sendKeys(Keys.TAB);
            ExtentLogger.info("[REMARKS] Narration Text popup — no match, pressed Tab for '" + remarksValue + "'");
        }

        WaitUtils.untilInvisible(NARRATION_POPUP_HEADER);
        JavascriptExecutorUtils.waitForExtJS();
    }

    private static By matchedNarrationRow(String text) {
        return By.xpath(
                "//div[contains(@class,'x-boundlist-floating')]" +
                "[.//b[normalize-space(text())='Narration Text']]" +
                "//td[@data-qtip='" + text + "']");
    }

    protected void clickCashBankSearch() {
        SeleniumUtils.click(cashBankSearch, WaitType.CLICKABLE, "CASH_BANK_SEARCH");
    }

    protected void selectCashBank(String cashbankName) {
        new CashBankPopup(parentPage).selectCashBank(cashbankName);
    }

    protected static String formatDate(LocalDate date) {
        Objects.requireNonNull(date,
                "documentdate is null — check the Excel 'Date' column format or the DataProvider source");
        return date.format(UI_DATE_FORMAT);
    }

    public String getOwnerSite() {
        return SeleniumUtils.getAttribute(ownerSiteDisplay, "value", "OWNER_SITE_DISPLAY");
    }

    public String getDocumentDate() {
        return SeleniumUtils.getAttribute(documentDate, "value", "DOCUMENT_DATE");
    }

    public String getDocScheme() {
        return SeleniumUtils.getAttribute(docScheme, "value", "DOC_SCHEME");
    }

    public String getReference() {
        return SeleniumUtils.getAttribute(reference, "value", "REFERENCE");
    }

    public String getCashBank() {
        return SeleniumUtils.getAttribute(cashBankDisplay, "value", "CASH_BANK_DISPLAY");
    }

    public String getRemarks() {
        return SeleniumUtils.getAttribute(remarks, "value", "REMARKS");
    }
}
