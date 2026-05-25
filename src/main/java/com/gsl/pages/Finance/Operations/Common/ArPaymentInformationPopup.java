package com.gsl.pages.Finance.Operations.Common;

import com.gsl.enums.WaitType;
import com.gsl.pages.enums.Finance.Operations.ARVoucher.ArPaymentInfoLabel;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

/**
 * AR Voucher-specific Payment Information popup.
 *
 * Instrument No: sendKeys then Tab — the app auto-populates Instrument Date directly.
 * Drawn on field label: "Drawn on:" (AR Voucher UI label).
 */
public class ArPaymentInformationPopup extends PaymentInformationPopup {

    private static final By DRAWN_ON = By.xpath(
            "//span[normalize-space()='" + ArPaymentInfoLabel.DRAWN_ON.label() + "']/ancestor::label"
            + "/following-sibling::div[contains(@class,'x-form-item-body')]//input[@data-ref='inputEl']");

    @Override
    public ArPaymentInformationPopup setInstrumentNo(String instrumentNo) {
        if (instrumentNo == null || instrumentNo.isBlank()) return this;
        SeleniumUtils.sendKeys(INSTRUMENT_NO, WaitType.CLICKABLE, instrumentNo, "INSTRUMENT_NO");
        SeleniumUtils.pressTab(INSTRUMENT_NO, "INSTRUMENT_NO");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    @Override
    public ArPaymentInformationPopup setDrawnOn(String drawnOn) {
        if (drawnOn == null || drawnOn.isBlank()) return this;
        SeleniumUtils.sendKeys(DRAWN_ON, WaitType.CLICKABLE, drawnOn, "DRAWN_ON");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }
}
