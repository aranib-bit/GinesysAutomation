package com.gsl.service.party;

import com.gsl.api.common.PartyClassRef;
import com.gsl.api.common.PartyKind;
import com.gsl.enums.ApiPlatform;

import java.util.List;

/**
 * The party master operations the backfills need, independent of the API platform behind them. {@code WebApiPartyMasterGateway} is the
 * implementation today; a PUBLICAPI gateway slots in here when its contract is available. A platform that cannot do something throws
 * {@link UnsupportedOperationException}, which makes {@link PartyMasterService} try the next platform.
 */
public interface PartyMasterGateway {

    ApiPlatform platform();

    /** The active classes parties can be created in (Customer class types and Vendor Master class types). */
    List<PartyClassRef> classes(PartyContext context);

    /** Creates a throw-away party of {@code cls} (the kind follows the class type) with the given name and unique id; the platform resolves its own environment codes. */
    void create(PartyClassRef cls, String name, String slId, PartyContext context);

    /** True when the master of {@code family} already lists a party with {@code slId}. */
    boolean exists(PartyKind.Family family, String slId, PartyContext context);
}
