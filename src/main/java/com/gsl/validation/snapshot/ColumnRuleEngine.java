package com.gsl.validation.snapshot;

/**
 * Evaluates one column's {@link ColumnRule} against a raw BEFORE/AFTER value pair — doc §1,
 * plan §1. Normalizes both sides via {@link DbValueNormalizer} before delegating the actual
 * pass/fail decision to {@link ComparisonStrategy#evaluate}.
 */
public class ColumnRuleEngine {

    public ColumnCheckResult evaluate(String tableName, String businessKey, String column,
            ColumnRule rule, Object beforeRaw, Object afterRaw, String currentUser) {

        String beforeDisplay = DbValueNormalizer.displayString(beforeRaw);
        String afterDisplay = DbValueNormalizer.displayString(afterRaw);
        String expectationLabel = buildExpectationLabel(rule);

        if (rule.getExpectation() == ChangeExpectation.IGNORE) {
            return ColumnCheckResult.pass(tableName, businessKey, column, expectationLabel, beforeDisplay, afterDisplay);
        }

        Object before = DbValueNormalizer.normalize(beforeRaw);
        Object after = DbValueNormalizer.normalize(afterRaw);
        boolean passed = rule.getStrategy().evaluate(before, after, rule, currentUser);

        if (passed) {
            return ColumnCheckResult.pass(tableName, businessKey, column, expectationLabel, beforeDisplay, afterDisplay);
        }

        // A known-issue column (a confirmed, already-tracked defect) never fails the test — surfaced
        // via ColumnCheckResult.knownIssue (passed=true, no ValidationFailure) instead, so it's
        // silently excluded from DiffEngine's `failures` list while staying just as visible in the
        // report as a real failure would be (see SnapshotValidationReporter's "⚠️ Known Issues" table).
        if (rule.isKnownIssue()) {
            return ColumnCheckResult.knownIssue(tableName, businessKey, column, expectationLabel,
                    beforeDisplay, afterDisplay, rule.getDiagnosticNote());
        }

        String message = "[" + tableName + "] " + businessKey + " column '" + column + "' — expected "
                + expectationLabel + " but before='" + beforeDisplay + "' after='" + afterDisplay + "'";
        if (rule.getDiagnosticNote() != null) {
            message += " | " + rule.getDiagnosticNote();
        }
        ValidationFailure failure = new ValidationFailure(tableName, businessKey, column, expectationLabel,
                afterDisplay, message, rule.getSeverity());
        return ColumnCheckResult.fail(tableName, businessKey, column, expectationLabel, beforeDisplay, afterDisplay, failure);
    }

    private static String buildExpectationLabel(ColumnRule rule) {
        return switch (rule.getExpectation()) {
            case MUST_CHANGE -> "MUST_CHANGE" + (rule.getExpectedValue() != null ? " (=" + rule.getExpectedValue() + ")" : "");
            case SYSTEM_CHANGE -> "SYSTEM_CHANGE (" + rule.getStrategy() + ")";
            case MUST_NOT_CHANGE -> "MUST_NOT_CHANGE";
            case IGNORE -> "IGNORE";
            case INSERT -> "INSERT";
            case DELETE -> "DELETE";
            case RECREATED -> "RECREATED";
        };
    }
}
