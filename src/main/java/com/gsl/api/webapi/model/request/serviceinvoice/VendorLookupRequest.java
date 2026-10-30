package com.gsl.api.webapi.model.request.serviceinvoice;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** {@code POST /ServiceInvoice/GetVendors} body: the site context plus the vendor filters. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class VendorLookupRequest {
    @JsonProperty("query")
    private String query;
    @JsonProperty("hideTotalCount")
    private boolean hideTotalCount;
    @JsonProperty("queryFieldName")
    private List<String> queryFieldName;
    @JsonProperty("connectedSite")
    private String connectedSite;
    @JsonProperty("workDate")
    private String workDate;
    @JsonProperty("connectedSiteOUCode")
    private int connectedSiteOuCode;
    @JsonProperty("connectedSiteType")
    private String connectedSiteType;
    @JsonProperty("pagingNotRequired")
    private boolean pagingNotRequired;
    @JsonProperty("searchText")
    private String searchText;
    @JsonProperty("page")
    private int page;
    @JsonProperty("start")
    private int start;
    @JsonProperty("limit")
    private int limit;
    @JsonProperty("sort")
    private String sort;
}
