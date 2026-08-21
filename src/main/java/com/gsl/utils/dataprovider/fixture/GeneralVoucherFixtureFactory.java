package com.gsl.utils.dataprovider.fixture;

import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherScenarioType;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * Routes General Voucher test methods to their applicable scenario types — mirrors
 * {@code ApVoucherFixtureFactory}. Kept minimal (one scenario type today) but present for
 * parity, and to give a future second scenario type (e.g. a negative-combination scenario)
 * an obvious home.
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

    // Entry-method alternation (plan §9.5) previously lived here as entryMethodFor(int) but
    // was moved to the domain-agnostic com.gsl.utils.dataprovider.fixture.finance.VoucherEditEntryMethod#forIndex(int)
    // — AR/AP Voucher's own future Edit-combination classes need the identical alternation
    // rule, which doesn't belong on a GV-only factory.
}
