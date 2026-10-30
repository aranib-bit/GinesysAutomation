package com.gsl.api.webapi.model.request.tds;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code POST tdssource} body; {@code sourceType} is C (corporate) or N (non-corporate). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class TdsSourceCreateRequest {
    @JsonProperty("sourceCode")
    private int sourceCode;
    @JsonProperty("tdsSource")
    private String tdsSource;
    @JsonProperty("sourceType")
    private String sourceType;
    @JsonProperty("connectedUser")
    private long connectedUser;
    @JsonProperty("dataversion")
    private int dataVersion;
}
