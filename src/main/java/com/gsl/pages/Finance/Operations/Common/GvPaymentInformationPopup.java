package com.gsl.pages.Finance.Operations.Common;

import com.gsl.enums.WaitType;
import com.gsl.pages.enums.Finance.Operations.GeneralVoucher.GvPaymentInfoLabel;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

/**
 * General Voucher-specific Payment Information popup, for the single Cash/Bank counter-leg
 * (see {@link com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherExpenseIncomeDetail
 * #handlePaymentInformation}).
 *
 * <p><b>Corrected to extend {@link ChequeRegisterPaymentInformationPopup}</b> (AP's shape), not
 * {@code ArPaymentInformationPopup}'s simpler always-auto-populate shape as this class originally
 * did. The determinant isn't voucher-type similarity — it's how {@code instrumentNo} test data is
 * sourced: GV's {@code CHEQUE} variant draws it from
 * {@code GeneralVoucherTemplateLoader.combinationTemplate} →
 * {@code UniqueChequeGenerator.generateUnique()}, the exact same generator AP's DB-driven
 * scenarios use — a synthetic, never-registered value. Every GV CHEQUE combination therefore
 * always lands on the "unknown cheque" branch, which requires the known/unknown handling (and the
 * in-popup "not present in system" warning dismissal) that only {@link
 * ChequeRegisterPaymentInformationPopup} implements; the old AR-style unconditional {@code
 * setInstrumentDate} call failed every live run because the Instrument Date field is disabled on
 * the unknown-cheque path, and {@code ArPaymentInformationPopup}'s shape has no way to detect
 * that. This class now only supplies GV's own Drawn-On field locator — everything else is shared.
 */
public class GvPaymentInformationPopup extends ChequeRegisterPaymentInformationPopup {

    private static final By DRAWN_ON = By.xpath(
            "//span[normalize-space()='" + GvPaymentInfoLabel.DRAWN_ON.label() + "']/ancestor::label"
            + "/following-sibling::div[contains(@class,'x-form-item-body')]//input[@data-ref='inputEl']");

    @Override
    public GvPaymentInformationPopup setDrawnOn(String drawnOn) {
        if (drawnOn == null || drawnOn.isBlank()) return this;
        SeleniumUtils.sendKeys(DRAWN_ON, WaitType.CLICKABLE, drawnOn, "DRAWN_ON");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }
}
