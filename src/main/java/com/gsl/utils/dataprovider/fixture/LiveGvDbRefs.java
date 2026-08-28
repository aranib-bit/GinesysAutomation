package com.gsl.utils.dataprovider.fixture;

import com.gsl.enums.CashBankType;
import com.gsl.model.finance.FinGl;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherVariant;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Immutable carrier of the live DB reference values fetched for one General Voucher
 * Expense/Income Detail combination — the GV analogue of {@link ApVoucherLiveDbRefs}/
 * {@link ArVoucherLiveDbRefs}, kept as its own small class rather than extending either of
 * those (GV's dimensions don't overlap with AP/AR's TDS/pending-document/classification
 * fields). AP and AR were themselves later split from a single shared type into these two,
 * for the same reason — see their own Javadoc.
 *
 * <p><b>Multi-GL / multi-Site (GL-count × Site-count breakup):</b> {@link #resolvedRows} carries
 * one entry per distinct GL selected for this combination — {@code glCount} entries (1 for the
 * mandatory baseline, {@literal >}1 for the {@code MULTI_GL}/{@code MULTI_GL_N_SITES} regression
 * variants — see {@link GeneralVoucherVariant#getGlCount()}). Each entry's own
 * {@link ResolvedGlRow#getSiteNames()} carries that GL's {@code siteCount} distinct sites,
 * mirroring AR's unique-SL-count × docs-per-SL two-axis breakup
 * ({@code SelectDocumentVariant#getSlCount()}/{@code #getDocsPerSl()}), renamed for GV's domain
 * (GLs instead of Sub Ledgers, Sites instead of Documents).
 */
@Value
@Builder
public class LiveGvDbRefs {

    /** First usable OS-OO-CM owner site the logged-in user is assigned to (admsite). */
    String ownerSiteName;

    /** Active Cash or Bank GL scoped to the owner site (fingl), matching {@link #cashBankType}. */
    String cashBankName;

    /** {@link CashBankType#CASH} or {@link CashBankType#BANK} — derived from the variant's payment info. */
    CashBankType cashBankType;

    /**
     * One entry per distinct GL row to build as a separate {@code ExpenseIncomeDetail} line —
     * see class Javadoc. Always non-empty; has exactly 1 entry for every combination except the
     * {@code MULTI_GL}/{@code MULTI_GL_N_SITES} regression variants.
     */
    List<ResolvedGlRow> resolvedRows;

    /** The originating combination — carried through for test-case naming/tagging. */
    GeneralVoucherVariant variant;

    /**
     * One resolved GL and its allocated Sites/Sub Ledger — one instance per
     * {@code ExpenseIncomeDetail} row that {@link com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherTemplateLoader}
     * builds for a combination.
     */
    @Value
    @Builder
    public static class ResolvedGlRow {

        /** The Expense/Income Detail GL resolved for this row (type + Site/SubLedger applicability). */
        FinGl gl;

        /**
         * Resolved allocation Site names for this row — empty for {@code TYPE1_NONE}, 1 entry for
         * the mandatory baseline, N entries for the {@code N_SITES}/{@code MULTI_GL_N_SITES}
         * regression variants (the row's amount is apportioned across all of them).
         */
        List<String> siteNames;

        /** Resolved Sub Ledger name — non-null only for {@code TYPE3_SITE_AND_SUBLEDGER}. */
        String subLedgerName;

        /** Resolved Sub Ledger id — non-null only for {@code TYPE3_SITE_AND_SUBLEDGER}. */
        String subLedgerId;
    }
}
