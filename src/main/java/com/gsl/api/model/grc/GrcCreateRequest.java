package com.gsl.api.model.grc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gsl.api.model.common.LookupValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

import java.util.List;

/** POST GRC/Save request body. Field set mirrors flows/createGrc.js. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GrcCreateRequest {
    @Builder.Default
    private int code = 0;
    private int vendorCode;
    private LookupValue admsite;
    private LookupValue admouCode;
    private String grcdt; // ISO date-time, e.g. "2026-07-27T00:00:00"

    @Singular
    private List<GrcLineItem> items;
    @Singular("charge")
    private List<GrcCharges> grcCharges;

    private Integer rcqty;
    private Integer grsamt;
}
