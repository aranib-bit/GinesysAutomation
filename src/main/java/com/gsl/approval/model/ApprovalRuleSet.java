package com.gsl.approval.model;

import lombok.Value;

import java.util.List;

/** An entity's active rule priority set as listed by the application: summaries only (no hierarchy code or conditions yet). */
@Value
public class ApprovalRuleSet {
    String entityCode;
    /** 0 when no priority set was ever saved; then only the default rule is listed. */
    int versionNo;
    String status;
    List<ApprovalRule> rules;
}
