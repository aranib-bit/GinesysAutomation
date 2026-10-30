package com.gsl.api.webapi.model.request.tds;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A rate of a TDS section for one source, valid from {@code dateFrom}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class TdsRateDetail {
    @JsonProperty("code")
    private int code;
    @JsonProperty("dateFrom")
    private String dateFrom;
    @JsonProperty("rate")
    private java.math.BigDecimal rate;
}
