package com.gsl.utils;

import com.gsl.enums.RuleGroup;
import com.gsl.enums.ValidationRule;
import com.gsl.enums.VoucherDomain;
import com.gsl.validation.RuleFilter;

import java.util.List;

public class RuleExecutor {

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
