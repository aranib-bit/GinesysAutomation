package com.gsl.enums;

/**
 * Dual-purpose grouping/selection tag for {@link ValidationRule} — each rule constant carries
 * one of these via {@link ValidationRule#getGroup()}, and the same enum is also passed as the
 * "which rules to run" selector to {@code RuleFilter.filterRules(VoucherDomain, RuleGroup)}.
 * See {@code RuleFilter#matchesGroup} for exactly how a selector value resolves to a rule subset.
 */
public enum RuleGroup {
    /** As a rule's own tag: irrelevant (mandatory-ness is tracked separately via {@link
     *  ValidationRule#isMandatory()}). As a selector: run only rules where {@code isMandatory()}
     *  is {@code true}, regardless of their {@link #GROUP_1}/{@link #GROUP_2} tag. */
    MANDATORY,
    /** A non-mandatory rule subset. As a selector: run only rules tagged {@code GROUP_1}. */
    GROUP_1,
    /** A second non-mandatory rule subset. As a selector: run only rules tagged {@code GROUP_2}. */
    GROUP_2,
    /** Selector-only: run every rule for the domain, regardless of mandatory-ness or group. */
    ALL
}
