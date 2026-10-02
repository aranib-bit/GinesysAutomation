package com.gsl.api.fixture.arvoucher;

import com.gsl.config.ApiConfig;
import com.gsl.config.ConfigFactory;
import lombok.Builder;
import lombok.Value;

/** Live reference values {@link ArVoucherApiScenarioType}'s POSITIVE scenarios exercise against. */
@Value
@Builder
public class ArVoucherFixtureContext {
    int ownerSiteCode;
    int ouCode;
    long empCode;
    int quantity;
    int rate;

    /** Builds the context from the {@code fixture.arvoucher.*} keys in {@code api.properties}. */
    public static ArVoucherFixtureContext fromConfig() {
        ApiConfig config = ConfigFactory.getApiConfig();
        return ArVoucherFixtureContext.builder()
                .ownerSiteCode(config.fixtureArVoucherOwnerSiteCode())
                .ouCode(config.fixtureArVoucherOuCode())
                .empCode(config.fixtureArVoucherEmpCode())
                .quantity(1)
                .rate(500)
                .build();
    }
}
