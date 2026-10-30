package com.gsl.api.webapi.model.request.serviceinvoice;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Query string of {@code RT/GetAdmSchemeDocList}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SchemeDocListQuery {
    @JsonProperty("siteCode")
    private int siteCode;
    @JsonProperty("menushcode")
    private String menuShortCode;
    @JsonProperty("pagingNotRequired")
    private boolean pagingNotRequired;
    @JsonProperty("isExcludeMannual")
    private boolean isExcludeMannual;
    @JsonProperty("gstApplicability")
    private String gstApplicability;
    @JsonProperty("workDate")
    private String workDate;
    @JsonProperty("page")
    private int page;
    @JsonProperty("start")
    private int start;
    @JsonProperty("limit")
    private int limit;
}
