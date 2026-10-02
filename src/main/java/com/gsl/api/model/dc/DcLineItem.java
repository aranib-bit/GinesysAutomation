package com.gsl.api.model.dc;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One line item inside {@link DcCreateRequest#getItems()} — the WebAPI/PublicAPI wire shape for
 * DC/Save's {@code items[]} array (a reduced field set relative to flows/createDc.js's full
 * per-line payload; only what {@code DcApiClient.createDc(String, int, int)} needs to build a
 * valid single-line DC). {@code batchSerialCode} is only populated for Batch/Serial items,
 * discovered live via {@code Batch/GetItemBatchSerialForPositiveStock} (see
 * {@link BatchSerialInfo}) — a Regular item leaves it null.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DcLineItem {
    private String itemId;
    private int quantity;
    private Integer rate;
    private Integer mrp;
    private Integer wsp;
    private String batchSerialCode;
}
