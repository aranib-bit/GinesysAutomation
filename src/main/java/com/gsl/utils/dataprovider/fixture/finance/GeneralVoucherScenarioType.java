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
    //
    // Each templateLoader here IS live-DB-driven (unlike the placeholder comment this replaced
    // suggested) — GeneralVoucherFixtureFactory.buildNegativeBoundaryPlaceholder wraps a real
    // GeneralVoucherFixtureOptions per scenario, and DbGeneralVoucherDataSource.loadFreshForScenario
    // resolves a real LiveGvDbRefs from it (see CreateGeneralVoucherNegativeBoundary's
    // @DataProvider) before calling loadData(refs) below — exactly like POSITIVE_COMBINATION.

    /** Type2 row where Allocate Sites Tagging Summary's Pending Amount != 0 (under-allocation). */
    NEG_SITE_ALLOCATION_PENDING(
            GeneralVoucherTemplateLoader::negSiteAllocationPendingTemplate,
            GeneralVoucherPredicateFactory.NEGATIVE_FORM_VALIDATION_BEHAVIOR
    ),

    /** Type3 row with Sub Ledger deliberately left blank — validation fires on the Site-button click. */
    NEG_SUBLEDGER_REQUIRED(
            GeneralVoucherTemplateLoader::negSubledgerRequiredTemplate,
            GeneralVoucherPredicateFactory.NEG_SUBLEDGER_REQUIRED_BEHAVIOR
    ),

    /** Type2 row whose Site button is clicked but nothing is added in the popup (empty allocation). */
    NEG_SITE_NOT_ALLOCATED(
            GeneralVoucherTemplateLoader::negSiteNotAllocatedTemplate,
            GeneralVoucherPredicateFactory.NEGATIVE_FORM_VALIDATION_BEHAVIOR
    ),

    /** Round 21 — Cash-type Cash/Bank ledger + a post-dated Document Date; the app correctly
     *  rejects the Save. Moved out of {@link #POSITIVE_COMBINATION}'s Mandatory sweep (plan
     *  §Round 13/21) into its own dedicated negative scenario. */
    NEG_CASH_POST_DATED(
            GeneralVoucherTemplateLoader::negCashPostDatedTemplate,
            GeneralVoucherPredicateFactory.NEG_CASH_POST_DATED_BEHAVIOR
    ),

    /** A complete valid voucher saved against a document scheme with no numbers left; Save must be
     *  rejected with a proper message and create nothing. The scheme is resolved in EXHAUSTED mode,
     *  which provisions and fills a dedicated test scheme when no full scheme exists. */
    NEG_DOC_SCHEME_EXHAUSTED(
            GeneralVoucherTemplateLoader::negDocSchemeExhaustedTemplate,
            GeneralVoucherPredicateFactory.NEG_DOC_SCHEME_EXHAUSTED_BEHAVIOR
    ),

    /** Round 16 — a GL-linked, user-assigned site belonging to a DIFFERENT Organizational Unit
     *  than the resolved Owner Site must not appear in the Allocate Sites picker's search
     *  results (confirmed live, Round 15: "S&CM"/OU=3 vs. "Hena Embroidery"/OU=1). */
    NEG_SITE_ALLOCATION_CROSS_OU(
            GeneralVoucherTemplateLoader::negSiteAllocationCrossOuTemplate,
            GeneralVoucherPredicateFactory.NEG_SITE_ALLOCATION_CROSS_OU_BEHAVIOR
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
