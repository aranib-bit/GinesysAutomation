package com.gsl.api.integration;

import com.gsl.api.client.ServiceInvoiceAdhocApiClient;
import com.gsl.api.model.serviceinvoiceadhoc.ServiceInvoiceAdhocCreateResponse;
import com.gsl.api.validator.ServiceInvoiceAdhocApiValidator;
import com.gsl.reports.ReportLogger;

/**
 * Hybrid API+DB setup for Service Invoice Adhoc: create via {@link ServiceInvoiceAdhocApiClient},
 * validate the response. DB-side row confirmation deferred pending live-schema access — see
 * {@code ItemDataSetup}. Also the engine behind {@code PendingApDocumentBackfillStrategy}.
 */
public final class ServiceInvoiceAdhocDataSetup {

    private ServiceInvoiceAdhocDataSetup() {}

    public static ServiceInvoiceAdhocCreateResponse setupServiceInvoiceAdhocForTest(
            ServiceInvoiceAdhocApiClient client, String vendorSlCode, int ownerSiteCode,
            int quantity, int rate) {
        ServiceInvoiceAdhocCreateResponse response =
                client.createServiceInvoiceAdhocWithDefaults(vendorSlCode, ownerSiteCode, quantity, rate);
        ServiceInvoiceAdhocApiValidator.validate(response);
        ReportLogger.business("[ServiceInvoiceAdhocDataSetup] Service Invoice Adhoc created via API — invoiceCode={}, schemeDocno={}",
                response.getInvoiceCode(), response.getSchemeDocno());
        if (response.getReleaseStatus() != null) {
            ReportLogger.dataBackfill("[ServiceInvoiceAdhocDataSetup] Save response releaseStatus='{}' for invoiceCode={}",
                    response.getReleaseStatus(), response.getInvoiceCode());
        }
        if (response.getValidationErrors() != null && !response.getValidationErrors().isEmpty()) {
            ReportLogger.dataBackfillWarn("[ServiceInvoiceAdhocDataSetup] validationErrors returned for invoiceCode={}: {}",
                    response.getInvoiceCode(), response.getValidationErrors());
        }
        return response;
    }
}
