package com.gsl.pages.Finance.Operations.Common;

import com.gsl.enums.PaymentSelectionType;
import com.gsl.enums.VoucherDomain;
import com.gsl.enums.WaitType;
import com.gsl.pages.enums.Finance.Operations.Common.PaymentInfoLabel;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherPaymentInfo;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.ui.FieldStyleSpec;
import com.gsl.utils.ui.UiFieldState;
import com.gsl.validation.ui.UiComponentValidator;
import com.gsl.validation.ui.UiValidationFailure;
import com.gsl.validation.ui.UiValidationLevel;
import com.gsl.validation.ui.UiValidationReporter;
import org.openqa.selenium.By;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base for the Payment Information popup shared by AR, AP and GV Voucher.
 *
 * Implements the Template Method pattern:
 *   fillAndSubmit() defines the fixed algorithm;
 *   setInstrumentNo() is an abstract step whose behaviour differs between AR (simple
 *   sendKeys+Tab) and AP/GV (known/unknown cheque-register branching, via
 *   {@link ChequeRegisterPaymentInformationPopup}) and is implemented by each concrete subclass.
 *
 * <p>{@code setDrawnOn(String)} is now a CONCRETE method here (not abstract) — the "Drawn On"/
 * "Cheque label" field's locator is byte-for-byte identical across AR/AP/GV (confirmed live via
 * screenshot for all three), varying only in its label text, which is why it doesn't need its own
 * per-domain subclass override anymore. That one differing label is centralized on {@link
 * VoucherDomain#getPaymentDrawnOnLabel()} — this class just reads it via the {@link #domain}
 * every subclass now supplies through its constructor. This replaces three separate, duplicated
 * single-constant label enums ({@code ArPaymentInfoLabel}/{@code ApPaymentInfoLabel}/{@code
 * GvPaymentInfoLabel}), one of which (GV's) carried a wrong, never-live-verified guess.
 *
 * <p>Label strings for the other fields (Payment Mode/Instrument No./Instrument Date/Reference)
 * are sourced from {@link PaymentInfoLabel}, which — unlike "Drawn On" — is genuinely common
 * across all three domains, so a UI label change there requires editing only that one enum, not
 * every XPath.
 */
public abstract class PaymentInformationPopup {

    /** Which voucher domain this popup instance serves — supplied by each concrete subclass's
     *  own no-arg constructor (it already IS domain-specific by construction), used only to look
     *  up {@link VoucherDomain#getPaymentDrawnOnLabel()} in {@link #setDrawnOn(String)}. */
    protected final VoucherDomain domain;

    protected PaymentInformationPopup(VoucherDomain domain) {
        this.domain = domain;
    }

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

    /**
     * Unscoped across every {@code x-window} in the DOM — matches whichever OK button comes first
     * in document order, which can be a stale/leftover window's rather than this popup's own.
     * Kept as the default (AR/AP have shown no live failure from this) but overridable per
     * subclass via {@link #okButtonLocator()} — see {@link GvPaymentInformationPopup} for the
     * window-scoped override, added after a confirmed live "OK button not visible" timeout
     * (reports/runs/20260913_141449, FIN-GV-ADD-COMBO-07) where GV's own Allocate Sites popup
     * (opened earlier in the same flow, unique to GV — AR/AP have no site-allocation step) left a
     * matching stale node ahead of the real, visible OK button in document order.
     */
    protected static final By OK_BUTTON = By.xpath(
            "//div[contains(@class,'x-window')]//span[normalize-space()='OK']"
            + "/ancestor::a[contains(@class,'x-btn') and not(@aria-hidden='true')]");

    /** Override to scope this to the specific popup window — see {@link #OK_BUTTON}'s Javadoc. */
    protected By okButtonLocator() {
        return OK_BUTTON;
    }

    // ── Abstract steps — AR vs. AP/GV implement this differently ──────────────

    public abstract PaymentInformationPopup setInstrumentNo(String instrumentNo);

    /**
     * Plain sendKeys+Tab Instrument No. entry — no cheque-register known/unknown lookup at all,
     * because the field simply doesn't need one for a received cheque (the app auto-populates
     * Instrument Date directly). Shared by {@link ArPaymentInformationPopup} and {@link
     * GvReceiptPaymentInformationPopup} (GV's Cr-side sibling) — extracted here (pure
     * extract-method, no behavior change) so the two don't duplicate this body.
     */
    protected final PaymentInformationPopup setInstrumentNoPlain(String instrumentNo) {
        if (instrumentNo == null || instrumentNo.isBlank()) return this;
        SeleniumUtils.sendKeys(INSTRUMENT_NO, WaitType.CLICKABLE, instrumentNo, "INSTRUMENT_NO");
        SeleniumUtils.pressTab(INSTRUMENT_NO, "INSTRUMENT_NO");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    // ── Shared steps ─────────────────────────────────────────────────────────

    /** Locator template for the "Drawn On"/"Cheque label" field — identical across AR/AP/GV,
     *  parameterized only by {@code label} (see {@link VoucherDomain#getPaymentDrawnOnLabel()}). */
    private static By drawnOnLocator(String label) {
        return By.xpath("//span[normalize-space()='" + label + "']/ancestor::label"
                + "/following-sibling::div[contains(@class,'x-form-item-body')]//input[@data-ref='inputEl']");
    }

    public PaymentInformationPopup setDrawnOn(String drawnOn) {
        if (drawnOn == null || drawnOn.isBlank()) return this;
        SeleniumUtils.sendKeys(drawnOnLocator(domain.getPaymentDrawnOnLabel()), WaitType.CLICKABLE, drawnOn, "DRAWN_ON");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    /** Extra trigger clicks allowed when the Payment Mode list did not open (first click not counted). */
    private static final int PAYMENT_MODE_REOPEN_ATTEMPTS = 2;

    /**
     * Clicks the Payment Mode trigger and makes sure the option list is actually showing. The click is
     * sometimes reported successful while the list stays closed (the ExtJS combo drops it again), and the
     * test then died 30s later waiting for the option — seen in runs 20261031_123502 (AP), _124927 (AR) and
     * _135940 (GV edit PaymentInfo) with no mask or popup in the way. Only a CLOSED list is re-clicked: a
     * click on an open one would close it. Every re-click is logged (log file + report warning) so the
     * flake stays visible, and if the list still will not open the original wait fails as before.
     */
    private void openPaymentModeList(By option, String optionLabel) {
        long probeSeconds = com.gsl.config.ConfigFactory.getWaitConfig().shortWaitSeconds();
        SeleniumUtils.click(PAYMENT_MODE_TRIGGER, WaitType.CLICKABLE, "PAYMENT_MODE_TRIGGER");
        JavascriptExecutorUtils.waitForExtJS();
        for (int retry = 1; retry <= PAYMENT_MODE_REOPEN_ATTEMPTS; retry++) {
            if (isOptionShowing(option, probeSeconds)) return;
            String msg = "[PaymentInformationPopup] Payment Mode list did not open after the trigger click "
                    + "('" + optionLabel + "' not visible within " + probeSeconds + "s) — clicking the trigger again, "
                    + "re-open " + retry + "/" + PAYMENT_MODE_REOPEN_ATTEMPTS;
            com.gsl.utils.Log.warn(msg);
            com.gsl.reports.ExtentLogger.warning(msg);
            SeleniumUtils.click(PAYMENT_MODE_TRIGGER, WaitType.CLICKABLE, "PAYMENT_MODE_TRIGGER_REOPEN_" + retry);
            JavascriptExecutorUtils.waitForExtJS();
        }
        if (!isOptionShowing(option, probeSeconds)) {
            String msg = "[PaymentInformationPopup] Payment Mode list still closed after "
                    + PAYMENT_MODE_REOPEN_ATTEMPTS + " re-clicks — '" + optionLabel + "' is not visible.";
            com.gsl.utils.Log.error(msg);
            com.gsl.reports.ExtentLogger.warning(msg);
        }
    }

    /** Whether the option is displayed within {@code seconds}; never throws. */
    private static boolean isOptionShowing(By option, long seconds) {
        try {
            return com.gsl.utils.Selenium.WaitUtils.webDriverWait(seconds).until(d ->
                    d.findElements(option).stream().anyMatch(org.openqa.selenium.WebElement::isDisplayed));
        } catch (org.openqa.selenium.TimeoutException e) {
            return false;
        }
    }

    public PaymentInformationPopup selectPaymentMode(PaymentSelectionType mode) {
        By option = By.xpath(String.format(PAYMENT_MODE_OPTION, mode.label()));
        openPaymentModeList(option, mode.label());
        SeleniumUtils.click(option, WaitType.CLICKABLE, "PAYMENT_MODE_OPTION_" + mode.label());
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    public PaymentInformationPopup setInstrumentDate(String instrumentDate) {
        if (instrumentDate == null || instrumentDate.isBlank()) return this;
        // The app greys Instrument Date out for an instrument number it does not know and sets it to the
        // Document Date itself on save (see ChequeRegisterPaymentInformationPopup). A hard 10s wait on a
        // field that stays disabled failed AR/GV cash-bank payment rows with a bare TimeoutException
        // (runs 20261031, 01:39-05:00 IST) — say so and carry on instead; the saved value is still verified
        // by the later UI/DB/API validation.
        if (!isInstrumentDateReady()) {
            com.gsl.reports.ExtentLogger.warning("[PaymentInformationPopup] Instrument Date stayed disabled/read-only — "
                    + "not set to '" + instrumentDate + "'; the app fills it from the Document Date on save.");
            return this;
        }
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

    /** Whether Instrument Date is displayed, enabled and editable within the interaction wait. */
    private boolean isInstrumentDateReady() {
        try {
            SeleniumUtils.waitForInputReady(INSTRUMENT_DATE);
            return true;
        } catch (org.openqa.selenium.TimeoutException e) {
            return false;
        }
    }

    public PaymentInformationPopup setReference(String reference) {
        if (reference == null || reference.isBlank()) return this;
        SeleniumUtils.sendKeys(REFERENCE, WaitType.CLICKABLE, reference, "REFERENCE");
        return this;
    }

    private Runnable beforeSubmitHook;

    /** Installs a callback run right after the last field is filled and just before OK is clicked —
     *  the only moment the popup shows its filled state, so a UI-validation recorder can check the
     *  "after fill" values without the fill code knowing about validation. {@code null} removes it. */
    public PaymentInformationPopup onBeforeSubmit(Runnable hook) {
        this.beforeSubmitHook = hook;
        return this;
    }

    /** Runs the {@link #onBeforeSubmit} hook; isolated so a validation error never breaks the fill. */
    protected void runBeforeSubmitHook() {
        if (beforeSubmitHook == null) return;
        try {
            beforeSubmitHook.run();
        } catch (RuntimeException e) {
            com.gsl.utils.Log.warn("[PaymentInformationPopup] before-submit hook skipped: {}", e.getMessage());
        }
    }

    public void clickOk() {
        SeleniumUtils.click(okButtonLocator(), WaitType.CLICKABLE, "PAYMENT_INFO_OK");
        JavascriptExecutorUtils.waitForExtJS();
    }

    // ── Template method — algorithm fixed here, variant steps delegated ───────

    public void fillAndSubmit(VoucherPaymentInfo payment) {
        selectPaymentMode(payment.getPaymentMode())
                .setInstrumentNo(payment.getInstrumentNo())
                .setInstrumentDate(payment.getInstrumentDate())
                .setDrawnOn(payment.getDrawnOn())
                .setReference(payment.getReference());
        runBeforeSubmitHook();
        clickOk();
    }

    // ── UI-contract validation ────────────────────────────────────────────────
    // Label locators — UNVERIFIED (same "span text -> ancestor::label" idiom this class's own
    // INSTRUMENT_NO/INSTRUMENT_DATE/REFERENCE/drawnOnLocator already use, proven live by the real
    // fill flow; the <label> ancestor itself, used here for text+style checks, has not been
    // independently confirmed).
    private static By labelLocator(String labelText) {
        return By.xpath("//span[normalize-space()='" + labelText + "']/ancestor::label");
    }

    // Popup-scoped locators for the UI-contract checks only (the business-flow locators above stay as
    // they are — proven live). Scope = the window titled "Payment Information"; each check falls back
    // to its unscoped form via pick(...) if that window title is not matched.
    private static final String POPUP_SCOPE = "(//div[contains(@class,'winTitle') and normalize-space()='Payment Information']"
            + "/ancestor::div[contains(@class,'x-window')][1])";

    private static By scopedLabel(String text) {
        return By.xpath(POPUP_SCOPE + "//span[normalize-space()='" + text + "']/ancestor::label");
    }

    private static By scopedInput(String text) {
        return By.xpath(POPUP_SCOPE + "//span[normalize-space()='" + text
                + "']/ancestor::div[contains(@class,'x-form-item')][1]//input[@data-ref='inputEl']");
    }

    /** Unscoped input of a captioned field, whatever its enabled/disabled state. */
    private static By anyStateInput(String text) {
        return By.xpath("//span[normalize-space()='" + text
                + "']/ancestor::div[contains(@class,'x-form-item')][1]//input[@data-ref='inputEl']");
    }

    /** {@code scoped} when it is present right now, else {@code fallback}. */
    private static By pick(By scoped, By fallback) {
        return UiComponentValidator.quickPresent(scoped) ? scoped : fallback;
    }

    /** Expected state of Instrument Date on the freshly opened popup, or {@code null} when it is not
     *  confirmed for this popup (the check is then reported as a Skip). Overridden where known. */
    protected UiFieldState instrumentDateStateBeforeFill() {
        return null;
    }

    /** Full UI-contract sweep for this popup — common to AR/AP/GV via this shared abstract base,
     *  same "common except for business difference" principle the rest of this framework already
     *  follows. The one domain-varying field (Drawn On/"Cheque label:") resolves its label text
     *  per-instance via {@link #domain}, exactly how {@link #setDrawnOn} already does it; the OK
     *  button resolves via the overridable {@link #okButtonLocator()}, already window-scoped per
     *  subclass (see {@code GvPaymentInformationPopup#okButtonLocator}). No subclass needs its own
     *  override of this method — the existing abstraction already isolates the one difference. */
    public void validateUiContract(UiValidationLevel level) {
        UiValidationReporter.renderAndAssert(collectUiContract(level));
    }

    /** The popup's own chrome (the original {@link #validateUiContract} body), returned instead of
     *  rendered so callers can nest it under a report node (see {@code GeneralVoucherAdd#validateRowUi}). */
    public List<UiValidationFailure> collectUiContract(UiValidationLevel level) {
        List<UiValidationFailure> failures = new ArrayList<>();

        failures.addAll(UiComponentValidator.validateLabel(labelLocator(PaymentInfoLabel.PAYMENT_MODE.label()),
                "PAYMENT_INFO_PAYMENT_MODE_LABEL", PaymentInfoLabel.PAYMENT_MODE.label(), FieldStyleSpec.FIELD_LABEL, level));

        failures.addAll(UiComponentValidator.validateLabel(labelLocator(PaymentInfoLabel.INSTRUMENT_NO.label()),
                "PAYMENT_INFO_INSTRUMENT_NO_LABEL", PaymentInfoLabel.INSTRUMENT_NO.label(), FieldStyleSpec.FIELD_LABEL, level));
        failures.addAll(UiComponentValidator.validateInputStyle(INSTRUMENT_NO, "PAYMENT_INFO_INSTRUMENT_NO",
                FieldStyleSpec.TEXT_INPUT, UiFieldState.EDITABLE, level));

        // Instrument Date and the Drawn On / Cheque label are only rendered once a non-cash payment
        // mode is selected (confirmed live: absent on the freshly opened popup), so before that they
        // are SKIP "not displayed" rather than a missing-element FAIL.
        // Instrument Date is PRESENT but DISABLED on a freshly opened GV Dr/Cheque popup (user-confirmed),
        // while INSTRUMENT_DATE (the business-flow locator) only matches an ENABLED input
        // (aria-disabled='false') — so the contract check uses a state-agnostic, popup-scoped locator.
        By instrumentDateLabel = pick(scopedLabel(PaymentInfoLabel.INSTRUMENT_DATE.label()),
                labelLocator(PaymentInfoLabel.INSTRUMENT_DATE.label()));
        if (UiComponentValidator.quickPresent(instrumentDateLabel)) {
            failures.addAll(UiComponentValidator.validateLabel(instrumentDateLabel,
                    "PAYMENT_INFO_INSTRUMENT_DATE_LABEL", PaymentInfoLabel.INSTRUMENT_DATE.label(), FieldStyleSpec.FIELD_LABEL, level));
            By dateInput = pick(scopedInput(PaymentInfoLabel.INSTRUMENT_DATE.label()),
                    anyStateInput(PaymentInfoLabel.INSTRUMENT_DATE.label()));
            UiFieldState dateState = instrumentDateStateBeforeFill();
            if (dateState != null) {
                failures.addAll(UiComponentValidator.validateInputStyle(dateInput, "PAYMENT_INFO_INSTRUMENT_DATE",
                        FieldStyleSpec.DATE_INPUT, dateState, level));
            } else {
                failures.addAll(UiComponentValidator.skip("PAYMENT_INFO_INSTRUMENT_DATE", "field state",
                        "not confirmed for this popup", "not asserted"));
            }
        } else {
            failures.addAll(UiComponentValidator.skip("PAYMENT_INFO_INSTRUMENT_DATE", "visibility",
                    "shown once a non-cash payment mode is selected", "not displayed yet"));
        }

        failures.addAll(UiComponentValidator.validateLabel(labelLocator(PaymentInfoLabel.REFERENCE.label()),
                "PAYMENT_INFO_REFERENCE_LABEL", PaymentInfoLabel.REFERENCE.label(), FieldStyleSpec.FIELD_LABEL, level));

        String drawnOnLabel = domain.getPaymentDrawnOnLabel();
        // Popup-scoped first (the unscoped span can hit a hidden/stale earlier match), unscoped as fallback.
        By drawnOn = pick(scopedLabel(drawnOnLabel), labelLocator(drawnOnLabel));
        if (UiComponentValidator.quickPresent(drawnOn)) {
            failures.addAll(UiComponentValidator.validateLabel(drawnOn,
                    "PAYMENT_INFO_DRAWN_ON_LABEL", drawnOnLabel, FieldStyleSpec.FIELD_LABEL, level));
        } else {
            failures.addAll(UiComponentValidator.skip("PAYMENT_INFO_DRAWN_ON_LABEL", "visibility",
                    "shown once a non-cash payment mode is selected", "not displayed yet"));
        }

        failures.addAll(UiComponentValidator.validateButton(okButtonLocator(), "PAYMENT_INFO_OK_BUTTON",
                "OK", true, level));

        return failures;
    }

    /** Value checks for the "after fill" state: Instrument No. and Reference read back equal what
     *  was entered (expected comes from the test data, never from the page). A blank/absent
     *  expected value is expected to read back blank. Instrument Date is not compared here — its
     *  on-screen format differs from the data format. */
    public List<UiValidationFailure> collectValuesUi(VoucherPaymentInfo expected) {
        List<UiValidationFailure> failures = new ArrayList<>();
        failures.addAll(UiComponentValidator.validateValue(INSTRUMENT_NO, "PAYMENT_INFO_INSTRUMENT_NO",
                expected.getInstrumentNo() == null ? "" : expected.getInstrumentNo()));
        failures.addAll(UiComponentValidator.validateValue(REFERENCE, "PAYMENT_INFO_REFERENCE",
                expected.getReference() == null ? "" : expected.getReference()));
        return failures;
    }
}
