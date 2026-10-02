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
@Builder(toBuilder = true)
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

    /**
     * When {@code true} (Type3 shapes only), restricts GL candidate selection in
     * {@code DbGeneralVoucherDataSource.loadFreshForScenario} to GLs with MORE THAN ONE
     * eligible {@code finsl_gl} row — i.e. genuinely ambiguous Sub Ledger selection, which the
     * app cannot auto-pick. Used only by the General Voucher negative scenario that exercises
     * "Sub Ledger required" (clicking Site with Sub Ledger blank); every positive combination
     * leaves this {@code false} (default) and accepts any single usable Sub Ledger, whether or
     * not the app would have auto-picked it.
     */
    @Builder.Default
    private final boolean requireAmbiguousSubLedger = false;

    /**
     * Round 16 — when {@code true} (Type2 shapes only, today), restricts GL candidate selection
     * in {@code DbGeneralVoucherDataSource.loadFreshForScenario} to GLs with at least one
     * {@code fingl_site}-linked, user-assigned site whose OWN {@code admou_code} DIFFERS from the
     * voucher's resolved Owner Site — i.e. deliberately picks a site the app's Allocate Sites
     * picker cannot show (confirmed live, Round 15: "S&CM"/OU=3 vs. Owner Site "Hena
     * Embroidery"/OU=1). Used only by the {@code NEG_SITE_ALLOCATION_CROSS_OU} negative scenario;
     * every positive combination leaves this {@code false} (default) and — per Round 15's own fix
     * — only ever resolves SAME-OU sites.
     */
    @Builder.Default
    private final boolean requireCrossOuSite = false;

    /**
     * Round 20 — carries the known/unknown-cheque decision {@code GeneralVoucherFixtureFactory
     * .assignKnownChequeFlags} already made on the originating {@link GeneralVoucherVariant},
     * across the placeholder/live-resolution boundary (Phase 1 builds a placeholder carrying only
     * these options; Phase 2's {@code DbGeneralVoucherDataSource.loadFreshForScenario} rebuilds a
     * fresh {@code GeneralVoucherVariant} from these options alone, not the original object — this
     * field is what lets that rebuild carry the decision through). Meaningless for any {@link
     * #paymentVariant} other than {@code CHEQUE}.
     */
    @Builder.Default
    private final boolean knownCheque = false;

    /** Round 6 — mirrors {@link GeneralVoucherVariant#isMultiRowPayment()}; carried across the
     *  placeholder/live-resolution boundary exactly like {@link #knownCheque}. */
    @Builder.Default
    private final boolean multiRowPayment = false;

    /** Round 6 — mirrors {@link GeneralVoucherVariant#getRowPayments()}; {@code null} = legacy single payment. */
    private final java.util.List<GeneralVoucherPaymentVariant> rowPayments;

    /** Round 6 — mirrors {@link GeneralVoucherVariant#getRowKnownCheque()}. */
    private final java.util.List<Boolean> rowKnownCheque;

    /** Payment variant of grid row {@code rowIndex} — see {@link GeneralVoucherVariant#paymentForRow(int)}. */
    public GeneralVoucherPaymentVariant paymentForRow(int rowIndex) {
        if (rowPayments != null) return rowIndex < rowPayments.size() ? rowPayments.get(rowIndex) : GeneralVoucherPaymentVariant.NONE;
        return rowIndex == 0 ? paymentVariant : GeneralVoucherPaymentVariant.NONE;
    }

    /** Known-cheque decision of grid row {@code rowIndex} — see {@link GeneralVoucherVariant#knownChequeForRow(int)}. */
    public boolean knownChequeForRow(int rowIndex) {
        if (rowKnownCheque != null) return rowIndex < rowKnownCheque.size() && Boolean.TRUE.equals(rowKnownCheque.get(rowIndex));
        return rowIndex == 0 && knownCheque;
    }

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
     * Builds and validates a negative/boundary scenario's fixture options — 1 GL/1 Site, with
     * an explicit {@link #requireAmbiguousSubLedger} choice (see its Javadoc). Used by
     * {@code CreateGeneralVoucherNegativeBoundary}'s {@code NEG_SUBLEDGER_REQUIRED} scenario;
     * every other caller uses the 5-arg {@link #of(GlAccountType, GlLineType, boolean, boolean,
     * GeneralVoucherPaymentVariant)} overload, which defaults this to {@code false}.
     */
    public static GeneralVoucherFixtureOptions of(GlAccountType accountType, GlLineType glLineType,
                                                   boolean debitSide, boolean reversal,
                                                   GeneralVoucherPaymentVariant paymentVariant,
                                                   boolean requireAmbiguousSubLedger) {
        GeneralVoucherFixtureOptions opts = GeneralVoucherFixtureOptions.builder()
                .accountType(accountType)
                .glLineType(glLineType)
                .debitSide(debitSide)
                .reversal(reversal)
                .paymentVariant(paymentVariant)
                .requireAmbiguousSubLedger(requireAmbiguousSubLedger)
                .build();
        opts.validateSideAllowed();
        return opts;
    }

    /**
     * Round 16 — builds and validates the {@code NEG_SITE_ALLOCATION_CROSS_OU} negative
     * scenario's fixture options (1 GL/1 Site, {@link #requireCrossOuSite} forced {@code true}).
     * Named distinctly (not another {@code of(...)} overload) since a 7th plain {@code boolean}
     * parameter alongside {@link #requireAmbiguousSubLedger}'s existing 6-arg overload would be
     * easy to transpose by accident at a call site.
     */
    public static GeneralVoucherFixtureOptions ofCrossOuSite(GlAccountType accountType, GlLineType glLineType,
                                                              boolean debitSide, boolean reversal,
                                                              GeneralVoucherPaymentVariant paymentVariant) {
        GeneralVoucherFixtureOptions opts = GeneralVoucherFixtureOptions.builder()
                .accountType(accountType)
                .glLineType(glLineType)
                .debitSide(debitSide)
                .reversal(reversal)
                .paymentVariant(paymentVariant)
                .requireCrossOuSite(true)
                .build();
        opts.validateSideAllowed();
        return opts;
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
