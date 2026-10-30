package com.gsl.api.webapi.model.response.tds;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A TDS agency of {@code TDSAgency/list}; {@code code} is its sub-ledger code. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TdsAgencyItem {
    @JsonProperty("code")
    private long code;
    @JsonProperty("agency_Id")
    private String agencyId;
    @JsonProperty("name")
    private String name;
    @JsonProperty("extinct")
    private String extinct;
    @JsonProperty("class_Type")
    private String classType;
}
