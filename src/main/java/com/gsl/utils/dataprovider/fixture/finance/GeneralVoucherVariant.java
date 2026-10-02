package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType;
import com.gsl.enums.GlAccountType;
import lombok.Builder;
import lombok.Getter;

/**
 * Describes one parameterised combination of the General Voucher Expense/Income Detail
 * matrix — the GV analogue of {@link SelectDocumentVariant}. Each instance maps to one
 * DataSupplier placeholder row. Instances are created by
 * {@link GeneralVoucherCombinations#allCombinations()}.
 */
@Builder(toBuilder = true)
@Getter
public class GeneralVoucherVariant {

    /** Unique short identifier used as the TestNG test-case ID, e.g. {@code "GV_ASSET_T2_CR_REV_N_BC"}. */
    String variantId;

    /** Human-readable name for ExtentReports/TestNG reports. */
    String testCaseName;

    /** GL accounting type — Expense/Income/Asset/Liability. */
    GlAccountType accountType;

    /** Site/SubLedger shape (only the 3 real {@code GlLineType} shapes). */
    GlLineType glLineType;

    /** {@code true} = Debit side, {@code false} = Credit side. */
    boolean debitSide;

    /** {@code true} = Is Reversal = Yes. */
    boolean reversal;

    /** Counter-leg Leg+Payment combination. */
    GeneralVoucherPaymentVariant paymentVariant;

    /** Execution bucket this variant belongs to — see {@link GeneralVoucherExecutionGroup}. */
    GeneralVoucherExecutionGroup executionGroup;

    /**
     * Number of distinct GL records of this variant's (accountType, glLineType) ledger type to
     * build as separate {@code ExpenseIncomeDetail} rows in one voucher — the GV analogue of
     * {@code SelectDocumentVariant#slCount} (unique Sub Ledgers). {@code 1} for the mandatory
     * baseline and every combination not explicitly enumerated with a multi-GL variant.
     */
    @Builder.Default
    int glCount = 1;

    /**
     * Number of distinct Sites each such GL row allocates its amount across — the GV analogue of
     * {@code SelectDocumentVariant#docsPerSl} (documents per SL), renamed since GV has no document
     * concept. Only meaningful for {@code TYPE2_SITE_ONLY}/{@code TYPE3_SITE_AND_SUBLEDGER} shapes;
     * {@code 1} for the mandatory baseline and every combination not explicitly enumerated with a
     * multi-site variant.
     */
    @Builder.Default
    int siteCount = 1;

    /**
     * Optional override for the Instrument/Cheque Date this variant's Payment Information uses
     * (only meaningful for {@link GeneralVoucherPaymentVariant#CHEQUE}/{@code NEFT_RTGS}) —
     * {@code null} (default) keeps the existing behavior of using today's date
     * ({@code LocalDate.now()}). Set this to a future date to exercise a genuinely
     * post-dated-cheque scenario (Instrument Date in the future, independent of Document Date);
     * the app's own accept/reject rule for that case isn't asserted by any combination yet — a
     * future variant can set this field once that rule needs coverage. Not wired into any
     * existing combination — capability-only, per the Round 22 GV plan.
     */
    java.time.LocalDate instrumentDateOverride;

    /**
     * Round 20 — {@code true} when this CHEQUE-payment row should resolve a real, registered
     * cheque number ({@code FinChqDetDao.findFirstUnused}) instead of a synthetic never-registered
     * one ({@code UniqueChequeGenerator.generateUnique()}). Meaningless for any other {@link
     * #paymentVariant}. Assigned by {@code GeneralVoucherFixtureFactory.assignKnownChequeFlags}
     * once per run, over the exact set of CHEQUE rows {@link GeneralVoucherCombinations
     * #allCombinations()} produced — an exact-ratio "controlled sequence, shuffled" assignment
     * (see {@code com.gsl.utils.WeightedSequenceBuilder}), not a per-row random roll. Defaults to
     * {@code false} (unknown) for any row the assignment pass never reaches (e.g. a non-CHEQUE
     * row, where the flag is simply never read).
     */
    @Builder.Default
    boolean knownCheque = false;

    /**
     * Round 6 — {@code true} for a Bank-header voucher whose {@link #glCount} grid rows EACH draw
     * their own payment ({@link #rowPayments}), instead of the legacy single payment on row 1.
     * {@link #paymentVariant} stays {@link GeneralVoucherPaymentVariant#CHEQUE} for these (it only
     * selects the Bank header). The per-row assignment itself is NOT hardcoded: it is made once per
     * run by {@code GeneralVoucherFixtureFactory.assignKnownChequeFlags} from the
     * {@code gv.row.payment.*.weight} config.
     */
    @Builder.Default
    boolean multiRowPayment = false;

    /** Round 6 — payment of grid row {@code i} (index = row order), set only when {@link #multiRowPayment}
     *  and after the factory's assignment pass; {@code null} otherwise (legacy single payment). */
    java.util.List<GeneralVoucherPaymentVariant> rowPayments;

    /** Round 6 — known/unknown decision of grid row {@code i}'s cheque (only meaningful where that
     *  row's payment is {@code CHEQUE}); {@code null} unless {@link #rowPayments} is set. */
    java.util.List<Boolean> rowKnownCheque;

    /** Payment variant of grid row {@code rowIndex}: its assigned one, else (legacy) {@link #paymentVariant}
     *  on row 0 and no payment on any other row. */
    public GeneralVoucherPaymentVariant paymentForRow(int rowIndex) {
        if (rowPayments != null) return rowIndex < rowPayments.size() ? rowPayments.get(rowIndex) : GeneralVoucherPaymentVariant.NONE;
        return rowIndex == 0 ? paymentVariant : GeneralVoucherPaymentVariant.NONE;
    }

    /** Known-cheque decision of grid row {@code rowIndex} (see {@link #rowKnownCheque}; legacy = {@link #knownCheque} on row 0). */
    public boolean knownChequeForRow(int rowIndex) {
        if (rowKnownCheque != null) return rowIndex < rowKnownCheque.size() && Boolean.TRUE.equals(rowKnownCheque.get(rowIndex));
        return rowIndex == 0 && knownCheque;
    }
}
