package com.gsl.pages.pagecomponent.Finance.Operations.Common;

import com.gsl.enums.VoucherDomain;
import com.gsl.enums.WaitType;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankParent;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankPopup;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherDocumentHeader;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

import java.time.format.DateTimeFormatter;

/**
 * Common XPaths and fill logic shared by AR, AP, General, and Contra Voucher
 * document-information page components.
 *
 * All XPaths that are identical across voucher types are declared here as
 * static final constants. The only difference between form types is the Date
 * field id (a known DOM typo in AR: "transanctionDate" vs "transactionDate"
 * in AP), which is resolved from the VoucherDomain enum passed by each subclass.
 *
 * Subclasses call fillCommonFields() and may interleave voucher-specific steps
 * (e.g. On Account radio for AP) using the protected setters exposed here.
 */
public abstract class VoucherDocumentInformationBase {

    protected static final String SCHEME_DOC_SELECTION = "//span[text()='%s']";

    private static final DateTimeFormatter UI_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── Common XPaths — identical across AR, AP, General, and Contra ─────────

    private static final By CASH_BANK_SEARCH =
            By.xpath("//span[text()='Cash/Bank:']/ancestor::label" +
                     "/following-sibling::div //div[contains(@id,'gslpicklistfieldextended-')" +
                     " and contains(@id,'-trigger-picker')]");

    private static final By DOC_SCHEME =
            By.xpath("//span[text()='Voucher No.:']/ancestor::label" +
                     "/following-sibling::div//input[contains(@id,'inputEl') and contains(@id,'combo')]");

    private static final By REFERENCE =
            By.xpath("//span[text()='Reference:']/ancestor::label" +
                     "/following-sibling::div//input[contains(@id,'textfield-')" +
                     " and contains(@data-componentid,'textfield-') and contains(@aria-disabled,'false')]");

    private static final By REMARKS =
            By.xpath("//span[text()='Remarks:']/ancestor::label" +
                     "/following-sibling::div//input[contains(@id,'narration')" +
                     " and contains(@data-componentid,'combo-')]");

    // ── Per-type locator — resolved from VoucherDomain ───────────────────────

    private final By documentDate;

    private final CashBankParent parentPage;

    protected VoucherDocumentInformationBase(CashBankParent parentPage, VoucherDomain voucherDomain) {
        this.parentPage   = parentPage;
        this.documentDate = resolveDocumentDate(voucherDomain);
    }

    /**
     * Returns the Date field locator for the given voucher domain.
     * AR uses "transanctionDate" (intentional DOM typo in the application).
     * AP uses the correctly spelled "transactionDate".
     */
    private static By resolveDocumentDate(VoucherDomain domain) {
        switch (domain) {
            case AR:
                return By.xpath("//span[text()='Date:']/ancestor::label" +
                                "/following-sibling::div//input[contains(@id,'transanctionDate')" +
                                " and contains(@data-componentid,'datefield-')]");
            case AP:
                return By.xpath("//span[text()='Date:']/ancestor::label" +
                                "/following-sibling::div//input[contains(@id,'transactionDate')" +
                                " and contains(@data-componentid,'datefield-')]");
            default:
                throw new IllegalArgumentException(
                        "No document date XPath defined for voucher domain: " + domain);
        }
    }

    // ── Template method — subclasses call this for the common section ─────────

    protected final void fillCommonFields(VoucherDocumentHeader docInfo) {
        setDocScheme(docInfo.getVoucherno());
        setDocumentDate(docInfo.getDocumentdate().format(UI_DATE_FORMAT));
        setReference(docInfo.getReference());
        clickCashBankSearch();
        selectCashBank(docInfo.getCashbank());
    }

    // ── Protected setters — available to subclasses for custom ordering ───────

    protected void setDocScheme(String docscheme) {
        JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.sendKeys(DOC_SCHEME, WaitType.CLICKABLE, docscheme, "DOC_SCHEME");
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

    protected void setReference(String reference) {
        if (reference == null || reference.isBlank()) return;
        SeleniumUtils.sendKeys(REFERENCE, WaitType.CLICKABLE, reference, "REFERENCE");
    }

    protected void setRemarks(String remarks) {
        if (remarks == null || remarks.isBlank()) return;
        SeleniumUtils.sendKeys(REMARKS, WaitType.CLICKABLE, remarks, "REMARKS");
    }

    protected void clickCashBankSearch() {
        SeleniumUtils.click(CASH_BANK_SEARCH, WaitType.CLICKABLE, "CASH_BANK_SEARCH");
    }

    protected void selectCashBank(String cashbankName) {
        new CashBankPopup(parentPage).selectCashBank(cashbankName);
    }

    public String getDocumentDate() {
        return SeleniumUtils.getAttribute(documentDate, "value", "DOCUMENT_DATE");
    }
}
