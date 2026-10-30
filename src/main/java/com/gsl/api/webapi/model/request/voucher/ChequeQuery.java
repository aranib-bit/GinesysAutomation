package com.gsl.api.webapi.model.request.voucher;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code GET /cheques}: the usable cheque leaves of a bank GL (one page is added by the pager). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChequeQuery {
    @JsonProperty("module")
    private String module;
    @JsonProperty("glCode")
    private long glCode;
    @JsonProperty("workDate")
    private String workDate;
}
