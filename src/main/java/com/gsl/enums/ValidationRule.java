package com.gsl.enums;

import java.util.function.BiPredicate;

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

    public boolean test(String a, String b) {
        return predicate.test(a, b);
    }

    public VoucherDomain getDomain() {
        return domain;
    }

    public RuleGroup getGroup() {
        return group;
    }

    public boolean isMandatory() {
        return mandatory;
    }
}
