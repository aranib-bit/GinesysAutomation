package com.gsl.api.webapi.model.response.approval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gsl.api.core.ApiEnvelopeBase;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Response of {@code GET ApprovalHierarchy/Get?id=}: the levels and approvers of one hierarchy (Auto Approve has no levels). */
@Getter
@Setter
public class ApprovalHierarchyResponse extends ApiEnvelopeBase {
    private Result result;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private Integer code;
        private String name;
        private String description;
        /** {@code "Y"} when the hierarchy can route new documents. */
        private String isActive;
        private String isSystemDefined;
        private Integer version;
        private Integer baseVersion;
        private Integer versionCode;
        private Integer activeRequestCount;
        private List<Level> levels;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Level {
        private Integer levelNo;
        private String levelLabel;
        /** {@code A} every approver must approve, {@code C} a custom number ({@code quorumCount}). */
        private String quorumType;
        private Integer quorumCount;
        private Integer dueDays;
        private String reminderSla;
        private Integer customSlaHours;
        private Integer activeApproverCount;
        private List<Approver> approvers;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Approver {
        /** {@code U} a named user ({@code userCode}), {@code R} a role ({@code roleId}). */
        private String approverType;
        private Integer userCode;
        private Integer roleId;
        private String name;
        private String detail;
        private Boolean isExtinct;
        private Integer activeUserCount;
    }
}
