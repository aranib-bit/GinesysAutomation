package com.gsl.api.model.transferout;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One SI (Transfer Out) line, mirroring flows/createTransferOut.js's items[] entries. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransferOutLineItem {
    @Builder.Default
    private int code = 0;
    private String dcCode;
    private String itemCode;
    private int invQty;
    private Integer rate;
    private String batchSerialCode;
}
