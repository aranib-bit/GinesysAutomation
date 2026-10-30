package com.gsl.api.client;

import com.gsl.api.webapi.model.response.common.StringResultResponse;
import com.gsl.api.core.ApiClient;
import com.gsl.api.webapi.WebApiEndpoints;
import com.gsl.api.model.dc.DcCreateRequest;
import com.gsl.api.model.dc.DcCreateResponse;
import com.gsl.api.model.dc.DcLineItem;
import com.gsl.reports.StepExecutor;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

import java.time.LocalDate;

/**
 * WebAPI/PublicAPI client for DC (Delivery Challan) creation. Mirrors
 * D:\PerformanceAutomationTesting\flows\createDc.js (5 endpoint calls in k6): barcode
 * generation + save implemented here; stock-discovery and prior-delivery-check lookups
 * follow the same pattern.
 */
public class DcApiClient extends ApiClient {

    /** Wraps an already-authenticated session {@code spec} — see {@link ApiClient}'s class Javadoc. */
    public DcApiClient(RequestSpecification spec) {
        super(spec);
    }

    /** GET /DC/GetPacketBarcode — server-issued document code, no collision risk. */
    public String fetchPacketBarcode() {
        Response response = getRaw(WebApiEndpoints.Dc.PACKET_BARCODE, Map.of());
        return bodyAs(response, StringResultResponse.class).getResult();
    }

    /** POST DC/Save — the final save call. */
    public DcCreateResponse createDc(DcCreateRequest request) {
        return post(WebApiEndpoints.Dc.SAVE, request, DcCreateResponse.class);
    }

    /**
     * Negative-scenario counterpart to {@link #createDc(DcCreateRequest)} — for a deliberately
     * incomplete/invalid {@code request}, returns the raw {@link Response} instead of throwing.
     */
    public Response createDcExpectFailure(DcCreateRequest request) {
        return postRawExpectFailure(endpointUrl(WebApiEndpoints.Dc.SAVE), request);
    }

}
