package com.gsl.service;

import com.gsl.api.client.DcApiClient;
import com.gsl.api.model.dc.DcCreateRequest;
import com.gsl.api.model.dc.DcCreateResponse;
import com.gsl.api.model.dc.DcLineItem;
import com.gsl.api.webapi.factory.ServiceInvoiceRequestFactory;
import com.gsl.reports.StepExecutor;

import java.time.LocalDate;

/** DC workflows: builds and saves a single-line DC for an item (the client only makes the HTTP calls). */
public class DcService {

    private final DcApiClient client;

    public DcService(DcApiClient client) {
        this.client = client;
    }

    /** Builds and saves a single-line DC for the given item, matching k6's runCreateDcFlow(). */
    public DcCreateResponse createDc(String itemCode, int quantity, int rate) {
        String barcode = StepExecutor.call("Get Packet Barcode for DC", client::fetchPacketBarcode);

        DcLineItem line = DcLineItem.builder()
                .itemId(itemCode)
                .quantity(quantity)
                .rate(rate)
                .build();

        DcCreateRequest request = DcCreateRequest.builder()
                .dcDate(ServiceInvoiceRequestFactory.stamp(LocalDate.now()))
                .packetBarcode(barcode)
                .item(line)
                .build();

        return StepExecutor.call("Create DC via API", () -> client.createDc(request));
    }
}
