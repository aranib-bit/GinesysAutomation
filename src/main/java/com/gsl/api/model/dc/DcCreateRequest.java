package com.gsl.api.model.dc;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

import java.util.List;

/**
 * POST DC/Save request body — the WebAPI/PublicAPI wire shape {@code DcApiClient} builds and
 * posts to create a Delivery Challan. A reduced field set relative to flows/createDc.js's full
 * save body (that k6 flow's {@code saveBody} carries roughly 30 fields covering
 * site/stockpoint/price-list/doc-scheme context); this DTO carries only what's needed for a
 * minimal single-line DC — {@code code}/{@code entryMode}/{@code status} default to the same
 * literal values the k6 flow sends for a new save ({@code 0}/{@code "Add"}/{@code "P"}),
 * {@code dcDate} is an ISO date-time string, and {@code packetBarcode} is the server-issued value
 * from {@code DcApiClient.fetchPacketBarcode()}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DcCreateRequest {
    @Builder.Default
    private int code = 0;
    @Builder.Default
    private String entryMode = "Add";
    @Builder.Default
    private String status = "P";
    private String dcDate; // ISO date-time
    private String packetBarcode;
    @Builder.Default
    private boolean ignoreNegativeStockCheck = false;

    @Singular
    private List<DcLineItem> items;
}
