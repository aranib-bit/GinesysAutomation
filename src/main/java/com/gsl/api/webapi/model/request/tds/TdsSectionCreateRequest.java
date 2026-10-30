package com.gsl.api.webapi.model.request.tds;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** {@code POST tdssection/tdssection} body: a TDS nature (name, section, agency) with its rate per source. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class TdsSectionCreateRequest {
    @JsonProperty("code")
    private int code;
    @JsonProperty("empCode")
    private long empCode;
    @JsonProperty("tDSName")
    private String tdsName;
    @JsonProperty("section")
    private String section;
    @JsonProperty("tDSAgencySLCode")
    private long agencySlCode;
    @JsonProperty("sectionType")
    private String sectionType;
    @JsonProperty("tdssourceDetails")
    private List<TdsSourceDetail> sourceDetails;
    @JsonProperty("ext")
    private String ext;
}
