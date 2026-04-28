package com.gsl.validation;

import com.gsl.enums.RuleGroup;
import com.gsl.enums.ValidationRule;
import com.gsl.enums.VoucherDomain;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RuleFilter {

    public static List<ValidationRule> filterRules(VoucherDomain domain, RuleGroup executionType) {
        return Arrays.stream(ValidationRule.values())
                .filter(rule -> rule.getDomain() == domain || rule.getDomain() == VoucherDomain.COMMON)
                .filter(rule -> matchesGroup(rule, executionType))
                .collect(Collectors.toList());
    }

    private static boolean matchesGroup(ValidationRule rule, RuleGroup executionType) {
        switch (executionType) {
            case MANDATORY: return rule.isMandatory();
            case GROUP_1:
            case GROUP_2:   return rule.getGroup() == executionType;
            case ALL:       return true;
            default:        return false;
        }
    }
}
