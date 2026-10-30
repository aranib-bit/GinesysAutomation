package com.gsl.service.party;

import com.gsl.api.core.ApiSessionBundle;
import com.gsl.api.core.PublicApiAvailabilityChecker;
import com.gsl.api.webapi.client.PartyMasterWebApiClient;
import com.gsl.config.ConfigFactory;
import com.gsl.enums.ApiPlatform;

import java.util.ArrayList;
import java.util.List;

/** Builds the gateways in the order of {@code backfill.party.platformOrder} (default {@code WEBAPI}); a platform that is not available is left out. */
public final class PartyMasterGateways {

    private PartyMasterGateways() {}

    public static List<PartyMasterGateway> ordered(ApiSessionBundle session) {
        List<PartyMasterGateway> out = new ArrayList<>();
        for (ApiPlatform platform : ConfigFactory.getApiConfig().partyPlatformOrder()) {
            switch (platform) {
                case WEBAPI -> out.add(new WebApiPartyMasterGateway(session.getVendorApiClient(), session.getCustomerApiClient(),
                        new PartyMasterWebApiClient(session.getBaseSpec())));
                case PUBLICAPI -> {
                    if (PublicApiAvailabilityChecker.isAvailable()) out.add(new PublicApiPartyMasterGateway());
                }
                default -> { /* K6BULK is a bulk path, not a single-party gateway */ }
            }
        }
        if (out.isEmpty()) {
            out.add(new WebApiPartyMasterGateway(session.getVendorApiClient(), session.getCustomerApiClient(), new PartyMasterWebApiClient(session.getBaseSpec())));
        }
        return out;
    }
}
