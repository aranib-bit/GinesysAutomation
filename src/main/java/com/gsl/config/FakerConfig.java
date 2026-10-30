package com.gsl.config;

import com.gsl.utils.Faker.party.Region;
import org.aeonbits.owner.Config;

/**
 * Settings shared by EVERY test-data generator ({@code com.gsl.utils.Faker}: party, document text, item, ... and the {@code FakerUtils}
 * facade), read from {@code faker.properties} (env folder first). One region/locale/seed for all generated data.
 */
@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({
        "system:properties",
        "file:${user.dir}/src/test/resources/env/${env}/faker.properties",
        "file:${user.dir}/src/test/resources/config/faker.properties"
})
public interface FakerConfig extends Config {

    /** INDIA (default), FOREIGN or MIXED. */
    @Key("testdata.faker.region")
    @DefaultValue("INDIA")
    Region region();

    /** Faker locale tags used for FOREIGN and MIXED. */
    @Key("testdata.faker.foreign.locales")
    @DefaultValue("en-GB,en-US,en-AU,en-CA")
    String foreignLocales();

    /** Optional seed: set it to repeat a run exactly (the effective seed is always logged). */
    @Key("testdata.faker.seed")
    Long seed();
}
