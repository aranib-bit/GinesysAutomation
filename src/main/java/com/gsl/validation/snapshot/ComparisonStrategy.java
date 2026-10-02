package com.gsl.validation.snapshot;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Column-level comparison mechanics — doc §23, plan §1. Each constant knows how to decide
 * pass/fail for one {@link ColumnRule} given the normalized BEFORE/AFTER values.
 *
 * <p>{@code EXPECTED_CALCULATION} is a Phase 2 placeholder (round-off/cascade calculations —
 * explicitly out of V1's scope per the plan's "Confirmed scope decisions" #2) — it always
 * passes today rather than guessing a formula, so a {@link ColumnRule} using it never fails
 * before the real calculation is implemented.
 */
public enum ComparisonStrategy {

    /** Before and after are exactly equal (post-normalization). */
    EXACT,

    /** Before and after differ. */
    NOT_EQUAL,

    /** After &gt; before (numeric or timestamp). */
    GREATER_THAN,

    /** After &lt; before (numeric or timestamp). */
    LESS_THAN,

    /** After equals the current logged-in user (case-insensitive). */
    CURRENT_USER,

    /** After is a timestamp within {@code toleranceSeconds} of "now" (and, when before is
     *  present/parsable, strictly &gt;= before). */
    CURRENT_TIMESTAMP_TOLERANCE,

    /** After equals {@link ColumnRule#getExpectedValue()}. */
    EXPECTED_VALUE,

    /** {@code after - before} equals {@link ColumnRule#getExpectedValue()} (numeric delta). */
    EXPECTED_DELTA,

    /** Phase 2 placeholder — see class Javadoc. Always passes today. */
    EXPECTED_CALCULATION,

    /** Alias for {@link #EXACT} — the natural strategy name for {@code MUST_NOT_CHANGE}. */
    UNCHANGED,

    /** After (or before, for a DELETE row check) is non-null/non-blank. */
    NOT_NULL,

    /** After (or before) is null/blank. */
    NULL,

    /** After is one of {@link ColumnRule#getAllowedValues()} (case-insensitive) — e.g.
     *  {@code release_status} must be exactly {@code "P"} or {@code "U"}, never anything else. */
    ONE_OF,

    /** After is numerically zero, OR blank/null (accepts either DB representation defensively —
     *  a "not applicable for this domain" numeric column, e.g. {@code finpost.adjamt} for General
     *  Voucher, is confirmed live to store a literal {@code 0}, not SQL {@code NULL}). */
    ZERO;

    /**
     * Evaluates this strategy for one column.
     *
     * @param before      normalized BEFORE value (already run through {@link DbValueNormalizer})
     * @param after       normalized AFTER value
     * @param rule        the owning {@link ColumnRule} (for expectedValue/toleranceSeconds)
     * @param currentUser the currently logged-in user, for {@link #CURRENT_USER}
     * @return {@code true} when the AFTER value satisfies this strategy's expectation
     */
    public boolean evaluate(Object before, Object after, ColumnRule rule, String currentUser) {
        return switch (this) {
            case EXACT, UNCHANGED -> DbValueNormalizer.equalsNormalized(before, after);
            case NOT_EQUAL -> !DbValueNormalizer.equalsNormalized(before, after);
            case NOT_NULL -> !DbValueNormalizer.isBlank(after);
            case NULL -> DbValueNormalizer.isBlank(after);
            case ONE_OF -> evaluateOneOf(after, rule);
            case ZERO -> evaluateZero(after);
            case CURRENT_USER -> after != null && currentUser != null
                    && after.toString().trim().equalsIgnoreCase(currentUser.trim());
            case CURRENT_TIMESTAMP_TOLERANCE -> evaluateTimestampTolerance(before, after, rule);
            case EXPECTED_VALUE -> evaluateExpectedValue(after, rule);
            case EXPECTED_DELTA -> evaluateExpectedDelta(before, after, rule);
            case GREATER_THAN -> compareOrdered(before, after) > 0;
            case LESS_THAN -> compareOrdered(before, after) < 0;
            // Phase 2 placeholder — never fails until the real calculation is implemented (see class Javadoc).
            case EXPECTED_CALCULATION -> true;
        };
    }

    /** {@code true} when {@code after} is a zero-valued numeric, or blank/null — see {@link #ZERO}. */
    private static boolean evaluateZero(Object after) {
        BigDecimal bd = DbValueNormalizer.asBigDecimal(after);
        return bd == null ? DbValueNormalizer.isBlank(after) : bd.compareTo(BigDecimal.ZERO) == 0;
    }

    /** {@code true} when {@code after}'s trimmed string form case-insensitively matches one of
     *  {@code rule}'s {@link ColumnRule#getAllowedValues()} — see {@link #ONE_OF}. */
    private static boolean evaluateOneOf(Object after, ColumnRule rule) {
        if (after == null || rule.getAllowedValues() == null) return false;
        String afterStr = after.toString().trim();
        return rule.getAllowedValues().stream().anyMatch(v -> v != null && v.trim().equalsIgnoreCase(afterStr));
    }

    /** {@code true} when {@code after} equals {@link ColumnRule#getExpectedValue()} — numerically
     *  (via {@link BigDecimal#compareTo}) when the expected value is a {@link BigDecimal}, else via
     *  {@link DbValueNormalizer#equalsNormalized}. See {@link #EXPECTED_VALUE}. */
    private static boolean evaluateExpectedValue(Object after, ColumnRule rule) {
        Object expected = rule.getExpectedValue();
        if (expected instanceof BigDecimal expectedBd) {
            BigDecimal actualBd = DbValueNormalizer.asBigDecimal(after);
            return actualBd != null && actualBd.compareTo(expectedBd) == 0;
        }
        return DbValueNormalizer.equalsNormalized(expected, after);
    }

    /** {@code true} when {@code after - before} numerically equals {@link ColumnRule#getExpectedValue()};
     *  {@code false} if any of the three isn't numeric. See {@link #EXPECTED_DELTA}. */
    private static boolean evaluateExpectedDelta(Object before, Object after, ColumnRule rule) {
        BigDecimal beforeBd = DbValueNormalizer.asBigDecimal(before);
        BigDecimal afterBd = DbValueNormalizer.asBigDecimal(after);
        BigDecimal expectedDelta = DbValueNormalizer.asBigDecimal(rule.getExpectedValue());
        if (beforeBd == null || afterBd == null || expectedDelta == null) return false;
        return afterBd.subtract(beforeBd).compareTo(expectedDelta) == 0;
    }

    /** {@code true} when {@code after} is timestamp-like, within {@code rule}'s
     *  {@link ColumnRule#getToleranceSeconds()} of "now", AND (when {@code before} is itself
     *  timestamp-like) not earlier than {@code before}. See {@link #CURRENT_TIMESTAMP_TOLERANCE}. */
    private static boolean evaluateTimestampTolerance(Object before, Object after, ColumnRule rule) {
        LocalDateTime afterTs = DbValueNormalizer.asLocalDateTime(after);
        if (afterTs == null) return false;
        LocalDateTime now = LocalDateTime.now();
        long secondsFromNow = Math.abs(java.time.Duration.between(afterTs, now).getSeconds());
        boolean withinTolerance = secondsFromNow <= rule.getToleranceSeconds();

        LocalDateTime beforeTs = DbValueNormalizer.asLocalDateTime(before);
        boolean movedForwardOrNewlySet = beforeTs == null || !afterTs.isBefore(beforeTs);
        return withinTolerance && movedForwardOrNewlySet;
    }

    /** Numeric-first, timestamp-fallback ordering compare; 0 (no-op, neither strategy fires) if neither shape applies. */
    private static int compareOrdered(Object before, Object after) {
        BigDecimal beforeBd = DbValueNormalizer.asBigDecimal(before);
        BigDecimal afterBd = DbValueNormalizer.asBigDecimal(after);
        if (beforeBd != null && afterBd != null) return afterBd.compareTo(beforeBd);

        LocalDateTime beforeTs = DbValueNormalizer.asLocalDateTime(before);
        LocalDateTime afterTs = DbValueNormalizer.asLocalDateTime(after);
        if (beforeTs != null && afterTs != null) return afterTs.compareTo(beforeTs);

        return 0;
    }
}
