package com.gsl.api.client;

import com.gsl.api.core.ApiClient;
import com.gsl.api.model.dc.DcCreateRequest;
import com.gsl.api.model.dc.DcCreateResponse;
import com.gsl.api.model.dc.DcLineItem;
import com.gsl.reports.StepExecutor;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

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
        Response response = getRaw("/DC/GetPacketBarcode");
        return response.jsonPath().getString("result");
    }

    /** POST DC/Save — the final save call. */
    public DcCreateResponse createDc(DcCreateRequest request) {
        return post("/DC/Save", request, DcCreateResponse.class);
    }

    /**
     * Negative-scenario counterpart to {@link #createDc(DcCreateRequest)} — for a deliberately
     * incomplete/invalid {@code request}, returns the raw {@link Response} instead of throwing.
     */
    public Response createDcExpectFailure(DcCreateRequest request) {
        return postRawExpectFailure("/DC/Save", request);
    }

    /** Builds and saves a single-line DC for the given item, matching k6's runCreateDcFlow(). */
    public DcCreateResponse createDc(String itemCode, int quantity, int rate) {
        String barcode = StepExecutor.call("Get Packet Barcode for DC", this::fetchPacketBarcode);

        DcLineItem line = DcLineItem.builder()
                .itemId(itemCode)
                .quantity(quantity)
                .rate(rate)
                .build();

        DcCreateRequest request = DcCreateRequest.builder()
                .dcDate(LocalDate.now() + "T00:00:00")
                .packetBarcode(barcode)
                .item(line)
                .build();

        return StepExecutor.call("Create DC via API", () -> createDc(request));
    }
}
