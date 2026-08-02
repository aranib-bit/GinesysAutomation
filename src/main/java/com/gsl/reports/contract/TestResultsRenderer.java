package com.gsl.reports.contract;

/**
 * Domain-neutral contract for flushing accumulated test results to the Extent Report.
 *
 * <p>Implementations choose positive vs negative rendering based on
 * {@link TestResultsAccumulator#isNegativeScenario()}.
 */
public interface TestResultsRenderer {
    void flush(TestResultsAccumulator accumulator);
}
