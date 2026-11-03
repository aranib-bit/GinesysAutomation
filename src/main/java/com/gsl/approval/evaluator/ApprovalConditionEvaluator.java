package com.gsl.approval.evaluator;

import com.gsl.approval.evaluator.ApprovalEvaluationException.Kind;
import com.gsl.approval.model.ApprovalCondition;
import com.gsl.approval.model.ApprovalDataType;
import com.gsl.approval.model.ApprovalEntityType;
import com.gsl.approval.model.ApprovalVariable;
import com.gsl.approval.model.ConditionEvaluation;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Evaluates ONE rule condition against the document's facts: known variable of the right basis, operator valid for its type, fact supplied,
 * values normalized, then the {@link ApprovalOperator} predicate. A line-basis condition holds when ANY line matches. Anything it cannot
 * evaluate is an {@link ApprovalEvaluationException}; it never returns a guessed {@code false}.
 */
public class ApprovalConditionEvaluator {

    public ConditionEvaluation evaluate(ApprovalEntityType entity, ApprovalCondition condition, Map<String, Object> facts) {
        ApprovalVariable variable = ApprovalVariableRegistry.find(entity, condition).orElseThrow(() -> new ApprovalEvaluationException(
                Kind.UNKNOWN_VARIABLE, "Rule variable '" + condition.getVarCode() + "' has no extractor for entity " + entity.code()));
        if (condition.getBasis() != null && !condition.getBasis().equalsIgnoreCase(variable.basis())) {
            throw new ApprovalEvaluationException(Kind.BASIS_MISMATCH, "Rule variable '" + condition.getVarCode() + "' is basis "
                    + variable.basis() + " but the rule uses basis " + condition.getBasis());
        }
        ApprovalOperator operator = ApprovalOperator.fromWire(condition.getOperator());
        ApprovalDataType type = variable.dataType();
        if (!operator.supports(type)) {
            throw new ApprovalEvaluationException(Kind.UNSUPPORTED_OPERATOR,
                    "Operator " + operator + " is not valid for " + type + " variable '" + condition.getVarCode() + "'");
        }
        if (facts == null || !facts.containsKey(condition.getVarCode())) {
            throw new ApprovalEvaluationException(Kind.MISSING_FACT,
                    "No document value was supplied for rule variable '" + condition.getVarCode() + "'");
        }
        Object raw = facts.get(condition.getVarCode());
        Object expected = ApprovalOperandReader.expected(condition, type, operator);
        boolean matched = variable.basis().equalsIgnoreCase("L")
                ? anyLineMatches(operator, type, condition.getVarCode(), raw, expected)
                : operator.test(ApprovalOperandReader.actual(type, raw, condition.getVarCode()), expected);
        return new ConditionEvaluation(condition.getVarCode(), operator.name(), expectedText(condition), String.valueOf(raw), matched);
    }

    private static boolean anyLineMatches(ApprovalOperator operator, ApprovalDataType type, String varCode, Object raw, Object expected) {
        List<?> lines = raw instanceof Collection<?> c ? List.copyOf(c) : raw == null ? List.of() : List.of(raw);
        if (lines.isEmpty()) {
            return operator == ApprovalOperator.NULL;
        }
        return lines.stream().anyMatch(line -> operator.test(ApprovalOperandReader.actual(type, line, varCode), expected));
    }

    private static String expectedText(ApprovalCondition c) {
        return c.getValueText() != null ? c.getValueText() : c.getValueCode();
    }
}
