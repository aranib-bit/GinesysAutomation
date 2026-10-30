package com.gsl.utils.Faker.party;

/** Maximum lengths of the party fields, from the {@code finsl} column definitions; generated values are trimmed to them. */
public final class FieldLimits {

    private FieldLimits() {}

    public static final int NAME = 100;
    public static final int ABBREVIATION = 25;
    public static final int SL_ID = 25;
    public static final int ADDRESS = 500;
    public static final int DISTRICT = 100;
    public static final int CITY = 100;
    public static final int PIN = 12;
    public static final int PHONE = 50;
    public static final int EMAIL = 100;
    public static final int WEBSITE = 100;
    public static final int CONTACT_PERSON = 100;
    public static final int PAN = 100;
    public static final int GSTIN = 15;
    public static final int BANK_NAME = 100;
    public static final int BANK_ACCOUNT = 100;
    public static final int IFSC = 100;

    /** {@code value} cut to {@code max} characters; null becomes "". */
    public static String fit(String value, int max) {
        if (value == null) return "";
        String v = value.trim();
        return v.length() <= max ? v : v.substring(0, max).trim();
    }
}
