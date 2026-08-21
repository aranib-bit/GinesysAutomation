package com.gsl.validation.snapshot.report;

import com.gsl.validation.snapshot.ColumnCheckResult;

import java.util.Set;

/**
 * Derives a {@link ChangeClassification} for one {@link ColumnCheckResult} — purely from data the
 * engine already computed (its {@code expectationLabel} + pass/fail) plus the audit exclude-list,
 * never a second hand-authored rule set.
 */
public final class ChangeClassifier {

    private ChangeClassifier() {}

    /**
     * @param check              the column check to classify
     * @param excludedColumnsUpper this table's {@code aud2_active_tab_col.exclude_update_of_column}
     *                             set (upper-case) — {@code null}/empty if not yet resolved
     */
    public static ChangeClassification classify(ColumnCheckResult check, Set<String> excludedColumnsUpper) {
        String label = check.expectationLabel();
        boolean excluded = excludedColumnsUpper != null && check.column() != null
                && excludedColumnsUpper.contains(check.column().toUpperCase());

        if (excluded) return ChangeClassification.AUDIT_CHANGE;
        if (check.knownIssueNote() != null) return ChangeClassification.KNOWN_ISSUE;
        if (label.startsWith("IGNORE")) return ChangeClassification.IGNORED;
        if (label.startsWith("EXPECTED")) return ChangeClassification.CASCADE_CHANGE; // row-level INSERT/DELETE
        if (label.startsWith("SYSTEM_CHANGE")) return ChangeClassification.SYSTEM_CHANGE;
        if (label.startsWith("MUST_CHANGE") && check.passed()) return ChangeClassification.USER_CHANGE;
        if (!check.passed()) return ChangeClassification.UNEXPECTED_CHANGE;
        return ChangeClassification.USER_CHANGE;
    }
}
