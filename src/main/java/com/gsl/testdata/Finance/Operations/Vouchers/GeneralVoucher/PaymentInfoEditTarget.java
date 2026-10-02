package com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher;

import com.gsl.utils.cheque.ChequeInstrumentResolver.ChequeBankMatch;
import lombok.Builder;
import lombok.Getter;

/**
 * Identifies the before/after {@link PaymentInformation} pair a Payment-Info Edit scenario
 * targets — the payment-info analogue of {@link DetailLineEditTarget}. Unlike that class there is
 * only one shape (change the Cheque instrument number on the voucher's single Cash/Bank
 * counter-leg row) — no {@code EditShape}-style enum is needed here.
 *
 * <p>{@link #beforePayment} is read straight off the claimed voucher's first {@code finvchdet}
 * row at data-source-build time (see {@code DbGeneralVoucherEditDataSource#buildPaymentEditTarget}),
 * the same "row 1 is the single counter-leg" convention {@link PaymentInformation}'s own Javadoc
 * documents. {@link #newPayment} is also assigned to {@code MergedFinanceGeneralVoucherData
 * #cashBankPaymentInfo} by that same data source, so the shared Extent report header (see
 * {@code GeneralVoucherExtentDescriptionLogger#appendExtraHeaderRows}) renders the post-edit
 * instrument number/bank-match with no special-casing there.
 */
@Getter
@Builder
public class PaymentInfoEditTarget {

    /** Pre-edit payment info, read from the claimed voucher's first {@code finvchdet} row
     *  (payment_mode/chqno/chqdt/drawnon). Never {@code null} for this scenario — {@code claimOne}
     *  only ever claims a candidate whose first row is already Cheque-mode with a non-null chqno. */
    private final PaymentInformation beforePayment;

    /** New payment info to apply via {@code GeneralVoucherAdd#handlePaymentInformation} against
     *  the already-open Edit form. */
    private final PaymentInformation newPayment;

    /** The resolver's outcome for {@link #beforePayment}'s chqno — kept for report/audit symmetry
     *  with {@link #newBankMatch}; not independently re-resolved (the claimed row is guaranteed
     *  Cheque-mode by {@code claimOne}'s filter, so this is set from that guarantee rather than a
     *  fresh DB lookup). */
    private final ChequeBankMatch beforeBankMatch;

    /** The resolver's outcome for {@link #newPayment}'s chqno — same value assigned to
     *  {@code MergedFinanceGeneralVoucherData#actualChequeBankMatch}. */
    private final ChequeBankMatch newBankMatch;

    /** The resolved new cheque's own {@code finchqdet.bookcode} ({@code
     *  ChequeInstrumentResolver.ChequeResolution#bookcode()}) — {@code null} for {@code
     *  CHEQUE_TO_NEFT} (no cheque-book concept) or an UNKNOWN resolution. Deliberately separate
     *  from {@link #newBankMatch}: two cheques can share the same GL/bank account (same {@code
     *  bankMatch}) yet come from different cheque BOOKS — see {@code ChequeResolution#bookcode()}'s
     *  own Javadoc — so {@code GeneralVoucherEditChangeSets#paymentInfoEdit}'s {@code bookcode}
     *  assertion compares this against the before row's actual {@code bookcode} directly, rather
     *  than inferring "same book" from "same bank" (a confirmed-wrong assumption, see reports/runs/
     *  20260916_160435, FIN-GV-EDIT-PAYMENT-01). */
    private final Long newBookcode;

    /** Which of the three Payment-Info edit shapes this target represents — {@code
     *  SAME_MODE_NEW_INSTRUMENT} (the original shape: stays Cheque, only the instrument no.
     *  changes) by default, so every target built before this field existed needs no change. */
    @Builder.Default
    private final PaymentModeShape shape = PaymentModeShape.SAME_MODE_NEW_INSTRUMENT;

    /** The three Payment-Info edit shapes. Unlike {@link DetailLineEditTarget.EditShape}, this
     *  doesn't need a dedicated before/after data shape per shape — {@link #beforePayment}/{@link
     *  #newPayment} already carry their own {@code PaymentSelectionType} independently, so a
     *  mode-change is just "before and after happen to differ." */
    public enum PaymentModeShape {
        /** Stays Cheque; only the instrument number changes to a fresh known/same-bank one. */
        SAME_MODE_NEW_INSTRUMENT,
        /** Changes from Cheque to NEFT/RTGS — {@link #newPayment}'s instrument no. becomes a
         *  synthetic NEFT reference (see {@code PaymentReferenceGenerator#generateNEFT()}),
         *  mirroring {@code GeneralVoucherTemplateLoader}'s own NEFT_RTGS construction; {@link
         *  #newBankMatch} is {@code null} (NEFT has no cheque-book concept). */
        CHEQUE_TO_NEFT,
        /** Changes from NEFT/RTGS to Cheque — {@link #newPayment}'s instrument no. is resolved
         *  via {@code ChequeInstrumentResolver#resolveKnownSameBank}, same as {@code
         *  SAME_MODE_NEW_INSTRUMENT}'s "after" side. */
        NEFT_TO_CHEQUE
    }
}
