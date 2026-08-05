package com.gsl.api.fixture.transferout;

import com.gsl.api.fixture.core.ScenarioKind;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/** Maps test method names to the {@link TransferOutApiScenarioType} constants they should run. */
public final class TransferOutApiFixtureFactory {

    private TransferOutApiFixtureFactory() {}

    private static final BiPredicate<TransferOutApiScenarioType, String> SHOULD_RUN =
            (scenario, methodName) -> switch (methodName) {
                case "transferOutPositive" -> scenario.getKind() == ScenarioKind.POSITIVE || scenario.getKind() == ScenarioKind.BOUNDARY;
                case "transferOutNegative" -> scenario.getKind() == ScenarioKind.NEGATIVE;
                default -> true;
            };

    public static List<TransferOutApiScenarioType> scenariosFor(String testMethodName) {
        return Arrays.stream(TransferOutApiScenarioType.values())
                .filter(scenario -> SHOULD_RUN.test(scenario, testMethodName))
                .collect(Collectors.toList());
    }
}
