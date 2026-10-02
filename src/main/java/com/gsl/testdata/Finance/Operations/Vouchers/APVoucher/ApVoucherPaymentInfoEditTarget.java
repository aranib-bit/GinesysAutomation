package com.gsl.testdata.Finance.Operations.Vouchers.APVoucher;

import lombok.Builder;
import lombok.Getter;

/**
 * Identifies the before/after {@link PaymentInformation} pair an {@code editApVoucherPaymentInfo}
 * scenario targets — the AP analogue of General Voucher's own {@code PaymentInfoEditTarget}, kept
 * deliberately leaner: no bank-match/bookcode resolution is modeled yet (GV's own version needed
 * that only for its own DB-validation depth — this round's DB validation reuses
 * {@code FinTagApValidator}/the existing changeset engine, neither of which asserts cheque-book
 * detail), so this class carries only what {@code ApVoucherAdd
 * #setPaymentInformationForExistingRow} + {@code ChequeRegisterPaymentInformationPopup
 * #getLastObservedPreviousInstrumentNo()}'s before/after cross-check actually needs. Extend with
 * bank-match/bookcode fields later if a live run's own DB validation calls for it — not guessed
 * ahead of that evidence.
 */
@Getter
@Builder
public class ApVoucherPaymentInfoEditTarget {

    /** The row's {@code LineNo} the payment info belongs to. */
    private final String lineNo;

    /** Pre-edit payment info, read from the claimed voucher's own sub-ledger row. */
    private final PaymentInformation beforePayment;

    /** New payment info to apply via {@code ApVoucherAdd#setPaymentInformationForExistingRow}
     *  against the already-open Edit form. */
    private final PaymentInformation newPayment;
}
