package com.gsl.api.fixture.serviceinvoiceadhoc;

import com.gsl.api.fixture.core.ScenarioKind;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * Maps test method names to the {@link ServiceInvoiceAdhocApiScenarioType} constants they should
 * run — same {@code BiPredicate SHOULD_RUN} dispatch shape as
 * {@code ArVoucherFixtureFactory}/{@code ApVoucherFixtureFactory}. Adding a new test method = add
 * one {@code case} to {@link #SHOULD_RUN}; nothing else changes.
 */
public final class ServiceInvoiceAdhocApiFixtureFactory {

    private ServiceInvoiceAdhocApiFixtureFactory() {}

    /**
     * {@code default -> true} is a safety catch-all that runs every scenario for unrecognised
     * method names — override with a specific case when that is too broad.
     */
    private static final BiPredicate<ServiceInvoiceAdhocApiScenarioType, String> SHOULD_RUN =
            (scenario, methodName) -> switch (methodName) {
                case "serviceInvoiceAdhocPositive" -> scenario.getKind() == ScenarioKind.POSITIVE
                        || scenario.getKind() == ScenarioKind.BOUNDARY;
                case "serviceInvoiceAdhocNegative" -> scenario.getKind() == ScenarioKind.NEGATIVE;
                default -> true;
            };

    public static List<ServiceInvoiceAdhocApiScenarioType> scenariosFor(String testMethodName) {
        return Arrays.stream(ServiceInvoiceAdhocApiScenarioType.values())
                .filter(scenario -> SHOULD_RUN.test(scenario, testMethodName))
                .collect(Collectors.toList());
    }
}
