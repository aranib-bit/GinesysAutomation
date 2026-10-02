package com.gsl.api.fixture.item;

import com.gsl.api.fixture.core.ScenarioKind;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * Maps test method names to the {@link ItemApiScenarioType} constants they should run — same
 * {@code BiPredicate SHOULD_RUN} dispatch shape as {@code ArVoucherApiFixtureFactory}/
 * {@code ServiceInvoiceAdhocApiFixtureFactory}. Adding a new test method = add one {@code case}
 * to {@link #SHOULD_RUN}; nothing else changes.
 */
public final class ItemApiFixtureFactory {

    private ItemApiFixtureFactory() {}

    /**
     * {@code default -> true} is a safety catch-all that runs every scenario for unrecognised
     * method names — override with a specific case when that is too broad.
     */
    private static final BiPredicate<ItemApiScenarioType, String> SHOULD_RUN =
            (scenario, methodName) -> switch (methodName) {
                case "itemPositive" -> scenario.getKind() == ScenarioKind.POSITIVE || scenario.getKind() == ScenarioKind.BOUNDARY;
                case "itemNegative" -> scenario.getKind() == ScenarioKind.NEGATIVE;
                default -> true;
            };

    /** Returns the {@link ItemApiScenarioType} constants {@link #SHOULD_RUN} selects for {@code testMethodName}. */
    public static List<ItemApiScenarioType> scenariosFor(String testMethodName) {
        return Arrays.stream(ItemApiScenarioType.values())
                .filter(scenario -> SHOULD_RUN.test(scenario, testMethodName))
                .collect(Collectors.toList());
    }
}
