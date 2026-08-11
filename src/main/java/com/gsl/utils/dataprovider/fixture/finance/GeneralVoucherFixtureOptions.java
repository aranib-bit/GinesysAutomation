package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.enums.CashBankType;
import com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType;
import com.gsl.enums.GlAccountType;
import lombok.Builder;
import lombok.Getter;

/**
 * Configurable DB query parameters for one General Voucher Expense/Income Detail
 * combination — the GV analogue of {@link ApVoucherFixtureOptions}, scoped to the
 * (type, shape, side, reversal, payment) dimensions {@link GeneralVoucherCombinations}
 * enumerates.
 */
@Getter
@Builder
public class GeneralVoucherFixtureOptions {

    /** GL accounting type — Expense/Income/Asset/Liability. Drives {@code findByTypeAndApplicability}'s {@code :type}. */
    private final GlAccountType accountType;

    /** Site/SubLedger shape — drives {@code slapp}/{@code costapp} query params. */
    private final GlLineType glLineType;

    /** {@code true} = row posts to Expense(Dr)/Asset(Dr)/Liability(Dr); {@code false} = Income(Cr)/Asset(Cr)/Liability(Cr). */
    private final boolean debitSide;

    /** Maps straight to {@code ExpenseIncomeDetail.isReversal} ("Yes"/"No"). */
    private final boolean reversal;

    /** Counter-leg Leg+Payment combination — Bank+Cheque / Bank+NEFT-RTGS / Bank+None / Cash. */
    private final GeneralVoucherPaymentVariant paymentVariant;

    /**
     * Number of distinct GL records of this combination's ledger type to build as separate
     * {@code ExpenseIncomeDetail} rows in one voucher — mirrors {@link GeneralVoucherVariant#getGlCount()}.
     * Defaults to 1 (today's single-row behavior) for callers that don't set it explicitly.
     */
    @Builder.Default
    private final int glCount = 1;

    /**
     * Number of distinct Sites each such GL row allocates its amount across — mirrors
     * {@link GeneralVoucherVariant#getSiteCount()}. Defaults to 1 (today's single-site behavior)
     * for callers that don't set it explicitly.
     */
    @Builder.Default
    private final int siteCount = 1;

    /** Derives the counter-leg GL type from {@link #paymentVariant}. */
    public CashBankType cashBankType() {
        return paymentVariant.cashBankType();
    }

    /**
     * Validates the (accountType, debitSide) pair against the user-confirmed amount-side
     * rule: Expense=Debit-only, Income=Credit-only, Asset/Liability=either.
     *
     * <p>This is a <b>test-data-authoring guard</b>, not a proven app-behavior assertion —
     * see the plan's Verification/Open items on whether the app itself enforces this.
     */
    public void validateSideAllowed() {
        if (accountType == GlAccountType.EXPENSE && !debitSide) {
            throw new IllegalArgumentException("Expense GL must be Debit-only — got Credit side.");
        }
        if (accountType == GlAccountType.INCOME && debitSide) {
            throw new IllegalArgumentException("Income GL must be Credit-only — got Debit side.");
        }
    }

    /** Builds and validates a combination's fixture options in one step — 1 GL/1 Site (today's default). */
    public static GeneralVoucherFixtureOptions of(GlAccountType accountType, GlLineType glLineType,
                                                   boolean debitSide, boolean reversal,
                                                   GeneralVoucherPaymentVariant paymentVariant) {
        return of(accountType, glLineType, debitSide, reversal, paymentVariant, 1, 1);
    }

    /**
     * Builds and validates a combination's fixture options, with explicit GL-count/Site-count
     * (the multi-GL × multi-Site regression dimensions — see {@link GeneralVoucherVariant#getGlCount()}/
     * {@link GeneralVoucherVariant#getSiteCount()}).
     */
    public static GeneralVoucherFixtureOptions of(GlAccountType accountType, GlLineType glLineType,
                                                   boolean debitSide, boolean reversal,
                                                   GeneralVoucherPaymentVariant paymentVariant,
                                                   int glCount, int siteCount) {
        GeneralVoucherFixtureOptions opts = GeneralVoucherFixtureOptions.builder()
                .accountType(accountType)
                .glLineType(glLineType)
                .debitSide(debitSide)
                .reversal(reversal)
                .paymentVariant(paymentVariant)
                .glCount(glCount)
                .siteCount(siteCount)
                .build();
        opts.validateSideAllowed();
        return opts;
    }
}
