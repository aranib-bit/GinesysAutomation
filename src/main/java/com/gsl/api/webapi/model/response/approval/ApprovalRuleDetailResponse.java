package com.gsl.api.webapi.model.response.approval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gsl.api.core.ApiEnvelopeBase;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Response of {@code GET ApprovalRule/Get?id=}: one rule with the hierarchy it routes to and its conditions (AND only, no nesting). */
@Getter
@Setter
public class ApprovalRuleDetailResponse extends ApiEnvelopeBase {
    private Result result;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private Integer code;
        private String entityCode;
        private String name;
        private String description;
        /** {@code "Y"} / {@code "N"} on the wire (the active-version list uses a boolean for the same fact). */
        private String isDefault;
        private String effectiveDate;
        private Integer hierarchyCode;
        private String hierarchyName;
        private Integer version;
        private Integer baseVersion;
        private Integer priority;
        private Integer totalRulesInPrioritySet;
        private Integer activeRequestCount;
        private List<Condition> conditions;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Condition {
        /** {@code H} header, {@code L} line. */
        private String basis;
        private String varCode;
        private String varName;
        private String dataType;
        private String operator;
        private String valueText;
        /** A code for LOOKUP variables, or a JSON array of codes for {@code IN}; null otherwise. */
        private String valueCode;
    }
}
