package com.gsl.diagnostics;

/**
 * A single named sub-section inside a DiagnosticReport.
 *
 * Implement this interface to add any new diagnostic panel — browser logs, network traffic,
 * HTML capture, server metrics, event viewer, etc — without touching TestListener or the
 * DiagnosticReport builder itself.
 */
public interface DiagnosticSection {

    /** Short title shown in the collapsible summary, e.g. "Browser Logs (85 entries)". */
    String sectionTitle();

    /**
     * Returns the inner HTML rendered inside the section's collapsible block.
     * Only called when hasData() is true.
     */
    String renderHtml();

    /** When false the section is silently omitted from the report. */
    boolean hasData();
}
