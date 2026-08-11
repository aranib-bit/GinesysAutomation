package com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher;

import com.gsl.enums.VoucherDomain;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankParent;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherDocumentInformationBase;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

/**
 * Page component for the General Voucher "Document Information" section.
 *
 * <p>Thinner than AP's subclass — General Voucher has no "On Account" concept,
 * so {@link #fillDocumentHeader} simply delegates to the inherited
 * {@code fillCommonFields} + sets Remarks.
 *
 * <p><b>UNVERIFIED (see plan's "Verification / Open items" §1):</b> the
 * read-only fields below ({@link #getBalance()}, {@link #getNetAmount()},
 * {@link #getNetAmountType()}, {@link #getReleasedStatus()}) are screenshot-only
 * fields not present in AP/AR's document-info section — locators are best-effort
 * placeholders, same style {@code ApVoucherListPage} already uses for its own
 * unverified elements, pending live DOM inspection.
 */
public class GeneralVoucherDocumentInformation extends VoucherDocumentInformationBase {

    // ── TODO: confirm against the live UI — placeholder locators, not yet verified ──
    private static final By BALANCE_DISPLAY = By.xpath(
            "//span[contains(@class,'x-form-item-label-text') and normalize-space()='Balance:']" +
            "/ancestor::div[contains(@class,'x-form-item')]//input[contains(@id,'-inputEl')]");

    private static final By NET_AMOUNT_DISPLAY = By.xpath(
            "//span[normalize-space()='Net Amount:']/following-sibling::*[1]");

    private static final By RELEASED_STATUS_DISPLAY = By.xpath(
            "//span[contains(@class,'x-status') and (normalize-space()='RELEASED' or normalize-space()='UNRELEASED')]");

    public GeneralVoucherDocumentInformation(CashBankParent parentPage) {
        super(parentPage, VoucherDomain.GV);
    }

    public GeneralVoucherDocumentInformation fillDocumentHeader(DocumentInformation docInfo) {
        fillCommonFields(docInfo);
        setRemarks(docInfo.getRemarks());
        return this;
    }

    /** Reads the read-only "Balance:" field (Cash/Bank ledger's current balance). */
    public String getBalance() {
        return SeleniumUtils.getAttribute(BALANCE_DISPLAY, "value", "GV_BALANCE");
    }

    /** Reads the "Net Amount:" text, including its Dr/Cr + Receipt/Payment suffix (e.g. "799.00 Dr (Receipt)"). */
    public String getNetAmount() {
        return SeleniumUtils.getAttribute(NET_AMOUNT_DISPLAY, "textContent", "GV_NET_AMOUNT");
    }

    /** {@code true} when the Net Amount text contains "Dr"; {@code false} when it contains "Cr". */
    public boolean getNetAmountType() {
        String text = getNetAmount();
        return text != null && text.toUpperCase().contains("DR");
    }

    /** Reads the "RELEASED"/"UNRELEASED" status badge. */
    public String getReleasedStatus() {
        return SeleniumUtils.getAttribute(RELEASED_STATUS_DISPLAY, "textContent", "GV_RELEASED_STATUS");
    }
}
