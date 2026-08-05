package com.gsl.api.fixture.transferout;

import com.gsl.config.ApiConfig;
import com.gsl.config.ConfigFactory;
import lombok.Builder;
import lombok.Value;

/** Live reference values {@link TransferOutApiScenarioType}'s POSITIVE scenarios exercise against. */
@Value
@Builder
public class TransferOutFixtureContext {
    String dcCode;
    String itemCode;
    int quantity;
    int rate;

    public static TransferOutFixtureContext fromConfig() {
        ApiConfig config = ConfigFactory.getApiConfig();
        return TransferOutFixtureContext.builder()
                .dcCode(config.fixtureTransferOutDcCode())
                .itemCode(config.fixtureTransferOutItemCode())
                .quantity(1)
                .rate(500)
                .build();
    }
}
