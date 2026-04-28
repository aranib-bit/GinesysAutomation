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
     * {@code admcls.clscode} filter for FIFO, REF_SITEWISE, and NO_ADJUSTMENT placeholders.
     * {@code 0} (default) = no filter — any SL with pending AR is accepted.
     * Positive value = restrict SL lookup to that specific classification code.
     *
     * <p>At test-execution time {@code DbArVoucherDataSource.loadFreshForScenario} routes to
     * {@code AdmClsQuery.findSlNamesWithPendingARByClsCode} when this is {@code > 0},
     * otherwise falls back to {@code PendingAdjQuery.findSlNamesWithPendingAR}.
     */
    @Builder.Default
    private final long slClsCode = 0L;

    /** Returns an instance with the default limits (1 SL, 1 document, no variant, no GL type). */
    public static ArVoucherFixtureOptions defaults() {
        return ArVoucherFixtureOptions.builder()
                .subLedgerCount(1)
                .documentCount(1)
                .build();
    }

    /**
     * Returns an instance configured for a specific GL type (used for FIFO / REF_SITEWISE_FIFO
     * Cash GL placeholders that do not require SL-classification filtering).
     */
    public static ArVoucherFixtureOptions forCashBankType(CashBankType type) {
        return ArVoucherFixtureOptions.builder()
                .subLedgerCount(1)
                .documentCount(1)
                .glType(type)
                .build();
    }

    /**
     * Returns an instance configured for a specific GL type and SL classification code.
     * Used for FIFO, REF_SITEWISE, and NO_ADJUSTMENT Bank GL placeholders that scope
     * the SL lookup to a particular system or user-defined classification type.
     *
     * @param type     the target GL type (CASH or BANK)
     * @param clsCode  admcls.clscode to filter pending SLs (0 = no filter)
     */
    public static ArVoucherFixtureOptions forCashBankTypeAndClsCode(CashBankType type, long clsCode) {
        return ArVoucherFixtureOptions.builder()
                .subLedgerCount(1)
                .documentCount(1)
                .glType(type)
                .slClsCode(clsCode)
                .build();
    }
}
