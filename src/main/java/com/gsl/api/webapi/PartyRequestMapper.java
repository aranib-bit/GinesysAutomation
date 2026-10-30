package com.gsl.api.webapi;

import com.gsl.api.common.CityRef;
import com.gsl.api.webapi.model.request.party.SubLedgerCreateRequest;
import com.gsl.utils.Faker.party.PartyProfile;
import com.gsl.utils.Faker.party.Region;

import java.util.List;

/**
 * Copies a generated {@link PartyProfile} into a party create request (the domain mapping lives here, not in the generic generator). The name
 * and the id are NOT copied: callers keep their own, identifiable throw-away names and unique ids.
 */
public final class PartyRequestMapper {

    private PartyRequestMapper() {}

    /**
     * Applies contact, address, tax and bank data. A GSTIN is only sent together with the "Normal Registered" GST category
     * ({@code registeredGstCategory}); when the environment's category is not known, or the profile has no GSTIN, the party stays unregistered.
     */
    public static void apply(SubLedgerCreateRequest r, PartyProfile p, Long registeredGstCategory, List<CityRef> cities) {
        CityRef city = cityFor(p, cities);
        r.setAbbre(p.abbreviation());
        r.setBaddr(p.address());
        r.setBdist(city == null ? p.district() : city.name());
        r.setBctname(city == null ? "" : city.name());
        r.setBpin(p.pin());
        r.setBpH1(p.landline());
        r.setBfX2(p.mobile());
        r.setBemail(p.email());
        r.setBwebsite(p.website());
        r.setBcp(p.contactPerson());
        r.setPan(p.pan());
        r.setBankname(p.bankName());
        r.setBankaccountno(p.bankAccount());
        r.setBankifccode(p.ifsc());
        if (!p.gstin().isEmpty() && registeredGstCategory != null) {
            r.setCpgstinno(p.gstin());
            r.setCpgstinstatecode(p.gstStateCode());
            r.setCpcategorycode(registeredGstCategory);
        }
    }

    /**
     * The city master row for a party. {@code bctname} is a foreign key to the city master, so a generated city name can never be sent as is:
     * an Indian party gets an Indian city and a foreign party a non-Indian one, picked deterministically from the profile (same profile, same
     * city). When the master has no suitable city the field stays blank, which the server accepts.
     */
    static CityRef cityFor(PartyProfile p, List<CityRef> cities) {
        if (p.city().isEmpty() || cities == null || cities.isEmpty()) return null;
        boolean indian = p.region() == Region.INDIA;
        List<CityRef> matching = cities.stream().filter(c -> indian == "INDIA".equalsIgnoreCase(c.country())).toList();
        if (matching.isEmpty()) return null;
        return matching.get(Math.floorMod(p.name().hashCode() + p.city().hashCode(), matching.size()));
    }
}
