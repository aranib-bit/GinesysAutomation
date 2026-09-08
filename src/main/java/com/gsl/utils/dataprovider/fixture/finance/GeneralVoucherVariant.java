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
}
