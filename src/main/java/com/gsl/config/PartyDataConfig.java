package com.gsl.config;

import com.gsl.utils.Faker.party.Completeness;
import org.aeonbits.owner.Config;

/** Party-specific settings of the data generator ({@code com.gsl.utils.Faker.party}); region, locales and seed are the shared {@link FakerConfig}. */
@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({
        "system:properties",
        "file:${user.dir}/src/test/resources/env/${env}/party.properties",
        "file:${user.dir}/src/test/resources/config/party.properties"
})
public interface PartyDataConfig extends Config {

    /** MINIMAL, TYPICAL (default) or FULL: which optional fields a generated party carries. */
    @Key("testdata.party.completeness")
    @DefaultValue("TYPICAL")
    Completeness completeness();
}
