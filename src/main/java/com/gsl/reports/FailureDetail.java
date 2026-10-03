package com.gsl.reports;

import lombok.Builder;
import lombok.Value;

/**
 * One mismatch/failure line of a failed test - a row of the workbook's "Failure Details" sheet.
 * All strings (blank, never {@code null}, when not applicable) so it outlives the test thread.
 */
@Value
@Builder
public class FailureDetail {

    /** {@code UI}, {@code Database}, {@code Snapshot} or {@code Test flow}. */
    String area;
    /** Where in the form/grid (section &gt; row chips), blank for non-UI areas. */
    String location;
    /** UI component or DB table/column the line is about, when it could be split out. */
    String component;
    /** Checked property (font-size, colour, field name ...), when it could be split out. */
    String property;
    String expected;
    String actual;
    /** The original single-line text, HTML-unescaped - always populated. */
    String detail;
}
