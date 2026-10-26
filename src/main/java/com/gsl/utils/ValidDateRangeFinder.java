package com.gsl.utils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.function.Predicate;

/**
 * Finds the contiguous run of acceptable dates around an anchor date using only a yes/no acceptance probe — no
 * knowledge of how the probe decides (accounting year, open month, ...). Generic mechanics only: it is handed a
 * {@code Predicate<LocalDate>} and knows nothing about vouchers or the API.
 *
 * <p>Each edge is found by doubling the step away from the anchor until the probe rejects (or {@code maxSpanDays} is
 * reached), then binary-searching between the last accepted and first rejected date. Cost is roughly
 * {@code 2 * (log2(span) + log2(span))} probes per call.
 *
 * <p>The result says whether each edge was actually observed. When the probe never rejects within
 * {@code maxSpanDays} the edge is reported as <em>unbounded</em> so the caller can fail loudly instead of treating
 * the search limit as a real boundary.
 */
public final class ValidDateRangeFinder {

    private ValidDateRangeFinder() {}

    /**
     * @param first        earliest accepted date found
     * @param last         latest accepted date found
     * @param firstBounded true when the date just before {@code first} was observed to be rejected
     * @param lastBounded  true when the date just after {@code last} was observed to be rejected
     * @param probes       how many times the probe was called
     */
    public record Range(LocalDate first, LocalDate last, boolean firstBounded, boolean lastBounded, int probes) {
        public boolean fullyBounded() { return firstBounded && lastBounded; }
    }

    /**
     * @param anchor      a date the probe is expected to accept (checked; {@link IllegalArgumentException} otherwise)
     * @param accepted    the acceptance probe
     * @param maxSpanDays furthest distance from the anchor to search in either direction
     */
    public static Range find(LocalDate anchor, Predicate<LocalDate> accepted, int maxSpanDays) {
        int[] probes = {0};
        Predicate<LocalDate> counting = d -> { probes[0]++; return accepted.test(d); };
        if (!counting.test(anchor)) {
            throw new IllegalArgumentException("ValidDateRangeFinder: anchor date " + anchor + " is not accepted by the probe");
        }
        Edge down = edge(anchor, -1, counting, maxSpanDays);
        Edge up = edge(anchor, +1, counting, maxSpanDays);
        return new Range(down.date, up.date, down.bounded, up.bounded, probes[0]);
    }

    private record Edge(LocalDate date, boolean bounded) {}

    /** Last accepted date walking from {@code anchor} in {@code direction} (-1 or +1). */
    private static Edge edge(LocalDate anchor, int direction, Predicate<LocalDate> accepted, int maxSpanDays) {
        long lastGood = 0;        // distance (days) of the farthest date known accepted
        long firstBad = -1;       // distance of the nearest date known rejected; -1 = none seen yet
        long step = 1;
        while (lastGood < maxSpanDays) {
            long next = Math.min(lastGood + step, maxSpanDays);
            if (accepted.test(anchor.plusDays(direction * next))) {
                lastGood = next;
                step *= 2;
            } else {
                firstBad = next;
                break;
            }
        }
        if (firstBad < 0) {
            return new Edge(anchor.plusDays(direction * lastGood), false);
        }
        while (firstBad - lastGood > 1) {
            long mid = lastGood + (firstBad - lastGood) / 2;
            if (accepted.test(anchor.plusDays(direction * mid))) {
                lastGood = mid;
            } else {
                firstBad = mid;
            }
        }
        return new Edge(anchor.plusDays(direction * lastGood), true);
    }

    /** Inclusive day count of {@code range}. */
    public static long spanDays(Range range) {
        return ChronoUnit.DAYS.between(range.first(), range.last()) + 1;
    }
}
