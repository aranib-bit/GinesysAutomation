package com.gsl.utils.Faker.party;

/**
 * The generated data of one party. Immutable and independent of any API: the {@code webapi} request mapper copies it into a create body.
 * Fields not covered by the requested {@link Completeness} are empty strings (what the UI sends for a blank field).
 */
public record PartyProfile(
        Region region,
        String name,
        String abbreviation,
        String address,
        String district,
        String city,
        String state,
        String pin,
        String landline,
        String mobile,
        String email,
        String website,
        String contactPerson,
        String pan,
        String gstin,
        String gstStateCode,
        String bankName,
        String bankAccount,
        String ifsc) {

    /** The same profile with a GSTIN for {@code stateCode} (built from this profile's PAN); only Indian profiles can have one. */
    public PartyProfile withGstinForState(String stateCode) {
        if (region != Region.INDIA) throw new IllegalStateException("Only an Indian party can have a GSTIN");
        return new PartyProfile(region, name, abbreviation, address, district, city, state, pin, landline, mobile, email, website,
                contactPerson, pan, IndianIdentifiers.gstin(stateCode, pan, 1), stateCode, bankName, bankAccount, ifsc);
    }
}
