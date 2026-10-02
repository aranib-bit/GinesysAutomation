package com.gsl.utils;

import com.gsl.enums.RuleGroup;
import com.gsl.enums.ValidationRule;
import com.gsl.enums.VoucherDomain;
import com.gsl.validation.RuleFilter;

import java.util.List;

/**
 * Standalone, manually-run demo/harness for the {@link ValidationRule}/{@link RuleGroup} rule
 * engine — selects a {@link VoucherDomain} and {@link RuleGroup} from JVM system properties,
 * resolves the matching rules via {@link RuleFilter#filterRules(VoucherDomain, RuleGroup)}, and
 * prints each rule's name and result to stdout.
 *
 * <p>TODO-confirm: every {@link ValidationRule} predicate is currently a placeholder evaluated
 * against the literal strings {@code "input1"}/{@code "input2"} (see {@link ValidationRule}'s own
 * Javadoc), so this class exercises the selection/wiring of the rule engine end-to-end, not any
 * real voucher-field validation — the printed pass/fail results don't yet reflect actual business
 * rules. Not a TestNG test class ({@code RuleTest} is the assertion-bearing counterpart); this is
 * a plain {@code main()} for ad hoc, manual inspection of which rules a given domain/group
 * resolves to.
 */
public class RuleExecutor {

    /**
     * Reads {@code voucherDomain} (default {@code "AR"}) and {@code executionType} (default
     * {@code "ALL"}) system properties, filters {@link ValidationRule}s accordingly, and prints
     * each rule's {@link ValidationRule#test(String, String)} result against the literal inputs
     * {@code "input1"}/{@code "input2"}.
     */
    public static void main(String[] args) {
        VoucherDomain domain = VoucherDomain.valueOf(System.getProperty("voucherDomain", "AR"));
        RuleGroup executionType = RuleGroup.valueOf(System.getProperty("executionType", "ALL"));

        List<ValidationRule> rules = RuleFilter.filterRules(domain, executionType);

        for (ValidationRule rule : rules) {
            boolean result = rule.test("input1", "input2");
            System.out.println(rule.name() + " -> " + result);
        }
    }
}
