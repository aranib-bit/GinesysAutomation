package com.gsl.enums;

import java.util.function.BiPredicate;

/**
 * A domain-scoped, group-tagged validation check evaluated by a generic two-input {@link
 * BiPredicate} — the data-driven rule engine behind {@code RuleFilter}/{@code RuleTest}/{@code
 * RuleExecutor}. {@link RuleFilter#filterRules(VoucherDomain, RuleGroup)} selects the subset of
 * these constants applicable to a given voucher domain (matching either that exact domain or
 * {@link VoucherDomain#COMMON}) and execution selector (see {@link RuleGroup}); {@code RuleTest}
 * then calls {@link #test} on each and asserts the mandatory ones pass.
 *
 * <p>TODO-confirm: every constant's predicate here is a placeholder (e.g. {@code a.equals(b)},
 * {@code a.length() > 3}) exercised against literal {@code "input1"}/{@code "input2"} strings in
 * {@code RuleTest}/{@code RuleExecutor} — this engine is wired end-to-end but not yet connected to
 * real field values from any voucher page/DB source, and the rule bodies don't encode any actual
 * business validation yet.
 */
public enum ValidationRule {

    AR_RULE_1 (VoucherDomain.AR,     RuleGroup.MANDATORY, true,  (a, b) -> a != null && b != null),
    AR_RULE_2 (VoucherDomain.AR,     RuleGroup.GROUP_1,   false, (a, b) -> a.equals(b)),
    AP_RULE_1 (VoucherDomain.AP,     RuleGroup.MANDATORY, true,  (a, b) -> a.length() > 3),
    AP_RULE_2 (VoucherDomain.AP,     RuleGroup.GROUP_2,   false, (a, b) -> !a.equals(b)),
    CONTRA_RULE_1(VoucherDomain.CONTRA, RuleGroup.MANDATORY, true, (a, b) -> a != null),
    CONTRA_RULE_2(VoucherDomain.CONTRA, RuleGroup.GROUP_1,  false, (a, b) -> a.length() > 5),
    COMMON_1  (VoucherDomain.COMMON, RuleGroup.MANDATORY, true,  (a, b) -> !a.isEmpty()),
    COMMON_2  (VoucherDomain.COMMON, RuleGroup.GROUP_1,   false, (a, b) -> a.length() == b.length());

    private final VoucherDomain domain;
    private final RuleGroup group;
    private final boolean mandatory;
    private final BiPredicate<String, String> predicate;

    ValidationRule(VoucherDomain domain, RuleGroup group, boolean mandatory, BiPredicate<String, String> predicate) {
        this.domain = domain;
        this.group = group;
        this.mandatory = mandatory;
        this.predicate = predicate;
    }

    /** Evaluates this rule's predicate against the two inputs. */
    public boolean test(String a, String b) {
        return predicate.test(a, b);
    }

    /** The voucher domain this rule applies to ({@link VoucherDomain#COMMON} rules apply to every domain). */
    public VoucherDomain getDomain() {
        return domain;
    }

    /** This rule's {@link RuleGroup} tag ({@link RuleGroup#GROUP_1}/{@link RuleGroup#GROUP_2} — mandatory-ness is tracked separately via {@link #isMandatory()}). */
    public RuleGroup getGroup() {
        return group;
    }

    /** {@code true} if this rule must pass — {@code RuleTest} hard-asserts on these; a {@code false} rule is only observed/logged. */
    public boolean isMandatory() {
        return mandatory;
    }
}
