package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.dataprovider.fixture.LiveGvDbRefs;

import java.util.function.Function;

/**
 * Binds a template loader for one General Voucher scenario — the GV analogue of
 * {@code ApVoucherScenarioType}, deliberately simplified: it carries only a template
 * loader, no behavior-supplier/{@code execute(data, page)} dispatch.
 *
 * <p><b>Why no behavior dispatch, unlike AP/AR:</b> {@code CreateGeneralVoucher}'s test
 * method already fills header/rows, saves, runs {@code PostSaveDialogHandler}, and
 * DB-validates generically for whatever {@link MergedFinanceGeneralVoucherData} it
 * receives — it doesn't branch on scenario type. AP/AR's {@code ScenarioType.execute(...)}
 * exists because their negative/boundary tests need per-scenario UI behavior; GV's single
 * positive combination scenario needs none of that, so adding an unused
 * {@code behaviorSupplier}/{@code execute} pair here would be dead code. A second scenario
 * type that genuinely needs per-scenario behavior (e.g. a future negative-combination
 * scenario) has an obvious place to add that pair when it's actually needed.
 */
public enum GeneralVoucherScenarioType {

    /** One positive combination row from {@link GeneralVoucherCombinations} — the bulk of the matrix. */
    POSITIVE_COMBINATION(GeneralVoucherTemplateLoader::combinationTemplate),

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
    MIXED_TYPES(refs -> { throw new UnsupportedOperationException(
            "MIXED_TYPES data is built by DbGeneralVoucherDataSource.loadFreshForMixedTypes, not loadData(refs)."); });

    private final Function<LiveGvDbRefs, MergedFinanceGeneralVoucherData> templateLoader;

    GeneralVoucherScenarioType(Function<LiveGvDbRefs, MergedFinanceGeneralVoucherData> templateLoader) {
        this.templateLoader = templateLoader;
    }

    public MergedFinanceGeneralVoucherData loadData(LiveGvDbRefs refs) {
        MergedFinanceGeneralVoucherData data = templateLoader.apply(refs);
        data.setScenarioType(this);
        return data;
    }
}
