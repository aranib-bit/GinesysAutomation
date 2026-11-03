package com.gsl.approval.service;

import com.gsl.approval.model.ApprovalRule;
import com.gsl.approval.model.ApprovalRuleSet;

/** Reads an entity's active rules from the application. The only way {@link ApprovalRuleService} reaches it, so it can be faked in tests. */
public interface ApprovalRuleGateway {

    /** The active priority set with rule summaries (name, priority, default flag); {@code null} when the application returned none. */
    ApprovalRuleSet fetchActiveRuleSet(String entityCode);

    /** One rule with its hierarchy code, effective date and conditions; {@code null} when the application returned none. */
    ApprovalRule fetchRuleDetail(int ruleCode);
}
