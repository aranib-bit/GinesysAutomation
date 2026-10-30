package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.pages.Finance.Operations.ArVoucherAdd;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.dataprovider.fixture.ArVoucherLiveDbRefs;

import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Each enum constant combines:
 * <ul>
 *   <li>A <b>template loader</b> — {@code Function<ArVoucherLiveDbRefs, MergedFinanceArVoucherData>}
 *       that builds the test data from live DB values.</li>
 *   <li>A <b>behavior supplier</b> — {@code Supplier<BiPredicate<MergedFinanceArVoucherData, ArVoucherAdd>>}
 *       that performs the UI interaction and returns true if the voucher was saved.</li>
 * </ul>
 *
 * The {@code Supplier} wrapper ensures lazy evaluation — safe for parallel test execution.
 */
public enum ArVoucherScenarioType implements
        VoucherScenarioType<MergedFinanceArVoucherData, ArVoucherAdd, ArVoucherLiveDbRefs> {

    /** Receipt payment with no adjustment type — mandatory fields only, no pending documents needed. */
    DOC_NO_ADJUSTMENT(
            ArVoucherTemplateLoader::docNoAdjustmentTemplate,
            ArVoucherPredicateFactory.POSITIVE_CREATE_BEHAVIOR
    ),

    /** Receipt payment via manual Select Document popup — see {@link SelectDocumentCombinations}
     *  for the full variant matrix driving this scenario. */
    SELECT_DOCUMENT(
            ArVoucherTemplateLoader::selectDocumentTemplate,
            ArVoucherPredicateFactory.POSITIVE_CREATE_BEHAVIOR
    ),

    /** Receipt payment via FIFO automatic set-off — no pre-loaded document list, FIFO auto-selects
     *  documents in the UI. */
    FIFO_SETOFF(
            ArVoucherTemplateLoader::fifoTemplate,
            ArVoucherPredicateFactory.POSITIVE_CREATE_BEHAVIOR
    ),

    /** Receipt payment via Reference Sitewise FIFO. */
    REFERENCE_SITEWISE_FIFO(
            ArVoucherTemplateLoader::refSitewiseFifoTemplate,
            ArVoucherPredicateFactory.POSITIVE_CREATE_BEHAVIOR
    ),

    // ─── T-03 Negative scenarios (expect a validation-error popup) ────────────

    /** Save with no Cash/Bank GL selected → "Cash/Bank is required". */
    NEG_BLANK_CASHBANK(
            ArVoucherTemplateLoader::negBlankCashbankTemplate,
            ArVoucherPredicateFactory.NEGATIVE_FORM_VALIDATION_BEHAVIOR
    ),

    /** Save with no Sub-Ledger row filled (empty grid) → "Sub-Ledger is required". */
    NEG_BLANK_SUBLEDGER(
            ArVoucherTemplateLoader::negBlankSubledgerTemplate,
            ArVoucherPredicateFactory.NEGATIVE_FORM_VALIDATION_BEHAVIOR
    ),

    /** Save with a mandatory Reference left blank → "Reference No. is required"
     *  (only when the resolved doc scheme enforces a mandatory reference). */
    NEG_BLANK_MANDATORY_REFERENCE(
            ArVoucherTemplateLoader::negBlankReferenceTemplate,
            ArVoucherPredicateFactory.NEGATIVE_FORM_VALIDATION_BEHAVIOR
    ),

    /** Save on a document scheme with no numbers left → proper rejection message; nothing created.
     *  The scheme is resolved in EXHAUSTED mode (provisions + fills a dedicated test scheme if none is full). */
    NEG_DOC_SCHEME_EXHAUSTED(
            ArVoucherTemplateLoader::negDocSchemeExhaustedTemplate,
            ArVoucherPredicateFactory.NEG_DOC_SCHEME_EXHAUSTED_BEHAVIOR
    ),

    // ─── T-03 Boundary pair: minimum line amount ──────────────────────────────

    /** Smallest positive line amount (0.01) — must save successfully. */
    BOUND_AMOUNT_MIN_VALID(
            ArVoucherTemplateLoader::boundAmountMinValidTemplate,
            ArVoucherPredicateFactory.BOUNDARY_PASS_BEHAVIOR
    ),

    /** Zero line amount — error dialog + inline row tooltip (two-step inline validation flow). */
    BOUND_AMOUNT_MIN_INVALID(
            ArVoucherTemplateLoader::boundAmountMinInvalidTemplate,
            ArVoucherPredicateFactory.INLINE_VALIDATION_BEHAVIOR
    );

    private final Function<ArVoucherLiveDbRefs, MergedFinanceArVoucherData> templateLoader;
    private final Supplier<BiPredicate<MergedFinanceArVoucherData, ArVoucherAdd>> behaviorSupplier;

    ArVoucherScenarioType(
            Function<ArVoucherLiveDbRefs, MergedFinanceArVoucherData> templateLoader,
            Supplier<BiPredicate<MergedFinanceArVoucherData, ArVoucherAdd>> behaviorSupplier) {
        this.templateLoader   = templateLoader;
        this.behaviorSupplier = behaviorSupplier;
    }

    /** True when the UI allocates across the sub-ledger's whole pending list (FIFO / reference-site-wise FIFO), so the scenario needs the whole sub-ledger to itself. */
    public boolean usesUiFifoAllocation() {
        return this == FIFO_SETOFF || this == REFERENCE_SITEWISE_FIFO;
    }

    /**
     * Builds the test data object from live DB values and stamps {@code scenarioType = this}
     * on it so the test method can dispatch to {@link #execute}.
     */
    @Override
    public MergedFinanceArVoucherData loadData(ArVoucherLiveDbRefs refs) {
        MergedFinanceArVoucherData data = templateLoader.apply(refs);
        data.setScenarioType(this);
        return data;
    }

    /**
     * Executes the UI interaction + assertion for this scenario.
     *
     * @param data the test data built by {@link #loadData}
     * @param page the ArVoucherAdd page component (injected by the test method)
     * @return true if the AR Voucher was saved successfully
     */
    @Override
    public boolean execute(MergedFinanceArVoucherData data, ArVoucherAdd page) {
        return behaviorSupplier.get().test(data, page);
    }
}
