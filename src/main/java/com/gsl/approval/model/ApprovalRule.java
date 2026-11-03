package com.gsl.approval.model;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

/** One rule of an entity's active priority set. {@code priority} 1 is evaluated first; the default rule is the catch-all. */
@Value
@Builder(toBuilder = true)
public class ApprovalRule {
    int ruleCode;
    String name;
    Integer priority;
    boolean defaultRule;
    /** {@code null} until the rule detail is merged in ({@code ApprovalRuleService}). */
    Integer hierarchyCode;
    String hierarchyName;
    String conditionSummary;
    /** {@code null} = always effective (the default rule). */
    LocalDate effectiveDate;
    @Builder.Default
    List<ApprovalCondition> conditions = List.of();

    /** Whether the rule applies on {@code workDate}: no effective date, or one that has been reached. */
    public boolean isEffective(LocalDate workDate) {
        return effectiveDate == null || !effectiveDate.isAfter(workDate);
    }
}
