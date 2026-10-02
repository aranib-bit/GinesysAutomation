package com.gsl.utils.dataprovider.fixture;

import com.gsl.config.ConfigFactory;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.WeightedSequenceBuilder;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherScenarioType;
import com.gsl.utils.dataprovider.fixture.finance.SelectDocumentVariant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

                // Adjustment amount-mismatch Confirmation ("Update payment amount" / "I will adjust more")
                case "createApVoucherDocumentMismatch" ->
                        scenario == ApVoucherScenarioType.DOC_PARTIAL_UPDATE_PAYMENT_SELECT_DOC
                                || scenario == ApVoucherScenarioType.DOC_PARTIAL_ADJUST_MORE_SELECT_DOC
                                || scenario == ApVoucherScenarioType.DOC_PARTIAL_ADJUST_MORE_FIFO
                                || scenario == ApVoucherScenarioType.DOC_PARTIAL_ADJUST_MORE_REFSITE_FIFO
                                || scenario == ApVoucherScenarioType.DOC_PARTIAL_MULTI_DOC_UPDATE_PAYMENT
                                || scenario == ApVoucherScenarioType.DOC_PARTIAL_PER_DOC_AMOUNT_LESS_THAN_PENDING
                                || scenario == ApVoucherScenarioType.DOC_PARTIAL_ADJUST_MORE_THEN_UPDATE
                                || scenario == ApVoucherScenarioType.DOC_AMOUNT_OVER_PENDING_NEGATIVE;

                // Regression: TDS nature-tagging scenarios (DB-verified)
                // Scenario 1: no nature tagged (53 vendors) — all fintdstype available
                // Scenario 2: exactly 1 nature tagged (AARUCHI FASHIONS etc.)
                case "createApVoucherAdvancePaymentTdsNatureRegression" ->
                        scenario == ApVoucherScenarioType.ADVANCE_WITH_TDS_NO_NATURE
                                || scenario == ApVoucherScenarioType.ADVANCE_WITH_TDS_SINGLE_NATURE;

                // T-03 — negative & boundary scenarios (advance-path + document-path validation)
                case "createApVoucherNegativeBoundary" ->
                        scenario == ApVoucherScenarioType.NEG_BLANK_CASHBANK
                                || scenario == ApVoucherScenarioType.NEG_BLANK_SUBLEDGER
                                || scenario == ApVoucherScenarioType.NEG_BLANK_SUBLEDGER_RECOVER
                                || scenario == ApVoucherScenarioType.BLANK_REFERENCE_ALLOWED
                                || scenario == ApVoucherScenarioType.BOUND_AMOUNT_MIN_VALID
                                || scenario == ApVoucherScenarioType.BOUND_AMOUNT_MIN_INVALID
                        || scenario == ApVoucherScenarioType.NEG_BLANK_CASHBANK_ADD_ROW
                        || scenario == ApVoucherScenarioType.NEG_BLANK_CASHBANK_ADD_ROW_RECOVER
                        || scenario == ApVoucherScenarioType.DOC_AMOUNT_NOT_ADJUSTED_INVALID
                        || scenario == ApVoucherScenarioType.DOC_AMOUNT_READ_ONLY;

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
     *   <li>{@link ApVoucherScenarioType#loadData(ApVoucherLiveDbRefs)} — build data from DB values</li>
     * </ol>
     *
     * @param testMethodName the exact test method name (e.g. {@code "createApVoucherAdvancePayment"})
     * @param refs           live DB reference values fetched by DbApVoucherDataSource
     * @return one data object per applicable scenario type
     */
    public List<MergedFinanceApVoucherData> generate(String testMethodName, ApVoucherLiveDbRefs refs) {
        return scenariosFor(testMethodName).stream()
                .map(scenario -> scenario.loadData(refs))
                .collect(Collectors.toList());
    }

    /**
     * Round 20 — decides known-vs-unknown for EVERY {@code CHEQUE}-payment row in {@code variants},
     * at an exact ratio (not a per-row random roll) via {@link WeightedSequenceBuilder}, using
     * AP's own configured weights ({@code ap.cheque.known.weight}/{@code ap.cheque.unknown.weight}).
     * Mirrors {@code GeneralVoucherFixtureFactory.assignKnownChequeFlags} exactly — see that
     * method's Javadoc for the full rationale. {@link SelectDocumentVariant} is immutable
     * ({@code @Builder(toBuilder = true)}) — a CHEQUE row assigned "known" is rebuilt via
     * {@code toBuilder()}, not mutated in place.
     *
     * @param variants the full, already-built (and already execution-group-filtered) variant list
     *                 — NOT mutated; a new list is returned.
     */
    public static List<SelectDocumentVariant> assignKnownChequeFlags(List<SelectDocumentVariant> variants) {
        List<Integer> chequeIndices = IntStream.range(0, variants.size())
                .filter(i -> variants.get(i).getPaymentMode() == SelectDocumentVariant.PaymentMode.CHEQUE)
                .boxed()
                .collect(Collectors.toList());

        List<Boolean> knownFlags = WeightedSequenceBuilder.build(chequeIndices.size(), List.of(
                new WeightedSequenceBuilder.Entry<>(true, ConfigFactory.getFinanceConfig().apChequeKnownWeight()),
                new WeightedSequenceBuilder.Entry<>(false, ConfigFactory.getFinanceConfig().apChequeUnknownWeight())));

        List<SelectDocumentVariant> result = new ArrayList<>(variants);
        for (int j = 0; j < chequeIndices.size(); j++) {
            int i = chequeIndices.get(j);
            result.set(i, result.get(i).toBuilder().knownCheque(knownFlags.get(j)).build());
        }
        return result;
    }
}
