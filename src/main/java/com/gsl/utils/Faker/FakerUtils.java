package com.gsl.utils.Faker;

import com.github.javafaker.Faker;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
}