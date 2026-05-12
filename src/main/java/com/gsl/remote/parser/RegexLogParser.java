package com.gsl.remote.parser;

import com.gsl.remote.model.LogEntry;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses log lines with a common timestamp-level-message format:
 *   2024-05-01T10:23:45.123Z ERROR Some message here
 *
 * Falls back to a raw-line LogEntry when the pattern does not match,
 * ensuring no log line is silently discarded.
 */
public final class RegexLogParser implements LogParser {

    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(?<timestamp>\\S+)\\s+(?<level>ERROR|WARN|WARNING|INFO|DEBUG|TRACE)\\s+(?<message>.*)$",
            Pattern.CASE_INSENSITIVE);

    @Override
    public Optional<LogEntry> parse(String line) {
        if (line == null || line.isBlank()) return Optional.empty();

        Matcher m = LOG_PATTERN.matcher(line.trim());
        if (m.matches()) {
            Instant ts = tryParseInstant(m.group("timestamp"));
            return Optional.of(LogEntry.builder()
                    .timestamp(ts)
                    .level(m.group("level").toUpperCase())
                    .message(m.group("message"))
                    .rawLine(line)
                    .build());
        }

        // Unrecognised format — return raw line so nothing is lost
        return Optional.of(LogEntry.builder()
                .timestamp(null)
                .level(null)
                .message(line.trim())
                .rawLine(line)
                .build());
    }

    private static Instant tryParseInstant(String raw) {
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
