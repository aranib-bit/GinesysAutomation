package com.gsl.approval.service;

import com.gsl.approval.model.ApprovalRule;
import com.gsl.approval.model.ApprovalRuleSet;
import com.gsl.approval.service.ApprovalConfigurationException.Kind;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads an entity's active rules, merges each rule's detail (hierarchy, conditions) into its summary and validates the set. Returns the
 * rules in priority order (1 first); choosing the matching rule is the resolver's job (Phase 3). Rules outside the active priority set
 * (created but never assigned) are never returned.
 */
public class ApprovalRuleService {

    private final ApprovalRuleGateway gateway;

    public ApprovalRuleService(ApprovalRuleGateway gateway) {
        this.gateway = gateway;
    }

    public List<ApprovalRule> getActiveRulesInPriorityOrder(String entityCode) {
        ApprovalRuleSet set = gateway.fetchActiveRuleSet(entityCode);
        if (set == null) {
            throw new ApprovalConfigurationException(Kind.INVALID_RESPONSE, "No active rule set was returned for entity '" + entityCode + "'");
        }
        if (set.getEntityCode() != null && !entityCode.equalsIgnoreCase(set.getEntityCode())) {
            throw new ApprovalConfigurationException(Kind.ENTITY_MISMATCH,
                    "Requested rules of '" + entityCode + "' but the rule set is for '" + set.getEntityCode() + "'");
        }
        if (set.getVersionNo() > 0 && !"ACTIVE".equalsIgnoreCase(set.getStatus())) {
            throw new ApprovalConfigurationException(Kind.INVALID_CONFIGURATION,
                    "Rule priority set v" + set.getVersionNo() + " of '" + entityCode + "' is " + set.getStatus() + ", not ACTIVE");
        }
        if (set.getRules().isEmpty()) {
            throw new ApprovalConfigurationException(Kind.INVALID_CONFIGURATION, "Entity '" + entityCode + "' has no active approval rules");
        }
        List<ApprovalRule> rules = set.getRules().stream().map(this::withDetail).sorted(byPriority()).toList();
        validate(entityCode, rules);
        return rules;
    }

    private ApprovalRule withDetail(ApprovalRule summary) {
        ApprovalRule detail = gateway.fetchRuleDetail(summary.getRuleCode());
        if (detail == null) {
            throw new ApprovalConfigurationException(Kind.NOT_FOUND, "Approval rule " + summary.getRuleCode() + " ('" + summary.getName() + "') has no detail");
        }
        if (detail.getHierarchyCode() == null) {
            throw new ApprovalConfigurationException(Kind.INVALID_CONFIGURATION,
                    "Approval rule " + summary.getRuleCode() + " ('" + summary.getName() + "') has no hierarchy");
        }
        // The priority and default flag come from the active set (the detail's own priority is the set it was last read from).
        return summary.toBuilder()
                .hierarchyCode(detail.getHierarchyCode())
                .hierarchyName(detail.getHierarchyName() != null ? detail.getHierarchyName() : summary.getHierarchyName())
                .effectiveDate(detail.getEffectiveDate())
                .conditions(detail.getConditions())
                .build();
    }

    private static Comparator<ApprovalRule> byPriority() {
        return Comparator.comparing(ApprovalRule::getPriority, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static void validate(String entityCode, List<ApprovalRule> rules) {
        Set<Integer> seen = new HashSet<>();
        for (ApprovalRule rule : rules) {
            if (rule.getPriority() == null) {
                throw new ApprovalConfigurationException(Kind.INVALID_CONFIGURATION,
                        "Active rule " + rule.getRuleCode() + " ('" + rule.getName() + "') of '" + entityCode + "' has no priority");
            }
            if (!seen.add(rule.getPriority())) {
                throw new ApprovalConfigurationException(Kind.INVALID_CONFIGURATION,
                        "Two active rules of '" + entityCode + "' share priority " + rule.getPriority() + " - which wins is not defined");
            }
        }
        long defaults = rules.stream().filter(ApprovalRule::isDefaultRule).count();
        if (defaults != 1) {
            throw new ApprovalConfigurationException(Kind.INVALID_CONFIGURATION,
                    "Entity '" + entityCode + "' must have exactly one default rule in its active set but has " + defaults);
        }
    }
}
