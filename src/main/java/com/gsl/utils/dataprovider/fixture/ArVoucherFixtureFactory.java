package com.gsl.utils.dataprovider.fixture;

import com.gsl.config.ConfigFactory;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.WeightedSequenceBuilder;
import com.gsl.utils.dataprovider.fixture.finance.ArVoucherScenarioType;
import com.gsl.utils.dataprovider.fixture.finance.SelectDocumentVariant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
                        scenario == ArVoucherScenarioType.DOC_NO_ADJUSTMENT
                                || scenario == ArVoucherScenarioType.SELECT_DOCUMENT
                                || scenario == ArVoucherScenarioType.FIFO_SETOFF
                                || scenario == ArVoucherScenarioType.REFERENCE_SITEWISE_FIFO;

                // T-03 — negative & boundary scenarios (kept out of the positive createArVoucher run)
                case "createArVoucherNegativeBoundary" ->
                        scenario == ArVoucherScenarioType.NEG_BLANK_CASHBANK
                                || scenario == ArVoucherScenarioType.NEG_BLANK_SUBLEDGER
                                || scenario == ArVoucherScenarioType.NEG_BLANK_MANDATORY_REFERENCE
                                || scenario == ArVoucherScenarioType.BOUND_AMOUNT_MIN_VALID
                                || scenario == ArVoucherScenarioType.BOUND_AMOUNT_MIN_INVALID;

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
     *   <li>{@link ArVoucherScenarioType#loadData(ArVoucherLiveDbRefs)} — build data from DB values</li>
     * </ol>
     *
     * @param testMethodName the exact test method name (e.g. {@code "createArVoucher"})
     * @param refs           live DB reference values fetched by DbArVoucherDataSource
     * @return one data object per applicable scenario type
     */
    public List<MergedFinanceArVoucherData> generate(String testMethodName, ArVoucherLiveDbRefs refs) {
        return scenariosFor(testMethodName).stream()
                .map(scenario -> scenario.loadData(refs))
                .collect(Collectors.toList());
    }

    /**
     * Round 20 — decides known-vs-unknown for EVERY {@code CHEQUE}-payment row in {@code variants},
     * at an exact ratio (not a per-row random roll) via {@link WeightedSequenceBuilder}, using AR's
     * own configured weights ({@code ar.cheque.known.weight}/{@code ar.cheque.unknown.weight} —
     * AR's known bucket is deliberately rare, 1% by default, unlike GV/AP's 90%). Mirrors {@code
     * GeneralVoucherFixtureFactory.assignKnownChequeFlags}/{@code
     * ApVoucherFixtureFactory.assignKnownChequeFlags} exactly.
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
                new WeightedSequenceBuilder.Entry<>(true, ConfigFactory.getFinanceConfig().arChequeKnownWeight()),
                new WeightedSequenceBuilder.Entry<>(false, ConfigFactory.getFinanceConfig().arChequeUnknownWeight())));

        List<SelectDocumentVariant> result = new ArrayList<>(variants);
        for (int j = 0; j < chequeIndices.size(); j++) {
            int i = chequeIndices.get(j);
            result.set(i, result.get(i).toBuilder().knownCheque(knownFlags.get(j)).build());
        }
        return result;
    }

    /**
     * Round 20 — AR-only second pass: within the (rare) subset {@link #assignKnownChequeFlags}
     * already marked "known", decides same-bank vs. different-bank match, again at an exact
     * ratio (default 1:1, {@code ar.cheque.sameBank.weight}/{@code ar.cheque.differentBank.weight}).
     * Call this AFTER {@link #assignKnownChequeFlags} — it only looks at rows already carrying
     * {@code knownCheque = true}.
     */
    public static List<SelectDocumentVariant> assignChequeBankMatchFlags(List<SelectDocumentVariant> variants) {
        List<Integer> knownIndices = IntStream.range(0, variants.size())
                .filter(i -> variants.get(i).isKnownCheque())
                .boxed()
                .collect(Collectors.toList());

        List<Boolean> differentBankFlags = WeightedSequenceBuilder.build(knownIndices.size(), List.of(
                new WeightedSequenceBuilder.Entry<>(true, ConfigFactory.getFinanceConfig().arChequeDifferentBankWeight()),
                new WeightedSequenceBuilder.Entry<>(false, ConfigFactory.getFinanceConfig().arChequeSameBankWeight())));

        List<SelectDocumentVariant> result = new ArrayList<>(variants);
        for (int j = 0; j < knownIndices.size(); j++) {
            int i = knownIndices.get(j);
            result.set(i, result.get(i).toBuilder().knownChequeDifferentBank(differentBankFlags.get(j)).build());
        }
        return result;
    }
}
