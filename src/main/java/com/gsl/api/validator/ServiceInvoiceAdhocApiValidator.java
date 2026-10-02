package com.gsl.api.validator;

import com.gsl.api.core.ApiException;
import com.gsl.api.model.serviceinvoiceadhoc.AdmSchemeDocEntry;
import com.gsl.api.model.serviceinvoiceadhoc.CalculateServiceChargesResult;
import com.gsl.api.model.serviceinvoiceadhoc.GetOtherDetailsResult;
import com.gsl.api.model.serviceinvoiceadhoc.OwnerSiteDetail;
import com.gsl.api.model.serviceinvoiceadhoc.ServiceInvoiceAdhocCreateResponse;
import com.gsl.api.model.serviceinvoiceadhoc.ServiceLookupResult;
import com.gsl.api.model.serviceinvoiceadhoc.VendorLookupResult;

import java.util.List;

/**
 * Validates {@link com.gsl.api.client.ServiceInvoiceAdhocApiClient} responses — both the
 * terminal {@code ServiceInvoice/Save} response (the only one this validator originally checked,
 * consumed by the AP Voucher backfill path) AND every intermediate lookup/calculate response, so
 * {@code com.tests.API.ServiceInvoiceAdhocApiTest}'s positive scenarios can assert against a
 * single, reusable source of truth for "what does a valid response look like" instead of
 * duplicating that logic inline per scenario.
 */
public final class ServiceInvoiceAdhocApiValidator {

    private ServiceInvoiceAdhocApiValidator() {}

    /**
     * The terminal Save response — success flag + non-blank scheme doc number.
     *
     * @throws ApiException if {@code response} reports {@code success=false} or has a blank {@code schemeDocno}
     */
    public static void validate(ServiceInvoiceAdhocCreateResponse response) {
        ApiResponseValidator.requireSuccess(response.isSuccess(), response.getError(), "ServiceInvoice/Save");
        ApiResponseValidator.requireNonBlank(response.getSchemeDocno(), "schemeDocno", "ServiceInvoice/Save");
    }

    /**
     * GetOwnerSiteOtherDetails — must resolve to a non-null result.
     *
     * @throws ApiException if {@code detail} is {@code null}
     */
    public static void validateOwnerSiteDetail(OwnerSiteDetail detail) {
        if (detail == null) {
            throw new ApiException("GetOwnerSiteOtherDetails returned no result", 0);
        }
    }

    /**
     * GetServices — a well-formed (possibly empty) list; never null.
     *
     * @throws ApiException if {@code services} is {@code null}
     */
    public static void validateServices(List<ServiceLookupResult> services) {
        if (services == null) {
            throw new ApiException("GetServices returned a null list", 0);
        }
    }

    /**
     * GetAdmSchemeDocList — a well-formed (possibly empty) list; never null.
     *
     * @throws ApiException if {@code schemes} is {@code null}
     */
    public static void validateSchemeDocList(List<AdmSchemeDocEntry> schemes) {
        if (schemes == null) {
            throw new ApiException("GetAdmSchemeDocList returned a null list", 0);
        }
    }

    /**
     * GetVendors — must resolve at least one live vendor for a valid site/ou.
     *
     * @throws ApiException if {@code vendors} is {@code null} or empty
     */
    public static void validateVendors(List<VendorLookupResult> vendors) {
        if (vendors == null || vendors.isEmpty()) {
            throw new ApiException("GetVendors returned no vendors", 0);
        }
    }

    /**
     * GetOtherDetails — must resolve to a non-null result for a valid vendor.
     *
     * @throws ApiException if {@code details} is {@code null}
     */
    public static void validateOtherDetails(GetOtherDetailsResult details) {
        if (details == null) {
            throw new ApiException("GetOtherDetails returned no result", 0);
        }
    }

    /**
     * CalculateServiceCharges — must resolve to a non-null result for valid input.
     *
     * @throws ApiException if {@code result} is {@code null}
     */
    public static void validateServiceCharges(CalculateServiceChargesResult result) {
        if (result == null) {
            throw new ApiException("CalculateServiceCharges returned no result", 0);
        }
    }
}
