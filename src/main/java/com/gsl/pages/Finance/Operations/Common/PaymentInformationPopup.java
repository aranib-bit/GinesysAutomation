package com.gsl.pages.Finance.Operations.Common;

import com.gsl.enums.PaymentSelectionType;
import com.gsl.enums.WaitType;
import com.gsl.pages.enums.Finance.Operations.Common.PaymentInfoLabel;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherPaymentInfo;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

/**
 * Abstract base for the Payment Information popup shared by AR and AP Voucher.
 *
 * Implements the Template Method pattern:
 *   fillAndSubmit() defines the fixed algorithm;
 *   setInstrumentNo() and setDrawnOn() are abstract steps whose behaviour
 *   differs between AR and AP and are implemented by each concrete subclass.
 *
 * Label strings are sourced from PaymentInfoLabel so a UI label change
 * requires editing only the enum, not every XPath.
 */
public abstract class PaymentInformationPopup {

    private static final By PAYMENT_MODE_TRIGGER = By.xpath(
            "//span[normalize-space()='" + PaymentInfoLabel.PAYMENT_MODE.label() + "']/ancestor::label"
            + "/following-sibling::div[contains(@class,'x-form-item-body')]"
            + "//div[contains(@class,'x-form-arrow-trigger')]");

    private static final String PAYMENT_MODE_OPTION =
            "//li[@role='option' and normalize-space()='%s']";

    protected static final By INSTRUMENT_NO = By.xpath(
            "//span[normalize-space()='" + PaymentInfoLabel.INSTRUMENT_NO.label() + "']"
            + " /ancestor::div[contains(@class,'x-form-item')]"
            + " /descendant::input[@data-ref='inputEl' and @aria-disabled='false']");

    protected static final By INSTRUMENT_DATE = By.xpath(
            "//span[normalize-space()='" + PaymentInfoLabel.INSTRUMENT_DATE.label() + "']"
            + " /ancestor::div[contains(@class,'x-form-item')]"
            + " /descendant::input[@data-ref='inputEl' and @aria-disabled='false']");

    private static final By REFERENCE = By.xpath(
            "//span[normalize-space()='" + PaymentInfoLabel.REFERENCE.label() + "']"
            + " /ancestor::label /following-sibling::div[contains(@class,'x-form-item-body')]"
            + " //input[contains(@class,'x-form-text') and not(@disabled) and not(@tabindex)]");

    protected static final By OK_BUTTON = By.xpath(
            "//div[contains(@class,'x-window')]//span[normalize-space()='OK']"
            + "/ancestor::a[contains(@class,'x-btn') and @aria-hidden='false']");

    // ── Abstract steps — AR and AP implement these differently ───────────────

    public abstract PaymentInformationPopup setInstrumentNo(String instrumentNo);

    public abstract PaymentInformationPopup setDrawnOn(String drawnOn);

    // ── Shared steps ─────────────────────────────────────────────────────────

    public PaymentInformationPopup selectPaymentMode(PaymentSelectionType mode) {
        SeleniumUtils.click(PAYMENT_MODE_TRIGGER, WaitType.CLICKABLE, "PAYMENT_MODE_TRIGGER");
        JavascriptExecutorUtils.waitForExtJS();
        By option = By.xpath(String.format(PAYMENT_MODE_OPTION, mode.label()));
        SeleniumUtils.click(option, WaitType.CLICKABLE, "PAYMENT_MODE_OPTION_" + mode.label());
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    public PaymentInformationPopup setInstrumentDate(String instrumentDate) {
        if (instrumentDate == null || instrumentDate.isBlank()) return this;
        SeleniumUtils.waitForInputReady(INSTRUMENT_DATE);
        String currentDate = SeleniumUtils.getDomProperty(INSTRUMENT_DATE, "value", "INSTRUMENT_DATE");
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

    public PaymentInformationPopup setReference(String reference) {
        if (reference == null || reference.isBlank()) return this;
        SeleniumUtils.sendKeys(REFERENCE, WaitType.CLICKABLE, reference, "REFERENCE");
        return this;
    }

    public void clickOk() {
        SeleniumUtils.click(OK_BUTTON, WaitType.CLICKABLE, "PAYMENT_INFO_OK");
        JavascriptExecutorUtils.waitForExtJS();
    }

    // ── Template method — algorithm fixed here, variant steps delegated ───────

    public void fillAndSubmit(VoucherPaymentInfo payment) {
        selectPaymentMode(payment.getPaymentMode())
                .setInstrumentNo(payment.getInstrumentNo())
                .setInstrumentDate(payment.getInstrumentDate())
                .setDrawnOn(payment.getDrawnOn())
                .setReference(payment.getReference())
                .clickOk();
    }
}
