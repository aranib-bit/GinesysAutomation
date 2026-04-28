package com.gsl.pages.Finance.Operations.Common;

import com.gsl.enums.PaymentSelectionType;
import com.gsl.enums.WaitType;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.PaymentInformation;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class PaymentInformationPopup {

    private static final By PAYMENT_MODE_TRIGGER   = By.xpath(
            "//span[normalize-space()='Payment Mode:']/ancestor::label" +
            "/following-sibling::div[contains(@class,'x-form-item-body')]" +
            "//div[contains(@class,'x-form-arrow-trigger')]");
    private static final String PAYMENT_MODE_OPTION = "//li[@role='option' and normalize-space()='%s']";

    private static final By INSTRUMENT_NO   = By.xpath(
            "//span[normalize-space()='Instrument No.:'] /ancestor::div[contains(@class,'x-form-item')] /descendant::input[@data-ref='inputEl' and @aria-disabled='false']");

    private static final By INSTRUMENT_DATE = By.xpath(
            "//span[normalize-space()='Instrument Date:'] /ancestor::div[contains(@class,'x-form-item')] /descendant::input[@data-ref='inputEl' and @aria-disabled='false']");

    private static final By DRAWN_ON        = By.xpath(
            "//span[normalize-space()='Drawn on:']/ancestor::label" +
            "/following-sibling::div[contains(@class,'x-form-item-body')]//input[@data-ref='inputEl']");

    private static final By REFERENCE       = By.xpath(
            "//span[normalize-space()='Reference:'] /ancestor::label /following-sibling::div[contains(@class,'x-form-item-body')] //input[contains(@class,'x-form-text') and not(@disabled) and not(@tabindex)]");

    private static final By OK_BUTTON       = By.xpath(
            "//div[contains(@class,'x-window')]//span[normalize-space()='OK']/ancestor::a[contains(@class,'x-btn') and @aria-hidden='false']");

    public PaymentInformationPopup selectPaymentMode(PaymentSelectionType mode) {
        SeleniumUtils.click(PAYMENT_MODE_TRIGGER, WaitType.CLICKABLE, "PAYMENT_MODE_TRIGGER");
        JavascriptExecutorUtils.waitForExtJS();
        By option = By.xpath(String.format(PAYMENT_MODE_OPTION, mode.label()));
        SeleniumUtils.click(option, WaitType.CLICKABLE, "PAYMENT_MODE_OPTION_" + mode.label());
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    public PaymentInformationPopup setInstrumentNo(String instrumentNo) {
        if (instrumentNo == null || instrumentNo.isBlank()) return this;
        SeleniumUtils.sendKeys(INSTRUMENT_NO, WaitType.CLICKABLE, instrumentNo, "INSTRUMENT_NO");
        SeleniumUtils.pressTab(INSTRUMENT_NO, "INSTRUMENT_NO");   // triggers app auto-population of Instrument Date with voucher date
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    public PaymentInformationPopup setInstrumentDate(String instrumentDate) {
        if (instrumentDate == null || instrumentDate.isBlank()) return this;

        // Wait for field to become enabled after Instrument No triggers auto-population
        SeleniumUtils.waitForInputReady(INSTRUMENT_DATE);

        // Read auto-populated value via DOM property (ExtJS sets value as property, not attribute)
        String currentDate = SeleniumUtils.getDomProperty(INSTRUMENT_DATE, "value", "INSTRUMENT_DATE");

        // Skip if already matches desired date
        if (instrumentDate.trim().equalsIgnoreCase(currentDate != null ? currentDate.trim() : "")) {
            System.out.println("ℹ️ Instrument Date already matches: " + instrumentDate + " — skipping.");
            return this;
        }

        System.out.println("✏️ Updating Instrument Date from [" + currentDate + "] to [" + instrumentDate + "]");
        SeleniumUtils.clear(INSTRUMENT_DATE, "INSTRUMENT_DATE");
        SeleniumUtils.sendKeys(INSTRUMENT_DATE, WaitType.CLICKABLE, instrumentDate, "INSTRUMENT_DATE");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    public PaymentInformationPopup setDrawnOn(String drawnOn) {
        if (drawnOn == null || drawnOn.isBlank()) return this;
        SeleniumUtils.sendKeys(DRAWN_ON, WaitType.CLICKABLE, drawnOn, "DRAWN_ON");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    public PaymentInformationPopup setReference(String reference) {
        if (reference == null || reference.isBlank()) return this;
        SeleniumUtils.sendKeys(REFERENCE, WaitType.CLICKABLE, reference, "REFERENCE");
        return this;
    }

    public void clickOk() {
        SeleniumUtils.click(OK_BUTTON, WaitType.CLICKABLE, "PAYMENT_INFO_OK");
        JavascriptExecutorUtils.waitForExtJS();
    }

    public void fillAndSubmit(PaymentInformation payment) {
        selectPaymentMode(payment.getPaymentMode())
                .setInstrumentNo(payment.getInstrumentNo())
                .setInstrumentDate(payment.getInstrumentDate())
                .setDrawnOn(payment.getDrawnOn())
                .setReference(payment.getReference())
                .clickOk();
    }
}
