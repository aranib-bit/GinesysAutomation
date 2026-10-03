package com.gsl.reports;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable snapshot of one executed test invocation, captured by {@link ResultCollector} for the
 * per-run coverage workbook ({@link CoverageWorkbookBuilder}). Plain strings and numbers only, so
 * it stays valid after the TestNG {@code ITestResult} it came from is gone.
 */
@Value
@Builder
public class CoverageResult {

    /** {@code GV}, {@code AR} or {@code AP}. */
    String module;

    /** {@code Create}, {@code Edit}, {@code Revert}, {@code Delete} or {@code UI Validation}. */
    String flow;

    /** Full per-row id as shown in the report title, e.g. {@code FIN-GV-EDIT-HEADER-01}; for a
     *  skip raised before any row was bound (DataSupplier-level skip) just the method's base id. */
    String testCaseId;

    /** Stable scenario id the coverage catalog joins on (GV: {@code scenarioTypeLabel}; AR/AP: the
     *  placeholder's test-case token or edit-scenario name); blank when the row carries none. */
    String variantId;

    /** Scenario-type label (e.g. the AR/AP {@code ArVoucherScenarioType} constant name); may be blank. */
    String scenarioType;

    /** Human-readable scenario text from the row's master test data, or the method description. */
    String description;

    /** Test method name. */
    String methodName;

    /** {@code PASS}, {@code FAIL} or {@code SKIP}. */
    String status;

    /** Wall-clock duration of this invocation in milliseconds. */
    long durationMs;

    /** First line of the failure/skip throwable, blank for a pass. */
    String reason;
}
