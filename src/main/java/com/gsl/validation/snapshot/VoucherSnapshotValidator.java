package com.gsl.validation.snapshot;

import org.assertj.core.api.SoftAssertions;

/**
 * Orchestrator: runs {@link DiffEngine} against a BEFORE/AFTER {@link VoucherSnapshot} pair,
 * renders the result via {@link SnapshotValidationReporter}, and collects every failure into one
 * shared {@link SoftAssertions} — doc §26-28, plan §1. {@link #assertAll()} must be called
 * exactly once, at the very end of the test, never per-table (plan §8's "collect everything,
 * assert once" rule).
 */
public class VoucherSnapshotValidator {

    private final DiffEngine diffEngine = new DiffEngine();
    private final SoftAssertions softAssertions = new SoftAssertions();

    /**
     * Diffs {@code before}/{@code after} per {@code context}'s persistence map + {@code changeSet}'s
     * scenario overrides, renders the Extent report, and stages every failure onto this
     * validator's shared {@link SoftAssertions} (not asserted yet — see {@link #assertAll()}).
     */
    public void validate(String label, DocumentContext context, VoucherSnapshot before, VoucherSnapshot after,
            ExpectedChangeSet changeSet) {
        DiffResult result = diffEngine.diff(context, before, after, changeSet);
        SnapshotValidationReporter.render(label, result);

        for (ValidationFailure failure : result.failures()) {
            softAssertions.assertThat(false).as(failure.message()).isTrue();
        }
    }

    /** Surfaces every failure collected across every {@link #validate} call so far. Call exactly once. */
    public void assertAll() {
        softAssertions.assertAll();
    }
}
