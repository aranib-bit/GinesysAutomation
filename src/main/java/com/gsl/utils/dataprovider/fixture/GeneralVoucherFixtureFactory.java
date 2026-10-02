package com.gsl.utils.dataprovider.fixture;

import com.gsl.config.ConfigFactory;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.WeightedSequenceBuilder;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherFixtureOptions;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherPaymentVariant;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherScenarioType;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherVariant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Routes General Voucher test methods to their applicable scenario types — mirrors
 * {@code ApVoucherFixtureFactory}. {@link #generate} is now confirmed dead, same as AP/AR's own
 * (their positive AND GV's negative-boundary data all depend on live DB refs unknown until
 * test-execution time, resolved per-scenario) — see {@link #buildNegativeBoundaryPlaceholder}
 * and {@code CreateGeneralVoucherNegativeBoundary}'s {@code @DataProvider} for how the
 * negative-boundary path actually resolves its data now.
 */
public class GeneralVoucherFixtureFactory {

    private static final BiPredicate<GeneralVoucherScenarioType, String> SHOULD_RUN =
            (scenario, methodName) -> switch (methodName) {
                case "createGeneralVoucher" -> scenario == GeneralVoucherScenarioType.POSITIVE_COMBINATION;
                case "createGeneralVoucherMixedTypes" -> scenario == GeneralVoucherScenarioType.MIXED_TYPES;
                // Revert has no MIXED_TYPES analogue — it reuses the same combination-matrix scenario
                // type as Create, but DbGeneralVoucherRevertDataSource resolves each combination to an
                // existing Posted voucher of the matching account type instead of building new data.
                case "revertGeneralVoucher" -> scenario == GeneralVoucherScenarioType.POSITIVE_COMBINATION;
                case "createGeneralVoucherNegativeBoundary" ->
                        scenario == GeneralVoucherScenarioType.NEG_SITE_ALLOCATION_PENDING
                                || scenario == GeneralVoucherScenarioType.NEG_SUBLEDGER_REQUIRED
                                || scenario == GeneralVoucherScenarioType.NEG_SITE_NOT_ALLOCATED
                                // Round 21: Cash-type payment moved out of Mandatory into this
                                // dedicated negative scenario (plan §Round 13/21).
                                || scenario == GeneralVoucherScenarioType.NEG_CASH_POST_DATED
                                // Round 16: a GL-linked, user-assigned, cross-OU site must not be
                                // selectable in Allocate Sites.
                                || scenario == GeneralVoucherScenarioType.NEG_SITE_ALLOCATION_CROSS_OU;
                default -> false;
            };

    /**
     * Returns the scenario types applicable to {@code testMethodName}.
     *
     * @param testMethodName the exact test method name (e.g. {@code "createGeneralVoucher"})
     */
    public static List<GeneralVoucherScenarioType> scenariosFor(String testMethodName) {
        return Arrays.stream(GeneralVoucherScenarioType.values())
                .filter(scenario -> SHOULD_RUN.test(scenario, testMethodName))
                .collect(Collectors.toList());
    }

    /**
     * Generates test data for all scenario types applicable to {@code testMethodName}, given one
     * already-resolved {@link LiveGvDbRefs} shared by every scenario. Now unused for the
     * negative-boundary path too — see {@link #buildNegativeBoundaryPlaceholder} — since each
     * NEG_* scenario needs its OWN differently-shaped refs (Type2 vs Type3, Bank vs Cash),
     * resolved individually via {@code DbGeneralVoucherDataSource.loadFreshForScenario}, not one
     * shared {@code refs} passed to all of them here. Kept for the same reason AP/AR keep their
     * own confirmed-dead {@code generate(...)} — interface consistency across the 3 fixture
     * factories.
     *
     * @param testMethodName the exact test method name
     * @param refs           live DB reference values shared by every scenario this method resolves
     * @return one data object per applicable scenario type
     */
    public List<MergedFinanceGeneralVoucherData> generate(String testMethodName, LiveGvDbRefs refs) {
        return scenariosFor(testMethodName).stream()
                .map(scenario -> scenario.loadData(refs))
                .collect(Collectors.toList());
    }

    /**
     * Builds a placeholder for one General Voucher negative/boundary scenario, carrying the
     * {@link GeneralVoucherFixtureOptions} it needs — mirrors
     * {@code DbGeneralVoucherDataSource.buildPlaceholder}'s shape for the positive combination
     * matrix, scoped to a single scenario (no variant/tags). Pass the result to {@code
     * DbGeneralVoucherDataSource.loadFreshForScenario(placeholder, methodName)} to resolve a real
     * {@link LiveGvDbRefs} and the final test data — see {@code CreateGeneralVoucherNegativeBoundary}'s
     * {@code @DataProvider}.
     *
     * @param scenario the NEG_* scenario type this placeholder resolves for
     * @param opts     the live-DB query parameters (GL type/shape/side/payment) this scenario needs
     */
    public static MergedFinanceGeneralVoucherData buildNegativeBoundaryPlaceholder(
            GeneralVoucherScenarioType scenario, GeneralVoucherFixtureOptions opts) {
        return MergedFinanceGeneralVoucherData.builder()
                .scenarioType(scenario)
                .fixtureOptions(opts)
                .expenseIncomeDetailList(List.of())   // empty = placeholder signal, same idiom as buildPlaceholder
                .siteAllocationList(List.of())
                .needsPaymentInfoWarning(false)
                .scenarioTypeLabel(scenario.name())
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    // Entry-method alternation (plan §9.5) previously lived here as entryMethodFor(int) but
    // was moved to the domain-agnostic com.gsl.utils.dataprovider.fixture.finance.VoucherEditEntryMethod#forIndex(int)
    // — AR/AP Voucher's own future Edit-combination classes need the identical alternation
    // rule, which doesn't belong on a GV-only factory.

    /**
     * Round 20 — decides known-vs-unknown for EVERY {@code CHEQUE}-payment row in {@code variants},
     * at an exact ratio (not a per-row random roll) via {@link WeightedSequenceBuilder}, using
     * this domain's own configured weights ({@code gv.cheque.known.weight}/{@code
     * gv.cheque.unknown.weight}). This is the Fixture Factory's own responsibility per the
     * user-directed design: it decides WHAT test data to generate (including this dimension),
     * while {@link WeightedSequenceBuilder} is only the mechanism for HOW OFTEN each outcome is
     * picked.
     *
     * <p>{@link GeneralVoucherVariant} is immutable ({@code @Builder(toBuilder = true)}) — a
     * CHEQUE row that's assigned "known" is rebuilt via {@code toBuilder()}, not mutated in place.
     * Every non-CHEQUE row is returned unchanged.
     *
     * @param variants the full, already-built combination list (e.g. from {@code
     *                 GeneralVoucherCombinations.allCombinations()}) — NOT mutated; a new list is
     *                 returned.
     */
    public static List<GeneralVoucherVariant> assignKnownChequeFlags(List<GeneralVoucherVariant> variants) {
        List<GeneralVoucherVariant> result = assignRowPayments(variants);

        // One slot per CHEQUE-payment row: legacy variants contribute row 0, multi-row-payment
        // variants every row whose assigned payment is CHEQUE.
        List<int[]> chequeSlots = new ArrayList<>();   // {variantIndex, rowIndex}
        for (int i = 0; i < result.size(); i++) {
            GeneralVoucherVariant v = result.get(i);
            int rows = v.isMultiRowPayment() ? v.getGlCount() : 1;
            for (int r = 0; r < rows; r++) {
                if (v.paymentForRow(r) == GeneralVoucherPaymentVariant.CHEQUE) chequeSlots.add(new int[]{i, r});
            }
        }

        List<Boolean> knownFlags = WeightedSequenceBuilder.build(chequeSlots.size(), List.of(
                new WeightedSequenceBuilder.Entry<>(true, ConfigFactory.getFinanceConfig().gvChequeKnownWeight()),
                new WeightedSequenceBuilder.Entry<>(false, ConfigFactory.getFinanceConfig().gvChequeUnknownWeight())));

        java.util.Map<Integer, List<Boolean>> perRowKnown = new java.util.HashMap<>();
        for (int j = 0; j < chequeSlots.size(); j++) {
            int vi = chequeSlots.get(j)[0], row = chequeSlots.get(j)[1];
            GeneralVoucherVariant v = result.get(vi);
            if (v.isMultiRowPayment()) {
                List<Boolean> known = perRowKnown.computeIfAbsent(vi,
                        k -> new ArrayList<>(java.util.Collections.nCopies(v.getGlCount(), false)));
                known.set(row, knownFlags.get(j));
            } else {
                result.set(vi, v.toBuilder().knownCheque(knownFlags.get(j)).build());
            }
        }
        perRowKnown.forEach((vi, known) -> result.set(vi, result.get(vi).toBuilder().rowKnownCheque(known).build()));
        return result;
    }

    /**
     * Round 6 — gives every grid row of every {@link GeneralVoucherVariant#isMultiRowPayment()}
     * variant its own payment variant (Cheque / NEFT-RTGS / none), drawn at an exact ratio over ALL
     * such rows of this run via {@link WeightedSequenceBuilder} from the configured
     * {@code gv.row.payment.*.weight} values — nothing hardcoded per row. Returns a new list
     * (variants are immutable); every other variant is returned unchanged.
     */
    public static List<GeneralVoucherVariant> assignRowPayments(List<GeneralVoucherVariant> variants) {
        int totalRows = variants.stream().filter(GeneralVoucherVariant::isMultiRowPayment)
                .mapToInt(GeneralVoucherVariant::getGlCount).sum();
        List<GeneralVoucherVariant> result = new ArrayList<>(variants);
        if (totalRows == 0) return result;

        var cfg = ConfigFactory.getFinanceConfig();
        List<GeneralVoucherPaymentVariant> drawn = WeightedSequenceBuilder.build(totalRows, List.of(
                new WeightedSequenceBuilder.Entry<>(GeneralVoucherPaymentVariant.CHEQUE, cfg.gvRowPaymentChequeWeight()),
                new WeightedSequenceBuilder.Entry<>(GeneralVoucherPaymentVariant.NEFT_RTGS, cfg.gvRowPaymentNeftWeight()),
                new WeightedSequenceBuilder.Entry<>(GeneralVoucherPaymentVariant.NONE, cfg.gvRowPaymentNoneWeight())));

        int next = 0;
        for (int i = 0; i < result.size(); i++) {
            GeneralVoucherVariant v = result.get(i);
            if (!v.isMultiRowPayment()) continue;
            List<GeneralVoucherPaymentVariant> rows = new ArrayList<>(drawn.subList(next, next + v.getGlCount()));
            next += v.getGlCount();
            result.set(i, v.toBuilder().rowPayments(rows).build());
        }
        return result;
    }
}
