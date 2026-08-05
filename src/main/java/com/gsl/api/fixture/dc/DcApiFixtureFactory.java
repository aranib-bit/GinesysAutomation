package com.gsl.api.fixture.dc;

import com.gsl.api.fixture.core.ScenarioKind;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/** Maps test method names to the {@link DcApiScenarioType} constants they should run. */
public final class DcApiFixtureFactory {

    private DcApiFixtureFactory() {}

    private static final BiPredicate<DcApiScenarioType, String> SHOULD_RUN =
            (scenario, methodName) -> switch (methodName) {
                case "dcPositive" -> scenario.getKind() == ScenarioKind.POSITIVE || scenario.getKind() == ScenarioKind.BOUNDARY;
                case "dcNegative" -> scenario.getKind() == ScenarioKind.NEGATIVE;
                default -> true;
            };

    public static List<DcApiScenarioType> scenariosFor(String testMethodName) {
        return Arrays.stream(DcApiScenarioType.values())
                .filter(scenario -> SHOULD_RUN.test(scenario, testMethodName))
                .collect(Collectors.toList());
    }
}
