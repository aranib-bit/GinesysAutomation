package com.gsl.api.webapi.model.response.tds;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A TDS section (nature) of {@code tdssection}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TdsSectionItem {
    @JsonProperty("code")
    private long code;
    @JsonProperty("tdsName")
    private String tdsName;
    @JsonProperty("section")
    private String section;
    @JsonProperty("tdsAgency")
    private String agency;
    @JsonProperty("extinct")
    private String extinct;
    @JsonProperty("tdsType")
    private String type;
}
