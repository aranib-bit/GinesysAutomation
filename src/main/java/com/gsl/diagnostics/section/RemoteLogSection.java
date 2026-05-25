package com.gsl.diagnostics.section;

import com.gsl.diagnostics.DiagnosticSection;
import com.gsl.remote.formatter.HtmlLogFormatter;
import com.gsl.remote.model.LogEntry;
import com.gsl.remote.model.LogFetchResponse;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Diagnostic section that renders remote server log entries inside the
 * "Logs & Diagnostics" collapsible block in ExtentReport.
 *
 * When the fetcher emits {@code ###FILE:###} markers (WinRM + SSH), entries are
 * grouped by source file. Each file gets its own collapsible sub-section with a
 * download button. Falls back to a flat list for EventLog and legacy fetches.
 */
public final class RemoteLogSection implements DiagnosticSection {

    private final LogFetchResponse  response;
    private final String            serverLabel;
    private final HtmlLogFormatter  formatter;
    private final String            skipReason;

    /** Normal constructor — used when log collection ran (response may have 0 entries). */
    public RemoteLogSection(LogFetchResponse response, String serverLabel) {
        this.response    = response;
        this.serverLabel = serverLabel;
        this.formatter   = new HtmlLogFormatter();
        this.skipReason  = null;
    }

    /**
     * Skip-reason constructor — used when collection was skipped (missing credentials,
     * host not configured, etc.). The section is always rendered so the report is
     * self-explanatory rather than silently absent.
     */
    public RemoteLogSection(String serverLabel, String skipReason) {
        this.response    = null;
        this.serverLabel = serverLabel;
        this.formatter   = new HtmlLogFormatter();
        this.skipReason  = skipReason;
    }

    @Override
    public String sectionTitle() {
        if (skipReason != null) return serverLabel + " — skipped";
        if (response == null)   return serverLabel + " (0 entries)";

        List<String> filesRead = response.getFilesRead();
        int fileCount  = (filesRead != null) ? filesRead.size() : 0;
        int entryCount = response.hasEntries() ? response.getEntries().size() : 0;
        int retries    = response.getRetriesUsed();
        long ms        = response.getDurationMs();

        String timingHint = ms > 0
                ? String.format(", %dms%s", ms, retries > 0 ? ", " + retries + " retries" : "")
                : "";

        if (fileCount > 0) {
            return String.format("%s (%d file%s, %d entr%s%s)",
                    serverLabel,
                    fileCount, fileCount == 1 ? "" : "s",
                    entryCount, entryCount == 1 ? "y" : "ies",
                    timingHint);
        }
        return String.format("%s (%d entr%s%s)",
                serverLabel, entryCount, entryCount == 1 ? "y" : "ies", timingHint);
    }

    @Override
    public String renderHtml() {
        if (skipReason != null) {
            return "<div style='color:#e67e22;font-family:monospace;font-size:11px;padding:4px;'>"
                    + esc(skipReason) + "</div>";
        }

        StringBuilder html = new StringBuilder();

        // Stderr — suppress raw PowerShell CLIXML progress output
        if (response != null && !response.getStderr().isBlank()) {
            String cleaned = suppressClixml(response.getStderr());
            if (!cleaned.isBlank()) {
                html.append("<div style='color:#c0392b;font-size:11px;font-family:monospace;")
                    .append("padding:2px 0 4px 0;'>stderr: ")
                    .append(esc(cleaned))
                    .append("</div>");
            }
        }

        if (response == null) {
            html.append(formatter.format(null));
            return html.toString();
        }

        // Always show the time window — useful context whether entries were found or not
        html.append(renderTimeWindow(response));

        List<String> filesRead = response.getFilesRead();

        // Per-file sub-sections when file markers were emitted by the fetcher
        if (filesRead != null && !filesRead.isEmpty()) {
            Map<String, List<LogEntry>> byFile      = response.getEntriesByFile();
            Map<String, String>         fileErrors  = response.getFileErrors();
            for (String filePath : filesRead) {
                List<LogEntry> fileEntries = byFile.getOrDefault(filePath, List.of());
                String baseName  = extractBaseName(filePath);
                int    count     = fileEntries.size();
                String fileError = (fileErrors != null) ? fileErrors.get(filePath) : null;

                // Error and 0-entry files: open by default so the outcome is immediately visible.
                // Files with entries: collapsed (content can be long).
                String openAttr = (fileError != null || count == 0) ? " open" : "";

                html.append("<details").append(openAttr)
                    .append(" style='margin:3px 0;border:1px solid #e0e0e0;border-radius:2px;'>")
                    .append("<summary style='cursor:pointer;font-size:11px;padding:3px 8px;")
                    .append("background:#f7f7f7;display:flex;align-items:center;gap:8px;list-style:none;'>")
                    .append("<span style='font-family:monospace;font-weight:bold;'>")
                    .append(esc(baseName)).append("</span>");

                if (fileError != null) {
                    html.append(" <span style='color:#c0392b;font-size:10px;'>&#10007; error</span>")
                        .append(" <span style='color:#c0392b;font-size:10px;'>")
                        .append(esc(fileError)).append("</span>");
                } else if (count == 0) {
                    html.append(" <span style='color:#27ae60;font-size:10px;'>&#10003; checked</span>")
                        .append(" <span style='color:#bbb;font-size:10px;'>&mdash; no matching entries</span>");
                } else {
                    html.append(" <span style='color:#555;'>(")
                        .append(count).append(count == 1 ? " entry" : " entries")
                        .append(")</span>")
                        .append(buildDownloadButton(fileEntries, baseName));
                }

                html.append("</summary>")
                    .append("<div style='padding:4px 8px;max-height:300px;overflow-y:auto;'>")
                    .append(fileError != null
                            ? "<div style='color:#c0392b;font-family:monospace;font-size:11px;padding:2px 0;'>"
                              + "Error reading file: " + esc(fileError) + "</div>"
                            : count > 0
                              ? formatter.format(fileEntries)
                              : "<div style='color:#bbb;font-size:11px;font-style:italic;padding:2px 0;'>"
                                + "Checked &mdash; no log entries matched the keyword filter in this test&#39;s time window."
                                + "</div>")
                    .append("</div></details>");
            }
            return html.toString();
        }

        // Flat fallback (EventLog or legacy fetches without file markers)
        List<LogEntry> entries = response.getEntries();
        if (entries == null || entries.isEmpty()) {
            html.append(renderNoEntriesInfo(response));
        } else {
            html.append(formatter.format(entries));
        }
        return html.toString();
    }

    @Override
    public boolean hasData() {
        return skipReason != null || response != null;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter TIME_ONLY_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private String renderTimeWindow(LogFetchResponse r) {
        Instant s = r.getTimeWindowStart();
        Instant e = r.getTimeWindowEnd();
        if (s == null || e == null) return "";

        // Always show the date on the start side; end is time-only when same calendar day.
        ZoneId zone = ZoneId.systemDefault();
        boolean sameDay = s.atZone(zone).toLocalDate().equals(e.atZone(zone).toLocalDate());
        DateTimeFormatter endFmt = sameDay ? TIME_ONLY_FMT : DATE_TIME_FMT;

        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-size:11px;color:#555;margin-bottom:4px;'>")
          .append("Searched: <strong>").append(DATE_TIME_FMT.format(s))
          .append(" &rarr; ").append(endFmt.format(e)).append("</strong>");
        if (r.getDurationMs() > 0) {
            sb.append(" <span style='color:#999;'>(").append(r.getDurationMs()).append(" ms)</span>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private String renderNoEntriesInfo(LogFetchResponse r) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='color:#999;font-size:11px;'>No log entries captured in the test time window.</div>");

        List<String> paths = r.getConfiguredPaths();
        if (paths != null && !paths.isEmpty()) {
            sb.append("<div style='font-size:11px;color:#555;margin-top:4px;'>Configured paths:</div>")
              .append("<ul style='margin:2px 0 0 16px;padding:0;font-family:monospace;font-size:11px;'>");
            for (String p : paths) {
                sb.append("<li style='padding:1px 0;'>").append(esc(p)).append("</li>");
            }
            sb.append("</ul>");
        }

        return sb.toString();
    }

    private String buildDownloadButton(List<LogEntry> entries, String baseName) {
        String text = entries.stream()
                .map(e -> e.getRawLine() != null ? e.getRawLine() : e.getMessage())
                .collect(Collectors.joining("\n"));
        String b64  = Base64.getEncoder()
                .encodeToString(text.getBytes(StandardCharsets.UTF_8));
        String name = baseName.toLowerCase().endsWith(".log") ? baseName : baseName + ".log";
        return "<a href='data:text/plain;charset=utf-8;base64," + b64 + "' "
                + "download='" + esc(name) + "' "
                + "style='font-size:10px;color:#2980b9;text-decoration:none;margin-left:4px;"
                + "border:1px solid #2980b9;border-radius:2px;padding:1px 5px;' "
                + "onclick='event.stopPropagation()'>&#8595; Download</a>";
    }

    private static String extractBaseName(String path) {
        if (path == null || path.isBlank()) return "Unknown";
        int lastSep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSep >= 0 ? path.substring(lastSep + 1) : path;
    }

    /** Strips PowerShell CLIXML progress output (#< CLIXML ... Objs>) from stderr. */
    private static String suppressClixml(String stderr) {
        if (stderr == null) return "";
        String trimmed = stderr.trim();
        if (trimmed.startsWith("#< CLIXML")) return "";
        return trimmed;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("'", "&#39;");
    }
}
