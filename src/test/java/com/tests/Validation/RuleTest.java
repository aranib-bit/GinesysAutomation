package com.tests.Validation;

import com.gsl.annotations.FrameworkValidation;
import com.gsl.enums.RuleGroup;
import com.gsl.enums.ValidationRule;
import com.gsl.enums.VoucherDomain;
import com.gsl.validation.RuleFilter;
import org.testng.Assert;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import java.util.List;

public class RuleTest {

    @Test
    @FrameworkValidation
    @Parameters({"voucherDomain", "executionType"})
    public void executeRules(String voucherDomain, String executionType) {
        VoucherDomain domain = VoucherDomain.valueOf(voucherDomain);
        RuleGroup group = RuleGroup.valueOf(executionType);
        List<ValidationRule> rules = RuleFilter.filterRules(domain, group);

        for (ValidationRule rule : rules) {
            boolean result = rule.test("input1", "input2");
            System.out.println(rule.name() + " [" + rule.getDomain() + "] -> " + result);
            if (rule.isMandatory()) {
                Assert.assertTrue(result, "Mandatory rule failed: " + rule.name());
            }
        }
    }
}
