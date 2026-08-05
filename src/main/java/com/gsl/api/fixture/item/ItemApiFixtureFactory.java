package com.gsl.api.fixture.item;

import com.gsl.api.fixture.core.ScenarioKind;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/** Maps test method names to the {@link ItemApiScenarioType} constants they should run. */
public final class ItemApiFixtureFactory {

    private ItemApiFixtureFactory() {}

    private static final BiPredicate<ItemApiScenarioType, String> SHOULD_RUN =
            (scenario, methodName) -> switch (methodName) {
                case "itemPositive" -> scenario.getKind() == ScenarioKind.POSITIVE || scenario.getKind() == ScenarioKind.BOUNDARY;
                case "itemNegative" -> scenario.getKind() == ScenarioKind.NEGATIVE;
                default -> true;
            };

    public static List<ItemApiScenarioType> scenariosFor(String testMethodName) {
        return Arrays.stream(ItemApiScenarioType.values())
                .filter(scenario -> SHOULD_RUN.test(scenario, testMethodName))
                .collect(Collectors.toList());
    }
}
