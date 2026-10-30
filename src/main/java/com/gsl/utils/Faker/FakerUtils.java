package com.gsl.utils.Faker;

import com.github.javafaker.Faker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Generic random test-data generator — wraps {@code javafaker} (name/email/phone/catch-phrase
 * generation) alongside plain seedable number/collection helpers (the shared {@link FakerContext}), behind one
 * shared static facade. Purely mechanical: every method produces a random value of a given shape
 * with no knowledge of which field it will populate or what business rule applies to it — callers
 * decide that. The Finance/Voucher-named generators ({@link #randomReference()},
 * {@link #randomRemarks()}, {@link #randomAmountLessThan(BigDecimal)}) are still domain-agnostic
 * in this sense: they know a reference/remarks/amount's generic *shape*, not any specific
 * voucher's business rule.
 */
public final class FakerUtils {


    private FakerUtils() {
        // utility class
    }

    // -------------------- Integer Generators --------------------

    /** Random int (full positive range) */
    public static int randomInt() {
        return FakerContext.current().random().nextInt(Integer.MAX_VALUE);
    }

    /** Random int between min & max (inclusive) */
    public static int randomInt(int min, int max) {
        return FakerContext.current().random().nextInt(min, max + 1);
    }

    // -------------------- Double Generators --------------------

    /** Random double (0.0 — 1.0) */
    public static double randomDouble() {
        return FakerContext.current().random().nextDouble();
    }

    /** Random double in {@code [min, max)}, rounded (half-up) to {@code scale} decimal places. */
    public static double randomDouble(double min, double max, int scale) {
        double value = FakerContext.current().random().nextDouble(min, max);

        return BigDecimal.valueOf(value)
                .setScale(scale, RoundingMode.HALF_UP)
                .doubleValue();
    }

    // -------------------- BigDecimal Generators --------------------

    /**
     * Returns a random BigDecimal in the range [1.00, max) with 2 decimal places.
     * Used to generate a realistic partial-settlement amount that is less than
     * a pending document total so the AR Voucher passes server-side validation.
     *
     * @param max upper bound (exclusive); must be greater than 1
     * @return random amount ≥ 1.00 and < max, rounded to 2 decimal places
     */
    public static BigDecimal randomAmountLessThan(BigDecimal max) {
        if (max == null || max.compareTo(BigDecimal.ONE) <= 0) {
            return BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP);
        }
        double value = FakerContext.current().random().nextDouble(1.0, max.doubleValue());
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    // -------------------- Collection Helpers --------------------

    /**
     * Returns a random element from the given list.
     *
     * @param list non-null, non-empty list to pick from
     * @param <T>  element type
     * @return a randomly selected element
     * @throws IllegalArgumentException if list is null or empty
     */
    public static <T> T randomElement(List<T> list) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("Cannot pick a random element from a null or empty list");
        }
        return list.get(FakerContext.current().random().nextInt(list.size()));
    }

    // -------------------- Helpers using jFaker --------------------

    /** Random full name, e.g. {@code "Jane Doe"}. */
    public static String randomName() {
        return FakerContext.current().faker().name().fullName();
    }

    /** Random email address. */
    public static String randomEmail() {
        return FakerContext.current().faker().internet().emailAddress();
    }

    /** Random cell-phone-formatted number. */
    public static String randomPhone() {
        return FakerContext.current().faker().phoneNumber().cellPhone();
    }

    // -------------------- Finance / Voucher Generators --------------------

    /**
     * Generates a random voucher reference in the format {@code REF-XXXX-9999}
     * (4 random alpha chars + 4 random digits), e.g. {@code REF-KBQM-4731}.
     *
     * <p>Suitable for AR/AP Voucher reference fields. For Finance-module tests
     * where duplicate references are intentionally tested, use the fixed string
     * {@code "REF-FIN-DUP"} instead.
     */
    public static String randomReference() {
        return "REF-" + FakerContext.current().faker().bothify("????-####").toUpperCase();
    }

    /**
     * Generates a random, human-readable remarks/narration string using a
     * business catch-phrase, e.g. {@code "Versatile radical function"}.
     *
     * <p>Used for voucher narration (finvchmain.nartext) in DB-mode tests.
     */
    public static String randomRemarks() {
        return FakerContext.current().faker().company().catchPhrase();
    }
}