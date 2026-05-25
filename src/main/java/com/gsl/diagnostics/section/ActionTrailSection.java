package com.gsl.diagnostics.section;

import com.gsl.diagnostics.DiagnosticSection;

import java.util.List;

/**
 * DiagnosticSection that renders the last N recorded test steps before failure.
 * Populated from ActionTrailBuffer.getForDisplay() — respects action.trail.display.limit.
 * The JSON exporter separately uses ActionTrailBuffer.getAll() for the full untruncated trail.
 */
public final class ActionTrailSection implements DiagnosticSection {

    private final List<String> displaySteps;
    private final int          totalCaptured;

    public ActionTrailSection(List<String> displaySteps, int totalCaptured) {
        this.displaySteps  = displaySteps  != null ? displaySteps : List.of();
        this.totalCaptured = totalCaptured;
    }

    @Override
    public String sectionTitle() {
        return "Action Trail (" + totalCaptured + " steps)";
    }

    @Override
    public boolean hasData() {
        return !displaySteps.isEmpty();
    }

    @Override
    public String renderHtml() {
        if (displaySteps.isEmpty()) {
            return "<div style='color:#888;font-style:italic;'>No steps were recorded. "
                 + "Ensure StepExecutor.step() is used in your test actions.</div>";
        }

        StringBuilder html = new StringBuilder();
        html.append("<ol style='margin:0;padding-left:20px;font-family:monospace;font-size:12px;'>");

        int startIndex = totalCaptured - displaySteps.size() + 1;
        for (int i = 0; i < displaySteps.size(); i++) {
            boolean isLast = (i == displaySteps.size() - 1);
            String  step   = esc(displaySteps.get(i));
            if (isLast) {
                html.append("<li style='color:#cc0000;font-weight:bold;padding:2px 0;'>")
                    .append(step).append(" &nbsp;<span style='color:#888;font-size:10px;'>(last recorded)</span></li>");
            } else {
                html.append("<li style='padding:2px 0;color:#333;'>").append(step).append("</li>");
            }
        }
        html.append("</ol>");

        if (displaySteps.size() < totalCaptured) {
            html.append("<div style='font-size:10px;color:#888;margin-top:4px;font-style:italic;'>")
                .append("Showing last ").append(displaySteps.size())
                .append(" of ").append(totalCaptured).append(" steps captured")
                .append("</div>");
        }

        return html.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
