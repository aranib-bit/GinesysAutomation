package com.gsl.api.fixture.arvoucher;

import com.gsl.api.fixture.core.ScenarioKind;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/** Maps test method names to the {@link ArVoucherApiScenarioType} constants they should run. */
public final class ArVoucherApiFixtureFactory {

    private ArVoucherApiFixtureFactory() {}

    private static final BiPredicate<ArVoucherApiScenarioType, String> SHOULD_RUN =
            (scenario, methodName) -> switch (methodName) {
                case "arVoucherPositive" -> scenario.getKind() == ScenarioKind.POSITIVE || scenario.getKind() == ScenarioKind.BOUNDARY;
                case "arVoucherNegative" -> scenario.getKind() == ScenarioKind.NEGATIVE;
                default -> true;
            };

    public static List<ArVoucherApiScenarioType> scenariosFor(String testMethodName) {
        return Arrays.stream(ArVoucherApiScenarioType.values())
                .filter(scenario -> SHOULD_RUN.test(scenario, testMethodName))
                .collect(Collectors.toList());
    }
}
