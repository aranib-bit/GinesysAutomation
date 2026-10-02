package com.gsl.api.model.dc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Shape of one candidate entry in GET /Batch/GetItemBatchSerialForPositiveStock's result array —
 * a batch/serial unit of a given item that currently has positive stock, along with the live
 * rate/mrp/wsp to carry into a Batch/Serial {@link DcLineItem} (see flows/createDc.js's
 * {@code candidate.batchSerialCode/rateCal/mrp/wsp} usage for Batch/Serial item types).
 *
 * <p><b>Not yet wired to a caller</b>: {@code DcApiClient} does not currently implement this
 * lookup (only {@code fetchPacketBarcode}/{@code createDc} exist there), so this class describes
 * the wire shape for a future {@code DcApiClient.fetchAvailableBatchSerial(...)}-style method,
 * not code exercised today.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatchSerialInfo {
    private String batchSerialCode;
    private int availableQty;
    private Integer rate;
    private Integer mrp;
    private Integer wsp;
}
