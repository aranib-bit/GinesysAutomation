package com.gsl.utils.dataprovider.fixture.finance;

/**
 * Tag taxonomy for General Voucher Expense/Income Detail combinations — mirrors
 * {@link ApVoucherTag}'s orthogonal-to-{@code ExecutionGroup} role and OR matching
 * semantics, resolved from system property / TestNG XML parameter {@code gv.tag}.
 *
 * <p>Tags are assigned at placeholder-build time by
 * {@code DbGeneralVoucherDataSource.tagsFor(GeneralVoucherVariant)}.
 */
public enum GeneralVoucherTag {

    // ─── GL account type ──────────────────────────────────────────────────────

    /** Variant's GL account type is Expense. */
    EXPENSE,
    /** Variant's GL account type is Income. */
    INCOME,
    /** Variant's GL account type is Asset. */
    ASSET,
    /** Variant's GL account type is Liability. */
    LIABILITY,

    // ─── Site/SubLedger shape ─────────────────────────────────────────────────

    /** Neither Site nor Sub-Ledger applicable on this GL line. */
    TYPE1_NONE,
    /** Site applicable, Sub-Ledger not applicable on this GL line. */
    TYPE2_SITE_ONLY,
    /** Both Site and Sub-Ledger applicable on this GL line. */
    TYPE3_SITE_AND_SUBLEDGER,

    // ─── Amount side ──────────────────────────────────────────────────────────

    /** Variant posts its amount on the Debit side. */
    DEBIT_SIDE,
    /** Variant posts its amount on the Credit side. */
    CREDIT_SIDE,

    // ─── Reversal ─────────────────────────────────────────────────────────────

    /** Variant has {@code IsReversal = Yes}. */
    REVERSAL_YES,
    /** Variant has {@code IsReversal = No}. */
    REVERSAL_NO,

    // ─── Leg + Payment Info ───────────────────────────────────────────────────

    /** Bank counter-leg, Cheque payment mode ({@link GeneralVoucherPaymentVariant#CHEQUE}). */
    PAYMENT_CHEQUE,
    /** Bank counter-leg, NEFT/RTGS payment mode ({@link GeneralVoucherPaymentVariant#NEFT_RTGS}). */
    PAYMENT_NEFT_RTGS,
    /** Bank counter-leg, payment info deliberately skipped ({@link GeneralVoucherPaymentVariant#NONE}). */
    PAYMENT_NONE,
    /** Cash counter-leg ({@link GeneralVoucherPaymentVariant#CASH}). */
    PAYMENT_CASH,
    /** Round 6 — Bank-header voucher whose grid rows each carry their own payment ({@link GeneralVoucherVariant#isMultiRowPayment()}). */
    PAYMENT_MULTI_ROW
}
