package com.gsl.api.webapi.model.request.serviceinvoice;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** {@code POST /ServiceInvoice/GetServices} body: the site context plus the service filters; nulls are sent exactly as the UI sends them. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ServiceLookupRequest {
    @JsonProperty("srvDate")
    private String srvDate;
    @JsonProperty("tdsTypeCode")
    private Integer tdsTypeCode;
    @JsonProperty("tdsSection")
    private String tdsSection;
    @JsonProperty("queryFieldName")
    private List<String> queryFieldName;
    @JsonProperty("availableSites")
    private List<Integer> availableSites;
    @JsonProperty("connectedSite")
    private String connectedSite;
    @JsonProperty("workDate")
    private String workDate;
    @JsonProperty("connectedSiteOUCode")
    private int connectedSiteOuCode;
    @JsonProperty("connectedSiteType")
    private String connectedSiteType;
    @JsonProperty("page")
    private int page;
    @JsonProperty("start")
    private int start;
    @JsonProperty("limit")
    private int limit;
    @JsonProperty("sort")
    private String sort;
}
