package com.gsl.utils;

/**
 * Weaves a short, run-scoped token into a base test-data name so repeat suite runs never collide
 * with names a previous run already created — used by {@code AccountClassLifecycle}, which has no
 * Delete action to clean up after itself, so {@code admcls} rows accumulate across runs by design.
 *
 * <p>The token is inserted immediately after any leading whitespace and before the first
 * non-whitespace character, NOT simply appended to the end — this keeps leading/trailing/internal
 * whitespace naming-convention test rows' actual whitespace characters exactly where the row
 * intends them (at the very start/end of the submitted value) instead of a token disturbing a
 * trailing-space assertion.
 *
 * <p>Rows that are exempt from uniqueification (blank/whitespace-only REJECT rows, and exact
 * duplicates of a permanent system row) must simply not call this class at all.
 */
public final class TestDataUniqueifier {

    private TestDataUniqueifier() {}

    /** Inserts a run-scoped numeric token right after any leading whitespace in {@code base}. */
    public static String uniqueify(String base) {
        if (base == null) {
            return null;
        }
        int firstNonWhitespace = 0;
        while (firstNonWhitespace < base.length() && Character.isWhitespace(base.charAt(firstNonWhitespace))) {
            firstNonWhitespace++;
        }
        String leading = base.substring(0, firstNonWhitespace);
        String rest = base.substring(firstNonWhitespace);
        return leading + token() + " " + rest;
    }

    /**
     * Builds a rerun-safe string of EXACTLY {@code targetLength} characters — a run-scoped token
     * followed by filler — for the boundary-length test, which has no static base name to
     * uniqueify (it's built from the live DB column length at test time).
     */
    public static String uniqueifyToExactLength(int targetLength) {
        String prefix = token() + "-";
        if (prefix.length() >= targetLength) {
            return prefix.substring(0, targetLength);
        }
        StringBuilder sb = new StringBuilder(prefix);
        while (sb.length() < targetLength) {
            sb.append('X');
        }
        return sb.toString();
    }

    private static String token() {
        return Long.toString(System.nanoTime() % 100_000);
    }
}
