package com.gsl.api.webapi.model.request.approval;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/** Body of {@code POST ApprovalAction/Approve}: the task to approve and the approver's remarks (HAR: {@code {"taskCode":227,"remarks":""}}). */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApproveRequest {
    @JsonProperty("taskCode")
    private final int taskCode;
    @JsonProperty("remarks")
    private final String remarks;

    public static ApproveRequest of(int taskCode, String remarks) {
        return ApproveRequest.builder().taskCode(taskCode).remarks(remarks == null ? "" : remarks).build();
    }
}
