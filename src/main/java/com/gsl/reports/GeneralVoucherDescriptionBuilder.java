package com.gsl.reports;

import com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherFixtureOptions;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherScenarioType;
import com.gsl.utils.dataprovider.fixture.finance.NegativeValidation;

/**
 * Builds a human-readable English sentence describing the General Voucher test scenario.
 *
 * <p>Unlike {@link ArVoucherDescriptionBuilder}/{@code ApVoucherDescriptionBuilder}, GV has no
 * compact {@code " | "}-token {@code testcasename} convention to parse for its combination matrix
 * — instead {@link #buildCombinationSentence} builds the sentence directly from
 * {@link GeneralVoucherFixtureOptions}, which (unlike {@code expenseIncomeDetailList}) is already
 * populated at placeholder time, before any DB load — so it's available in time to win
 * {@code TestListener}'s title-priority check (sentence beats richName beats the static
 * {@code @Test} description; see {@code DbGeneralVoucherDataSource}'s "Root cause #3").
 */
public final class GeneralVoucherDescriptionBuilder {

    private GeneralVoucherDescriptionBuilder() {}

    /**
     * Returns a descriptive sentence for the given test data, or {@code null} if
     * the data does not contain enough information to build one.
     */
    public static String build(MergedFinanceGeneralVoucherData data) {
        if (data == null) return null;

        if (data.getExpectedValidation() != null) {
            String negDesc = buildNegativeDescription(data.getExpectedValidation());
            if (negDesc != null) return negDesc;
        }

        if (data.getScenarioType() == GeneralVoucherScenarioType.MIXED_TYPES) {
            return "Verify that a General Voucher is created successfully with entries across "
                    + "multiple ledger types (Asset/Liability/Income/Expense, Type1/2/3) in a single voucher.";
        }

        if (data.getFixtureOptions() != null) {
            String sentence = buildCombinationSentence(data.getFixtureOptions());
            if (sentence != null) return sentence;
        }

        if (data.getExpenseIncomeDetailList() == null || data.getExpenseIncomeDetailList().isEmpty())
            return null;

        return "Verify that a General Voucher is created successfully with "
                + data.getExpenseIncomeDetailList().size() + " Expense/Income line(s).";
    }

    /**
     * Builds the combination-matrix sentence from the placeholder-time-available
     * {@code fixtureOptions} — states the ledger type (account type + GL line type), the
     * GL-count/Site-count breakup (see {@code GeneralVoucherCombinations}'s class Javadoc), the
     * debit/credit side, reversal, and payment variant. Mirrors AP/AR's own token-to-sentence
     * phrasing style, e.g. "Verify that an AP Voucher is created successfully using Bank Ledger
     * ... with Select Document Adjustment (Full Amount) and NONE Payment."
     */
    private static String buildCombinationSentence(GeneralVoucherFixtureOptions o) {
        if (o.getAccountType() == null || o.getGlLineType() == null || o.getPaymentVariant() == null) return null;

        String glTypePhrase = switch (o.getGlLineType()) {
            case TYPE1_NONE -> "no Site or Sub Ledger applicability";
            case TYPE2_SITE_ONLY -> "Site allocation only";
            case TYPE3_SITE_AND_SUBLEDGER -> "Site allocation and a Sub Ledger";
        };
        String paymentPhrase = switch (o.getPaymentVariant()) {
            case CHEQUE -> "Cheque payment";
            case NEFT_RTGS -> "NEFT/RTGS payment";
            case NONE -> "no Payment info (instrument-missing warning expected)";
            case CASH -> "Cash payment";
        };

        int glCount = Math.max(1, o.getGlCount());
        int siteCount = Math.max(1, o.getSiteCount());

        String glPhrase = glCount == 1
                ? "a " + o.getAccountType().label() + " GL"
                : glCount + " " + o.getAccountType().label() + " GLs";
        String sitePhrase = o.getGlLineType() == GlLineType.TYPE1_NONE
                ? ""
                : siteCount == 1
                        ? ", allocated to 1 Site"
                        : ", each allocated across " + siteCount + " unique Sites";

        return "Verify that a General Voucher is created successfully using " + glPhrase + " with "
                + glTypePhrase + sitePhrase + ", a " + (o.isDebitSide() ? "Debit" : "Credit") + " line, "
                + (o.isReversal() ? "with Reversal" : "no Reversal") + ", and " + paymentPhrase + ".";
    }

    private static String buildNegativeDescription(NegativeValidation expected) {
        return switch (expected) {
            case GV_SITE_ALLOCATION_PENDING -> "Verify that saving a Site-applicable line with an "
                    + "incomplete site allocation shows the site-allocation-pending validation error.";
            case GV_SUBLEDGER_REQUIRED -> "Verify that clicking Site on a Sub-Ledger-applicable row "
                    + "with no Sub Ledger selected shows the sub-ledger-required validation error.";
            case GV_SITE_NOT_ALLOCATED -> "Verify that saving a Site-applicable line whose Site was "
                    + "never allocated shows the site-not-allocated validation error.";
            default -> null;
        };
    }
}
