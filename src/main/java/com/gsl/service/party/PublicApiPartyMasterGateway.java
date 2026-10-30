package com.gsl.service.party;

import com.gsl.api.common.PartyClassRef;
import com.gsl.api.common.PartyKind;
import com.gsl.enums.ApiPlatform;

import java.util.List;

/**
 * Seam for the PUBLICAPI party master (OAuth2 client credentials). Not implemented until its contract is available; every operation throws
 * {@link UnsupportedOperationException}, so a configured PUBLICAPI position in {@code backfill.party.platformOrder} falls through to the next
 * platform instead of failing.
 */
public final class PublicApiPartyMasterGateway implements PartyMasterGateway {

    @Override
    public ApiPlatform platform() {
        return ApiPlatform.PUBLICAPI;
    }

    @Override
    public List<PartyClassRef> classes(PartyContext context) {
        throw new UnsupportedOperationException("PUBLICAPI party master is not implemented yet");
    }

    @Override
    public void create(PartyClassRef cls, String name, String slId, PartyContext context) {
        throw new UnsupportedOperationException("PUBLICAPI party master is not implemented yet");
    }

    @Override
    public boolean exists(PartyKind.Family family, String slId, PartyContext context) {
        throw new UnsupportedOperationException("PUBLICAPI party master is not implemented yet");
    }
}
