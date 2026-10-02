package com.gsl.validation.ui;

/**
 * One confirmed, already-tracked UI defect that must stay VISIBLE in the report without failing
 * the test — the UI counterpart of the DB snapshot validator's {@code ColumnRule.knownIssue}.
 *
 * <p>An entry matches a failing check on {@code component} + {@code property} AND the exact
 * {@code actual} value: a DIFFERENT wrong value is a new problem and still FAILS, so a known issue
 * can never mask drift. Register entries in {@link UiKnownIssues}.
 *
 * @param component the result's component key, e.g. {@code FOOTER_BUTTON_COPY}
 * @param property  the checked property, e.g. {@code background-color}
 * @param actual    the exact observed value that is the known (wrong) behaviour
 * @param note      why it is known / what is awaited, shown beside the amber row
 */
public record UiKnownIssue(String component, String property, String actual, String note) {

    boolean matches(String component, String property, String actual) {
        return this.component.equalsIgnoreCase(component == null ? "" : component)
                && this.property.equalsIgnoreCase(property == null ? "" : property)
                && this.actual.equals(actual == null ? "" : actual.trim());
    }

    boolean sameCheck(String component, String property) {
        return this.component.equalsIgnoreCase(component == null ? "" : component)
                && this.property.equalsIgnoreCase(property == null ? "" : property);
    }
}
