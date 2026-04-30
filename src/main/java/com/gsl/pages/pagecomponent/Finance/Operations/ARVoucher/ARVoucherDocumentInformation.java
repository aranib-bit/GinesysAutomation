package com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher;

import com.gsl.enums.WaitType;
import com.gsl.pages.Finance.Operations.ArVoucherAdd;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankParent;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankPopup;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.DocumentInformation;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

import java.time.format.DateTimeFormatter;

public class ARVoucherDocumentInformation {

    private static final By CASHBANKSEARCH=By.xpath("//span[text()='Cash/Bank:']/ancestor::label/following-sibling::div //div[contains(@id,'gslpicklistfieldextended-') and contains(@id,'-trigger-picker')]");
    private static final By DOC_SCHEME_SELECTION=By.xpath("//span[text()='Voucher No.:']/ancestor::label/following-sibling::div//input[contains(@id, 'inputEl') and contains(@id,'combo')]");
    private static final String SCHEME_DOC_SELECTION="//span[text()='%s']";
    private static final By DOCUMENT_DATE=By.xpath("//span[text()='Date:']/ancestor::label/following-sibling::div//input[contains(@id, 'transanctionDate') and contains(@data-componentid,'datefield-')]");
    private static final By REFERENCE=By.xpath("//span[text()='Reference:']/ancestor::label/following-sibling::div//input[contains(@id, 'textfield-') and contains(@data-componentid,'textfield-') and contains(@aria-disabled,'false')]");
    private static final By REMARKS=By.xpath("//span[text()='Remarks:']/ancestor::label/following-sibling::div//input[contains(@id, 'narration') and contains(@data-componentid,'combo-')]");

    private static final DateTimeFormatter UI_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private CashBankParent parentPage;

    public ARVoucherDocumentInformation(CashBankParent parentPage) {
        this.parentPage = parentPage;
    }

    public ARVoucherDocumentInformation fillDocumentHeader(DocumentInformation docInfo) {
        String formattedDate = docInfo.getDocumentdate().format(UI_DATE_FORMAT);
        setDocScheme(docInfo.getVoucherno());
        setDocumentDate(formattedDate);
        setReference(docInfo.getReference());
        clickCashBankSearch();
        selectCashBank(docInfo.getCashbank());
        setRemarks(docInfo.getRemarks());
        return this;
    }

    public String getDocumentDate() {
        return SeleniumUtils.getAttribute(DOCUMENT_DATE, "value", "DOCUMENT_DATE");
    }

    private ARVoucherDocumentInformation setDocumentDate(String date) {
        String current = getDocumentDate();
        if (current != null && current.equalsIgnoreCase(date)) {
            Log.info("DOCUMENT_DATE is already '{}' — skipping update", date);
            return this;
        }
        Log.info("DOCUMENT_DATE current='{}', setting to '{}'", current, date);
        SeleniumUtils.clear(DOCUMENT_DATE, "DOCUMENT_DATE");
        SeleniumUtils.sendKeys(DOCUMENT_DATE, WaitType.CLICKABLE, date, "DOCUMENT_DATE");
        return this;
    }

    private ARVoucherDocumentInformation setReference(String reference) {
        SeleniumUtils.sendKeys(REFERENCE,WaitType.CLICKABLE,reference, "REFERENCE");
        return this;
    }

    private ARVoucherDocumentInformation setRemarks(String remarks) {
        SeleniumUtils.sendKeys(REMARKS,WaitType.CLICKABLE,remarks, "REMARKS");
        return this;
    }

    private ARVoucherDocumentInformation setDocScheme(String docscheme) {
        JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.sendKeys(DOC_SCHEME_SELECTION, WaitType.CLICKABLE, docscheme, "DOC_SCHEME_SELECTION");
        JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.click(getDocSchemeSelection(docscheme), WaitType.CLICKABLE, "SCHEME_DOC_SELECTION");
        return this;
    }

    private By getDocSchemeSelection(String docscheme) {
        return By.xpath(String.format(SCHEME_DOC_SELECTION, docscheme));
    }

    private CashBankPopup clickCashBankSearch(){
        SeleniumUtils.click(CASHBANKSEARCH,WaitType.CLICKABLE, "CASHBANKSEARCH");
        return new CashBankPopup(parentPage);
    }

    private ArVoucherAdd selectCashBank(String cashbankName) {
        CashBankPopup popup = new CashBankPopup(parentPage);
        return (ArVoucherAdd) popup.selectCashBank(cashbankName);
    }
}
