package com.gsl.api.webapi.model.request.tds;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** The rates a TDS section carries for one TDS source. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class TdsSourceDetail {
    @JsonProperty("sourceCode")
    private long sourceCode;
    @JsonProperty("sourceName")
    private String sourceName;
    @JsonProperty("tdsrateDetails")
    private List<TdsRateDetail> rateDetails;
}
