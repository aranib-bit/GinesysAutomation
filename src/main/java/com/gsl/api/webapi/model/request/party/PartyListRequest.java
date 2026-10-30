package com.gsl.api.webapi.model.request.party;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Body of {@code VendorMaster/GetVendors} and {@code Customer/GetCustomers}: one page of the party list, sorted by name. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartyListRequest {
    public static final String SORT_BY_NAME = "[{\"property\":\"slname\",\"direction\":\"ASC\"}]";

    @JsonProperty("connectedSite")
    private String connectedSite;
    @JsonProperty("connectedSiteOUCode")
    private int connectedSiteOuCode;
    @JsonProperty("workDate")
    private String workDate;
    @JsonProperty("page")
    private int page;
    @JsonProperty("start")
    private int start;
    @JsonProperty("limit")
    private int limit;
    @Builder.Default
    @JsonProperty("sort")
    private String sort = SORT_BY_NAME;
}
