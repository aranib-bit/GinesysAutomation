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
    EXPENSE,
    INCOME,
    ASSET,
    LIABILITY,

    // ─── Site/SubLedger shape ─────────────────────────────────────────────────
    TYPE1_NONE,
    TYPE2_SITE_ONLY,
    TYPE3_SITE_AND_SUBLEDGER,

    // ─── Amount side ──────────────────────────────────────────────────────────
    DEBIT_SIDE,
    CREDIT_SIDE,

    // ─── Reversal ─────────────────────────────────────────────────────────────
    REVERSAL_YES,
    REVERSAL_NO,

    // ─── Leg + Payment Info ───────────────────────────────────────────────────
    PAYMENT_CHEQUE,
    PAYMENT_NEFT_RTGS,
    PAYMENT_NONE,
    PAYMENT_CASH
}
