package com.gsl.approval.api;

import com.gsl.api.webapi.client.ApprovalApiClient;
import com.gsl.api.webapi.model.response.approval.ApprovalActiveRuleSetResponse;
import com.gsl.api.webapi.model.response.approval.ApprovalHierarchyResponse;
import com.gsl.api.webapi.model.response.approval.ApprovalRuleDetailResponse;
import com.gsl.approval.model.ApprovalApprover;
import com.gsl.approval.model.ApprovalCondition;
import com.gsl.approval.model.ApprovalHierarchy;
import com.gsl.approval.model.ApprovalLevel;
import com.gsl.approval.model.ApprovalRule;
import com.gsl.approval.model.ApprovalRuleSet;
import com.gsl.approval.service.ApprovalConfigurationException;
import com.gsl.approval.service.ApprovalHierarchyGateway;
import com.gsl.approval.service.ApprovalRuleGateway;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Rule and hierarchy gateways over {@link ApprovalApiClient}: maps the response POJOs to the approval model and a failed call to an
 * {@link ApprovalConfigurationException}. No decisions here - validating the configuration is the services' job.
 */
public class ApiApprovalPolicyGateway implements ApprovalRuleGateway, ApprovalHierarchyGateway {

    private final ApprovalApiClient client;

    public ApiApprovalPolicyGateway(ApprovalApiClient client) {
        this.client = client;
    }

    @Override
    public ApprovalRuleSet fetchActiveRuleSet(String entityCode) {
        ApprovalActiveRuleSetResponse.Result r =
                ApprovalApiCalls.read("Reading the active approval rules of " + entityCode, () -> client.getActiveRuleSet(entityCode));
        if (r == null) {
            return null;
        }
        List<ApprovalRule> rules = r.getRules() == null ? List.of() : r.getRules().stream().map(ApiApprovalPolicyGateway::toSummary).toList();
        return new ApprovalRuleSet(r.getEntityCode(), r.getVersionNo() == null ? 0 : r.getVersionNo(), r.getStatus(), rules);
    }

    @Override
    public ApprovalRule fetchRuleDetail(int ruleCode) {
        ApprovalRuleDetailResponse.Result r = ApprovalApiCalls.read("Reading approval rule " + ruleCode, () -> client.getRule(ruleCode));
        if (r == null) {
            return null;
        }
        List<ApprovalCondition> conditions = r.getConditions() == null ? List.of() : r.getConditions().stream()
                .map(c -> ApprovalCondition.builder().basis(c.getBasis()).varCode(c.getVarCode()).varName(c.getVarName())
                        .dataType(c.getDataType()).operator(c.getOperator()).valueText(c.getValueText()).valueCode(c.getValueCode()).build())
                .toList();
        return ApprovalRule.builder()
                .ruleCode(r.getCode() == null ? ruleCode : r.getCode())
                .name(r.getName())
                .priority(r.getPriority())
                .defaultRule("Y".equalsIgnoreCase(r.getIsDefault()))
                .hierarchyCode(r.getHierarchyCode())
                .hierarchyName(r.getHierarchyName())
                .effectiveDate(toDate(r.getEffectiveDate()))
                .conditions(conditions)
                .build();
    }

    @Override
    public ApprovalHierarchy fetchHierarchy(int hierarchyCode) {
        ApprovalHierarchyResponse.Result r =
                ApprovalApiCalls.read("Reading approval hierarchy " + hierarchyCode, () -> client.getHierarchy(hierarchyCode));
        if (r == null) {
            return null;
        }
        List<ApprovalLevel> levels = r.getLevels() == null ? List.of() : r.getLevels().stream()
                .map(ApiApprovalPolicyGateway::toLevel)
                .sorted(Comparator.comparingInt(ApprovalLevel::getLevelNo))
                .toList();
        return ApprovalHierarchy.builder()
                .code(r.getCode() == null ? hierarchyCode : r.getCode())
                .name(r.getName())
                .active("Y".equalsIgnoreCase(r.getIsActive()))
                .versionNo(r.getVersion())
                .levels(levels)
                .build();
    }

    private static ApprovalRule toSummary(ApprovalActiveRuleSetResponse.Rule r) {
        return ApprovalRule.builder()
                .ruleCode(Objects.requireNonNull(r.getRuleCode(), "active rule without ruleCode"))
                .name(r.getRuleName())
                .priority(r.getPriority())
                .defaultRule(Boolean.TRUE.equals(r.getIsDefault()))
                .hierarchyName(r.getHierarchyName())
                .conditionSummary(r.getConditionSummary())
                .effectiveDate(toDate(r.getEffectiveDate()))
                .build();
    }

    private static ApprovalLevel toLevel(ApprovalHierarchyResponse.Level l) {
        boolean all = !"C".equalsIgnoreCase(l.getQuorumType());
        List<ApprovalApprover> approvers = l.getApprovers() == null ? List.of() : l.getApprovers().stream()
                .map(a -> ApprovalApprover.builder()
                        .type("R".equalsIgnoreCase(a.getApproverType()) ? ApprovalApprover.Type.ROLE : ApprovalApprover.Type.USER)
                        .userCode(a.getUserCode())
                        .roleId(a.getRoleId())
                        .name(a.getName())
                        .extinct(Boolean.TRUE.equals(a.getIsExtinct()))
                        .activeUserCount(a.getActiveUserCount())
                        .build())
                .toList();
        return ApprovalLevel.builder()
                .levelNo(l.getLevelNo() == null ? 0 : l.getLevelNo())
                .label(l.getLevelLabel())
                .quorum(all ? ApprovalLevel.Quorum.ALL : ApprovalLevel.Quorum.CUSTOM)
                .quorumCount(all ? null : l.getQuorumCount())
                .dueDays(l.getDueDays())
                .approvers(approvers)
                .build();
    }

    /** The application sends {@code 2026-10-30T00:00:00} (or null); only the date matters. */
    private static LocalDate toDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text).toLocalDate();
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(text);
            } catch (DateTimeParseException ignored) {
                throw new ApprovalConfigurationException(ApprovalConfigurationException.Kind.INVALID_RESPONSE,
                        "Unreadable approval date '" + text + "'", e);
            }
        }
    }
}
