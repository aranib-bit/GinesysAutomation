package com.gsl.diagnostics;

import com.gsl.reports.ExtentLogger;
import com.gsl.utils.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Assembles multiple DiagnosticSection instances under one collapsible parent block
 * in ExtentReport. Each section becomes a nested {@code <details>} element.
 *
 * Usage:
 * <pre>
 *   new DiagnosticReport()
 *       .add(new BrowserLogSection(logs, description))
 *       .add(new NetworkLogSection(traffic, protocol))
 *       .add(new HtmlCaptureSection(driver, testName))
 *       .add(new ServerUtilSection(ServerRole.APP))
 *       .add(new EventLogSection())
 *       .attachToReport();
 * </pre>
 *
 * Sections where {@code hasData()} returns false are silently skipped.
 * If no sections have data nothing is attached.
 */
public final class DiagnosticReport {

    private final List<DiagnosticSection> sections = new ArrayList<>();

    /** Adds a section. Sections where hasData()==false are skipped at render time. */
    public DiagnosticReport add(DiagnosticSection section) {
        if (section != null) sections.add(section);
        return this;
    }

    /**
     * Builds the diagnostics HTML and attaches it as a named child node on the current
     * ExtentTest (P8). Using createNode() ensures the block always renders inline with
     * other step nodes — appearing last in the step list — rather than at the top of
     * the test entry as a parent-level info message.
     */
    public void attachToReport() {
        List<DiagnosticSection> active = sections.stream()
                .filter(DiagnosticSection::hasData)
                .collect(Collectors.toList());

        if (active.isEmpty()) {
            Log.debug("[DiagnosticReport] All sections reported no data — Logs & Diagnostics node skipped.");
            return;
        }

        // Subtitle line listing which sections are present
        String outerSummary = active.stream()
                .map(DiagnosticSection::sectionTitle)
                .collect(Collectors.joining("  ·  "));

        StringBuilder innerHtml = new StringBuilder();
        // Compact subtitle bar
        innerHtml.append("<div style='font-size:11px;color:#666;padding:4px 0 6px 0;")
                 .append("border-bottom:1px solid #eee;margin-bottom:6px;'>")
                 .append(esc(outerSummary))
                 .append("</div>");

        // One collapsible <details> block per section
        for (DiagnosticSection s : active) {
            innerHtml.append("<details style='margin:4px 0;border:1px solid #e8e8e8;border-radius:3px;'>")
                     .append("<summary style='cursor:pointer;font-weight:bold;font-size:12px;")
                     .append("padding:5px 8px;background:#f8f8f8;'>")
                     .append(esc(s.sectionTitle()))
                     .append("</summary>")
                     .append("<div style='padding:6px 8px;'>")
                     .append(s.renderHtml())
                     .append("</div></details>");
        }

        // Attach as a child node so it renders at the END of the step list
        ExtentLogger.infoHtmlOnChildNode("Logs &amp; Diagnostics", innerHtml.toString());
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
