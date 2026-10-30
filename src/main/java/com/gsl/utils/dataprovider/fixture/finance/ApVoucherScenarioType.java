package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.pages.Finance.Operations.ApVoucherAdd;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.dataprovider.fixture.ApVoucherLiveDbRefs;

import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Each enum constant combines:
 * <ul>
 *   <li>A <b>template loader</b> — {@code Function<ApVoucherLiveDbRefs, MergedFinanceApVoucherData>}
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
public enum ApVoucherScenarioType implements
        VoucherScenarioType<MergedFinanceApVoucherData, ApVoucherAdd, ApVoucherLiveDbRefs> {

    // ─── Advance Payment (On Account = Yes) ──────────────────────────────────

    /**
     * Simple vendor advance with no TDS deduction — mandatory baseline.
     * Queries {@code finsl} for a usable vendor SL; no pending documents needed.
     */
    ADVANCE_NO_TDS(
            ApVoucherTemplateLoader::advanceNoTdsTemplate,
            ApVoucherPredicateFactory.ADVANCE_POSITIVE_BEHAVIOR,
            true
    ),

    /**
     * Vendor advance with TDS deduction — TDS rows are filled after sub-ledger rows.
     * Restricts SL lookup to TDS-applicable vendor sub-ledgers.
     * Smoke baseline: any {@code tds_app='Y'} vendor (no nature-count filter).
     */
    ADVANCE_WITH_TDS(
            ApVoucherTemplateLoader::advanceWithTdsTemplate,
            ApVoucherPredicateFactory.ADVANCE_POSITIVE_BEHAVIOR,
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
            ApVoucherPredicateFactory.ADVANCE_POSITIVE_BEHAVIOR,
            true
    ),

    /**
     * Regression 2: vendor with {@code tds_app='Y'} and <b>exactly 1</b> nature tagged in
     * {@code fintds_exception} — only that nature is available.
     * Example: AARUCHI FASHIONS (slcode=193) → tdstypecode=3 → "TDS ON TECHNICAL SERVICE".
     */
    ADVANCE_WITH_TDS_SINGLE_NATURE(
            ApVoucherTemplateLoader::advanceWithTdsTemplate,
            ApVoucherPredicateFactory.ADVANCE_POSITIVE_BEHAVIOR,
            true
    ),

    // ─── Multi-SL Advance Payment ─────────────────────────────────────────────

    /**
     * Two SL lines with Bank GL and mixed payment information (NEFT/RTGS + Cheque).
     * Exercises the multi-row payment popup flow end-to-end.
     * Regression Phase 2 baseline for multi-SL advance scenarios.
     */
    ADVANCE_MULTI_SL_WITH_PAYMENT(
            ApVoucherTemplateLoader::advanceMultiSlWithPaymentTemplate,
            ApVoucherPredicateFactory.ADVANCE_POSITIVE_BEHAVIOR,
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
            ApVoucherPredicateFactory.DOC_POSITIVE_BEHAVIOR,
            false
    ),

    /**
     * Document payment via manual Select Document popup.
     * Pending AP documents are reserved via {@code DocumentSelector.selectAtomic}.
     */
    DOC_SELECT_DOCUMENT(
            ApVoucherTemplateLoader::docSelectDocumentTemplate,
            ApVoucherPredicateFactory.DOC_POSITIVE_BEHAVIOR,
            false
    ),

    /**
     * Document payment via FIFO automatic set-off.
     * No pre-loaded document list — FIFO auto-selects documents in the UI.
     */
    DOC_FIFO(
            ApVoucherTemplateLoader::docFifoTemplate,
            ApVoucherPredicateFactory.DOC_POSITIVE_BEHAVIOR,
            false
    ),

    /**
     * Document payment via Reference Sitewise FIFO.
     */
    DOC_REFERENCE_SITEWISE_FIFO(
            ApVoucherTemplateLoader::docRefSitewiseFifoTemplate,
            ApVoucherPredicateFactory.DOC_POSITIVE_BEHAVIOR,
            false
    ),

    // ─── Adjustment amount-mismatch Confirmation ("Update payment amount" / "I will adjust more") ──
    // Document payments whose adjusted total deliberately differs from the row's payment amount.
    // All share DOC_MISMATCH_BEHAVIOR; the per-scenario script (mismatch plan + expected payment)
    // comes from ApVoucherTemplateLoader#docPartialTemplate. Confirmed live in Add (user screenshot,
    // 2026-09-30). Tiering: MANDATORY = UPDATE_SINGLE; PHASE1 = ADJUST_MORE_SELECT_DOC, MULTI_DOC_UPDATE,
    // PER_DOC_LESS_THAN_PENDING; PHASE2 = ADJUST_MORE_FIFO / _REFSITE_FIFO / _THEN_UPDATE;
    // PHASE3 = OVER_PENDING (exploratory).

    /** One document adjusted for half its pending → "Update payment amount". */
    DOC_PARTIAL_UPDATE_PAYMENT_SELECT_DOC(
            refs -> ApVoucherTemplateLoader.docPartialTemplate(refs, ApVoucherTemplateLoader.DocPartialKind.UPDATE_SINGLE),
            ApVoucherPredicateFactory.DOC_MISMATCH_BEHAVIOR,
            false
    ),

    /** Adjust the first document, then "I will adjust more" with a second via Select Document. */
    DOC_PARTIAL_ADJUST_MORE_SELECT_DOC(
            refs -> ApVoucherTemplateLoader.docPartialTemplate(refs, ApVoucherTemplateLoader.DocPartialKind.ADJUST_MORE_SELECT_DOC),
            ApVoucherPredicateFactory.DOC_MISMATCH_BEHAVIOR,
            false
    ),

    /** As above, adding the remainder via FIFO Set-off. */
    DOC_PARTIAL_ADJUST_MORE_FIFO(
            refs -> ApVoucherTemplateLoader.docPartialTemplate(refs, ApVoucherTemplateLoader.DocPartialKind.ADJUST_MORE_FIFO),
            ApVoucherPredicateFactory.DOC_MISMATCH_BEHAVIOR,
            false
    ),

    /** As above, adding the remainder via Reference Site wise FIFO set-off. */
    DOC_PARTIAL_ADJUST_MORE_REFSITE_FIFO(
            refs -> ApVoucherTemplateLoader.docPartialTemplate(refs, ApVoucherTemplateLoader.DocPartialKind.ADJUST_MORE_REFSITE_FIFO),
            ApVoucherPredicateFactory.DOC_MISMATCH_BEHAVIOR,
            false
    ),

    /** Two documents each adjusted for half their pending → "Update payment amount". */
    DOC_PARTIAL_MULTI_DOC_UPDATE_PAYMENT(
            refs -> ApVoucherTemplateLoader.docPartialTemplate(refs, ApVoucherTemplateLoader.DocPartialKind.MULTI_DOC_UPDATE),
            ApVoucherPredicateFactory.DOC_MISMATCH_BEHAVIOR,
            false
    ),

    /** One document adjusted just below its pending (in range) → "Update payment amount". */
    DOC_PARTIAL_PER_DOC_AMOUNT_LESS_THAN_PENDING(
            refs -> ApVoucherTemplateLoader.docPartialTemplate(refs, ApVoucherTemplateLoader.DocPartialKind.PER_DOC_LESS_THAN_PENDING),
            ApVoucherPredicateFactory.DOC_MISMATCH_BEHAVIOR,
            false
    ),

    /** Adjust more once (partial second document), still short → then "Update payment amount". */
    DOC_PARTIAL_ADJUST_MORE_THEN_UPDATE(
            refs -> ApVoucherTemplateLoader.docPartialTemplate(refs, ApVoucherTemplateLoader.DocPartialKind.ADJUST_MORE_THEN_UPDATE),
            ApVoucherPredicateFactory.DOC_MISMATCH_BEHAVIOR,
            false
    ),

    /** Exploratory negative: per-document amount above the document's pending — the app's response
     *  (clamp / block / popup) is unconfirmed; a failure here records the observed behavior. */
    DOC_AMOUNT_OVER_PENDING_NEGATIVE(
            refs -> ApVoucherTemplateLoader.docPartialTemplate(refs, ApVoucherTemplateLoader.DocPartialKind.OVER_PENDING),
            ApVoucherPredicateFactory.DOC_MISMATCH_BEHAVIOR,
            false
    ),

    // ─── T-03 Negative scenarios (expect a validation-error popup) ────────────
    // All run on the advance path so a usable vendor SL is resolved without needing
    // pending documents; each template mutates exactly one field to trigger its error.

    /**
     * FIN-AP-ADD-NEG-01: Save with no Cash/Bank GL selected → "Cash/Bank is required".
     * Header filled (Cash/Bank left blank) → click Add Row (blank row, no data) → Save → verify error.
     */
    NEG_BLANK_CASHBANK(
            ApVoucherTemplateLoader::negBlankCashbankTemplate,
            ApVoucherPredicateFactory.NEG_BLANK_CASHBANK_SAVE_BEHAVIOR,
            true
    ),

    /**
     * FIN-AP-ADD-NEG-01 T-1: Click Add Row without Cash/Bank selected →
     * Error popup "Please select cash/bank ledger first." — assert header + body, dismiss, discard.
     */
    NEG_BLANK_CASHBANK_ADD_ROW(
            ApVoucherTemplateLoader::negBlankCashbankAddRowTemplate,
            ApVoucherPredicateFactory.NEG_BLANK_CASHBANK_ADD_ROW_BEHAVIOR,
            true
    ),

    /**
     * FIN-AP-ADD-NEG-01 T-2: Same Add Row error, then fills Cash/Bank and saves successfully
     * (proves form is recoverable without a page reload).
     */
    NEG_BLANK_CASHBANK_ADD_ROW_RECOVER(
            ApVoucherTemplateLoader::negBlankCashbankAddRowRecoverTemplate,
            ApVoucherPredicateFactory.NEG_BLANK_CASHBANK_ADD_ROW_RECOVER_BEHAVIOR,
            true
    ),

    /** Save with no Sub-Ledger row filled (empty grid) → "Sub-Ledger is required". T-1: dismiss OK → discard → logout. */
    NEG_BLANK_SUBLEDGER(
            ApVoucherTemplateLoader::negBlankSubledgerTemplate,
            ApVoucherPredicateFactory.NEGATIVE_FORM_VALIDATION_BEHAVIOR,
            true
    ),

    /** T-2: same blank-SL error, but after dismissing OK the test fills SL rows and saves successfully. */
    NEG_BLANK_SUBLEDGER_RECOVER(
            ApVoucherTemplateLoader::negBlankSubledgerRecoverTemplate,
            ApVoucherPredicateFactory.NEG_BLANK_SUBLEDGER_RECOVER_BEHAVIOR,
            true
    ),

    /** An extinct vendor must not be offered for a transaction: the sub-ledger lookup and the Sub Ledger picker must both omit it. API mode only. */
    NEG_EXTINCT_VENDOR_NOT_OFFERED(
            ApVoucherTemplateLoader::negExtinctVendorTemplate,
            ApVoucherPredicateFactory.NEG_EXTINCT_VENDOR_NOT_OFFERED_BEHAVIOR,
            true
    ),

    /** Save on a document scheme with no numbers left → proper rejection message; nothing created.
     *  The scheme is resolved in EXHAUSTED mode (provisions + fills a dedicated test scheme if none is full). */
    NEG_DOC_SCHEME_EXHAUSTED(
            ApVoucherTemplateLoader::negDocSchemeExhaustedTemplate,
            ApVoucherPredicateFactory.NEG_DOC_SCHEME_EXHAUSTED_BEHAVIOR,
            true
    ),

    // ─── T-03 Boundary pair: blank reference allowed ──────────────────────────

    /**
     * Two AP Vouchers with no Reference — both must save successfully.
     * Confirms blank reference is allowed (AP does not enforce mandatory reference).
     */
    BLANK_REFERENCE_ALLOWED(
            ApVoucherTemplateLoader::blankReferenceAllowedTemplate,
            ApVoucherPredicateFactory.BOUNDARY_PASS_BEHAVIOR,
            true
    ),

    // ─── T-03 Boundary pair: minimum line amount ──────────────────────────────
    // VALID = smallest positive amount (saves); INVALID = zero (rejected).

    /** Advance with the smallest positive amount (0.01) — must save successfully. */
    BOUND_AMOUNT_MIN_VALID(
            ApVoucherTemplateLoader::boundAmountMinValidTemplate,
            ApVoucherPredicateFactory.BOUNDARY_PASS_BEHAVIOR,
            true
    ),

    /** Advance with a zero line amount — error dialog + inline row tooltip (two-step inline validation flow). */
    BOUND_AMOUNT_MIN_INVALID(
            ApVoucherTemplateLoader::boundAmountMinInvalidTemplate,
            ApVoucherPredicateFactory.INLINE_VALIDATION_BEHAVIOR,
            true
    ),

    /** On Account No, sub-ledger added with zero amount, no document adjusted → error dialog + inline tooltip. */
    DOC_AMOUNT_NOT_ADJUSTED_INVALID(
            ApVoucherTemplateLoader::docAmountNotAdjustedTemplate,
            ApVoucherPredicateFactory.DOC_INLINE_VALIDATION_BEHAVIOR,
            false
    ),

    /** On Account No, document adjusted (amount auto-populated) → clicking Amount cell shows read-only dialog. */
    DOC_AMOUNT_READ_ONLY(
            ApVoucherTemplateLoader::docAmountReadOnlyTemplate,
            ApVoucherPredicateFactory.DOC_AMOUNT_READ_ONLY_BEHAVIOR,
            false
    );

    // ─────────────────────────────────────────────────────────────────────────

    private final Function<ApVoucherLiveDbRefs, MergedFinanceApVoucherData> templateLoader;
    private final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>> behaviorSupplier;

    /**
     * {@code true} when this scenario targets the Advance Payment path (On Account = Yes).
     * {@code false} for Document Payment (On Account = No).
     * Used by {@link com.gsl.utils.dataprovider.source.finance.DbApVoucherDataSource} to
     * decide whether to query pending AP documents.
     */
    private final boolean onAccount;

    ApVoucherScenarioType(
            Function<ApVoucherLiveDbRefs, MergedFinanceApVoucherData> templateLoader,
            Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>> behaviorSupplier,
            boolean onAccount) {
        this.templateLoader    = templateLoader;
        this.behaviorSupplier  = behaviorSupplier;
        this.onAccount         = onAccount;
    }

    /** True when the UI allocates across the sub-ledger's whole pending list (FIFO / reference-site-wise FIFO), so the scenario needs the whole sub-ledger to itself. */
    public boolean usesUiFifoAllocation() {
        return this == DOC_FIFO || this == DOC_REFERENCE_SITEWISE_FIFO
                || this == DOC_PARTIAL_ADJUST_MORE_FIFO || this == DOC_PARTIAL_ADJUST_MORE_REFSITE_FIFO;
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
    @Override
    public MergedFinanceApVoucherData loadData(ApVoucherLiveDbRefs refs) {
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
    @Override
    public boolean execute(MergedFinanceApVoucherData data, ApVoucherAdd page) {
        return behaviorSupplier.get().test(data, page);
    }
}
