package com.gsl.validation.snapshot;

import lombok.Builder;
import lombok.Getter;

import java.util.function.Function;

/**
 * One column's expected before/after behavior — doc §23, plan §1.
 *
 * <p>{@link #expectedValue} carries a statically-known new value (e.g. the exact new
 * {@code narration} string a header edit sets) for {@link ComparisonStrategy#EXPECTED_VALUE}.
 * {@link #expectedValueFn} is the dynamic equivalent — resolved against the AFTER row's raw
 * column map at diff time — reserved for a Phase 2 calculation/derived-value column (doc
 * §25's {@code EXPECTED_CALCULATION}/{@code EXPECTED_DELTA} strategies); V1's header/detail-line
 * edits only ever need the static {@link #expectedValue}.
 */
@Getter
@Builder
public class ColumnRule {

    /** Column name, lower-case, matching the raw JDBI {@code mapToMap()} key. */
    private final String column;

    private final ChangeExpectation expectation;

    /** Comparison mechanics for this column — see {@link ComparisonStrategy}. */
    private final ComparisonStrategy strategy;

    /** Statically-known expected new value, for {@link ComparisonStrategy#EXPECTED_VALUE}/{@code EXPECTED_DELTA}. */
    private final Object expectedValue;

    /** Dynamic expected-value resolver — Phase 2 reserved, see class Javadoc. */
    private final Function<java.util.Map<String, Object>, Object> expectedValueFn;

    /** Tolerance window (seconds) for {@link ComparisonStrategy#CURRENT_TIMESTAMP_TOLERANCE}. Default 300s (5 min). */
    @Builder.Default
    private final long toleranceSeconds = 300L;

    /** Extra context appended to this column's failure message when the check fails (e.g. "this
     *  column has no legitimate reason to change for this voucher domain — a drift is a backend
     *  defect, not a test-data issue"). {@code null} for the common case of no extra context. */
    private final String diagnosticNote;

    /** Severity surfaced on the resulting {@link ValidationFailure} — {@code ERROR} by default for
     *  every existing factory; a column known to be especially defect-signaling (see
     *  {@link #mustNotChange(String, String)}) can escalate to {@code CRITICAL}. Not consulted at
     *  all when {@link #knownIssue} is {@code true} — see {@link #knownIssue(String, String)}. */
    @Builder.Default
    private final ValidationFailure.Severity severity = ValidationFailure.Severity.ERROR;

    /** {@code true} for a column whose mismatch is a KNOWN, already-tracked issue (e.g. a confirmed
     *  backend defect reported separately) — see {@link #knownIssue(String, String)}. A mismatch on
     *  such a column is surfaced via {@link ColumnCheckResult#knownIssue} instead of a
     *  {@link ValidationFailure}, so it never fails the test, but stays just as visible in the
     *  report. {@code false} for every other factory. */
    @Builder.Default
    private final boolean knownIssue = false;

    /** Convenience factory for the common {@code MUST_NOT_CHANGE}/{@code UNCHANGED} case. */
    public static ColumnRule mustNotChange(String column) {
        return ColumnRule.builder()
                .column(column)
                .expectation(ChangeExpectation.MUST_NOT_CHANGE)
                .strategy(ComparisonStrategy.UNCHANGED)
                .build();
    }

    /**
     * Convenience factory for a {@code MUST_NOT_CHANGE} column that also carries a diagnostic note
     * explaining WHY a drift here is significant — surfaced on the failure message regardless of
     * the actual before/after values seen. Escalates {@link #severity} to {@code CRITICAL} since a
     * column worth annotating this way is, by definition, one where a drift is a stronger signal
     * than an ordinary field mismatch (e.g. a column the domain has no legitimate reason to touch
     * at all).
     */
    public static ColumnRule mustNotChange(String column, String diagnosticNote) {
        return ColumnRule.builder()
                .column(column)
                .expectation(ChangeExpectation.MUST_NOT_CHANGE)
                .strategy(ComparisonStrategy.UNCHANGED)
                .diagnosticNote(diagnosticNote)
                .severity(ValidationFailure.Severity.CRITICAL)
                .build();
    }

    /**
     * Convenience factory for a {@code MUST_NOT_CHANGE} column whose mismatch is a KNOWN,
     * already-tracked issue — e.g. a confirmed backend defect reported separately, where the
     * automated check continuing to hard-fail the test run after run adds no value. Unlike
     * {@link #mustNotChange(String, String)}, a mismatch here does NOT fail the test (see
     * {@link ColumnRuleEngine}/{@link ColumnCheckResult#knownIssue}) — it is instead surfaced in
     * its own "⚠️ Known Issues" section of the report, just as visibly as a real failure would be.
     */
    public static ColumnRule knownIssue(String column, String diagnosticNote) {
        return ColumnRule.builder()
                .column(column)
                .expectation(ChangeExpectation.MUST_NOT_CHANGE)
                .strategy(ComparisonStrategy.UNCHANGED)
                .diagnosticNote(diagnosticNote)
                .knownIssue(true)
                .build();
    }

    /** Convenience factory for a column that must change to a known new value. */
    public static ColumnRule mustChangeTo(String column, Object newValue) {
        return ColumnRule.builder()
                .column(column)
                .expectation(ChangeExpectation.MUST_CHANGE)
                .strategy(ComparisonStrategy.EXPECTED_VALUE)
                .expectedValue(newValue)
                .build();
    }

    /** Convenience factory for a column that must change, exact new value not asserted. */
    public static ColumnRule mustChange(String column) {
        return ColumnRule.builder()
                .column(column)
                .expectation(ChangeExpectation.MUST_CHANGE)
                .strategy(ComparisonStrategy.NOT_EQUAL)
                .build();
    }

    /** Convenience factory for a framework-maintained "changed to current timestamp" column. */
    public static ColumnRule systemTimestamp(String column, long toleranceSeconds) {
        return ColumnRule.builder()
                .column(column)
                .expectation(ChangeExpectation.SYSTEM_CHANGE)
                .strategy(ComparisonStrategy.CURRENT_TIMESTAMP_TOLERANCE)
                .toleranceSeconds(toleranceSeconds)
                .build();
    }

    /** Convenience factory for a framework-maintained "changed to current user" column, where
     *  the AFTER value is compared directly against the logged-in username (case-insensitive) —
     *  use this only when the column itself stores a username string. */
    public static ColumnRule systemUser(String column) {
        return ColumnRule.builder()
                .column(column)
                .expectation(ChangeExpectation.SYSTEM_CHANGE)
                .strategy(ComparisonStrategy.CURRENT_USER)
                .build();
    }

    /**
     * Convenience factory for a framework-maintained column whose new value is already known at
     * changeset-build time (e.g. an ecode-FK "last modified by" column, resolved once from the
     * current username via {@code HrEmpDao.findEcodeByAbbre(...)} before this rule is built,
     * since the column itself stores an employee code, not a comparable username string — see
     * {@code VoucherEditChangeSets}). {@code SYSTEM_CHANGE} labeling (not {@code MUST_CHANGE}) —
     * the value is framework-maintained, not user-supplied — reusing the proven
     * {@code EXPECTED_VALUE} comparison rather than extending {@link ComparisonStrategy#CURRENT_USER}
     * itself, which stays reserved for columns that really do store a username string.
     */
    public static ColumnRule systemChangeTo(String column, Object expectedValue) {
        return ColumnRule.builder()
                .column(column)
                .expectation(ChangeExpectation.SYSTEM_CHANGE)
                .strategy(ComparisonStrategy.EXPECTED_VALUE)
                .expectedValue(expectedValue)
                .build();
    }

    /** Convenience factory for a column deliberately excluded from comparison. */
    public static ColumnRule ignore(String column) {
        return ColumnRule.builder()
                .column(column)
                .expectation(ChangeExpectation.IGNORE)
                .strategy(ComparisonStrategy.UNCHANGED)
                .build();
    }
}
