package com.gsl.api.webapi.model.request.tds;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Body of the TDS master lists ({@code tdssource/all}, {@code TDSAgency/list}, {@code tdssection}); only the sort differs. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class TdsListRequest {
    @JsonProperty("connectedSite")
    private int connectedSite;
    @JsonProperty("connectedUser")
    private long connectedUser;
    @JsonProperty("ConnectedSiteOUCode")
    private int connectedSiteOuCode;
    @JsonProperty("workDate")
    private String workDate;
    @JsonProperty("page")
    private int page;
    @JsonProperty("start")
    private int start;
    @JsonProperty("limit")
    private int limit;
    @JsonProperty("sort")
    private String sort;
}
