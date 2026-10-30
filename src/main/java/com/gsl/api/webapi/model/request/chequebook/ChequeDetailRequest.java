package com.gsl.api.webapi.model.request.chequebook;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One cheque leaf of a create request. {@code @JsonProperty} keeps the backend's exact names; nulls are sent as {@code null} like the HAR. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ChequeDetailRequest {

    @JsonProperty("CHQNO")
    private String chequeNumber;

    @JsonProperty("CHQDT")
    private String chequeDate;

    /** {@code Long}, not {@code long}: the API receives {@code null} for an unused leaf. */
    @JsonProperty("vchCode")
    private Long voucherCode;

    @JsonProperty("Type")
    private String type;

    @JsonProperty("Stat")
    private String status;
}
