package com.gsl.api.model.transferout;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

import java.util.List;

/** POST SI/Save request body (Transfer Out). Field set mirrors flows/createTransferOut.js. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransferOutCreateRequest {
    @Builder.Default
    private int code = 0;
    @Builder.Default
    private String entryMode = "Add";
    private String invoiceDate; // ISO date-time

    @Singular
    private List<TransferOutLineItem> items;

    private Integer ownerSiteCode;
    private Integer tradeGroupCode;
    private Integer docSchemeCode;
}
