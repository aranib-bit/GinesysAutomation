package com.gsl.api.fixture.core;

/**
 * Classifies what an {@code *ApiScenarioType} constant is exercising — used for reporting
 * ({@link com.gsl.reports.ApiCallReportLogger} section labels) and for
 * {@code <Flow>ApiFixtureFactory} dispatch (e.g. run only {@code NEGATIVE} scenarios for a
 * test method named {@code *NegativeBoundary}, mirroring
 * {@code ArVoucherFixtureFactory}'s {@code SHOULD_RUN} convention).
 */
public enum ScenarioKind {
    /** Valid input — the call is expected to succeed and return a well-formed result. */
    POSITIVE,
    /** Invalid/missing/garbage input — the call is expected to fail or return an empty/absent result. */
    NEGATIVE,
    /** Edge-of-valid-range input (smallest/largest accepted value) — expected to still succeed. */
    BOUNDARY
}
