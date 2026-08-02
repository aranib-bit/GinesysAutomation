package com.gsl.reports.contract;

/**
 * Single plug-in point for a reporting domain (Finance, Inventory, Procurement, …).
 *
 * <p>Registered once per suite via {@link DomainReporterRegistry#register}.
 * {@link com.gsl.listeners.TestListener} resolves the correct reporter at test time
 * and delegates all domain-specific concerns to it — the listener itself contains
 * no domain DTO imports.
 *
 * <p>Implementation contract:
 * <ul>
 *   <li>{@link #supports} — fast guard; called on every test start</li>
 *   <li>{@link #extractTestData} — called only when {@link #supports} returns true</li>
 *   <li>{@link #buildSentence} / {@link #buildRichName} — pure, no side effects</li>
 *   <li>{@link #onTestSuccess} / {@link #onTestFailure} — MUST call
 *       {@link TestResultsAccumulator#clear()} before returning (in a finally block)</li>
 * </ul>
 */
public interface DomainReporter {

    /**
     * Returns {@code true} if this reporter owns at least one parameter in the array.
     * Called on every test — keep it cheap (instanceof scan only).
     */
    boolean supports(Object[] params);

    /**
     * Extracts the domain DTO from the test parameters.
     * Called only when {@link #supports} returned {@code true}.
     */
    Object extractTestData(Object[] params);

    /**
     * Human-readable sentence fragment used as the Extent test title.
     * Returns {@code null} when the data is not yet resolved (placeholder).
     */
    String buildSentence(Object testData);

    /**
     * The test-case name to use as the Extent description, or {@code null} when the
     * name is a trivial default that adds no value (e.g. the bare method name).
     */
    String buildRichName(Object testData);

    /**
     * Returns the current thread's accumulated results for this domain.
     * Implementations delegate to their own ThreadLocal (e.g. {@code ActualResultsHolder.get()}).
     */
    TestResultsAccumulator getAccumulator();

    /**
     * Renders Actual Results to the Extent Report on test pass.
     * MUST clear the accumulator in a {@code finally} block.
     */
    void onTestSuccess(TestResultsAccumulator accumulator);

    /**
     * Renders Actual Results to the Extent Report on test fail.
     * MUST clear the accumulator in a {@code finally} block.
     */
    void onTestFailure(TestResultsAccumulator accumulator);
}
