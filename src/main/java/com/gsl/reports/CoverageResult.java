package com.gsl.reports;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Immutable snapshot of one executed test invocation, captured by {@link ResultCollector} for the
 * per-run coverage workbook ({@link CoverageWorkbookBuilder}). Plain strings and numbers only, so
 * it stays valid after the TestNG {@code ITestResult} it came from is gone.
 *
 * <p>Two id forms exist on purpose: {@link #testCaseId} is the unbracketed internal join key
 * (e.g. {@code FIN-GV-ADD-COMBO-01}); {@link #displayTestCaseId()} is the bracketed form the
 * report title and the workbook's "Test Case ID" column show ({@code [FIN-GV-ADD-COMBO-01]}).
 */
@Value
@Builder(toBuilder = true)
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

    /** One-line failure/skip reason (the readable summary, never just "1 validation area failed:"). */
    String reason;

    // -- report-title parts ------------------------------------------------------------------------

    /** The sentence after the id in the Extent title ("Verify that a General Voucher is created ..."). */
    String titleDescription;

    /** 1-based position of this row in the data provider's run list, {@code 0} when unknown. */
    int runIndex;

    /** Size of the data provider's run list, {@code 0} when unknown. */
    int runTotal;

    /** Pipe-separated scenario line ({@code GV | Income GL | Site allocation only | ...}). */
    String scenarioSummary;

    // -- execution facts ---------------------------------------------------------------------------

    /** Epoch millis; {@code 0} = unknown. */
    long startMs;
    long endMs;
    String thread;
    String voucherNo;
    /** Login user (pool account) that ran the test. */
    String user;
    String author;

    /** Number of retried (superseded) attempts before this final result. */
    int retries;

    /** {@code true} when the final attempt passed after at least one retry (a flaky pass). */
    boolean passedOnRetry;

    // -- failure ------------------------------------------------------------------------------------

    @Builder.Default FailureCategory failureCategory = FailureCategory.NONE;

    /** HTML-unescaped full failure text (capped), for the Test Details sheet. */
    String failureMessage;

    /** Name of the first failed step (from the step timeline), blank when none. */
    String failingStep;

    /** Absolute path of the kept failure screenshot, blank when none. */
    String screenshotFile;

    /** One entry per mismatch, for the Failure Details sheet. */
    @Builder.Default List<FailureDetail> failures = List.of();

    // -- test data (as shown in the Extent report) -------------------------------------------------

    /** The Extent "Test Data - Header" rows (label, value), without the Field/Value header row. */
    @Builder.Default List<String[]> headerFields = List.of();

    /** The Extent "Expected Results" rows (outcome, details), without the header row. */
    @Builder.Default List<String[]> expectedRows = List.of();

    /** Compact expected-results line, e.g. {@code UI Save; DB finvchmain, finvchdet, finpost}. */
    String expectedSummary;

    /** Bracketed id as in the report title, e.g. {@code [FIN-GV-ADD-COMBO-01]}. */
    public String displayTestCaseId() {
        return testCaseId == null || testCaseId.isBlank() ? "" : "[" + testCaseId + "]";
    }

    /** {@code "1/9"}, blank when the run position is unknown. */
    public String runLabel() {
        return runIndex > 0 && runTotal > 0 ? runIndex + "/" + runTotal : "";
    }
}
