package com.gsl.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generic "exact ratio" sequence builder — Round 20 (General/AP/AR Voucher known/unknown cheque
 * split). Builds a list of exactly {@code count} values, containing entries in proportion to
 * their weights (rounded, remainder absorbed by the last entry), in SHUFFLED order.
 *
 * <p>This is the "controlled sequence, shuffled" pattern for when an EXACT ratio is required over
 * a known, fixed batch size — distinct from a per-call weighted RANDOM pick, which is only a
 * statistical approximation over many independent calls and can drift noticeably from the target
 * ratio for a small batch (e.g. "you should not expect exactly 7 CASH/2 CHEQUE/1 NEFT out of 10").
 * Fits this codebase's Fixture Factory classes well because their combination lists
 * ({@code GeneralVoucherCombinations.allCombinations()}, {@code SelectDocumentCombinations}'s own
 * equivalent) are already built as ONE fixed, known-size list up front, not a live open-ended
 * stream — so the exact sequence can be built once, per run, over exactly the rows that need it.
 *
 * <p>Uses weights, not percentages — entries don't need to sum to 100 (e.g. {@code 9}/{@code 1}
 * reads the same as 90%/10% without that constraint).
 */
public final class WeightedSequenceBuilder {

    private WeightedSequenceBuilder() {}

    /** One weighted value to include in the built sequence. */
    public record Entry<T>(T value, int weight) {}

    /**
     * Builds a list of exactly {@code count} values, containing {@code entries} in proportion to
     * their weights (rounded; the LAST entry absorbs whatever rounding remainder is left, so the
     * total is always exactly {@code count}), then shuffles the result.
     *
     * @throws IllegalArgumentException if the entries' total weight is not positive
     */
    public static <T> List<T> build(int count, List<Entry<T>> entries) {
        int totalWeight = entries.stream().mapToInt(Entry::weight).sum();
        if (totalWeight <= 0) {
            throw new IllegalArgumentException("Total weight must be greater than zero");
        }
        List<T> seq = new ArrayList<>(count);
        int assigned = 0;
        for (int i = 0; i < entries.size(); i++) {
            Entry<T> e = entries.get(i);
            int n = (i == entries.size() - 1)
                    ? (count - assigned) // last entry absorbs the rounding remainder
                    : (int) Math.round(count * ((double) e.weight() / totalWeight));
            for (int k = 0; k < n; k++) {
                seq.add(e.value());
            }
            assigned += n;
        }
        Collections.shuffle(seq, ThreadLocalRandom.current());
        return seq;
    }
}
