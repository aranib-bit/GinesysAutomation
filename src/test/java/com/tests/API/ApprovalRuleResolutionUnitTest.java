package com.tests.API;

import com.gsl.approval.evaluator.ApprovalConditionEvaluator;
import com.gsl.approval.evaluator.ApprovalEvaluationException;
import com.gsl.approval.evaluator.ApprovalEvaluationException.Kind;
import com.gsl.approval.evaluator.ApprovalRuleResolver;
import com.gsl.approval.facts.ApVoucherApprovalFacts;
import com.gsl.approval.model.ApprovalCondition;
import com.gsl.approval.model.ApprovalEntityType;
import com.gsl.approval.model.ApprovalRule;
import com.gsl.approval.model.RuleDecision;
import com.gsl.approval.model.RuleEvaluation.Outcome;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Phase 3 of the approval workflow: rule evaluation and priority resolution. The rule sets are the ones in the HARs (Approval RulePrioritySets:
 * "AP Approval Bank" / "AP Approval WEB" / default; the staging set: Below 10L / 10 L or more / RAHULQA / default).
 */
public class ApprovalRuleResolutionUnitTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);
    private final ApprovalRuleResolver resolver = new ApprovalRuleResolver(new ApprovalConditionEvaluator());

    // ---- builders --------------------------------------------------------------------------------------------------------------------------

    private static ApprovalCondition cond(String basis, String var, String type, String op, String text, String code) {
        return ApprovalCondition.builder().basis(basis).varCode(var).dataType(type).operator(op).valueText(text).valueCode(code).build();
    }

    private static ApprovalRule rule(int code, String name, int priority, ApprovalCondition... conditions) {
        return ApprovalRule.builder().ruleCode(code).name(name).priority(priority).hierarchyCode(code).conditions(List.of(conditions)).build();
    }

    private static ApprovalRule defaultRule(int priority) {
        return ApprovalRule.builder().ruleCode(4).name("Default Rule").priority(priority).defaultRule(true).hierarchyCode(1).build();
    }

    /** The staging AP set: amount split at 10 lakh, a remarks rule, then the default. */
    private static List<ApprovalRule> stagingSet() {
        return List.of(
                rule(29, "Below 10L", 1, cond("H", "TOTAL_AMOUNT", "NUMBER", "LT", "1000000", null)),
                rule(30, "10 L or more", 2, cond("H", "TOTAL_AMOUNT", "NUMBER", "GTE", "1000000", null)),
                rule(11, "Rahul_QA_AP_Rule", 3, cond("H", "REMARKS", "TEXT", "LIKE", "RAHULQA", null)),
                defaultRule(4));
    }

    /** The PrioritySets HAR set: cash/bank = A Bank, entry source = WEB, then the default. */
    private static List<ApprovalRule> bankWebSet() {
        return List.of(
                rule(5, "AP Approval Bank", 1, cond("H", "CASH_BANK", "LOOKUP", "EQ", "A Bank", "4")),
                rule(6, "AP Approval WEB", 2, cond("H", "ENTRY_SOURCE", "LOOKUP", "EQ", "WEB", "WEB")),
                defaultRule(3));
    }

    private RuleDecision resolveAp(List<ApprovalRule> rules, Map<String, Object> facts) {
        return resolver.resolve(ApprovalEntityType.AP, rules, facts, TODAY);
    }

    private static String winner(RuleDecision d) {
        return d.matched().map(ApprovalRule::getName).orElse(null);
    }

    // ---- priority and amount boundaries -----------------------------------------------------------------------------------------------------

    @Test
    public void smallAmountMatchesTheFirstRuleAndLaterRulesAreNotReached() {
        RuleDecision d = resolveAp(stagingSet(), ApVoucherApprovalFacts.builder().totalAmount(new BigDecimal("500")).remarks("x").build());

        assertEquals(winner(d), "Below 10L");
        assertEquals(d.getEvaluations().get(0).getOutcome(), Outcome.MATCHED);
        assertEquals(d.getEvaluations().get(1).getOutcome(), Outcome.NOT_REACHED);
        assertEquals(d.getEvaluations().get(3).getOutcome(), Outcome.NOT_REACHED);
        assertEquals(d.getEvaluations().get(0).getConditions().get(0).getActual(), "500");
    }

    @Test
    public void largeAmountSkipsTheFirstRule() {
        RuleDecision d = resolveAp(stagingSet(), ApVoucherApprovalFacts.builder().totalAmount(new BigDecimal("1200000")).remarks("x").build());
        assertEquals(winner(d), "10 L or more");
        assertEquals(d.getEvaluations().get(0).getOutcome(), Outcome.NOT_MATCHED);
    }

    @Test
    public void exactBoundaryBelongsToTheGteRule() {
        Map<String, Object> facts = ApVoucherApprovalFacts.builder().totalAmount(new BigDecimal("1000000")).remarks("x").build();
        assertEquals(winner(resolveAp(stagingSet(), facts)), "10 L or more");
    }

    @Test
    public void justBelowAndJustAboveTheBoundary() {
        assertEquals(winner(resolveAp(stagingSet(), ApVoucherApprovalFacts.builder().totalAmount(new BigDecimal("999999.99")).remarks("x").build())), "Below 10L");
        assertEquals(winner(resolveAp(stagingSet(), ApVoucherApprovalFacts.builder().totalAmount(new BigDecimal("1000000.01")).remarks("x").build())), "10 L or more");
    }

    @Test
    public void decimalScaleDoesNotChangeEquality() {
        List<ApprovalRule> rules = List.of(rule(1, "Exactly 10L", 1, cond("H", "TOTAL_AMOUNT", "NUMBER", "EQ", "1000000", null)), defaultRule(2));
        assertEquals(winner(resolveAp(rules, ApVoucherApprovalFacts.builder().totalAmount(new BigDecimal("1000000.00")).build())), "Exactly 10L");
    }

    @Test
    public void numbersGivenAsStringsAreComparedNumerically() {
        // "9" must not sort after "10" as text.
        List<ApprovalRule> rules = List.of(rule(1, "Over 10", 1, cond("H", "PAYMENT_AMOUNT", "NUMBER", "GT", "10", null)), defaultRule(2));
        assertEquals(winner(resolveAp(rules, Map.of("PAYMENT_AMOUNT", "9"))), "Default Rule");
        assertEquals(winner(resolveAp(rules, Map.of("PAYMENT_AMOUNT", "100"))), "Over 10");
    }

    @Test
    public void ruleOrderDoesNotDependOnTheOrderPassedIn() {
        List<ApprovalRule> shuffled = new ArrayList<>(stagingSet());
        Collections.reverse(shuffled);
        assertEquals(winner(resolveAp(shuffled, ApVoucherApprovalFacts.builder().totalAmount(new BigDecimal("500")).remarks("x").build())), "Below 10L");
    }

    // ---- lookup, text, line and fallback ----------------------------------------------------------------------------------------------------

    @Test
    public void lookupConditionsCompareByCode() {
        Map<String, Object> bank = ApVoucherApprovalFacts.builder().cashBank(4).entrySource("MANUAL").build();
        assertEquals(winner(resolveAp(bankWebSet(), bank)), "AP Approval Bank");

        Map<String, Object> web = ApVoucherApprovalFacts.builder().cashBank(9).entrySource("WEB").build();
        assertEquals(winner(resolveAp(bankWebSet(), web)), "AP Approval WEB");
    }

    @Test
    public void noMatchingRuleFallsBackToTheDefault() {
        Map<String, Object> other = ApVoucherApprovalFacts.builder().cashBank(9).entrySource("MANUAL").build();
        RuleDecision d = resolveAp(bankWebSet(), other);
        assertEquals(winner(d), "Default Rule");
        assertTrue(d.matched().orElseThrow().isDefaultRule());
    }

    @Test
    public void noApplicableRuleWhenThereIsNoDefault() {
        List<ApprovalRule> noDefault = List.of(rule(5, "AP Approval Bank", 1, cond("H", "CASH_BANK", "LOOKUP", "EQ", "A Bank", "4")));
        RuleDecision d = resolveAp(noDefault, ApVoucherApprovalFacts.builder().cashBank(9).build());
        assertFalse(d.matched().isPresent());
        assertEquals(d.getEvaluations().get(0).getOutcome(), Outcome.NOT_MATCHED);
    }

    @Test
    public void lineConditionHoldsWhenAnyLineMatches() {
        List<ApprovalRule> rules = List.of(
                rule(31, "Suppliers", 1, cond("L", "SUB_LEDGER", "LOOKUP", "IN", "[\"A\",\"B\"]", "[193,206]")), defaultRule(2));
        assertEquals(winner(resolveAp(rules, ApVoucherApprovalFacts.builder().subLedgers(List.of(5, 206)).build())), "Suppliers");
        assertEquals(winner(resolveAp(rules, ApVoucherApprovalFacts.builder().subLedgers(List.of(5, 7)).build())), "Default Rule");
        assertEquals(winner(resolveAp(rules, ApVoucherApprovalFacts.builder().subLedgers(List.of()).build())), "Default Rule");
    }

    @Test
    public void textLikeIsCaseInsensitiveContains() {
        List<ApprovalRule> rules = List.of(rule(11, "Rahul", 1, cond("H", "REMARKS", "TEXT", "LIKE", "RAHULQA", null)), defaultRule(2));
        assertEquals(winner(resolveAp(rules, ApVoucherApprovalFacts.builder().remarks("batch rahulqa 7").build())), "Rahul");
        assertEquals(winner(resolveAp(rules, ApVoucherApprovalFacts.builder().remarks("plain").build())), "Default Rule");
    }

    @Test
    public void betweenIsInclusiveForNumbersAndDates() {
        List<ApprovalRule> amount = List.of(rule(1, "Band", 1, cond("H", "TOTAL_AMOUNT", "NUMBER", "BETWEEN", "[100,200]", null)), defaultRule(2));
        assertEquals(winner(resolveAp(amount, Map.of("TOTAL_AMOUNT", new BigDecimal("100")))), "Band");
        assertEquals(winner(resolveAp(amount, Map.of("TOTAL_AMOUNT", new BigDecimal("200")))), "Band");
        assertEquals(winner(resolveAp(amount, Map.of("TOTAL_AMOUNT", new BigDecimal("200.01")))), "Default Rule");

        List<ApprovalRule> dates = List.of(rule(2, "Q", 1, cond("H", "VOUCHER_DATE", "DATE", "BETWEEN", "2026-04-01,2026-06-30", null)), defaultRule(2));
        assertEquals(winner(resolveAp(dates, Map.of("VOUCHER_DATE", LocalDate.of(2026, 6, 30)))), "Q");
        assertEquals(winner(resolveAp(dates, Map.of("VOUCHER_DATE", LocalDate.of(2026, 7, 1)))), "Default Rule");
    }

    @Test
    public void nullHandling() {
        List<ApprovalRule> eq = List.of(rule(1, "R", 1, cond("H", "REFERENCE", "TEXT", "EQ", "x", null)), defaultRule(2));
        assertEquals(winner(resolveAp(eq, Collections.singletonMap("REFERENCE", null))), "Default Rule");

        List<ApprovalRule> isNull = List.of(rule(1, "NoRef", 1, cond("H", "REFERENCE", "TEXT", "NULL", null, null)), defaultRule(2));
        assertEquals(winner(resolveAp(isNull, Collections.singletonMap("REFERENCE", null))), "NoRef");
        assertEquals(winner(resolveAp(isNull, Map.of("REFERENCE", "abc"))), "Default Rule");

        List<ApprovalRule> notNull = List.of(rule(1, "HasRef", 1, cond("H", "REFERENCE", "TEXT", "NOTNULL", null, null)), defaultRule(2));
        assertEquals(winner(resolveAp(notNull, Map.of("REFERENCE", "abc"))), "HasRef");
    }

    @Test
    public void ruleNotYetEffectiveIsSkipped() {
        ApprovalRule future = rule(7, "Future", 1, cond("H", "TOTAL_AMOUNT", "NUMBER", "GT", "0", null)).toBuilder()
                .effectiveDate(TODAY.plusDays(1)).build();
        RuleDecision d = resolveAp(List.of(future, defaultRule(2)), Map.of("TOTAL_AMOUNT", new BigDecimal("5")));
        assertEquals(winner(d), "Default Rule");
        assertEquals(d.getEvaluations().get(0).getOutcome(), Outcome.NOT_EFFECTIVE);
    }

    @Test
    public void udfVariablesUseTheConditionsOwnType() {
        List<ApprovalRule> rules = List.of(rule(9, "Udf", 1, cond("H", "UDFSTRING03", "TEXT", "EQ", "Surat", null)), defaultRule(2));
        assertEquals(winner(resolveAp(rules, Map.of("UDFSTRING03", "surat"))), "Udf");
    }

    // ---- things that must fail loudly -------------------------------------------------------------------------------------------------------

    private static void assertKind(Kind expected, Runnable action) {
        assertEquals(expectThrows(ApprovalEvaluationException.class, action::run).getKind(), expected);
    }

    @Test
    public void missingFactIsReportedNotTreatedAsNoMatch() {
        assertKind(Kind.MISSING_FACT, () -> resolveAp(stagingSet(), Map.of("REMARKS", "x")));
    }

    @Test
    public void unknownVariableIsReported() {
        List<ApprovalRule> rules = List.of(rule(1, "R", 1, cond("H", "NO_SUCH_VAR", "TEXT", "EQ", "x", null)), defaultRule(2));
        assertKind(Kind.UNKNOWN_VARIABLE, () -> resolveAp(rules, Map.of("NO_SUCH_VAR", "x")));
    }

    @Test
    public void entityWithoutRegisteredVariablesFailsOnAnyConditionButResolvesTheDefault() {
        List<ApprovalRule> withCondition = List.of(rule(1, "R", 1, cond("H", "NET_AMOUNT", "NUMBER", "GT", "1", null)), defaultRule(2));
        assertKind(Kind.UNKNOWN_VARIABLE, () -> resolver.resolve(ApprovalEntityType.PO, withCondition, Map.of("NET_AMOUNT", 5), TODAY));
        assertEquals(winner(resolver.resolve(ApprovalEntityType.PO, List.of(defaultRule(1)), Map.of(), TODAY)), "Default Rule");
    }

    @Test
    public void unknownAndTypeInvalidOperatorsAreReported() {
        List<ApprovalRule> unknown = List.of(rule(1, "R", 1, cond("H", "TOTAL_AMOUNT", "NUMBER", "FOO", "1", null)), defaultRule(2));
        assertKind(Kind.UNSUPPORTED_OPERATOR, () -> resolveAp(unknown, Map.of("TOTAL_AMOUNT", 1)));
        List<ApprovalRule> gtOnLookup = List.of(rule(1, "R", 1, cond("H", "CASH_BANK", "LOOKUP", "GT", "4", "4")), defaultRule(2));
        assertKind(Kind.UNSUPPORTED_OPERATOR, () -> resolveAp(gtOnLookup, Map.of("CASH_BANK", 4)));
    }

    @Test
    public void basisMismatchIsReported() {
        List<ApprovalRule> rules = List.of(rule(1, "R", 1, cond("L", "TOTAL_AMOUNT", "NUMBER", "GT", "1", null)), defaultRule(2));
        assertKind(Kind.BASIS_MISMATCH, () -> resolveAp(rules, Map.of("TOTAL_AMOUNT", 5)));
    }

    @Test
    public void unreadableValuesAreReported() {
        assertKind(Kind.BAD_VALUE, () -> resolveAp(stagingSet(), Map.of("TOTAL_AMOUNT", "abc", "REMARKS", "x")));
        List<ApprovalRule> badBetween = List.of(rule(1, "R", 1, cond("H", "TOTAL_AMOUNT", "NUMBER", "BETWEEN", "100", null)), defaultRule(2));
        assertKind(Kind.BAD_VALUE, () -> resolveAp(badBetween, Map.of("TOTAL_AMOUNT", 5)));
    }

    @Test
    public void aRuleThatCannotMatchNeverTripsOnALaterBadCondition() {
        // First condition is false, so the unsupported second one is never evaluated.
        List<ApprovalRule> rules = List.of(rule(1, "R", 1,
                cond("H", "TOTAL_AMOUNT", "NUMBER", "GT", "1000", null), cond("H", "REMARKS", "TEXT", "FOO", "x", null)), defaultRule(2));
        assertEquals(winner(resolveAp(rules, Map.of("TOTAL_AMOUNT", 5, "REMARKS", "x"))), "Default Rule");
    }
}
