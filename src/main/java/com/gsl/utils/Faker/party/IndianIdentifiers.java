package com.gsl.utils.Faker.party;

import java.util.Random;

/** Well-formed Indian identifiers (PAN, GSTIN with its checksum, IFSC, PIN, mobile, bank account). Pure functions of the given {@link Random}. */
public final class IndianIdentifiers {

    private IndianIdentifiers() {}

    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    /** The 36-character alphabet of the GSTIN checksum. */
    private static final String GSTIN_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    /** The 4th PAN character: holder type (C company, P person, F firm, H HUF, A association, T trust, B body, L local authority, J juridical, G government). */
    private static final String PAN_HOLDER_TYPES = "CPFHATBLJG";

    /** Valid GST state codes ("01".."37"). */
    public static final int[] GST_STATE_CODES = java.util.stream.IntStream.rangeClosed(1, 37).toArray();

    private static char pick(String alphabet, Random r) {
        return alphabet.charAt(r.nextInt(alphabet.length()));
    }

    /** A PAN of the given holder type (the 4th character): {@code AAAHA9999A}. */
    public static String pan(Random r, char holderType) {
        if (PAN_HOLDER_TYPES.indexOf(holderType) < 0) throw new IllegalArgumentException("Not a PAN holder type: " + holderType);
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 3; i++) sb.append(pick(LETTERS, r));
        sb.append(holderType).append(pick(LETTERS, r));
        sb.append(String.format("%04d", 1 + r.nextInt(9999)));
        return sb.append(pick(LETTERS, r)).toString();
    }

    /** A company PAN. */
    public static String companyPan(Random r) {
        return pan(r, 'C');
    }

    /** True for a well-formed PAN ({@code AAAAA9999A}, upper case). */
    public static boolean isValidPan(String pan) {
        return pan != null && pan.matches("[A-Z]{3}[CPFHATBLJG][A-Z][0-9]{4}[A-Z]");
    }

    /** The GSTIN checksum character of the first 14 characters. */
    public static char gstinCheckChar(String first14) {
        int sum = 0;
        for (int i = 0; i < 14; i++) {
            int value = GSTIN_ALPHABET.indexOf(first14.charAt(i));
            if (value < 0) throw new IllegalArgumentException("Bad GSTIN character: " + first14.charAt(i));
            int product = value * (i % 2 == 0 ? 1 : 2);
            sum += product / 36 + product % 36;
        }
        return GSTIN_ALPHABET.charAt((36 - sum % 36) % 36);
    }

    /** A GSTIN: state code + PAN + entity number (1-9) + 'Z' + checksum. */
    public static String gstin(String stateCode, String pan, int entityNumber) {
        if (stateCode == null || !stateCode.matches("\\d{2}")) throw new IllegalArgumentException("State code must be two digits: " + stateCode);
        if (!isValidPan(pan)) throw new IllegalArgumentException("Not a valid PAN: " + pan);
        if (entityNumber < 1 || entityNumber > 9) throw new IllegalArgumentException("Entity number must be 1-9: " + entityNumber);
        String first14 = stateCode + pan + entityNumber + "Z";
        return first14 + gstinCheckChar(first14);
    }

    /** True when the 15-character GSTIN has the right shape and checksum. */
    public static boolean isValidGstin(String gstin) {
        return gstin != null && gstin.matches("\\d{2}[A-Z]{3}[CPFHATBLJG][A-Z]\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]")
                && gstinCheckChar(gstin.substring(0, 14)) == gstin.charAt(14);
    }

    /** A two-digit GST state code chosen at random. */
    public static String randomStateCode(Random r) {
        return String.format("%02d", GST_STATE_CODES[r.nextInt(GST_STATE_CODES.length)]);
    }

    /** An IFSC: four letters, a zero, six alphanumerics. */
    public static String ifsc(Random r) {
        StringBuilder sb = new StringBuilder(11);
        for (int i = 0; i < 4; i++) sb.append(pick(LETTERS, r));
        sb.append('0');
        for (int i = 0; i < 6; i++) sb.append(pick(DIGITS + LETTERS, r));
        return sb.toString();
    }

    /** A six-digit PIN that does not start with 0. */
    public static String pin(Random r) {
        return String.valueOf(1 + r.nextInt(9)) + String.format("%05d", r.nextInt(100000));
    }

    /** A ten-digit mobile number starting 6-9. */
    public static String mobile(Random r) {
        return String.valueOf(6 + r.nextInt(4)) + String.format("%09d", r.nextInt(1_000_000_000));
    }

    /** An eleven-digit bank account number. */
    public static String bankAccount(Random r) {
        return String.valueOf(1 + r.nextInt(9)) + String.format("%010d", r.nextLong(10_000_000_000L));
    }
}
