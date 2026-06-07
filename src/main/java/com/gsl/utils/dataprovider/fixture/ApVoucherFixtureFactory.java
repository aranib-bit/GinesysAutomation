package com.gsl.utils.dataprovider.fixture;

import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherScenarioType;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * Routes AP Voucher test methods to their applicable scenario types.
 *
 * <p>A {@link BiPredicate}{@code <ApVoucherScenarioType, String>} gates which scenario
 * types run for each test method name. Adding a new test method = add one {@code case}
 * to {@link #SHOULD_RUN}; nothing else changes.
 *
 * <h3>Method → Scenario mapping</h3>
 * <ul>
 *   <li>{@code createApVoucherAdvancePayment}  → {@code ADVANCE_NO_TDS}, {@code ADVANCE_WITH_TDS}</li>
 *   <li>{@code createApVoucherDocumentPayment} → {@code DOC_SELECT_DOCUMENT},
 *       {@code DOC_FIFO}, {@code DOC_REFERENCE_SITEWISE_FIFO}</li>
 * </ul>
 *
 * <p>Unlike AR Voucher, there is no {@code default -> true} catch-all — every AP Voucher
 * method must be explicitly listed so that a new method never accidentally runs all scenarios.
 */
public class ApVoucherFixtureFactory {

    /**
     * Maps test method names to the scenario types they should run.
     *
     * <p>To add a new negative scenario:
     * <ol>
     *   <li>Add the new {@link ApVoucherScenarioType} constant.</li>
     *   <li>Add it to the relevant {@code case} here.</li>
     * </ol>
     */
    private static final BiPredicate<ApVoucherScenarioType, String> SHOULD_RUN =
            (scenario, methodName) -> switch (methodName) {

                case "createApVoucherAdvancePayment" ->
                        scenario == ApVoucherScenarioType.ADVANCE_NO_TDS
                                || scenario == ApVoucherScenarioType.ADVANCE_WITH_TDS;

                case "createApVoucherDocumentPayment" ->
                        scenario == ApVoucherScenarioType.DOC_SELECT_DOCUMENT
                                || scenario == ApVoucherScenarioType.DOC_FIFO
                                || scenario == ApVoucherScenarioType.DOC_REFERENCE_SITEWISE_FIFO;

                // Regression: TDS nature-tagging scenarios (DB-verified)
                // Scenario 1: no nature tagged (53 vendors) — all fintdstype available
                // Scenario 2: exactly 1 nature tagged (AARUCHI FASHIONS etc.)
                case "createApVoucherAdvancePaymentTdsNatureRegression" ->
                        scenario == ApVoucherScenarioType.ADVANCE_WITH_TDS_NO_NATURE
                                || scenario == ApVoucherScenarioType.ADVANCE_WITH_TDS_SINGLE_NATURE;

                // Any unrecognised method name gets nothing — intentionally no catch-all
                default -> false;
            };

    /**
     * Returns the scenario types applicable to {@code testMethodName}.
     * Used by the DataSupplier to build placeholders without fetching DB data.
     *
     * @param testMethodName the exact test method name (e.g. {@code "createApVoucherAdvancePayment"})
     * @return ordered list of scenario types that should run for this method
     */
    public static List<ApVoucherScenarioType> scenariosFor(String testMethodName) {
        return Arrays.stream(ApVoucherScenarioType.values())
                .filter(scenario -> SHOULD_RUN.test(scenario, testMethodName))
                .collect(Collectors.toList());
    }

    /**
     * Generates test data for all scenario types applicable to {@code testMethodName}.
     *
     * <p>Flow:
     * <ol>
     *   <li>{@code ApVoucherScenarioType.values()} — all scenario types</li>
     *   <li>{@link #SHOULD_RUN} BiPredicate — filter to applicable scenarios</li>
     *   <li>{@link ApVoucherScenarioType#loadData(LiveDbRefs)} — build data from DB values</li>
     * </ol>
     *
     * @param testMethodName the exact test method name (e.g. {@code "createApVoucherAdvancePayment"})
     * @param refs           live DB reference values fetched by DbApVoucherDataSource
     * @return one data object per applicable scenario type
     */
    public List<MergedFinanceApVoucherData> generate(String testMethodName, LiveDbRefs refs) {
        return scenariosFor(testMethodName).stream()
                .map(scenario -> scenario.loadData(refs))
                .collect(Collectors.toList());
    }
}
