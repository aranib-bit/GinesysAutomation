package com.gsl.pages.Finance.Operations.Common;

import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import com.gsl.pages.enums.Finance.Operations.APVoucher.ApPaymentInfoLabel;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherPaymentInfo;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * AP Voucher-specific Payment Information popup.
 *
 * Instrument No: the field is an ExtJS ComboBox backed by the bank's cheque register.
 *   - Known cheque  → suggestion boundlist appears; click the matching item.
 *                     Instrument Date auto-populated and enabled immediately.
 *   - Unknown cheque → Tab moves focus to Cheque label; no popup at this point.
 *                     First OK triggers: "Given cheque is not present in system…" warning.
 *                     Clicking Yes closes the warning; Instrument Date becomes enabled.
 *                     A second OK then saves the payment.
 *
 * Cheque label field (AP) corresponds to Drawn on (AR).
 */
public class ApPaymentInformationPopup extends PaymentInformationPopup {

    private static final By CHEQUE_LABEL = By.xpath(
            "//span[normalize-space()='" + ApPaymentInfoLabel.CHEQUE_LABEL.label() + "']/ancestor::label"
            + "/following-sibling::div[contains(@class,'x-form-item-body')]//input[@data-ref='inputEl']");

    private static final String INSTRUMENT_NO_SUGGESTION =
            "//li[@role='option' and normalize-space()='%s']";

    private static final By UNKNOWN_CHEQUE_WARNING = By.xpath(
            "//div[contains(@class,'x-window')]"
            + "//*[contains(normalize-space(),'not present in system')]");

    private static final By UNKNOWN_CHEQUE_WARNING_YES = By.xpath(
            "//div[contains(@class,'x-window')"
            + "  and .//*[contains(normalize-space(),'not present in system')]]"
            + "//span[normalize-space()='Yes']/ancestor::a[contains(@class,'x-btn')]");

    private static final int SUGGESTION_WAIT_SECONDS = 5;
    private static final int WARNING_WAIT_SECONDS    = 5;

    @Override
    public ApPaymentInformationPopup setInstrumentNo(String instrumentNo) {
        if (instrumentNo == null || instrumentNo.isBlank()) return this;

        SeleniumUtils.sendKeys(INSTRUMENT_NO, WaitType.CLICKABLE, instrumentNo, "INSTRUMENT_NO");

        By suggestion = By.xpath(String.format(INSTRUMENT_NO_SUGGESTION, instrumentNo));

        if (SeleniumUtils.isElementPresent(suggestion, SUGGESTION_WAIT_SECONDS)) {
            // Known cheque: suggestion found — date auto-populated and enabled immediately
            SeleniumUtils.click(suggestion, WaitType.CLICKABLE, "INSTRUMENT_NO_SUGGESTION_" + instrumentNo);
        } else {
            // Unknown cheque: Tab commits the value; focus moves to Cheque label:
            // Warning does NOT appear here — it appears only after the first OK click
            SeleniumUtils.pressTab(INSTRUMENT_NO, "INSTRUMENT_NO");
        }

        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    @Override
    public ApPaymentInformationPopup setDrawnOn(String drawnOn) {
        if (drawnOn == null || drawnOn.isBlank()) return this;
        SeleniumUtils.sendKeys(CHEQUE_LABEL, WaitType.CLICKABLE, drawnOn, "CHEQUE_LABEL");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    /**
     * AP-specific fillAndSubmit with two distinct paths:
     *
     * Known cheque (suggestion clicked):
     *   Instrument Date is enabled immediately → fill date → fill reference → OK → popup closes.
     *
     * Unknown cheque (Tab pressed, no suggestion):
     *   Instrument Date is greyed out → skip date → fill reference → OK →
     *   warning appears → click Yes → BOTH warning AND payment popup close.
     *   Instrument Date is automatically set to Document Date by the application.
     */
    @Override
    public void fillAndSubmit(VoucherPaymentInfo payment) {
        selectPaymentMode(payment.getPaymentMode())
                .setInstrumentNo(payment.getInstrumentNo())
                .setDrawnOn(payment.getDrawnOn());

        // Known-cheque: date field enabled immediately after suggestion click → set it now.
        // Unknown-cheque: date field is greyed out → skip; app sets it to Document Date on save.
        if (isInstrumentDateReady()) {
            setInstrumentDate(payment.getInstrumentDate());
        }

        setReference(payment.getReference());

        SeleniumUtils.click(OK_BUTTON, WaitType.CLICKABLE, "PAYMENT_INFO_OK");
        JavascriptExecutorUtils.waitForExtJS();

        // Unknown-cheque path: warning appears after OK; clicking Yes closes both warning and popup.
        // Known-cheque path: no warning; popup already closed by the OK above.
        handleChequeWarningIfPresent();
    }

    private boolean handleChequeWarningIfPresent() {
        if (SeleniumUtils.isElementPresent(UNKNOWN_CHEQUE_WARNING, WARNING_WAIT_SECONDS)) {
            SeleniumUtils.click(UNKNOWN_CHEQUE_WARNING_YES, WaitType.CLICKABLE, "CHEQUE_NOT_IN_SYSTEM_YES");
            JavascriptExecutorUtils.waitForExtJS();
            return true;
        }
        return false;
    }

    private boolean isInstrumentDateReady() {
        try {
            new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(3))
                    .until(driver -> {
                        try {
                            WebElement el = driver.findElement(INSTRUMENT_DATE);
                            return el.isDisplayed()
                                    && el.isEnabled()
                                    && el.getAttribute("readonly") == null;
                        } catch (org.openqa.selenium.NoSuchElementException e) {
                            return false;
                        }
                    });
            return true;
        } catch (org.openqa.selenium.TimeoutException e) {
            return false;
        }
    }
}
