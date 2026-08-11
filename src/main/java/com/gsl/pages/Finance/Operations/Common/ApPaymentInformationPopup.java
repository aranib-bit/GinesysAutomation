package com.gsl.pages.Finance.Operations.Common;

import com.gsl.enums.WaitType;
import com.gsl.pages.enums.Finance.Operations.APVoucher.ApPaymentInfoLabel;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

/**
 * AP Voucher-specific Payment Information popup — the known/unknown cheque-register branching
 * (Instrument No./Instrument Date/warning-dialog handling) lives in the shared
 * {@link ChequeRegisterPaymentInformationPopup}; this class only supplies AP's own Drawn-On field,
 * which AP labels "Cheque label:" (corresponds to AR/GV's "Drawn on:").
 */
public class ApPaymentInformationPopup extends ChequeRegisterPaymentInformationPopup {

    private static final By CHEQUE_LABEL = By.xpath(
            "//span[normalize-space()='" + ApPaymentInfoLabel.CHEQUE_LABEL.label() + "']/ancestor::label"
            + "/following-sibling::div[contains(@class,'x-form-item-body')]//input[@data-ref='inputEl']");

    @Override
    public ApPaymentInformationPopup setDrawnOn(String drawnOn) {
        if (drawnOn == null || drawnOn.isBlank()) return this;
        SeleniumUtils.sendKeys(CHEQUE_LABEL, WaitType.CLICKABLE, drawnOn, "CHEQUE_LABEL");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }
}
