package com.gsl.api.model.dc;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One DC line, mirroring flows/createDc.js's items[] entries. */
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
