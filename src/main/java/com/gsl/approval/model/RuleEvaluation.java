package com.gsl.approval.model;

import lombok.Value;

import java.util.List;

/** How one rule fared in a resolution: what it was and, for evaluated rules, each condition's result. */
@Value
public class RuleEvaluation {

    /** The rule's outcome in this resolution. */
    public enum Outcome { MATCHED, NOT_MATCHED, NOT_EFFECTIVE, NOT_REACHED }

    ApprovalRule rule;
    Outcome outcome;
    List<ConditionEvaluation> conditions;
}
