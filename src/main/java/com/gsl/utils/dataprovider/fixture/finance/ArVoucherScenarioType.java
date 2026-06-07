package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.pages.Finance.Operations.ArVoucherAdd;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.dataprovider.fixture.LiveDbRefs;

import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Each enum constant combines:
 * <ul>
 *   <li>A <b>template loader</b> — {@code Function<LiveDbRefs, MergedFinanceArVoucherData>}
 *       that builds the test data from live DB values.</li>
 *   <li>A <b>behavior supplier</b> — {@code Supplier<BiPredicate<MergedFinanceArVoucherData, ArVoucherAdd>>}
 *       that performs the UI interaction and returns true if the voucher was saved.</li>
 * </ul>
 *
 * The {@code Supplier} wrapper ensures lazy evaluation — safe for parallel test execution.
 */
public enum ArVoucherScenarioType {

    DOC_NO_ADJUSTMENT(
            ArVoucherTemplateLoader::docNoAdjustmentTemplate,
            ArVoucherPredicateFactory.DOC_NO_ADJUSTMENT_BEHAVIOR
    ),

    SELECT_DOCUMENT(
            ArVoucherTemplateLoader::selectDocumentTemplate,
            ArVoucherPredicateFactory.SELECT_DOCUMENT_BEHAVIOR
    ),

    FIFO_SETOFF(
            ArVoucherTemplateLoader::fifoTemplate,
            ArVoucherPredicateFactory.FIFO_SETOFF_BEHAVIOR
    ),

    REFERENCE_SITEWISE_FIFO(
            ArVoucherTemplateLoader::refSitewiseFifoTemplate,
            ArVoucherPredicateFactory.REF_SITEWISE_FIFO_BEHAVIOR
    );

    private final Function<LiveDbRefs, MergedFinanceArVoucherData> templateLoader;
    private final Supplier<BiPredicate<MergedFinanceArVoucherData, ArVoucherAdd>> behaviorSupplier;

    ArVoucherScenarioType(
            Function<LiveDbRefs, MergedFinanceArVoucherData> templateLoader,
            Supplier<BiPredicate<MergedFinanceArVoucherData, ArVoucherAdd>> behaviorSupplier) {
        this.templateLoader   = templateLoader;
        this.behaviorSupplier = behaviorSupplier;
    }

    /**
     * Builds the test data object from live DB values and stamps {@code scenarioType = this}
     * on it so the test method can dispatch to {@link #execute}.
     */
    public MergedFinanceArVoucherData loadData(LiveDbRefs refs) {
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
    public boolean execute(MergedFinanceArVoucherData data, ArVoucherAdd page) {
        return behaviorSupplier.get().test(data, page);
    }
}
