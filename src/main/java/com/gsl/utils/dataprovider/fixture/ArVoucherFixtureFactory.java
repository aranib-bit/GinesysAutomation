package com.gsl.utils.dataprovider.fixture;

import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.dataprovider.fixture.finance.ArVoucherScenarioType;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * Generates {@link MergedFinanceArVoucherData} instances for DB-mode tests.
 *
 * <p>A {@link BiPredicate}{@code <ArVoucherScenarioType, String>} gates which scenario
 * types apply to each test method name. Adding a new test method = add one {@code case}
 * to {@link #SHOULD_RUN}; nothing else changes.
 */
public class ArVoucherFixtureFactory {

    /**
     * Maps test method names to the scenario types they should run.
     * {@code default -> true} is a safety catch-all that runs all scenarios for
     * unrecognised method names — override with a specific case when that is too broad.
     */
    private static final BiPredicate<ArVoucherScenarioType, String> SHOULD_RUN =
            (scenario, methodName) -> switch (methodName) {
                case "createArVoucher" ->
                        scenario == ArVoucherScenarioType.NO_ADJUSTMENT
                                || scenario == ArVoucherScenarioType.SELECT_DOCUMENT
                                || scenario == ArVoucherScenarioType.FIFO_SETOFF;
                // Add new test methods here, e.g.:
                // case "createArVoucherRefSitewise" ->
                //     scenario == ArVoucherScenarioType.REFERENCE_SITEWISE_FIFO;
                default -> true;
            };

    /**
     * Returns the scenario types applicable to {@code testMethodName}.
     * Used by the DataSupplier to build placeholders without fetching DB data.
     *
     * @param testMethodName the exact test method name (e.g. {@code "createArVoucher"})
     * @return ordered list of scenario types that should run for this method
     */
    public static List<ArVoucherScenarioType> scenariosFor(String testMethodName) {
        return Arrays.stream(ArVoucherScenarioType.values())
                .filter(scenario -> SHOULD_RUN.test(scenario, testMethodName))
                .collect(Collectors.toList());
    }

    /**
     * Generates test data for all scenario types applicable to {@code testMethodName}.
     *
     * <p>Flow:
     * <ol>
     *   <li>{@code ArVoucherScenarioType.values()} — all scenario types</li>
     *   <li>{@link #SHOULD_RUN} BiPredicate — filter to applicable scenarios</li>
     *   <li>{@link ArVoucherScenarioType#loadData(LiveDbRefs)} — build data from DB values</li>
     * </ol>
     *
     * @param testMethodName the exact test method name (e.g. {@code "createArVoucher"})
     * @param refs           live DB reference values fetched by DbArVoucherDataSource
     * @return one data object per applicable scenario type
     */
    public List<MergedFinanceArVoucherData> generate(String testMethodName, LiveDbRefs refs) {
        return scenariosFor(testMethodName).stream()
                .map(scenario -> scenario.loadData(refs))
                .collect(Collectors.toList());
    }
}
