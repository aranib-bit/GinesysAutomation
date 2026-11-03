package com.gsl.approval.model;

import lombok.Value;

import java.util.List;
import java.util.Optional;

/** The result of resolving the applicable rule: the winner (none = no rule applies) and every rule's evaluation in priority order. */
@Value
public class RuleDecision {
    ApprovalRule matchedRule;
    List<RuleEvaluation> evaluations;

    public Optional<ApprovalRule> matched() {
        return Optional.ofNullable(matchedRule);
    }
}
