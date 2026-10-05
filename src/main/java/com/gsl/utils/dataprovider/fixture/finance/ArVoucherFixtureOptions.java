package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.enums.CashBankType;
import lombok.Builder;
import lombok.Getter;

/**
 * Configurable DB query limits for the AR Voucher fixture factory.
 *
 * <p>Controls how many sub-ledger rows and how many documents per sub-ledger
 * are fetched from the database during Phase 2 lazy loading.
 *
 * <p>Use {@link #defaults()} for the initial 1-SL / 1-document combination.
 * Override via {@link #builder()} when a test needs more rows:
 * <pre>
 *   ArVoucherFixtureOptions opts = ArVoucherFixtureOptions.builder()
 *       .subLedgerCount(2)
 *       .documentCount(3)
 *       .build();
 * </pre>
 *
 * <p>For SELECT_DOCUMENT sub-scenarios, {@link #selectDocVariant} carries the full
 * parameterisation through the placeholder lifecycle so that
 * {@link com.gsl.utils.dataprovider.source.finance.DbArVoucherDataSource#loadFreshForScenario}
 * can dispatch to the correct template overload.
 */
@Getter
@Builder
public class ArVoucherFixtureOptions {

    /** Maximum number of sub-ledger rows to fetch from v_pending_adj. */
    private final int subLedgerCount;

    /** Maximum number of documents per sub-ledger to fetch from v_pending_adj. */
    private final int documentCount;

    /**
     * The SELECT_DOCUMENT variant that drives this fixture run.
     * {@code null} for FIFO and Reference-Sitewise-FIFO scenarios.
     */
    private final SelectDocumentVariant selectDocVariant;

    /**
     * Target GL type for FIFO and Reference-Sitewise-FIFO placeholders.
     * {@code null} when the fixture uses a {@link SelectDocumentVariant} (the variant carries its
     * own {@link SelectDocumentVariant#getGlType()}), or when no explicit GL type is needed.
     */
    private final CashBankType glType;

    /**
     * {@code admcls.clscode} filter for FIFO, REF_SITEWISE, and DOC_NO_ADJUSTMENT placeholders.
     * {@code 0} (default) = no filter — any SL with pending AR is accepted.
     * Positive value = restrict SL lookup to that specific classification code.
     *
     * <p>At test-execution time {@code DbArVoucherDataSource.loadFreshForScenario} routes to
     * {@code AdmClsQuery.findSlNamesWithPendingARByClsCode} when this is {@code > 0},
     * otherwise falls back to {@code PendingAdjQuery.findSlNamesWithPendingAR}.
     */
    @Builder.Default
    private final long slClsCode = 0L;

    /**
     * Which kind of document scheme the data source should resolve for this scenario — AUTO for every
     * normal scenario; EXHAUSTED for the "Save on a full scheme" negative scenario.
     */
    @Builder.Default
    private final com.gsl.utils.dataprovider.source.finance.common.DocSchemeMode docSchemeMode =
            com.gsl.utils.dataprovider.source.finance.common.DocSchemeMode.AUTO;

    /**
     * Optional document-type filter applied to the pending-document query.
     * When non-null, only documents matching {@code v_pending_adj.entype = docTypeFilter}
     * are fetched. Examples: {@code "INV"} (Invoice), {@code "DN"} (Debit Note).
     * {@code null} (default) = no filter — all document types are accepted.
     *
     * <p>Used by Phase-3 variant REG-09 (specific doc type). Requires the pending-document
     * query in {@code ArVoucherTemplateLoader} to apply {@code AND entype = :docTypeFilter}
     * when this field is non-null.
     */
    private final String docTypeFilter;

    /**
     * Returns an instance with the default limits.
     *
     * <p>{@code documentCount} reads {@code doc.selector.documentcount.default} from
     * config (default {@code 3}). A hard-coded {@code 1} is unsafe under parallel —
     * multiple threads compete for the only doc per SL, and the loser sees
     * "no sub-ledger with pending AR" even though docs exist.
     */
    public static ArVoucherFixtureOptions defaults() {
        int floor = com.gsl.config.ConfigFactory.getWaitConfig().docSelectorDocumentCountDefault();
        return ArVoucherFixtureOptions.builder()
                .subLedgerCount(1)
                .documentCount(Math.max(1, floor))
                .build();
    }

    /**
     * Returns an instance configured for a specific GL type (used for FIFO / REF_SITEWISE_FIFO
     * Cash GL placeholders that do not require SL-classification filtering).
     */
    /** Options for the "Save on a full document scheme" negative scenario: resolves the scheme in EXHAUSTED mode. */
    public static ArVoucherFixtureOptions forExhaustedScheme(CashBankType type) {
        int floor = com.gsl.config.ConfigFactory.getWaitConfig().docSelectorDocumentCountDefault();
        return ArVoucherFixtureOptions.builder()
                .subLedgerCount(1)
                .documentCount(Math.max(1, floor))
                .glType(type)
                .docSchemeMode(com.gsl.utils.dataprovider.source.finance.common.DocSchemeMode.EXHAUSTED)
                .build();
    }

    public static ArVoucherFixtureOptions forCashBankType(CashBankType type) {
        int floor = com.gsl.config.ConfigFactory.getWaitConfig().docSelectorDocumentCountDefault();
        return ArVoucherFixtureOptions.builder()
                .subLedgerCount(1)
                .documentCount(Math.max(1, floor))
                .glType(type)
                .build();
    }

    /**
     * Returns an instance configured for a specific GL type and SL classification code.
     * Used for FIFO, REF_SITEWISE, and DOC_NO_ADJUSTMENT Bank GL placeholders that scope
     * the SL lookup to a particular system or user-defined classification type.
     *
     * @param type     the target GL type (CASH or BANK)
     * @param clsCode  admcls.clscode to filter pending SLs (0 = no filter)
     */
    public static ArVoucherFixtureOptions forCashBankTypeAndClsCode(CashBankType type, long clsCode) {
        int floor = com.gsl.config.ConfigFactory.getWaitConfig().docSelectorDocumentCountDefault();
        return ArVoucherFixtureOptions.builder()
                .subLedgerCount(1)
                .documentCount(Math.max(1, floor))
                .glType(type)
                .slClsCode(clsCode)
                .build();
    }
}
