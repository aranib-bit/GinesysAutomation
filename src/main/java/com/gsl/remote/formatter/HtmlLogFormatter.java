package com.gsl.remote.formatter;

import com.gsl.remote.model.LogEntry;

import java.util.List;

/**
 * Formats log entries as an HTML snippet suitable for embedding inside an ExtentReport
 * collapsible section. Applies severity-based colour coding and truncation notice.
 *
 * <p>Used wherever a {@link com.gsl.remote.model.LogFetchResponse}'s entries need to be shown
 * inline in a test report rather than dumped as plain text — see {@link LogFormatter} for the
 * general contract.
 */
public final class HtmlLogFormatter implements LogFormatter {

    private static final int DEFAULT_MAX_DISPLAY = 500;

    private final int maxDisplay;

    /** Formats with the default cap of {@value #DEFAULT_MAX_DISPLAY} displayed lines. */
    public HtmlLogFormatter() {
        this.maxDisplay = DEFAULT_MAX_DISPLAY;
    }

    /** Formats with a caller-supplied cap on the number of lines rendered before truncating. */
    public HtmlLogFormatter(int maxDisplay) {
        this.maxDisplay = maxDisplay;
    }

    /** Renders up to {@code maxDisplay} entries as colour-coded {@code <div>} lines, with a truncation notice for the remainder. */
    @Override
    public String format(List<LogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "<div style='color:#999;font-size:11px;'>No log entries captured.</div>";
        }

        int total   = entries.size();
        int display = Math.min(total, maxDisplay);

        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:monospace;font-size:11px;line-height:1.4;'>");

        for (int i = 0; i < display; i++) {
            LogEntry e = entries.get(i);
            String colour = levelColour(e.getLevel());
            sb.append("<div style='color:")
              .append(colour)
              .append(";padding:1px 0;white-space:pre-wrap;word-break:break-word;'>")
              .append(esc(e.getRawLine() != null ? e.getRawLine() : e.getMessage()))
              .append("</div>");
        }

        if (total > maxDisplay) {
            sb.append("<div style='color:#888;font-style:italic;margin-top:4px;'>")
              .append("… ").append(total - maxDisplay).append(" more lines not shown")
              .append("</div>");
        }

        sb.append("</div>");
        return sb.toString();
    }

    /** Maps a log level to its display colour; unknown/null levels get the INFO colour. */
    private static String levelColour(String level) {
        if (level == null) return "#555";
        return switch (level.toUpperCase()) {
            case "ERROR"            -> "#c0392b";
            case "WARN", "WARNING"  -> "#e67e22";
            case "DEBUG", "TRACE"   -> "#7f8c8d";
            default                 -> "#2c3e50";   // INFO and unknown
        };
    }

    /** Escapes {@code &}, {@code <}, {@code >} so raw log text is safe to embed as HTML. */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
