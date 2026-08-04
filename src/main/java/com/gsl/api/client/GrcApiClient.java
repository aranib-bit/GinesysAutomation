package com.gsl.api.client;

import com.gsl.api.core.ApiClient;
import com.gsl.api.model.common.LookupValue;
import com.gsl.api.model.grc.GrcCreateRequest;
import com.gsl.api.model.grc.GrcCreateResponse;
import com.gsl.api.model.grc.GrcLineItem;
import com.gsl.api.model.grc.VendorTaxRegion;
import com.gsl.reports.ApiCallReportLogger;
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

    public GrcApiClient(RequestSpecification spec) {
        super(spec);
    }

    /** GET /GRC/GetGRCVendorTaxRegion */
    public VendorTaxRegion fetchVendorTaxRegion(int vendorCode) {
        Response response = spec.queryParam("vendorCode", vendorCode).get("/GRC/GetGRCVendorTaxRegion");
        validateResponse(response, "/GRC/GetGRCVendorTaxRegion");
        return response.jsonPath().getObject("result", VendorTaxRegion.class);
    }

    /** POST GRC/Save — the final save call. */
    public GrcCreateResponse createGrc(GrcCreateRequest request) {
        return post("/GRC/Save", request, GrcCreateResponse.class);
    }

    /** Builds and saves a single-line GRC for the given item, matching k6's runCreateGrcFlow(). */
    public GrcCreateResponse createGrc(String itemCode, int vendorCode, int siteCode, int ouCode,
                                        int quantity, int rate) {
        ApiCallReportLogger.setSection("Get Vendor Tax Region for GRC");
        fetchVendorTaxRegion(vendorCode); // resolved for correctness/logging parity with k6; not yet consumed in the payload below

        GrcLineItem line = GrcLineItem.builder()
                .itemId(itemCode)
                .acqty(quantity)
                .acrate(rate)
                .grsamt(quantity * rate)
                .netamt(quantity * rate)
                .build();

        GrcCreateRequest request = GrcCreateRequest.builder()
                .vendorCode(vendorCode)
                .admsite(LookupValue.ofCode(siteCode))
                .admouCode(LookupValue.ofCode(ouCode))
                .grcdt(LocalDate.now() + "T00:00:00")
                .item(line)
                .rcqty(quantity)
                .grsamt(quantity * rate)
                .build();

        ApiCallReportLogger.setSection("Create GRC via API");
        return createGrc(request);
    }
}
