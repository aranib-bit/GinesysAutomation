package com.gsl.pages.Finance.Operations.Common;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.VoucherDomain;
import com.gsl.enums.WaitType;
import com.gsl.reports.ExtentLogger;
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

    protected ChequeRegisterPaymentInformationPopup(VoucherDomain domain) {
        super(domain);
    }

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

    /**
     * The Instrument No. field's value as read immediately before this popup last typed into it —
     * {@code null} for Create (field always starts empty) and for the first payment entry on any
     * voucher; non-null only on genuine Edit-reuse, where the field arrives pre-filled with the
     * voucher's existing cheque number. Captured here (not just used to decide whether to clear)
     * because it's the one place that observes the field's genuine pre-edit value directly from
     * the live UI — the Payment-Info-Edit scenario's audit-trail validation needs a reliable old
     * value to assert the aud2_finvchdet old→new chqno transition against, and this is that value.
     */
    private String lastObservedPreviousInstrumentNo;

    public String getLastObservedPreviousInstrumentNo() {
        return lastObservedPreviousInstrumentNo;
    }

    /**
     * Reads the Instrument No. field's value exactly as it stands when this popup first opens —
     * called first thing in {@link #fillAndSubmit}, BEFORE {@link #selectPaymentMode}. Previously
     * this read happened inside {@link #setInstrumentNo} instead, i.e. AFTER {@code
     * selectPaymentMode} had already re-clicked the Payment Mode trigger and re-selected an
     * option — including for {@code SAME_MODE_NEW_INSTRUMENT}, which reselects the SAME mode the
     * field already had. That reselect appears to transiently reset/re-render the ExtJS Instrument
     * No. combo (clearing it or making it briefly {@code aria-disabled='true'}, which {@link
     * PaymentInformationPopup#INSTRUMENT_NO}'s locator requires to be {@code 'false'}), so the
     * old read-after-mode-select came back {@code null} even when the field was genuinely
     * pre-filled — a confirmed live bug (reports/runs/20260916_133846, FIN-GV-EDIT-PAYMENT-02/03):
     * {@code GeneralVoucherEditPredicateFactory}'s pre-edit cross-check then aborted before Save
     * because the live-observed value ('null') didn't match the DB-resolved one. Capturing here,
     * before any mode reselection touches the field, removes the race outright.
     *
     * <p>Uses {@code isElementPresent} (a short, non-throwing probe) rather than {@code
     * getDomProperty}'s own visibility wait — on a fresh Create popup the Instrument No. field may
     * not even be rendered yet (Payment Mode hasn't been set to Cheque at all on first open), and
     * this runs BEFORE that selection now, so a blind {@code getDomProperty} here would wait out a
     * full timeout on every Create call instead of the instant {@code null} it correctly returned
     * before this fix moved the read earlier.
     */
    public ChequeRegisterPaymentInformationPopup captureCurrentInstrumentNo() {
        if (!SeleniumUtils.isElementPresent(INSTRUMENT_NO, SUGGESTION_WAIT_SECONDS)) {
            lastObservedPreviousInstrumentNo = null;
            return this;
        }
        String previousValue = SeleniumUtils.getDomProperty(INSTRUMENT_NO, "value", "INSTRUMENT_NO");
        lastObservedPreviousInstrumentNo = (previousValue == null || previousValue.isBlank()) ? null : previousValue;
        return this;
    }

    @Override
    public ChequeRegisterPaymentInformationPopup setInstrumentNo(String instrumentNo) {
        if (instrumentNo == null || instrumentNo.isBlank()) return this;

        // Edit-reuse fix: the field may already carry the voucher's existing cheque number
        // (Create's field always starts empty, so sendKeys-with-no-clear was safe there) — typing
        // without clearing first would APPEND to that value instead of replacing it. Mirrors
        // PaymentInformationPopup#setInstrumentDate's own read-current-value-then-clear pattern
        // (same package). Zero behavior change for Create: currentValue is always blank there, so
        // the clear() call is skipped exactly as before this fix. The previous value itself is
        // captured by captureCurrentInstrumentNo(), called BEFORE selectPaymentMode() in
        // fillAndSubmit() — see that method's Javadoc for why the read moved out of here.
        if (lastObservedPreviousInstrumentNo != null) {
            SeleniumUtils.clear(INSTRUMENT_NO, "INSTRUMENT_NO");
        }

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
        // Capture the field's genuine pre-edit value BEFORE selectPaymentMode touches it — see
        // captureCurrentInstrumentNo()'s Javadoc.
        captureCurrentInstrumentNo();
        selectPaymentMode(payment.getPaymentMode())
                .setInstrumentNo(payment.getInstrumentNo())
                .setDrawnOn(payment.getDrawnOn());

        // Live-UI-observed old→new record, independent of whatever the data source's own DB read
        // resolved as the "before" value — a Payment-Info-Edit scenario can cross-check the two
        // agree (see PaymentInfoEditTarget/GeneralVoucherEditPredicateFactory#PAYMENT_INFO_EDIT_BEHAVIOR)
        // before relying on either for the aud2_finvchdet old-value audit assertion. Blank for
        // Create (lastObservedPreviousInstrumentNo is null there — nothing to log).
        if (lastObservedPreviousInstrumentNo != null) {
            ExtentLogger.info("[ChequeRegisterPaymentInformationPopup] Instrument No. changed (live UI): '"
                    + lastObservedPreviousInstrumentNo + "' → '" + payment.getInstrumentNo() + "'");
        }

        // Known-cheque: date field enabled immediately after suggestion click → set it now.
        // Unknown-cheque: date field is greyed out → skip; app sets it to Document Date on save.
        boolean knownCheque = isInstrumentDateReady();
        if (knownCheque) {
            setInstrumentDate(payment.getInstrumentDate());
        }

        setReference(payment.getReference());
        runBeforeSubmitHook();

        // okButtonLocator() (not the raw OK_BUTTON field) so GvPaymentInformationPopup's
        // window-scoped override actually applies here — this is the exact call site implicated
        // in the confirmed live "OK button not visible" timeout (reports/runs/20260913_141449,
        // FIN-GV-ADD-COMBO-07).
        SeleniumUtils.click(okButtonLocator(), WaitType.CLICKABLE, "PAYMENT_INFO_OK");
        JavascriptExecutorUtils.waitForExtJS();

        // Unknown-cheque path: warning appears after OK; clicking Yes closes both warning and popup.
        // Known-cheque path: no warning; popup already closed by the OK above.
        boolean warningHandled = handleChequeWarningIfPresent();
        if (!knownCheque && !warningHandled) {
            // Real, reproducible anomaly (confirmed live, reports/runs/20260913_170619 and
            // _173951, FIN-GV-ADD-COMBO-01): the unknown-cheque branch was taken (Instrument Date
            // never became ready) yet the app's own "not present in system" warning never
            // appeared either — meaning it's unconfirmed whether this popup actually closed after
            // OK. Previously silently swallowed (handleChequeWarningIfPresent's `false` return was
            // never checked) — surfaced here so a future failure downstream of this (e.g. Save
            // never producing its success message) has this as a visible lead instead of nothing.
            ExtentLogger.warning("[ChequeRegisterPaymentInformationPopup] Instrument No. took the "
                    + "unknown-cheque path (Instrument Date never enabled) but the expected 'not "
                    + "present in system' confirmation never appeared after OK — this popup's "
                    + "actual close state is unconfirmed.");
        }
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
