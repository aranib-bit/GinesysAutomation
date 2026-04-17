package com.gsl.utils.Faker;

import com.github.javafaker.Faker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class FakerUtils {

    private static final Faker faker = new Faker();

    private FakerUtils() {
        // utility class
    }

    // -------------------- Integer Generators --------------------

    /** Random int (full positive range) */
    public static int randomInt() {
        faker.random().nextInt(Integer.MAX_VALUE);
        return ThreadLocalRandom.current().nextInt();
    }

    /** Random int between min & max (inclusive) */
    public static int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    // -------------------- Double Generators --------------------

    /** Random double (0.0 — 1.0) */
    public static double randomDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }

    /** Random double between range with scale */
    public static double randomDouble(double min, double max, int scale) {
        double value = ThreadLocalRandom.current().nextDouble(min, max);

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
        double value = ThreadLocalRandom.current().nextDouble(1.0, max.doubleValue());
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
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    // -------------------- Helpers using jFaker --------------------

    public static String randomName() {
        return faker.name().fullName();
    }

    public static String randomEmail() {
        return faker.internet().emailAddress();
    }

    public static String randomPhone() {
        return faker.phoneNumber().cellPhone();
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
        return "REF-" + faker.bothify("????-####").toUpperCase();
    }

    /**
     * Generates a random, human-readable remarks/narration string using a
     * business catch-phrase, e.g. {@code "Versatile radical function"}.
     *
     * <p>Used for voucher narration (finvchmain.nartext) in DB-mode tests.
     */
    public static String randomRemarks() {
        return faker.company().catchPhrase();
    }
}