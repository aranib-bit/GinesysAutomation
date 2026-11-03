package com.gsl.api.webapi.model.response.approval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gsl.api.core.ApiEnvelopeBase;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Response of {@code GET ApprovalAction/GetApprovalPath}. The history and the rule match trace the application also returns are not mapped:
 * the workflow only needs the state of each level.
 */
@Getter
@Setter
public class ApprovalPathResponse extends ApiEnvelopeBase {
    private Result result;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        /** The whole request: {@code PENDING}, {@code APPROVED}, ... */
        private String status;
        private String ruleName;
        private Integer ruleVersionNo;
        private String hierarchyName;
        private Integer hierarchyVersionNo;
        private Integer priorityVersionNo;
        private Boolean isDefaultRule;
        private String requestedBy;
        private String requestedOn;
        private List<Level> levels;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Level {
        private Integer levelNo;
        private String levelLabel;
        private Integer quorumReq;
        private Integer approvedCount;
        private String status;
        private String sentOn;
        private List<Approver> approvers;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Approver {
        private Integer taskCode;
        private String approverName;
        private String status;
    }
}
