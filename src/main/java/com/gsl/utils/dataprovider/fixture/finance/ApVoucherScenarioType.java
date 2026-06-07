package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.pages.Finance.Operations.ApVoucherAdd;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.dataprovider.fixture.LiveDbRefs;

import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Each enum constant combines:
 * <ul>
 *   <li>A <b>template loader</b> — {@code Function<LiveDbRefs, MergedFinanceApVoucherData>}
 *       that builds the test data from live DB values.</li>
 *   <li>A <b>behavior supplier</b> — {@code Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>}
 *       that performs the UI interaction and returns true if the voucher was saved.</li>
 *   <li>An <b>onAccount flag</b> — {@code true} for Advance Payment scenarios (On Account = Yes),
 *       {@code false} for Document Payment scenarios (On Account = No).</li>
 * </ul>
 *
 * <p>The {@code Supplier} wrapper ensures lazy evaluation — safe for parallel test execution.
 *
 * <h3>Scenario routing</h3>
 * <ul>
 *   <li>{@code ADVANCE_*} scenarios → {@code createApVoucherAdvancePayment} test method</li>
 *   <li>{@code DOC_*} scenarios     → {@code createApVoucherDocumentPayment} test method</li>
 * </ul>
 */
public enum ApVoucherScenarioType {

    // ─── Advance Payment (On Account = Yes) ──────────────────────────────────

    /**
     * Simple vendor advance with no TDS deduction — mandatory baseline.
     * Queries {@code finsl} for a usable vendor SL; no pending documents needed.
     */
    ADVANCE_NO_TDS(
            ApVoucherTemplateLoader::advanceNoTdsTemplate,
            ApVoucherPredicateFactory.ADVANCE_NO_TDS_BEHAVIOR,
            true
    ),

    /**
     * Vendor advance with TDS deduction — TDS rows are filled after sub-ledger rows.
     * Restricts SL lookup to TDS-applicable vendor sub-ledgers.
     * Smoke baseline: any {@code tds_app='Y'} vendor (no nature-count filter).
     */
    ADVANCE_WITH_TDS(
            ApVoucherTemplateLoader::advanceWithTdsTemplate,
            ApVoucherPredicateFactory.ADVANCE_WITH_TDS_BEHAVIOR,
            true
    ),

    // ─── ADVANCE_WITH_TDS Regression — TDS Nature Tagging Scenarios ───────────

    /**
     * Regression 1: vendor with {@code tds_app='Y'} and <b>no</b> natures tagged in
     * {@code fintds_exception} — all active {@code fintdstype} rows are candidates.
     * 53 such vendors confirmed in DB.
     */
    ADVANCE_WITH_TDS_NO_NATURE(
            ApVoucherTemplateLoader::advanceWithTdsTemplate,
            ApVoucherPredicateFactory.ADVANCE_WITH_TDS_BEHAVIOR,
            true
    ),

    /**
     * Regression 2: vendor with {@code tds_app='Y'} and <b>exactly 1</b> nature tagged in
     * {@code fintds_exception} — only that nature is available.
     * Example: AARUCHI FASHIONS (slcode=193) → tdstypecode=3 → "TDS ON TECHNICAL SERVICE".
     */
    ADVANCE_WITH_TDS_SINGLE_NATURE(
            ApVoucherTemplateLoader::advanceWithTdsTemplate,
            ApVoucherPredicateFactory.ADVANCE_WITH_TDS_BEHAVIOR,
            true
    ),

    // ─── Document Payment (On Account = No) ──────────────────────────────────

    /**
     * Document payment with no adjustment type — mandatory fields only.
     * Mirrors AR Voucher's {@code DOC_NO_ADJUSTMENT}; uses {@code debit_credit='C'}
     * in the pending-document query.
     */
    DOC_NO_ADJUSTMENT(
            ApVoucherTemplateLoader::docNoAdjustmentTemplate,
            ApVoucherPredicateFactory.DOC_NO_ADJUSTMENT_BEHAVIOR,
            false
    ),

    /**
     * Document payment via manual Select Document popup.
     * Pending AP documents are reserved via {@code DocumentSelector.selectAtomic}.
     */
    DOC_SELECT_DOCUMENT(
            ApVoucherTemplateLoader::docSelectDocumentTemplate,
            ApVoucherPredicateFactory.DOC_SELECT_DOCUMENT_BEHAVIOR,
            false
    ),

    /**
     * Document payment via FIFO automatic set-off.
     * No pre-loaded document list — FIFO auto-selects documents in the UI.
     */
    DOC_FIFO(
            ApVoucherTemplateLoader::docFifoTemplate,
            ApVoucherPredicateFactory.DOC_FIFO_BEHAVIOR,
            false
    ),

    /**
     * Document payment via Reference Sitewise FIFO.
     */
    DOC_REFERENCE_SITEWISE_FIFO(
            ApVoucherTemplateLoader::docRefSitewiseFifoTemplate,
            ApVoucherPredicateFactory.DOC_REFERENCE_SITEWISE_FIFO_BEHAVIOR,
            false
    );

    // ─────────────────────────────────────────────────────────────────────────

    private final Function<LiveDbRefs, MergedFinanceApVoucherData> templateLoader;
    private final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>> behaviorSupplier;

    /**
     * {@code true} when this scenario targets the Advance Payment path (On Account = Yes).
     * {@code false} for Document Payment (On Account = No).
     * Used by {@link com.gsl.utils.dataprovider.source.finance.DbApVoucherDataSource} to
     * decide whether to query pending AP documents.
     */
    private final boolean onAccount;

    ApVoucherScenarioType(
            Function<LiveDbRefs, MergedFinanceApVoucherData> templateLoader,
            Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>> behaviorSupplier,
            boolean onAccount) {
        this.templateLoader    = templateLoader;
        this.behaviorSupplier  = behaviorSupplier;
        this.onAccount         = onAccount;
    }

    /** Returns {@code true} when this scenario targets the Advance Payment path. */
    public boolean isOnAccount() {
        return onAccount;
    }

    /**
     * Builds the test data object from live DB values, stamps {@code scenarioType = this}
     * and sets the {@code onAccount} flag on the data object so the test method dispatches
     * correctly.
     *
     * @param refs live DB reference values fetched by DbApVoucherDataSource
     * @return fully built test data object ready for injection into the test
     */
    public MergedFinanceApVoucherData loadData(LiveDbRefs refs) {
        MergedFinanceApVoucherData data = templateLoader.apply(refs);
        data.setScenarioType(this);
        return data;
    }

    /**
     * Executes the UI interaction + assertion for this scenario.
     *
     * @param data the test data built by {@link #loadData}
     * @param page the ApVoucherAdd page component (injected by the test method)
     * @return true if the AP Voucher was saved (or validation error confirmed for negative scenarios)
     */
    public boolean execute(MergedFinanceApVoucherData data, ApVoucherAdd page) {
        return behaviorSupplier.get().test(data, page);
    }
}
