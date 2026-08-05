package com.gsl.api.fixture.serviceinvoiceadhoc;

import com.gsl.config.ApiConfig;
import com.gsl.config.ConfigFactory;
import lombok.Builder;
import lombok.Value;

/**
 * Live reference values {@link ServiceInvoiceAdhocApiScenarioType}'s POSITIVE/BOUNDARY scenarios
 * exercise against — the same kind of already-known-valid site/vendor pair
 * {@code PendingApDocumentBackfillStrategy} resolves live from the DB for this exact flow, but
 * sourced from config here since these tests exercise the API layer's own request/response
 * contract, not the DB-resolution logic {@code DbApVoucherDataSource} already covers separately.
 */
@Value
@Builder
public class ServiceInvoiceAdhocFixtureContext {
    int ownerSiteCode;
    /** No distinct "OU code" source exists yet — assumed 1:1 with ownerSiteCode, matching
     *  {@code ServiceInvoiceAdhocApiClient.createServiceInvoiceAdhocWithDefaults}'s own assumption. */
    int ouCode;
    String vendorSlCode;
    int quantity;
    int rate;

    public static ServiceInvoiceAdhocFixtureContext fromConfig() {
        ApiConfig config = ConfigFactory.getApiConfig();
        int ownerSiteCode = Integer.parseInt(config.fixtureServiceInvoiceAdhocOwnerSiteCode());
        return ServiceInvoiceAdhocFixtureContext.builder()
                .ownerSiteCode(ownerSiteCode)
                .ouCode(ownerSiteCode)
                .vendorSlCode(config.fixtureServiceInvoiceAdhocVendorSlCode())
                .quantity(1)
                .rate(500)
                .build();
    }
}
