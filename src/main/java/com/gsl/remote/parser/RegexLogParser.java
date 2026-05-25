package com.gsl.remote.parser;

import com.gsl.config.ConfigFactory;
import com.gsl.remote.model.LogEntry;

import java.util.Optional;

/**
 * Parses log lines from all GINESYS server-side log files into {@link LogEntry} instances.
 *
 * <p>Recognised formats are defined in {@link LogFormat} and tried in declaration order:
 * <ol>
 *   <li>{@link LogFormat#FINANCE_API} — custom Serilog with {@code #} field separators</li>
 *   <li>{@link LogFormat#LOG4NET} — Log4Net level-first with {@code ,} millisecond separator</li>
 *   <li>{@link LogFormat#ISO_SINGLE_TOKEN} — ISO-8601 single-token fallback</li>
 * </ol>
 *
 * <p>Any line not matching any format is returned as a raw {@link LogEntry} with
 * {@code null} timestamp so no log line is ever silently discarded.
 *
 * <p>To add or modify a log format, edit {@link LogFormat} — no changes required here.
 */
public final class RegexLogParser implements LogParser {

    @Override
    public Optional<LogEntry> parse(String line) {
        if (line == null || line.isBlank()) return Optional.empty();

        String trimmed = line.trim();
        String offset  = serverTimezoneOffset();

        for (LogFormat format : LogFormat.values()) {
            Optional<LogEntry> entry = format.tryParse(trimmed, offset);
            if (entry.isPresent()) return entry;
        }

        // Unrecognised format (e.g. stack trace continuation lines) — raw entry, nothing is lost
        return Optional.of(LogEntry.builder()
                .timestamp(null)
                .level(null)
                .message(trimmed)
                .rawLine(line)
                .build());
    }

    private static String serverTimezoneOffset() {
        try {
            String offset = ConfigFactory.getConfig().remoteLogsServerTimezoneOffset();
            return (offset != null && !offset.isBlank()) ? offset : "Z";
        } catch (Exception e) {
            return "Z";
        }
    }
}
