package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.pages.Finance.Operations.GeneralVoucherAdd;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.dataprovider.fixture.LiveGvDbRefs;

import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Binds a template loader + behavior for one General Voucher scenario — the GV analogue of
 * {@code ApVoucherScenarioType}/{@code ArVoucherScenarioType}, now implementing the shared
 * {@link VoucherScenarioType} contract like AP/AR.
 *
 * <p><b>History:</b> this enum originally carried only a template loader, no
 * behavior-supplier/{@code execute(data, page)} dispatch — {@code CreateGeneralVoucher}'s test
 * method filled/saved/verified/DB-validated inline instead. When GV's negative/boundary
 * scenarios later needed per-scenario UI behavior, they were wired through a parallel ad hoc
 * {@code @DataProvider} tuple in {@code CreateGeneralVoucherNegativeBoundary} instead of this
 * enum — the exact bypass {@link VoucherScenarioType} exists to make impossible. Retrofitted
 * per the {@code VoucherScenarioType} unification: both positive constants now delegate to
 * {@link GeneralVoucherPredicateFactory#POSITIVE_CREATE_BEHAVIOR}, and 3 new negative constants
 * cover the scenarios {@code CreateGeneralVoucherNegativeBoundary} exercises.
 */
public enum GeneralVoucherScenarioType implements
        VoucherScenarioType<MergedFinanceGeneralVoucherData, GeneralVoucherAdd, LiveGvDbRefs> {

    /** One positive combination row from {@link GeneralVoucherCombinations} — the bulk of the matrix. */
    POSITIVE_COMBINATION(
            GeneralVoucherTemplateLoader::combinationTemplate,
            GeneralVoucherPredicateFactory.POSITIVE_CREATE_BEHAVIOR
    ),

    /**
     * The one true "MIXED" scenario — a single voucher whose Expense/Income Detail rows span
     * multiple different ledger types (Asset/Type1, Liability/Type2, Income/Type3). Unlike
     * {@link #POSITIVE_COMBINATION}, this scenario's data is built by
     * {@code DbGeneralVoucherDataSource.loadFreshForMixedTypes} (which merges several
     * single-ledger-type resolutions via {@link GeneralVoucherTemplateLoader#mergeIntoMixedVoucher})
     * rather than through {@link #loadData}'s single-{@code LiveGvDbRefs} path — so
     * {@link #loadData} is never called for this scenario; the {@code templateLoader} here is a
     * placeholder to satisfy the enum's constructor and is never invoked.
     */
    MIXED_TYPES(
            refs -> { throw new UnsupportedOperationException(
                    "MIXED_TYPES data is built by DbGeneralVoucherDataSource.loadFreshForMixedTypes, not loadData(refs)."); },
            GeneralVoucherPredicateFactory.POSITIVE_CREATE_BEHAVIOR
    ),

    // ─── Negative scenarios (expect a validation-error popup) — see plan §4's 3-data/2-behavior note ──

    /** Type2 row where Allocate Sites Tagging Summary's Pending Amount != 0 (under-allocation). */
    NEG_SITE_ALLOCATION_PENDING(
            refs -> GeneralVoucherPredicateFactory.siteAllocationPending(),
            GeneralVoucherPredicateFactory.NEGATIVE_FORM_VALIDATION_BEHAVIOR
    ),

    /** Type3 row with Sub Ledger deliberately left blank — validation fires on the Site-button click. */
    NEG_SUBLEDGER_REQUIRED(
            refs -> GeneralVoucherPredicateFactory.subLedgerRequired(),
            GeneralVoucherPredicateFactory.NEG_SUBLEDGER_REQUIRED_BEHAVIOR
    ),

    /** Type2 row whose Site button is clicked but nothing is added in the popup (empty allocation). */
    NEG_SITE_NOT_ALLOCATED(
            refs -> GeneralVoucherPredicateFactory.siteNotAllocated(),
            GeneralVoucherPredicateFactory.NEGATIVE_FORM_VALIDATION_BEHAVIOR
    );

    private final Function<LiveGvDbRefs, MergedFinanceGeneralVoucherData> templateLoader;
    private final Supplier<BiPredicate<MergedFinanceGeneralVoucherData, GeneralVoucherAdd>> behaviorSupplier;

    GeneralVoucherScenarioType(
            Function<LiveGvDbRefs, MergedFinanceGeneralVoucherData> templateLoader,
            Supplier<BiPredicate<MergedFinanceGeneralVoucherData, GeneralVoucherAdd>> behaviorSupplier) {
        this.templateLoader   = templateLoader;
        this.behaviorSupplier = behaviorSupplier;
    }

    @Override
    public MergedFinanceGeneralVoucherData loadData(LiveGvDbRefs refs) {
        MergedFinanceGeneralVoucherData data = templateLoader.apply(refs);
        data.setScenarioType(this);
        return data;
    }

    @Override
    public boolean execute(MergedFinanceGeneralVoucherData data, GeneralVoucherAdd page) {
        return behaviorSupplier.get().test(data, page);
    }
}
