package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.pages.Finance.Operations.Common.PaymentInfoCheckWarning;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.Selenium.ExtJsQuickTipUtils;
import org.openqa.selenium.By;

import java.util.Arrays;

/**
 * Shared negative/inline-validation assertion steps for General Voucher, AR Voucher, and
 * AP Voucher. Factors out the "Save → generic detail-section dialog → OK → hover row →
 * assert 'Stop:' + scenario message (+ optional bold-HTML check)" shape previously duplicated
 * across {@code GeneralVoucherPredicateFactory.NEGATIVE_FORM_VALIDATION_BEHAVIOR},
 * {@code ApVoucherPredicateFactory.INLINE_VALIDATION_BEHAVIOR}/{@code DOC_INLINE_VALIDATION_BEHAVIOR},
 * and {@code ArVoucherPredicateFactory.INLINE_VALIDATION_BEHAVIOR}.
 *
 * <p>General Voucher's own version previously omitted the "Stop:" severity-marker assertion that
 * AP/AR's inline validation already required — centralizing the flow here makes that omission
 * structurally impossible for any future scenario (run 20260809_000447 caught this for GV's
 * {@code GV_SITE_ALLOCATION_PENDING}/{@code GV_SITE_NOT_ALLOCATED} scenarios).
 *
 * <p>Deliberately does NOT wrap {@code page.clickSave()} — the three voucher "Add" page objects
 * share no common interface for that call, so each factory still invokes its own
 * {@code page.clickSave()} and passes the returned {@link DocumentPopup} in here.
 */
public final class NegativeValidationSteps {

    private NegativeValidationSteps() {}

    /**
     * Asserts the generic "Some problems encountered in the detail section" dialog
     * ({@link NegativeValidation#ZERO_AMOUNT_DIALOG}) is showing after Save, logs the match,
     * then dismisses it via OK — leaving the caller on the form so a later hover step can read
     * the scenario-specific message from the row's own tooltip (see {@link #assertRowTooltip}).
     *
     * <p>Always calls {@link DocumentPopup#proceedPastPaymentInfoWarningIfPresent} first — a
     * no-op unless the message box currently on screen contains "instrument number", so safe
     * to standardize across GV/AR/AP (previously only GV's behavior did this).
     *
     * @param popup   the {@link DocumentPopup} returned by the caller's own {@code page.clickSave()}
     * @param warning which voucher type's payment-info warning to proceed past, if present
     * @return the actual dialog text, for callers that want to log/inspect it further
     */
    public static String assertDetailSectionDialog(DocumentPopup popup, PaymentInfoCheckWarning warning) {
        popup.proceedPastPaymentInfoWarningIfPresent(warning);
        String expected = NegativeValidation.ZERO_AMOUNT_DIALOG.expectedMessage();
        String actual = popup.verifyErrorMessage(expected);
        ReportLogger.pass("Dialog OK — Expected: [" + expected + "] | Received: [" + actual + "]");
        popup.clickOkOnErrorDialog();
        return actual;
    }

    /**
     * Hovers the given row indicator and asserts the ExtJS QuickTip contains the blocking
     * "Stop:" severity marker ({@link NegativeValidation#ZERO_AMOUNT_TOOLTIP_STOP}) plus every
     * {@code additionalExpectedSubstrings} value. "Stop:" is asserted unconditionally here —
     * GV's own inline validation previously omitted it (the bug this refactor fixes); AP/AR
     * already asserted it per call site, this just centralizes it so no future scenario can
     * drop it again.
     *
     * <p>Also asserts the bold-HTML marker when {@code warning.rendersBoldStopMarker()} is true
     * ({@link PaymentInfoCheckWarning#rendersBoldStopMarker()}) — keyed off the same enum
     * constant the caller already passed to {@link #assertDetailSectionDialog}, so no separate
     * boolean flag or second enum is needed at the call site.
     *
     * @param rowIndicatorLocator locator for the row's hover target (dots / indicator button)
     * @param warning              which voucher type is being asserted (drives the bold-HTML check)
     * @param additionalExpectedSubstrings the scenario-specific message(s) expected alongside "Stop:"
     * @return the actual tooltip text
     */
    public static String assertRowTooltip(By rowIndicatorLocator, PaymentInfoCheckWarning warning,
                                           String... additionalExpectedSubstrings) {
        String[] allExpected = new String[additionalExpectedSubstrings.length + 1];
        allExpected[0] = NegativeValidation.ZERO_AMOUNT_TOOLTIP_STOP.expectedMessage();
        System.arraycopy(additionalExpectedSubstrings, 0, allExpected, 1, additionalExpectedSubstrings.length);

        String actual = ExtJsQuickTipUtils.hoverAndVerify(rowIndicatorLocator, allExpected);
        if (warning.rendersBoldStopMarker()) {
            ExtJsQuickTipUtils.verifyVisibleHtml(NegativeValidation.ZERO_AMOUNT_TOOLTIP_STOP_BOLD.expectedMessage());
        }
        ReportLogger.pass("Tooltip OK — Expected: " + Arrays.toString(allExpected)
                + " | Received: [" + actual + "]");
        return actual;
    }
}
