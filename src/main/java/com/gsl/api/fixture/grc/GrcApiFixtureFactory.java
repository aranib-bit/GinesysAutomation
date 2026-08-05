package com.gsl.api.fixture.grc;

import com.gsl.api.fixture.core.ScenarioKind;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/** Maps test method names to the {@link GrcApiScenarioType} constants they should run. */
public final class GrcApiFixtureFactory {

    private GrcApiFixtureFactory() {}

    private static final BiPredicate<GrcApiScenarioType, String> SHOULD_RUN =
            (scenario, methodName) -> switch (methodName) {
                case "grcPositive" -> scenario.getKind() == ScenarioKind.POSITIVE || scenario.getKind() == ScenarioKind.BOUNDARY;
                case "grcNegative" -> scenario.getKind() == ScenarioKind.NEGATIVE;
                default -> true;
            };

    public static List<GrcApiScenarioType> scenariosFor(String testMethodName) {
        return Arrays.stream(GrcApiScenarioType.values())
                .filter(scenario -> SHOULD_RUN.test(scenario, testMethodName))
                .collect(Collectors.toList());
    }
}
