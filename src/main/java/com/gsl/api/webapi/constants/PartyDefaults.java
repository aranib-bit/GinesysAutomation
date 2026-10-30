package com.gsl.api.webapi.constants;

/** Stable defaults of the party create requests that are not part of the POJO defaults (they differ between kinds or are placeholders until generated). */
public final class PartyDefaults {

    private PartyDefaults() {}

    /** {@code saletype} a customer sends (a vendor sends ""). */
    public static final String CUSTOMER_SALE_TYPE = "O";
    /** {@code pricetype} when the environment has no sale price list. */
    public static final String DEFAULT_PRICE_TYPE = "M";
    /** Placeholder contact data until the party data generator supplies realistic values. */
    public static final String PLACEHOLDER_CONTACT_PERSON = "abc";
    public static final String PLACEHOLDER_MOBILE = "1234567890";
}
