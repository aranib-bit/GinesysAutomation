package com.gsl.diagnostics.section;

import com.gsl.diagnostics.DiagnosticSection;
import com.gsl.remote.formatter.HtmlLogFormatter;
import com.gsl.remote.model.LogFetchResponse;

/**
 * Diagnostic section that renders remote server log entries inside the
 * "Logs & Diagnostics" collapsible block in ExtentReport.
 *
 * Plugs into the existing {@link com.gsl.diagnostics.DiagnosticReport} builder
 * without touching TestListener's report chain.
 *
 * Usage:
 * <pre>
 *   new DiagnosticReport()
 *       .add(new RemoteLogSection(response, "DB Server (172.22.4.4)"))
 *       .attachToReport();
 * </pre>
 */
public final class RemoteLogSection implements DiagnosticSection {

    private final LogFetchResponse  response;
    private final String            serverLabel;
    private final HtmlLogFormatter  formatter;

    public RemoteLogSection(LogFetchResponse response, String serverLabel) {
        this.response    = response;
        this.serverLabel = serverLabel;
        this.formatter   = new HtmlLogFormatter();
    }

    @Override
    public String sectionTitle() {
        if (response == null || !response.hasEntries()) {
            return "Remote Logs — " + serverLabel + " (0 entries)";
        }
        int  entries = response.getEntries().size();
        int  retries = response.getRetriesUsed();
        long ms      = response.getDurationMs();
        return String.format("Remote Logs — %s (%d entries, %dms%s)",
                serverLabel, entries, ms,
                retries > 0 ? ", " + retries + " retries" : "");
    }

    @Override
    public String renderHtml() {
        StringBuilder html = new StringBuilder();

        // Meta bar: exit code + stderr summary
        if (response != null && !response.getStderr().isBlank()) {
            html.append("<div style='color:#c0392b;font-size:11px;font-family:monospace;")
                .append("padding:2px 0 4px 0;'>stderr: ")
                .append(esc(response.getStderr()))
                .append("</div>");
        }

        html.append(formatter.format(
                response != null ? response.getEntries() : null));

        return html.toString();
    }

    @Override
    public boolean hasData() {
        return response != null && response.hasEntries();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
