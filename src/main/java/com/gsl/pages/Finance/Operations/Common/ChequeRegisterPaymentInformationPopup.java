package com.gsl.pages.Finance.Operations.Common;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.WaitType;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherPaymentInfo;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;

/**
 * Shared base for Payment Information popups whose Instrument No. field is an ExtJS ComboBox
 * backed by a live cheque register, with two runtime-detected paths — extracted out of
 * {@code ApPaymentInformationPopup} (originally AP-only) once confirmed that {@code
 * GvPaymentInformationPopup} needed the exact same handling, not AR's simpler shape (see this
 * class's own history in the project plan's "Post-implementation finding 4"):
 * <ul>
 *   <li><b>Known cheque</b> — typing the number produces a matching suggestion boundlist item;
 *       clicking it enables Instrument Date immediately.</li>
 *   <li><b>Unknown cheque</b> — no suggestion appears; Tab commits the typed value. Instrument
 *       Date stays disabled until the first OK triggers the app's own "Given cheque is not
 *       present in system…" warning — clicking Yes closes both the warning and the popup, and
 *       the app sets Instrument Date to the Document Date server-side.</li>
 * </ul>
 *
 * <p><b>{@code ArPaymentInformationPopup} does NOT extend this</b> — AR's own UI auto-populates
 * Instrument Date immediately with no register lookup at all, so it never hits either path (see
 * its own class Javadoc).
 *
 * <p><b>Which sibling to extend is decided by how {@code instrumentNo} is SOURCED, not by voucher
 * type.</b> Any voucher whose test data draws cheque numbers from {@code UniqueChequeGenerator}
 * (a synthetic, in-memory-only value never checked against any DB/cheque-register table) will
 * always land on the "unknown cheque" path and needs this class's handling — confirmed true for
 * both AP's DB-driven scenarios and every General Voucher CHEQUE combination (see {@code
 * GeneralVoucherTemplateLoader.combinationTemplate}). No DAO/query exists anywhere in this
 * codebase that checks cheque-register existence — known-vs-unknown is discovered purely from the
 * live ExtJS suggestion boundlist at runtime, exactly as done here.
 */
public abstract class ChequeRegisterPaymentInformationPopup extends PaymentInformationPopup {

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
    public ChequeRegisterPaymentInformationPopup setInstrumentNo(String instrumentNo) {
        if (instrumentNo == null || instrumentNo.isBlank()) return this;

        SeleniumUtils.sendKeys(INSTRUMENT_NO, WaitType.CLICKABLE, instrumentNo, "INSTRUMENT_NO");

        By suggestion = By.xpath(String.format(INSTRUMENT_NO_SUGGESTION, instrumentNo));

        if (SeleniumUtils.isElementPresent(suggestion, SUGGESTION_WAIT_SECONDS)) {
            // Known cheque: suggestion found — date auto-populated and enabled immediately
            SeleniumUtils.click(suggestion, WaitType.CLICKABLE, "INSTRUMENT_NO_SUGGESTION_" + instrumentNo);
        } else {
            // Unknown cheque: Tab commits the value; focus moves off the field.
            // Warning does NOT appear here — it appears only after the first OK click.
            SeleniumUtils.pressTab(INSTRUMENT_NO, "INSTRUMENT_NO");
        }

        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    /**
     * Known/unknown fillAndSubmit with two distinct paths:
     *
     * <p>Known cheque (suggestion clicked): Instrument Date is enabled immediately → fill date →
     * fill reference → OK → popup closes.
     *
     * <p>Unknown cheque (Tab pressed, no suggestion): Instrument Date is greyed out → skip date →
     * fill reference → OK → warning appears → click Yes → BOTH warning AND payment popup close.
     * Instrument Date is automatically set to Document Date by the application.
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
            WaitUtils.webDriverWait(ConfigFactory.getWaitConfig().shortWaitSeconds())
                    .until(driver -> {
                        try {
                            WebElement el = driver.findElement(INSTRUMENT_DATE);
                            return el.isDisplayed()
                                    && el.isEnabled()
                                    && el.getAttribute("readonly") == null;
                        } catch (NoSuchElementException e) {
                            return false;
                        }
                    });
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }
}
