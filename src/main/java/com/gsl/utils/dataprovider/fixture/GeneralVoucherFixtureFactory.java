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
}
