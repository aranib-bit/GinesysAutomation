package com.gsl.api.webapi.model.request.approval;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/** Body of {@code POST GlobalUser/GetListOfGlobalAppEmpUsers}: the User list screen's paged request (HAR: CreateUserqaprod). */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class GlobalUserListRequest {
    @JsonProperty("connectedSite")
    private final String connectedSite;
    @JsonProperty("connectedSiteOUCode")
    private final int connectedSiteOuCode;
    @JsonProperty("workDate")
    private final String workDate;
    @JsonProperty("page")
    private final int page;
    @JsonProperty("start")
    private final int start;
    @JsonProperty("limit")
    private final int limit;

    /** Page {@code page} (1-based) of {@code limit} rows, as the grid asks for it. */
    public static GlobalUserListRequest page(String connectedSite, int connectedSiteOuCode, String workDate, int page, int limit) {
        return GlobalUserListRequest.builder().connectedSite(connectedSite).connectedSiteOuCode(connectedSiteOuCode).workDate(workDate)
                .page(page).start((page - 1) * limit).limit(limit).build();
    }
}
