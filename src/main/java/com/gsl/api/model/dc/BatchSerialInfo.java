package com.gsl.api.model.dc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of GET /Batch/GetItemBatchSerialForPositiveStock — an available batch/serial with positive stock. */
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
