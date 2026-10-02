package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInfoEditTarget;

/**
 * Binds a Payment-Info-Edit "instrument date vs. entry date" combination to its expected outcome —
 * the data+outcome analogue of {@link GeneralVoucherScenarioType}'s templateLoader+behaviorSupplier
 * pairing, adapted to how {@code editGeneralVoucherPaymentInfo} actually works: unlike Create's
 * negative scenarios (which genuinely differ in UI *interaction sequence* — a dialog check on a
 * button click vs. on Save — and so need their own {@code BiPredicate}), every combination here
 * shares the exact same interaction (open the popup, fill the target's payment info, click Save,
 * then the already-generic {@code expectedValidation != null} branch in {@code
 * EditGeneralVoucher.runEditFlow}, Step 9b). Only the DATA (shape, Dr/Cr, date) and the EXPECTED
 * OUTCOME differ between combinations — a {@code BiPredicate} per constant here would just be four
 * copies of identical logic, so this binds data+outcome instead, the same way {@code
 * NegativeValidation} already binds a scenario to its expected error text.
 *
 * <p><b>Full Dr/Cr × final-mode × date matrix</b> (see the plan's Round 9 notes for the full
 * analysis) — only 4 of the 8 combinations are meaningfully testable via the live UI:
 * <ul>
 *   <li>Dr + NEFT/RTGS (either date) is a dead end: {@code GvPaymentInformationPopup}'s
 *       known/unknown-cheque branching takes the "unknown" path for a synthetic NEFT reference
 *       (it never matches a real registered-cheque suggestion), which never calls {@code
 *       setInstrumentDate} at all — the app silently defaults Instrument Date to the entry date
 *       server-side regardless of what a test tries to set, so no date override ever reaches the
 *       UI. Not represented here.</li>
 *   <li>Dr + Cheque uses the SAME popup's "known cheque" branch (a real registered instrument
 *       from {@code ChequeInstrumentResolver#resolveKnownSameBank}) — {@code setInstrumentDate}
 *       IS called, so a future date is genuinely testable there too — see {@link
 *       #CHEQUE_FUTURE_DATE_DR_ACCEPTED}.</li>
 *   <li>Cr (either mode) always uses {@code GvReceiptPaymentInformationPopup}, which calls {@code
 *       setInstrumentDate} unconditionally — every Cr combination here is genuinely testable, and
 *       — until these scenarios existed — the existing {@code MANDATORY} Payment-Info variants
 *       never explicitly required a Cr row at all, so this popup's own {@code setInstrumentDate}
 *       call may have had zero prior explicit regression coverage.</li>
 * </ul>
 */
public enum PaymentInfoDateScenario {

    /** Cr row, final mode NEFT/RTGS, Instrument Date set AFTER the voucher's own entry date — the
     *  app is expected to REJECT the save ("Instrument date cannot be greater than entry date").
     *  The scenario that originally surfaced the {@code LocalDate.now()} bug (reports/runs/
     *  20260917_005651 and 20260922_225443, FIN-GV-EDIT-PAYMENT-02). */
    NEFT_FUTURE_DATE_CR_REJECTED(
            PaymentInfoEditTarget.PaymentModeShape.CHEQUE_TO_NEFT, true, true,
            NegativeValidation.GV_PAYMENT_NEFT_FUTURE_INSTRUMENT_DATE_NOT_ALLOWED),

    /** Cr row, final mode NEFT/RTGS, Instrument Date left at the voucher's own (unchanged) entry
     *  date — expected to SUCCEED. Isolates "does the Cr-side NEFT/RTGS path work at all" from
     *  "is the future-date rule enforced" ({@link #NEFT_FUTURE_DATE_CR_REJECTED}) — without this,
     *  a failure in the negative scenario can't be told apart from a broken Cr+NEFT path itself. */
    NEFT_CURRENT_DATE_CR_ACCEPTED(
            PaymentInfoEditTarget.PaymentModeShape.CHEQUE_TO_NEFT, true, false, null),

    /** Cr row, final mode Cheque, Instrument Date set AFTER the voucher's own entry date —
     *  expected to SUCCEED (Cheque, unlike NEFT/RTGS, allows a post-dated instrument — confirmed
     *  by the user). */
    CHEQUE_FUTURE_DATE_CR_ACCEPTED(
            PaymentInfoEditTarget.PaymentModeShape.SAME_MODE_NEW_INSTRUMENT, true, true, null),

    /** Dr row, final mode Cheque, Instrument Date set AFTER the voucher's own entry date —
     *  expected to SUCCEED, same business rule as {@link #CHEQUE_FUTURE_DATE_CR_ACCEPTED} but
     *  exercised through the OTHER popup implementation ({@code GvPaymentInformationPopup}'s
     *  known-cheque branch, not {@code GvReceiptPaymentInformationPopup}). */
    CHEQUE_FUTURE_DATE_DR_ACCEPTED(
            PaymentInfoEditTarget.PaymentModeShape.SAME_MODE_NEW_INSTRUMENT, false, true, null);

    private final PaymentInfoEditTarget.PaymentModeShape shape;
    private final boolean requireCrRow;
    private final boolean futureDate;
    private final NegativeValidation expectedRejection;

    PaymentInfoDateScenario(PaymentInfoEditTarget.PaymentModeShape shape, boolean requireCrRow,
            boolean futureDate, NegativeValidation expectedRejection) {
        this.shape = shape;
        this.requireCrRow = requireCrRow;
        this.futureDate = futureDate;
        this.expectedRejection = expectedRejection;
    }

    public PaymentInfoEditTarget.PaymentModeShape shape() { return shape; }

    /** {@code true} requires the claimed voucher's counter-leg to be Credit; {@code false}
     *  requires Debit — see this enum's own class Javadoc for why Dr/Cr must be pinned instead
     *  of left to whichever row {@code claimOne} happens to pick first. */
    public boolean requireCrRow() { return requireCrRow; }

    /** {@code true} when the new Instrument Date must be set AFTER the voucher's own entry date;
     *  {@code false} leaves it at the entry date (the post-fix default for {@code CHEQUE_TO_NEFT},
     *  or unset/{@code null} for the Cheque-final shapes). */
    public boolean futureDate() { return futureDate; }

    /** The expected rejection message, or {@code null} when this combination is expected to
     *  succeed. */
    public NegativeValidation expectedRejection() { return expectedRejection; }
}
