package com.gsl.utils.Faker.party;

import com.github.javafaker.Faker;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Indian party data: Faker {@code en-IN} names and addresses plus valid PAN, GSTIN, IFSC, PIN and mobile numbers. */
public final class IndianPartyGenerator implements PartyDataGenerator {

    private static final List<String> BANKS = List.of("State Bank of India", "HDFC Bank", "ICICI Bank", "Axis Bank", "Punjab National Bank",
            "Bank of Baroda", "Canara Bank", "Kotak Mahindra Bank", "Union Bank of India", "IndusInd Bank");

    private final Faker faker;
    private final Random random;

    public IndianPartyGenerator(Random random) {
        this.random = random;
        this.faker = new Faker(new Locale("en", "IN"), random);
    }

    @Override
    public PartyProfile next(Completeness level) {
        String name = FieldLimits.fit(faker.company().name(), FieldLimits.NAME);
        String contact = FieldLimits.fit(faker.name().fullName(), FieldLimits.CONTACT_PERSON);
        String pan = IndianIdentifiers.companyPan(random);
        String stateCode = IndianIdentifiers.randomStateCode(random);

        boolean typical = level.atLeast(Completeness.TYPICAL);
        boolean full = level.atLeast(Completeness.FULL);
        String city = typical ? FieldLimits.fit(faker.address().city(), FieldLimits.CITY) : "";
        return new PartyProfile(Region.INDIA,
                name,
                FieldLimits.fit(Abbreviations.of(name), FieldLimits.ABBREVIATION),
                typical ? FieldLimits.fit(faker.address().streetAddress(), FieldLimits.ADDRESS) : "",
                city,
                city,
                typical ? FieldLimits.fit(faker.address().state(), FieldLimits.CITY) : "",
                typical ? IndianIdentifiers.pin(random) : "",
                typical ? FieldLimits.fit(String.valueOf(2_000_000 + random.nextInt(7_999_999)), FieldLimits.PHONE) : "",
                IndianIdentifiers.mobile(random),
                typical ? FieldLimits.fit(Emails.of(contact, name), FieldLimits.EMAIL) : "",
                full ? FieldLimits.fit(Emails.website(name), FieldLimits.WEBSITE) : "",
                contact,
                pan,
                full ? IndianIdentifiers.gstin(stateCode, pan, 1) : "",
                full ? stateCode : "",
                full ? BANKS.get(random.nextInt(BANKS.size())) : "",
                full ? IndianIdentifiers.bankAccount(random) : "",
                full ? IndianIdentifiers.ifsc(random) : "");
    }
}
