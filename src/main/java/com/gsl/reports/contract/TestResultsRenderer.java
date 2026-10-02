package com.gsl.reports.contract;

/**
 * Domain-neutral contract for flushing accumulated test results to the Extent Report.
 *
 * <p>Implementations choose positive vs negative rendering based on
 * {@link TestResultsAccumulator#isNegativeScenario()}.
 */
public interface TestResultsRenderer {

    /** Renders {@code accumulator}'s current contents to the Extent Report, choosing positive vs
     *  negative markup based on {@link TestResultsAccumulator#isNegativeScenario()}. */
    void flush(TestResultsAccumulator accumulator);
}
