package com.gsl.utils.Faker.party;

/**
 * Which optional fields a generated party carries. Deterministic by level (not by probability) so a run is explainable; the levels follow
 * how often the live data fills each field (customers: address 94%, city 92%, PIN 72%, e-mail 47%, website 39%, GSTIN 35%, bank ~40%).
 */
public enum Completeness {
    /** Name, abbreviation, mobile, contact person, PAN. */
    MINIMAL,
    /** MINIMAL + address, city, district, state, PIN, landline, e-mail. */
    TYPICAL,
    /** TYPICAL + website, GSTIN (Indian only) and bank details. */
    FULL;

    public boolean atLeast(Completeness other) {
        return compareTo(other) >= 0;
    }
}
