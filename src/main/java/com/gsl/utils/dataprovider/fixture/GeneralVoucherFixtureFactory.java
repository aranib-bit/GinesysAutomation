package com.gsl.utils.dataprovider.fixture;

import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherScenarioType;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * Routes General Voucher test methods to their applicable scenario types — mirrors
 * {@code ApVoucherFixtureFactory}. Unlike AP/AR's own {@code generate(...)} (confirmed dead —
 * their positive data depends on live DB refs unknown until test-execution time), GV's
 * negative-boundary data has no such dependency, so {@link #generate} here is the one case
 * where this mechanism is actually exercised — see {@code CreateGeneralVoucherNegativeBoundary}.
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
                                || scenario == GeneralVoucherScenarioType.NEG_SITE_NOT_ALLOCATED;
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
     * Generates test data for all scenario types applicable to {@code testMethodName}.
     *
     * @param testMethodName the exact test method name (e.g. {@code "createGeneralVoucherNegativeBoundary"})
     * @param refs           live DB reference values — {@code null} is safe here since every
     *                       negative-boundary scenario's {@code templateLoader} ignores it
     *                       (its data is hand-built in Java, not resolved from live DB refs)
     * @return one data object per applicable scenario type
     */
    public List<MergedFinanceGeneralVoucherData> generate(String testMethodName, LiveGvDbRefs refs) {
        return scenariosFor(testMethodName).stream()
                .map(scenario -> scenario.loadData(refs))
                .collect(Collectors.toList());
    }

    // Entry-method alternation (plan §9.5) previously lived here as entryMethodFor(int) but
    // was moved to the domain-agnostic com.gsl.utils.dataprovider.fixture.finance.VoucherEditEntryMethod#forIndex(int)
    // — AR/AP Voucher's own future Edit-combination classes need the identical alternation
    // rule, which doesn't belong on a GV-only factory.
}
