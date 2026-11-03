package com.gsl.api.webapi.model.response.approval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gsl.api.core.ApiEnvelopeBase;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Response of {@code GET ApprovalRulePriority/GetActiveVersion}: the entity's active priority set, rules already in priority order. */
@Getter
@Setter
public class ApprovalActiveRuleSetResponse extends ApiEnvelopeBase {
    private Result result;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private String entityCode;
        /** 0 with a null status when no priority set was ever saved (only the default rule is returned). */
        private Integer versionNo;
        private String status;
        private String createdBy;
        private String createdOn;
        private List<Rule> rules;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Rule {
        private Integer ruleCode;
        private String ruleName;
        private String description;
        /** Wrapper: a missing priority must stay distinguishable from a real one. */
        private Integer priority;
        private String hierarchyName;
        private String conditionSummary;
        private String effectiveDate;
        private Integer version;
        private Boolean isDefault;
    }
}
