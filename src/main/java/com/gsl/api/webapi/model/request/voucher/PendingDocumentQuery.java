package com.gsl.api.webapi.model.request.voucher;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code GET DocumentAdjustments/{apvoucher|arvoucher}}: the documents with a pending balance for one sub-ledger (one page is added by the pager). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PendingDocumentQuery {
    @JsonProperty("glCode")
    private long glCode;
    @JsonProperty("slCode")
    private long slCode;
    @JsonProperty("ouCode")
    private int ouCode;
    @JsonProperty("date")
    private String date;
    @JsonProperty("docType")
    private String docType;
    @JsonProperty("workDate")
    private String workDate;
}
