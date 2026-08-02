package com.gsl.api.model.grc;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One GRC line, mirroring flows/createGrc.js's items[] entries. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GrcLineItem {
    private String itemId;
    private int acqty;
    private int acrate;
    private Integer grsamt;
    private Integer netamt;
    private String batchSerialCode;
    private Integer mrp;
    private Integer wsp;
}
