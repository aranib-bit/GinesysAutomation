package com.gsl.api.fixture.core;

import lombok.Builder;
import lombok.Value;

/**
 * Self-reported result of running one {@code *ApiScenarioType} constant's action against a live
 * endpoint. Deliberately NOT a bare boolean — {@code detail} always carries what was actually
 * observed (a value, a caught {@link com.gsl.api.core.ApiException} message, an HTTP status),
 * so a failing scenario's {@code Assert} message (in the {@code com.tests.API.*} test classes)
 * is self-explanatory in an ExtentReports run without needing to attach a debugger.
 *
 * <p>Every scenario action decides for itself what "passed" means for that specific endpoint —
 * a positive lookup passes when it returns real data without throwing; a negative lookup passes
 * when it either throws {@link com.gsl.api.core.ApiException} (server-side validation rejected
 * the input) OR returns an empty/absent result (the more common shape for a GET lookup given a
 * nonexistent key, since most of these endpoints are forgiving rather than strict). This mirrors
 * {@code ServiceInvoiceAdhocApiClient.resolveVendor}'s own documented philosophy: absence of a
 * match is not automatically an error unless the endpoint's real contract says so.
 */
@Value
@Builder
public class ScenarioOutcome {
    boolean passed;
    String detail;

    public static ScenarioOutcome pass(String detail) {
        return ScenarioOutcome.builder().passed(true).detail(detail).build();
    }

    public static ScenarioOutcome fail(String detail) {
        return ScenarioOutcome.builder().passed(false).detail(detail).build();
    }
}
