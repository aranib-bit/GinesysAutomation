package com.gsl.api.webapi.model.request.approval;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/** Body of {@code POST ApprovalEntityConfig/GetEntityConfigs}: the grid paging the screen sends. */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApprovalEntityConfigsRequest {
    @JsonProperty("page")
    private final int page;
    @JsonProperty("start")
    private final int start;
    @JsonProperty("limit")
    private final int limit;

    /** First page, as the Approval Entity screen requests it. */
    public static ApprovalEntityConfigsRequest firstPage(int limit) {
        return ApprovalEntityConfigsRequest.builder().page(1).start(0).limit(limit).build();
    }
}
