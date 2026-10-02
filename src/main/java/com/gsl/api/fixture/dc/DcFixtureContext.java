package com.gsl.api.fixture.dc;

import com.gsl.config.ApiConfig;
import com.gsl.config.ConfigFactory;
import lombok.Builder;
import lombok.Value;

/** Live reference values {@link DcApiScenarioType}'s POSITIVE scenarios exercise against. */
@Value
@Builder
public class DcFixtureContext {
    String itemCode;
    int quantity;
    int rate;

    /** Builds the context from the {@code fixture.dc.*} keys in {@code api.properties}. */
    public static DcFixtureContext fromConfig() {
        ApiConfig config = ConfigFactory.getApiConfig();
        return DcFixtureContext.builder()
                .itemCode(config.fixtureDcItemCode())
                .quantity(1)
                .rate(500)
                .build();
    }
}
