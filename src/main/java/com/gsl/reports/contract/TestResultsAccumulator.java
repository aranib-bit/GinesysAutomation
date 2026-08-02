package com.gsl.reports.contract;

/**
 * Domain-neutral contract for the per-test ThreadLocal result accumulator.
 *
 * <p>Validators deposit results into the concrete implementation (e.g.
 * {@link com.gsl.reports.ActualResultsHolder}) after each DB fetch. The
 * {@link DomainReporter} receives this interface on success/failure so it
 * can render and clear without {@link com.gsl.listeners.TestListener} knowing
 * the concrete Finance type.
 */
public interface TestResultsAccumulator {
    boolean isNegativeScenario();
    void resetAccumulator();
}
