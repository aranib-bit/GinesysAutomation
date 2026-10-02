package com.gsl.validation;

import com.gsl.enums.RuleGroup;
import com.gsl.enums.ValidationRule;
import com.gsl.enums.VoucherDomain;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Selects the subset of {@link ValidationRule} constants applicable to a given voucher domain and
 * execution selector — the entry point into the data-driven rule engine described on {@link
 * ValidationRule}'s own Javadoc. A domain-scoped caller (e.g. {@code RuleTest}) uses this instead
 * of iterating {@code ValidationRule.values()} itself and re-implementing the domain/group
 * matching logic.
 */
public class RuleFilter {

    /**
     * Returns every {@link ValidationRule} whose domain matches {@code domain} (or is {@link
     * VoucherDomain#COMMON}) and whose group matches {@code executionType} per {@link
     * #matchesGroup}.
     */
    public static List<ValidationRule> filterRules(VoucherDomain domain, RuleGroup executionType) {
        return Arrays.stream(ValidationRule.values())
                .filter(rule -> rule.getDomain() == domain || rule.getDomain() == VoucherDomain.COMMON)
                .filter(rule -> matchesGroup(rule, executionType))
                .collect(Collectors.toList());
    }

    /**
     * {@code MANDATORY} selects every mandatory rule regardless of its {@link RuleGroup};
     * {@code GROUP_1}/{@code GROUP_2} select an exact group match; {@code ALL} selects everything.
     */
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
