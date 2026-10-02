package com.gsl.validation.ui;

import java.util.List;
import java.util.Optional;

/**
 * Registry of {@link UiKnownIssue}s — a CONFIRMED, already-tracked UI defect that must stay visible
 * (amber, non-blocking) without failing a test; the UI counterpart of the DB snapshot validator's
 * {@code ColumnRule.knownIssue}. Adding one is one entry below, no change to any check.
 * {@link UiValidationFailure} consults it when a check would FAIL: an exact match
 * (component + property + actual value) turns the result into
 * {@link UiValidationFailure.Status#KNOWN_ISSUE} with the note attached.
 *
 * <p><b>Not for style drift.</b> Expected font/colour values are a baseline, not a defect list: the
 * observed live values are recorded in the Excel style baseline ({@code UiStyleBaseline}), which is
 * why the Copy button, Add-button text and Allocate Sites label entries that used to live here were
 * removed (2026-10-03). Use this registry only for a real defect awaiting a fix; remove the entry
 * once the app is fixed — the report lists registered entries whose check now PASSES under
 * "known issue now resolved".
 */
public final class UiKnownIssues {

    private UiKnownIssues() {}

    private static final List<UiKnownIssue> ENTRIES = List.of();

    /** The note of the registered issue this FAIL matches exactly, if any. */
    public static Optional<String> match(String component, String property, String actual) {
        for (UiKnownIssue e : ENTRIES) {
            if (e.matches(component, property, actual)) return Optional.of(e.note());
        }
        return Optional.empty();
    }

    /** All registered entries (for the "now resolved" check in the report). */
    public static List<UiKnownIssue> all() {
        return ENTRIES;
    }
}
