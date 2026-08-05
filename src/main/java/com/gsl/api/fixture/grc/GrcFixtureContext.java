package com.gsl.api.fixture.grc;

import com.gsl.config.ApiConfig;
import com.gsl.config.ConfigFactory;
import lombok.Builder;
import lombok.Value;

/** Live reference values {@link GrcApiScenarioType}'s POSITIVE scenarios exercise against. */
@Value
@Builder
public class GrcFixtureContext {
    int vendorCode;
    int siteCode;
    int ouCode;
    String itemCode;
    int quantity;
    int rate;

    public static GrcFixtureContext fromConfig() {
        ApiConfig config = ConfigFactory.getApiConfig();
        return GrcFixtureContext.builder()
                .vendorCode(config.fixtureGrcVendorCode())
                .siteCode(config.fixtureGrcSiteCode())
                .ouCode(config.fixtureGrcOuCode())
                .itemCode(config.fixtureGrcItemCode())
                .quantity(1)
                .rate(500)
                .build();
    }
}
