package com.gsl.utils.Faker.party;

import com.github.javafaker.Faker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Foreign party data from a list of Faker locales (e.g. en-GB, en-US, en-AU, en-CA): names, addresses, postal codes, phone and e-mail in
 * the locale's style. Never a GSTIN or IFSC (the live master has no foreign-currency or foreign-tax precedent); the PAN keeps the Indian
 * format because the PAN field is part of every create body.
 */
public final class ForeignPartyGenerator implements PartyDataGenerator {

    private final List<Faker> fakers = new ArrayList<>();
    private final Random random;

    public ForeignPartyGenerator(Random random, List<String> localeTags) {
        this.random = random;
        if (localeTags.isEmpty()) throw new IllegalArgumentException("At least one foreign locale is required");
        for (String tag : localeTags) fakers.add(new Faker(Locale.forLanguageTag(tag.trim()), random));
    }

    @Override
    public PartyProfile next(Completeness level) {
        Faker faker = fakers.get(random.nextInt(fakers.size()));
        String name = FieldLimits.fit(faker.company().name(), FieldLimits.NAME);
        String contact = FieldLimits.fit(faker.name().fullName(), FieldLimits.CONTACT_PERSON);
        boolean typical = level.atLeast(Completeness.TYPICAL);
        boolean full = level.atLeast(Completeness.FULL);
        String city = typical ? FieldLimits.fit(faker.address().city(), FieldLimits.CITY) : "";
        return new PartyProfile(Region.FOREIGN,
                name,
                FieldLimits.fit(Abbreviations.of(name), FieldLimits.ABBREVIATION),
                typical ? FieldLimits.fit(faker.address().streetAddress(), FieldLimits.ADDRESS) : "",
                city,
                city,
                typical ? FieldLimits.fit(faker.address().state(), FieldLimits.CITY) : "",
                typical ? FieldLimits.fit(faker.address().zipCode(), FieldLimits.PIN) : "",
                typical ? FieldLimits.fit(faker.phoneNumber().phoneNumber(), FieldLimits.PHONE) : "",
                FieldLimits.fit(faker.phoneNumber().cellPhone(), FieldLimits.PHONE),
                typical ? FieldLimits.fit(Emails.of(contact, name), FieldLimits.EMAIL) : "",
                full ? FieldLimits.fit(Emails.website(name), FieldLimits.WEBSITE) : "",
                contact,
                IndianIdentifiers.companyPan(random),
                "",
                "",
                full ? FieldLimits.fit(faker.company().name() + " Bank", FieldLimits.BANK_NAME) : "",
                full ? IndianIdentifiers.bankAccount(random) : "",
                "");
    }
}
