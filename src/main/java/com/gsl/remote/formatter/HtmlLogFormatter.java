package com.gsl.remote.formatter;

import com.gsl.remote.model.LogEntry;

import java.util.List;

/**
 * Formats log entries as an HTML snippet suitable for embedding inside an ExtentReport
 * collapsible section. Applies severity-based colour coding and truncation notice.
 */
public final class HtmlLogFormatter implements LogFormatter {

    private static final int DEFAULT_MAX_DISPLAY = 500;

    private final int maxDisplay;

    public HtmlLogFormatter() {
        this.maxDisplay = DEFAULT_MAX_DISPLAY;
    }

    public HtmlLogFormatter(int maxDisplay) {
        this.maxDisplay = maxDisplay;
    }

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

    private static String levelColour(String level) {
        if (level == null) return "#555";
        return switch (level.toUpperCase()) {
            case "ERROR"            -> "#c0392b";
            case "WARN", "WARNING"  -> "#e67e22";
            case "DEBUG", "TRACE"   -> "#7f8c8d";
            default                 -> "#2c3e50";   // INFO and unknown
        };
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
