package com.gsl.reports;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits an Extent test-node title of the shape
 * {@code [FIN-GV-ADD-COMBO-01] Verify that ... Cheque payment. [1/9]} (built in
 * {@code TestListener.onTestStart}) into its parts, so the coverage workbook shows exactly the
 * report's wording: the bracketed id, the sentence, and the run position as separate columns.
 *
 * @param bracketedId  the id including its brackets, e.g. {@code [FIN-GV-ADD-COMBO-01]}; blank if the title has none
 * @param id           the id without brackets (the workbook's internal join form)
 * @param description  the sentence between the id and the run suffix
 * @param runIndex     {@code 1} of {@code 1/9}; {@code 0} when the title has no run suffix
 * @param runTotal     {@code 9} of {@code 1/9}; {@code 0} when absent
 */
public record TestTitle(String bracketedId, String id, String description, int runIndex, int runTotal) {

    private static final Pattern TITLE = Pattern.compile(
            "^\\s*(?:\\[([^\\]]+)\\]\\s*)?(.*?)(?:\\s*\\[(\\d+)/(\\d+)\\])?\\s*$", Pattern.DOTALL);

    /** Parses {@code title}; never throws - an unparsable/blank title yields blank fields. */
    public static TestTitle parse(String title) {
        if (title == null || title.isBlank()) return new TestTitle("", "", "", 0, 0);
        Matcher m = TITLE.matcher(title);
        if (!m.matches()) return new TestTitle("", "", title.strip(), 0, 0);
        String id = m.group(1) == null ? "" : m.group(1).strip();
        int idx = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
        int tot = m.group(4) == null ? 0 : Integer.parseInt(m.group(4));
        return new TestTitle(id.isEmpty() ? "" : "[" + id + "]", id, m.group(2).strip(), idx, tot);
    }

    /** {@code "1/9"}, or blank when the title carried no run suffix. */
    public String runLabel() {
        return runIndex > 0 && runTotal > 0 ? runIndex + "/" + runTotal : "";
    }
}
