package com.gsl.validation.snapshot;

/**
 * Evaluates one column's {@link ColumnRule} against a raw BEFORE/AFTER value pair — doc §1,
 * plan §1. Normalizes both sides via {@link DbValueNormalizer} before delegating the actual
 * pass/fail decision to {@link ComparisonStrategy#evaluate}.
 */
public class ColumnRuleEngine {

    /** Convenience overload for the ordinary both-present diff path — equivalent to the 8-arg
     *  overload with {@code hasBeforeState=true}. See that overload's Javadoc. */
    public ColumnCheckResult evaluate(String tableName, String businessKey, String column,
            ColumnRule rule, Object beforeRaw, Object afterRaw, String currentUser) {
        return evaluate(tableName, businessKey, column, rule, beforeRaw, afterRaw, currentUser, true);
    }

    /**
     * @param hasBeforeState {@code false} for a column evaluated on an INSERT/DELETE-explode row
     *                       (Create/Delete-mode — there is genuinely no before-state either
     *                       direction, see {@link DiffEngine#diffDataTable}'s override-evaluation
     *                       branch), so the failure message omits the "before=" fragment instead of
     *                       always showing a misleading placeholder. {@code true} (the 7-arg
     *                       overload's default) for the ordinary both-present diff path, where a
     *                       real before-value genuinely exists.
     */
    public ColumnCheckResult evaluate(String tableName, String businessKey, String column,
            ColumnRule rule, Object beforeRaw, Object afterRaw, String currentUser, boolean hasBeforeState) {

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

        // Item D (Round 9): a Create/Delete-mode row has no genuine before-state — the message
        // used to always say "before='—'" regardless, reading as if a real comparison happened.
        String message = hasBeforeState
                ? "[" + tableName + "] " + businessKey + " column '" + column + "' — expected "
                        + expectationLabel + " but before='" + beforeDisplay + "' after='" + afterDisplay + "'"
                : "[" + tableName + "] " + businessKey + " column '" + column + "' — expected "
                        + expectationLabel + " but actual='" + afterDisplay + "'";
        if (rule.getDiagnosticNote() != null) {
            message += " | " + rule.getDiagnosticNote();
        }
        ValidationFailure failure = new ValidationFailure(tableName, businessKey, column, expectationLabel,
                afterDisplay, message, rule.getSeverity());
        return ColumnCheckResult.fail(tableName, businessKey, column, expectationLabel, beforeDisplay, afterDisplay, failure);
    }

    /** Renders {@code rule}'s {@link ChangeExpectation} (plus, for {@code MANDATORY}, its
     *  {@link ComparisonStrategy}-specific detail) into the human-readable label shown as
     *  "Expectation" throughout the report. */
    private static String buildExpectationLabel(ColumnRule rule) {
        return switch (rule.getExpectation()) {
            case MUST_CHANGE -> "MUST_CHANGE" + (rule.getExpectedValue() != null ? " (=" + rule.getExpectedValue() + ")" : "");
            case SYSTEM_CHANGE -> "SYSTEM_CHANGE (" + rule.getStrategy() + ")";
            case MUST_NOT_CHANGE -> "MUST_NOT_CHANGE";
            case IGNORE -> "IGNORE";
            case INSERT -> "INSERT";
            case DELETE -> "DELETE";
            case RECREATED -> "RECREATED";
            case MANDATORY -> switch (rule.getStrategy()) {
                case NULL -> "MANDATORY (must be blank)";
                case ZERO -> "MANDATORY (must be zero)";
                case ONE_OF -> "MANDATORY (one of " + String.join(", ", rule.getAllowedValues()) + ")";
                default -> "MANDATORY (not null)";
            };
        };
    }
}
