package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.enums.CashBankType;
import com.gsl.enums.Finance.Operations.APVoucher.TdsApplicableAmountBehaviour;
import com.gsl.enums.Finance.Operations.APVoucher.TdsNatureMode;
import lombok.Builder;
import lombok.Getter;

/**
 * Configurable DB query parameters for the AP Voucher fixture factory.
 *
 * <p>Controls how many sub-ledger rows are fetched and (for document-payment scenarios)
 * how many documents per sub-ledger are fetched from the database during Phase 2
 * lazy loading.
 *
 * <p>Use the dedicated factory methods for the most common configurations:
 * <pre>
 *   // Advance payment (no pending docs needed)
 *   ApVoucherFixtureOptions opts = ApVoucherFixtureOptions.forAdvance(CashBankType.BANK);
 *
 *   // Advance with TDS
 *   ApVoucherFixtureOptions opts = ApVoucherFixtureOptions.forAdvanceWithTds(CashBankType.BANK);
 *
 *   // Document payment
 *   ApVoucherFixtureOptions opts = ApVoucherFixtureOptions.forCashBankTypeAndClsCode(CashBankType.BANK, clsCode);
 * </pre>
 *
 * <p>Override via {@link #builder()} when a test needs more rows:
 * <pre>
 *   ApVoucherFixtureOptions opts = ApVoucherFixtureOptions.builder()
 *       .subLedgerCount(2)
 *       .documentCount(3)
 *       .glType(CashBankType.BANK)
 *       .build();
 * </pre>
 */
@Getter
@Builder
public class ApVoucherFixtureOptions {

    /** Maximum number of vendor sub-ledger rows to fetch from finsl / v_pending_adj. */
    private final int subLedgerCount;

    /**
     * Maximum number of documents per sub-ledger to fetch from v_pending_adj.
     * {@code 0} for Advance scenarios — no pending documents are needed.
     */
    private final int documentCount;

    /**
     * Target GL type — {@link CashBankType#CASH} or {@link CashBankType#BANK}.
     * Drives which Cash/Bank GL is picked from the DB and enforces payment-mode rules.
     * Defaults to {@link CashBankType#BANK} when not explicitly set.
     */
    @Builder.Default
    private final CashBankType glType = CashBankType.BANK;

    /**
     * {@code admcls.clscode} filter for DOC_NO_ADJUSTMENT, DOC_FIFO, DOC_REF_SITEWISE_FIFO,
     * and DOC_SELECT_DOCUMENT placeholders.
     * {@code 0} (default) = no filter — any vendor SL is accepted.
     * Positive value = restrict SL lookup to that specific classification code.
     *
     * <p>At test-execution time {@code DbApVoucherDataSource.loadFreshForScenario} routes to
     * {@code AdmClsQuery.findSlNamesWithPendingAPByClsCode} when this is {@code > 0},
     * otherwise falls back to {@code PendingAdjQuery.findSlNamesWithPendingAP}.
     */
    @Builder.Default
    private final long slClsCode = 0L;

    /**
     * When {@code true}, the SL lookup restricts to TDS-applicable vendor sub-ledgers
     * (e.g. {@code finsl.tds_app = 'Y'}).
     * Used for {@link ApVoucherScenarioType#ADVANCE_WITH_TDS} scenarios.
     */
    @Builder.Default
    private final boolean tdsApplicable = false;

    /**
     * Optional document-type filter applied to the pending-document query.
     * When non-null, only documents matching {@code v_pending_adj.entype = docTypeFilter}
     * are fetched. Examples: {@code "INV"} (Invoice), {@code "DN"} (Debit Note).
     * {@code null} (default) = no filter — all document types are accepted.
     *
     * <p>Used by Phase-3 variants that target specific document types.
     */
    private final String docTypeFilter;

    /**
     * Which TDS Applicable Amount behaviour to exercise for {@code ADVANCE_WITH_TDS} scenarios.
     * Defaults to {@link TdsApplicableAmountBehaviour#GROSS_EQUALS_TDS} (the smoke baseline).
     *
     * <p>Passed through {@link com.gsl.utils.dataprovider.fixture.LiveDbRefs} into
     * {@link com.gsl.utils.dataprovider.fixture.finance.ApVoucherTemplateLoader#buildTdsRows}
     * so that the generated {@link com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.TdsAndNetPayable}
     * rows carry the correct behaviour name for execution-time dispatch.
     */
    @Builder.Default
    private final TdsApplicableAmountBehaviour tdsBehaviour = TdsApplicableAmountBehaviour.GROSS_EQUALS_TDS;

    /**
     * Controls which vendor TDS-nature-tagging situation to target for
     * {@code ADVANCE_WITH_TDS} regression scenarios.
     *
     * <p>Drives two things:
     * <ol>
     *   <li>Vendor-selection SQL — a subquery on {@code fintds_exception} narrows
     *       {@code findVendorSlsForAdvance()} to only vendors matching the scenario.</li>
     *   <li>TDS name resolution — passed to
     *       {@link com.gsl.database.query.TdsNameQuery#resolve} as context.</li>
     * </ol>
     *
     * <p>{@code null} (default) = no nature-count filter (smoke baseline — any
     * {@code tds_app='Y'} vendor is accepted).
     */
    private final TdsNatureMode tdsNatureMode;

    /**
     * Preferred TDS category used when {@code tdsNatureMode = MULTI_NATURE_BY_TYPE}.
     * {@code 'S'} = Services, {@code 'G'} = Goods.
     * Passed to {@link com.gsl.database.query.TdsNameQuery#resolve} to filter
     * candidates from {@code fintdstype} when multiple natures are tagged.
     * {@code null} for all other modes.
     */
    private final String preferredTdsType;

    // ─── Factory methods ──────────────────────────────────────────────────────

    /**
     * Returns a default instance (Bank GL, 1 SL, default doc count, no TDS, no cls filter).
     * Use as the starting point when no specific configuration is required.
     */
    public static ApVoucherFixtureOptions defaults() {
        int floor = com.gsl.config.ConfigFactory.getWaitConfig().docSelectorDocumentCountDefault();
        return ApVoucherFixtureOptions.builder()
                .subLedgerCount(1)
                .documentCount(Math.max(1, floor))
                .build();
    }

    /**
     * Returns an instance for an Advance Payment scenario (no pending documents).
     * {@code documentCount} is set to {@code 0} — the SL lookup does not query
     * {@code v_pending_adj} at all; only {@code finsl} is queried for a usable vendor SL.
     *
     * @param glType the target GL type (CASH or BANK)
     */
    public static ApVoucherFixtureOptions forAdvance(CashBankType glType) {
        return ApVoucherFixtureOptions.builder()
                .subLedgerCount(1)
                .documentCount(0)
                .glType(glType)
                .tdsApplicable(false)
                .build();
    }

    /**
     * Returns an instance for an Advance Payment with TDS scenario.
     * Restricts the SL lookup to TDS-applicable vendor sub-ledgers.
     * Defaults to {@link TdsApplicableAmountBehaviour#GROSS_EQUALS_TDS}.
     *
     * @param glType the target GL type (CASH or BANK)
     */
    public static ApVoucherFixtureOptions forAdvanceWithTds(CashBankType glType) {
        return ApVoucherFixtureOptions.builder()
                .subLedgerCount(1)
                .documentCount(0)
                .glType(glType)
                .tdsApplicable(true)
                .build();
    }

    /**
     * Returns an instance for an Advance Payment with TDS scenario with an explicit
     * TDS Applicable Amount behaviour to exercise.
     *
     * <p>Used by {@link com.gsl.utils.dataprovider.source.finance.DbApVoucherDataSource}
     * to emit separate placeholders for each execution group:
     * <ul>
     *   <li>MANDATORY       → {@link TdsApplicableAmountBehaviour#GROSS_EQUALS_TDS}</li>
     *   <li>REGRESSION_PHASE1 → {@link TdsApplicableAmountBehaviour#PARTIAL_EDIT}</li>
     *   <li>REGRESSION_PHASE2 → {@link TdsApplicableAmountBehaviour#CLEAR_ERROR_THEN_PARTIAL_EDIT}</li>
     * </ul>
     *
     * @param glType    the target GL type (CASH or BANK)
     * @param behaviour which TDS Applicable Amount scenario to exercise
     */
    public static ApVoucherFixtureOptions forAdvanceWithTds(CashBankType glType,
                                                             TdsApplicableAmountBehaviour behaviour) {
        return ApVoucherFixtureOptions.builder()
                .subLedgerCount(1)
                .documentCount(0)
                .glType(glType)
                .tdsApplicable(true)
                .tdsBehaviour(behaviour)
                .build();
    }

    /**
     * Returns an instance for a Document Payment scenario with a specific GL type.
     * Used for Cash GL placeholders that do not require SL-classification filtering.
     *
     * @param type the target GL type (CASH or BANK)
     */
    public static ApVoucherFixtureOptions forCashBankType(CashBankType type) {
        int floor = com.gsl.config.ConfigFactory.getWaitConfig().docSelectorDocumentCountDefault();
        return ApVoucherFixtureOptions.builder()
                .subLedgerCount(1)
                .documentCount(Math.max(1, floor))
                .glType(type)
                .build();
    }

    /**
     * Returns an instance for a Document Payment scenario with a specific GL type and
     * SL classification code.
     * Used for Bank GL placeholders that scope the SL lookup to a particular
     * system or user-defined classification type.
     *
     * @param type    the target GL type (CASH or BANK)
     * @param clsCode admcls.clscode to filter pending vendor SLs (0 = no filter)
     */
    public static ApVoucherFixtureOptions forCashBankTypeAndClsCode(CashBankType type, long clsCode) {
        int floor = com.gsl.config.ConfigFactory.getWaitConfig().docSelectorDocumentCountDefault();
        return ApVoucherFixtureOptions.builder()
                .subLedgerCount(1)
                .documentCount(Math.max(1, floor))
                .glType(type)
                .slClsCode(clsCode)
                .build();
    }

    // ─── TDS Nature Regression factory methods ────────────────────────────────

    /**
     * Regression 1: vendor with {@code tds_app='Y'} and <b>no</b> natures tagged
     * in {@code fintds_exception} (53 such vendors exist in DB).
     * All active natures in {@code fintdstype} are available as candidates.
     *
     * @param glType target GL type (CASH or BANK)
     */
    public static ApVoucherFixtureOptions forAdvanceWithTdsNoNature(CashBankType glType) {
        return builder()
                .subLedgerCount(1)
                .documentCount(0)
                .glType(glType)
                .tdsApplicable(true)
                .tdsNatureMode(TdsNatureMode.ANY_NATURE)
                .build();
    }

    /**
     * Regression 2: vendor with {@code tds_app='Y'} and <b>exactly 1</b> nature tagged
     * in {@code fintds_exception} (e.g. AARUCHI FASHIONS, slcode=193, tdstypecode=3).
     * Only that single tagged nature is available.
     *
     * @param glType target GL type (CASH or BANK)
     */
    public static ApVoucherFixtureOptions forAdvanceWithTdsSingleNature(CashBankType glType) {
        return builder()
                .subLedgerCount(1)
                .documentCount(0)
                .glType(glType)
                .tdsApplicable(true)
                .tdsNatureMode(TdsNatureMode.SINGLE_NATURE)
                .build();
    }

    /**
     * Returns an instance for a multi-sub-ledger Advance Payment scenario.
     * Requests 2 SL rows from the DB so the template can produce 2 lines with
     * independent payment information (e.g. NEFT/RTGS + Cheque).
     *
     * @param glType target GL type (CASH or BANK)
     */
    public static ApVoucherFixtureOptions forAdvanceMultiSl(CashBankType glType) {
        return builder()
                .subLedgerCount(2)
                .documentCount(0)
                .glType(glType)
                .tdsApplicable(false)
                .build();
    }

    /**
     * Regression 3: vendor with {@code tds_app='Y'} and <b>multiple</b> natures tagged
     * in {@code fintds_exception} — pick any nature by {@code slcode % candidates.size()}.
     *
     * <p><em>Note:</em> No vendors with COUNT &gt; 1 exist in the current DB.
     * Use when data becomes available.
     *
     * @param glType target GL type (CASH or BANK)
     */
    public static ApVoucherFixtureOptions forAdvanceWithTdsMultiNatureAny(CashBankType glType) {
        return builder()
                .subLedgerCount(1)
                .documentCount(0)
                .glType(glType)
                .tdsApplicable(true)
                .tdsNatureMode(TdsNatureMode.MULTI_NATURE_ANY)
                .build();
    }

    /**
     * Regression 4: vendor with {@code tds_app='Y'}, multiple natures tagged, and at least
     * one matching {@code preferredTdsType} ({@code 'S'} = Services, {@code 'G'} = Goods).
     *
     * <p><em>Note:</em> No vendors with COUNT &gt; 1 exist in the current DB.
     * Use when data becomes available.
     *
     * @param glType           target GL type (CASH or BANK)
     * @param preferredTdsType {@code 'S'} or {@code 'G'} — preferred TDS category
     */
    public static ApVoucherFixtureOptions forAdvanceWithTdsMultiNatureByType(
            CashBankType glType, String preferredTdsType) {
        return builder()
                .subLedgerCount(1)
                .documentCount(0)
                .glType(glType)
                .tdsApplicable(true)
                .tdsNatureMode(TdsNatureMode.MULTI_NATURE_BY_TYPE)
                .preferredTdsType(preferredTdsType)
                .build();
    }
}
