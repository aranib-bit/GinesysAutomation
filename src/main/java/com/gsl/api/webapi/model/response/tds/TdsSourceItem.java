package com.gsl.api.webapi.model.response.tds;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A TDS source of {@code tdssource/all}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TdsSourceItem {
    @JsonProperty("code")
    private long code;
    @JsonProperty("tdsSource")
    private String name;
    @JsonProperty("sourceType")
    private String sourceType;
}
