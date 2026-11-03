package com.gsl.api.webapi.model.request.approval;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/** Body of {@code POST ApprovalInbox/GetPending} and {@code GetArchive}: one entity's tasks, one page at a time. */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApprovalInboxRequest {
    @JsonProperty("entityCode")
    private final String entityCode;
    @JsonProperty("page")
    private final int page;
    @JsonProperty("start")
    private final int start;
    @JsonProperty("limit")
    private final int limit;

    /** Page {@code page} (1-based) of {@code limit} tasks of {@code entityCode}. */
    public static ApprovalInboxRequest page(String entityCode, int page, int limit) {
        return ApprovalInboxRequest.builder().entityCode(entityCode).page(page).start((page - 1) * limit).limit(limit).build();
    }
}
