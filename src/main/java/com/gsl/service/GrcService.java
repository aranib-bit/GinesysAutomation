package com.gsl.service;

import com.gsl.api.client.GrcApiClient;
import com.gsl.api.model.common.LookupValue;
import com.gsl.api.model.grc.GrcCreateRequest;
import com.gsl.api.model.grc.GrcCreateResponse;
import com.gsl.api.model.grc.GrcLineItem;
import com.gsl.api.webapi.factory.ServiceInvoiceRequestFactory;
import com.gsl.reports.StepExecutor;

import java.time.LocalDate;

/** GRC workflows: builds and saves a single-line GRC for an item (the client only makes the HTTP calls). */
public class GrcService {

    private final GrcApiClient client;

    public GrcService(GrcApiClient client) {
        this.client = client;
    }

    /** Builds and saves a single-line GRC for the given item, matching k6's runCreateGrcFlow(). */
    public GrcCreateResponse createGrc(String itemCode, int vendorCode, int siteCode, int ouCode,
                                        int quantity, int rate) {
        // resolved for correctness/logging parity with k6; not yet consumed in the payload below
        StepExecutor.call("Get Vendor Tax Region for GRC", () -> client.fetchVendorTaxRegion(vendorCode));

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
                .grcdt(ServiceInvoiceRequestFactory.stamp(LocalDate.now()))
                .item(line)
                .rcqty(quantity)
                .grsamt(quantity * rate)
                .build();

        return StepExecutor.call("Create GRC via API", () -> client.createGrc(request));
    }
}
