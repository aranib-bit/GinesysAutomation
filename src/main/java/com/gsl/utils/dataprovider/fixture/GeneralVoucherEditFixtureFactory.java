package com.gsl.utils.dataprovider.fixture;

import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditVariant;

import java.util.function.BiPredicate;

/**
 * Maps GV Edit test-method names to the {@link GeneralVoucherEditVariant.ScenarioType} they
 * should run — plan §4K, mirroring {@link ArVoucherFixtureFactory#scenariosFor}'s
 * routing-clarity role for AR. Replaces the inline {@code isDetailLineMethod}/{@code isHeaderMethod}
 * boolean checks that used to live directly in {@code DbGeneralVoucherEditDataSource#load} — a
 * routing-clarity win only, no behavior change: each of the two test methods still runs exactly
 * the variants whose {@code ScenarioType} matches it.
 */
public final class GeneralVoucherEditFixtureFactory {

    private GeneralVoucherEditFixtureFactory() {}

    private static final BiPredicate<GeneralVoucherEditVariant.ScenarioType, String> SHOULD_RUN =
            (scenario, methodName) -> switch (methodName) {
                case "editGeneralVoucherHeader" -> scenario == GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY;
                case "editGeneralVoucherDetailLine" -> scenario == GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE;
                case "editGeneralVoucherPaymentInfo" -> scenario == GeneralVoucherEditVariant.ScenarioType.PAYMENT_INFO;
                // Unknown method name — DbGeneralVoucherEditDataSource#load already throws its own
                // SkipException for this case before ever calling shouldRun(), so this default is
                // unreachable in practice; false is the safe value if that guard is ever removed.
                default -> false;
            };

    /** @return whether {@code testMethodName} should run the given scenario type. */
    public static boolean shouldRun(GeneralVoucherEditVariant.ScenarioType scenario, String testMethodName) {
        return SHOULD_RUN.test(scenario, testMethodName);
    }
}
