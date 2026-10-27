package com.gsl.validation.api;

/**
 * One API validation check as a table row (Check | Expected | Actual | Status), shared by the Create/Revert/Delete and Edit API validators
 * and by the report renderers. {@code group} is the sub-table it belongs to ("Document", "Line 1", "Line 2", ...).
 */
public record ApiCheckRow(String group, String check, String expected, String actual, boolean passed) {

    /** Splits {@code "Line 2 GL"} into group {@code "Line 2"} and check {@code "GL"}; anything else is in {@code "Document"}. */
    public static ApiCheckRow of(String label, String expected, String actual, boolean passed) {
        String group = "Document";
        String check = label;
        if (label.startsWith("Line ") || label.startsWith("Added line")) {
            int space = label.startsWith("Line ") ? label.indexOf(' ', 5) : label.indexOf(' ', 10);
            if (space > 0) {
                group = label.substring(0, space);
                check = label.substring(space + 1);
            }
        }
        return new ApiCheckRow(group, check, expected, actual, passed);
    }
}
