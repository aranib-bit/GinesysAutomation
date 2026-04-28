package com.gsl.utils.payment;

import com.github.javafaker.Faker;

import java.util.Locale;
import java.util.UUID;

/**
 * Generates realistic, unique payment reference numbers for each payment system.
 *
 * All values are dynamically produced on every call — no hardcoded strings.
 * Follows the same static-Faker pattern as FakerUtils but uses its own instance
 * so locale (en-IN) is isolated to payment generation.
 */
public final class PaymentReferenceGenerator {

    private static final Faker faker = new Faker(new Locale("en-IN"));

    private PaymentReferenceGenerator() {}

    /** IMPS — 12 numeric digits, e.g. "034567891234" */
    public static String generateIMPS() {
        return faker.number().digits(12);
    }

    /** UPI — 12–16 numeric digits, e.g. "034567891234567" */
    public static String generateUPI() {
        int length = faker.number().numberBetween(12, 17);
        return faker.number().digits(length);
    }

    /**
     * NEFT — 16 uppercase alphanumeric chars.
     * Format: 4-alpha (bank code) + 8 epoch-ms digits + 4 random digits.
     * e.g. "HDFC202504281234"
     */
    public static String generateNEFT() {
        String bank = faker.bothify("????").toUpperCase();
        String epochSlice = String.valueOf(System.currentTimeMillis()).substring(0, 8);
        String seq = faker.number().digits(4);
        return (bank + epochSlice + seq).substring(0, 16).toUpperCase();
    }

    /**
     * RTGS — 22 uppercase alphanumeric chars.
     * Format: 5-alpha (bank/branch code) + 17 alphanumeric body.
     * e.g. "ICICI17032025ABCDE1234"
     */
    public static String generateRTGS() {
        String prefix = faker.bothify("?????").toUpperCase();
        String body   = faker.bothify("#################").toUpperCase();
        return (prefix + body).substring(0, 22).toUpperCase();
    }

    /** SWIFT UETR — UUID v4, e.g. "550e8400-e29b-41d4-a716-446655440000" */
    public static String generateSWIFT() {
        return UUID.randomUUID().toString();
    }

    /** ACH — 15 numeric digits, e.g. "034567891234567" */
    public static String generateACH() {
        return faker.number().digits(15);
    }

    /** SEPA — 10–35 alphanumeric chars, e.g. "SEPA1234567890" */
    public static String generateSEPA() {
        int length = faker.number().numberBetween(10, 36);
        return faker.bothify("???????????????????????????????????").substring(0, length).toUpperCase();
    }
}
