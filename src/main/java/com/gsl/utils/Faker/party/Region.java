package com.gsl.utils.Faker.party;

/** Which kind of party data to generate. */
public enum Region {
    /** Indian names, addresses, PIN codes, mobiles, PAN, GSTIN, IFSC (Faker {@code en-IN}). */
    INDIA,
    /** Foreign names, addresses, postal codes and phone formats (Faker locales from {@code testdata.party.foreign.locales}); never a GSTIN. */
    FOREIGN,
    /** Each generated party is Indian or foreign at random. */
    MIXED
}
