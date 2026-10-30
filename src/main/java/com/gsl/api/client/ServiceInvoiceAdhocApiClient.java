package com.gsl.api.client;

import com.gsl.api.webapi.model.response.common.BooleanResultResponse;
import com.gsl.api.webapi.model.response.common.StringResultResponse;
import com.gsl.api.core.ApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.core.QueryParams;
import com.gsl.api.model.serviceinvoiceadhoc.AdmSchemeDocEntry;
import com.gsl.api.model.serviceinvoiceadhoc.CalculateServiceChargesResult;
import com.gsl.api.model.serviceinvoiceadhoc.GetOtherDetailsResult;
import com.gsl.api.model.serviceinvoiceadhoc.OwnerSiteDetail;
import com.gsl.api.model.serviceinvoiceadhoc.ServiceInvoiceAdhocCreateRequest;
import com.gsl.api.model.serviceinvoiceadhoc.ServiceInvoiceAdhocCreateResponse;
import com.gsl.api.model.serviceinvoiceadhoc.ServiceLookupResult;
import com.gsl.api.model.serviceinvoiceadhoc.SimpleServiceCharge;
import com.gsl.api.model.serviceinvoiceadhoc.TagSite;
import com.gsl.api.model.serviceinvoiceadhoc.TdsRateAndAmount;
import com.gsl.api.model.serviceinvoiceadhoc.VendorLookupResult;
import com.gsl.api.webapi.WebApiEndpoints;
import com.gsl.api.webapi.factory.ServiceInvoiceRequestFactory;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.time.LocalDate;
import java.util.List;

/**
 * WebAPI client for Service Invoice Adhoc: one method per endpoint of the real 15-step flow (captured in the k6 flow
 * {@code serviceInvoiceAdhoc.js} and confirmed against a live HAR). HTTP only; the workflow that threads the responses together, which is also
 * the backfill of AP Voucher's document-payment shortfall, lives in {@link com.gsl.service.ServiceInvoiceAdhocService}.
 *
 * <p>The 11 pure-UI GridState calls (grid/window state persistence) and the 2 vestigial calls (GetUDFMetaData, GetListOfReportlayoutInOrder, never
 * consumed by any later step) are intentionally not implemented: none of them carry business data or affect whether Save succeeds.
 */
public class ServiceInvoiceAdhocApiClient extends ApiClient {

    /** Wraps an already-authenticated session {@code spec} — see {@link ApiClient}'s class Javadoc. */
    public ServiceInvoiceAdhocApiClient(RequestSpecification spec) {
        super(spec);
    }

    /** GET /ServiceInvoice/GetOwnerSiteOtherDetails */
    public OwnerSiteDetail fetchOwnerSiteDetail(int ownerSiteCode) {
        Response response = getRawQuery(WebApiEndpoints.ServiceInvoice.OWNER_SITE_DETAILS, ServiceInvoiceRequestFactory.ownerSiteDetail(ownerSiteCode));
        return response.jsonPath().getObject("result", OwnerSiteDetail.class);
    }

    /** POST /ServiceInvoice/GetServices — the services offered at the owner site. */
    public List<ServiceLookupResult> fetchServices(int ownerSiteCode, int ouCode) {
        Response response = postRaw(WebApiEndpoints.ServiceInvoice.SERVICES,
                ServiceInvoiceRequestFactory.serviceLookup(ownerSiteCode, ouCode, LocalDate.now()));
        return response.jsonPath().getList("result.items", ServiceLookupResult.class);
    }

    /** GET /RT/GetAdmSchemeDocList */
    public List<AdmSchemeDocEntry> fetchSchemeDocList(int siteCode) {
        Response response = getRawQuery(WebApiEndpoints.ServiceInvoice.SCHEME_DOC_LIST,
                ServiceInvoiceRequestFactory.schemeDocList(siteCode, LocalDate.now()));
        return response.jsonPath().getList("result.items", AdmSchemeDocEntry.class);
    }

    /** POST /ServiceInvoice/GetVendors */
    public List<VendorLookupResult> fetchVendors(int ownerSiteCode, int ouCode) {
        Response response = postRaw(WebApiEndpoints.ServiceInvoice.VENDORS,
                ServiceInvoiceRequestFactory.vendorLookup(ownerSiteCode, ouCode, LocalDate.now()),
                QueryParams.toMap(ServiceInvoiceRequestFactory.vendorLookupQuery(ownerSiteCode, ouCode)));
        return response.jsonPath().getList("result.items", VendorLookupResult.class);
    }

    /** POST /ServiceInvoice/ValidateReferenceNo — throws if the server reports a real validation message. */
    public void validateReferenceNo(int ownerSiteCode, int vendorCode, String invoiceDate, String referenceNo) {
        Response response = postRaw(WebApiEndpoints.ServiceInvoice.VALIDATE_REFERENCE_NO,
                ServiceInvoiceRequestFactory.validateReferenceNo(ownerSiteCode, vendorCode, invoiceDate, referenceNo));
        String validationMessage = bodyAs(response, StringResultResponse.class).getResult();
        if (validationMessage != null) {
            throw new ApiException("ReferenceNo '" + referenceNo + "' failed validation: " + validationMessage, 0);
        }
    }

    /** GET /ServiceInvoice/GetOtherDetails — the real source of tradeGroupCode/term/refSite/tdsSource. */
    public GetOtherDetailsResult fetchOtherDetails(int ownerSiteCode, int ownerOuCode, int vendorCode, String transactionDate) {
        Response response = getRawQuery(WebApiEndpoints.ServiceInvoice.OTHER_DETAILS,
                ServiceInvoiceRequestFactory.otherDetails(ownerSiteCode, ownerOuCode, vendorCode, transactionDate));
        return response.jsonPath().getObject("result", GetOtherDetailsResult.class);
    }

    /** GET /ServiceInvoice/IsLedgerTaggedInFINGL_SITE */
    public boolean checkLedgerTagged(int glCode, int ownerSiteCode) {
        Response response = getRawQuery(WebApiEndpoints.ServiceInvoice.LEDGER_TAGGED, ServiceInvoiceRequestFactory.ledgerTagged(glCode, ownerSiteCode));
        return bodyAs(response, BooleanResultResponse.class).isTrue();
    }

    /**
     * POST /ServiceInvoice/CalculateServiceCharges — the live tax/charge engine. Its response is what gets embedded in the eventual Save request;
     * nothing here is a pre-canned charge table.
     */
    public CalculateServiceChargesResult calculateServiceCharges(
            ServiceLookupResult service, int quantity, int rate,
            int docSchemeCode, int vendorCode, int siteCode, String tranDate,
            int tradeGroupCode, int termCode, double exchangeRate) {
        Response response = postRaw(WebApiEndpoints.ServiceInvoice.CALCULATE_CHARGES,
                ServiceInvoiceRequestFactory.calculateCharges(service, quantity, rate),
                QueryParams.toMap(ServiceInvoiceRequestFactory.calculateChargesQuery(
                        docSchemeCode, vendorCode, siteCode, tranDate, tradeGroupCode, termCode, exchangeRate)));
        return response.jsonPath().getObject("result", CalculateServiceChargesResult.class);
    }

    /** POST /ServiceInvoice/GetCalculatedTdsRateAndAmount — bare array, empty when TDS isn't applicable. */
    public List<TdsRateAndAmount> calculateTdsRateAndAmount(
            ServiceLookupResult service, int quantity, int rate, List<SimpleServiceCharge> serviceCharges,
            List<TagSite> tagSites, int tdsSourceCode, int vendorCode, String srvDate, int admOuCode) {
        Response response = postRaw(WebApiEndpoints.ServiceInvoice.CALCULATE_TDS,
                ServiceInvoiceRequestFactory.tdsCalculation(service, quantity, rate, serviceCharges, tagSites, tdsSourceCode, vendorCode, srvDate, admOuCode));
        return response.jsonPath().getList("result", TdsRateAndAmount.class);
    }

    /** POST ServiceInvoice/Save — the final save call. */
    public ServiceInvoiceAdhocCreateResponse createServiceInvoiceAdhoc(ServiceInvoiceAdhocCreateRequest request) {
        return post(WebApiEndpoints.ServiceInvoice.SAVE, request, ServiceInvoiceAdhocCreateResponse.class);
    }

    /**
     * Negative-scenario counterpart to {@link #createServiceInvoiceAdhoc(ServiceInvoiceAdhocCreateRequest)} — for a deliberately incomplete/invalid
     * {@code request} (e.g. missing mandatory fields), returns the raw {@link Response} instead of throwing, so a test can assert on the HTTP status,
     * the {@code success} flag or the error message directly. See {@code getRawExpectFailure} in {@link ApiClient} — never use this for a request
     * expected to succeed.
     */
    public Response createServiceInvoiceAdhocExpectFailure(ServiceInvoiceAdhocCreateRequest request) {
        return postRawExpectFailure(endpointUrl(WebApiEndpoints.ServiceInvoice.SAVE), request);
    }
}
