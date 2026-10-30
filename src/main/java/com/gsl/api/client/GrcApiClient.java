package com.gsl.api.client;

import com.gsl.api.core.ApiClient;
import com.gsl.api.webapi.WebApiEndpoints;
import com.gsl.api.webapi.model.request.grc.VendorTaxRegionQuery;
import com.gsl.api.model.common.LookupValue;
import com.gsl.api.model.grc.GrcCreateRequest;
import com.gsl.api.model.grc.GrcCreateResponse;
import com.gsl.api.model.grc.GrcLineItem;
import com.gsl.api.model.grc.VendorTaxRegion;
import com.gsl.reports.StepExecutor;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * WebAPI/PublicAPI client for GRC (Goods Receipt) creation. Mirrors
 * D:\PerformanceAutomationTesting\flows\createGrc.js (11 endpoint calls in k6) — implements
 * the vendor tax lookup + save; remaining lookups (trade group, purchase term, batch/serial
 * validation, charge calculation) follow the same get()/post() pattern via ApiClient.
 */
public class GrcApiClient extends ApiClient {

    /** Wraps an already-authenticated session {@code spec} — see {@link ApiClient}'s class Javadoc. */
    public GrcApiClient(RequestSpecification spec) {
        super(spec);
    }

    /**
     * GET /GRC/GetGRCVendorTaxRegion — routed through {@code getRaw()} (i.e. a fresh,
     * throwaway request derived from the shared spec), NOT {@code spec.queryParam(...).get(...)}
     * directly: the latter mutates the session-lifetime {@code spec} instance itself, exactly the
     * bug class {@link com.gsl.api.core.ApiClient#fresh()}'s javadoc documents (a stale query
     * param from one call leaking into a later, unrelated call on the same shared spec).
     */
    public VendorTaxRegion fetchVendorTaxRegion(int vendorCode) {
        Response response = getRawQuery(WebApiEndpoints.Grc.VENDOR_TAX_REGION, new VendorTaxRegionQuery(vendorCode));
        return response.jsonPath().getObject("result", VendorTaxRegion.class);
    }

    /** POST GRC/Save — the final save call. */
    public GrcCreateResponse createGrc(GrcCreateRequest request) {
        return post(WebApiEndpoints.Grc.SAVE, request, GrcCreateResponse.class);
    }

    /**
     * Negative-scenario counterpart to {@link #createGrc(GrcCreateRequest)} — for a deliberately
     * incomplete/invalid {@code request}, returns the raw {@link Response} instead of throwing.
     */
    public Response createGrcExpectFailure(GrcCreateRequest request) {
        return postRawExpectFailure(endpointUrl(WebApiEndpoints.Grc.SAVE), request);
    }

}
