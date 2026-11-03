package com.gsl.approval.evaluator;

import com.gsl.approval.model.ApprovalCondition;
import com.gsl.approval.model.ApprovalEntityType;
import com.gsl.approval.model.ApprovalRule;
import com.gsl.approval.model.ConditionEvaluation;
import com.gsl.approval.model.RuleDecision;
import com.gsl.approval.model.RuleEvaluation;
import com.gsl.approval.model.RuleEvaluation.Outcome;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Picks the applicable rule: rules in priority order (1 first), the first effective rule whose conditions all hold wins, and the default rule
 * (no conditions) is the catch-all. Conditions are ANDed - the application allows no OR and no nesting. Pure logic over already-loaded rules:
 * no HTTP, no document access. Returns every rule's outcome so the report can show why the winner won.
 */
public class ApprovalRuleResolver {

    private final ApprovalConditionEvaluator conditionEvaluator;

    public ApprovalRuleResolver(ApprovalConditionEvaluator conditionEvaluator) {
        this.conditionEvaluator = conditionEvaluator;
    }

    /**
     * @param rules   the active rules, normally already in priority order (sorted again here, so the order never depends on the caller)
     * @param facts   rule variable code to the document's value (see {@code ApVoucherApprovalFacts})
     * @param workDate the date effective dates are checked against
     * @throws ApprovalEvaluationException when a rule that has to be evaluated uses something that cannot be evaluated
     */
    public RuleDecision resolve(ApprovalEntityType entity, List<ApprovalRule> rules, Map<String, Object> facts, LocalDate workDate) {
        List<ApprovalRule> ordered = rules.stream()
                .sorted(Comparator.comparing(ApprovalRule::getPriority, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<RuleEvaluation> evaluations = new ArrayList<>();
        ApprovalRule winner = null;
        for (ApprovalRule rule : ordered) {
            if (winner != null) {
                evaluations.add(new RuleEvaluation(rule, Outcome.NOT_REACHED, List.of()));
            } else if (!rule.isEffective(workDate)) {
                evaluations.add(new RuleEvaluation(rule, Outcome.NOT_EFFECTIVE, List.of()));
            } else {
                List<ConditionEvaluation> results = new ArrayList<>();
                boolean all = allHold(entity, rule, facts, results);
                evaluations.add(new RuleEvaluation(rule, all ? Outcome.MATCHED : Outcome.NOT_MATCHED, results));
                if (all) {
                    winner = rule;
                }
            }
        }
        return new RuleDecision(winner, evaluations);
    }

    /** ANDs the conditions in order and stops at the first that fails, so a rule that cannot match never trips on a later unsupported condition. */
    private boolean allHold(ApprovalEntityType entity, ApprovalRule rule, Map<String, Object> facts, List<ConditionEvaluation> results) {
        for (ApprovalCondition condition : rule.getConditions()) {
            ConditionEvaluation result = conditionEvaluator.evaluate(entity, condition, facts);
            results.add(result);
            if (!result.isMatched()) {
                return false;
            }
        }
        return true;
    }
}
