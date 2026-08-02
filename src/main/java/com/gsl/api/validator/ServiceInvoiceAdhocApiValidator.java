package com.gsl.api.validator;

import com.gsl.api.model.serviceinvoiceadhoc.ServiceInvoiceAdhocCreateResponse;

/** Validates a Service Invoice Adhoc (ServiceInvoice/Save) response: success + scheme docno. */
public final class ServiceInvoiceAdhocApiValidator {

    private ServiceInvoiceAdhocApiValidator() {}

    public static void validate(ServiceInvoiceAdhocCreateResponse response) {
        ApiResponseValidator.requireSuccess(response.isSuccess(), response.getError(), "ServiceInvoice/Save");
        ApiResponseValidator.requireNonBlank(response.getSchemeDocno(), "schemeDocno", "ServiceInvoice/Save");
    }
}
