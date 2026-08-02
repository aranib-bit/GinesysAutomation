package com.gsl.api.model.dc;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

import java.util.List;

/** POST DC/Save request body. Field set mirrors flows/createDc.js. */
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
