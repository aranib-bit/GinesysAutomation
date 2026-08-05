package com.gsl.api.fixture.item;

import com.gsl.config.ApiConfig;
import com.gsl.config.ConfigFactory;
import lombok.Builder;
import lombok.Value;

/** Live reference values {@link ItemApiScenarioType}'s POSITIVE scenarios exercise against. */
@Value
@Builder
public class ItemFixtureContext {
    int inventoryGroupCode;

    public static ItemFixtureContext fromConfig() {
        ApiConfig config = ConfigFactory.getApiConfig();
        return ItemFixtureContext.builder()
                .inventoryGroupCode(config.fixtureItemInventoryGroupCode())
                .build();
    }
}
