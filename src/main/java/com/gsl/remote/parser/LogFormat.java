package com.gsl.remote.parser;

import com.gsl.remote.model.LogEntry;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enumeration of all recognised server-side log formats in the GINESYS stack.
 *
 * <p>Adding support for a new log format = adding one constant here.
 * {@link RegexLogParser} iterates {@code values()} in declaration order and returns
 * the first match; the raw-line fallback at the end of the loop ensures no line is
 * ever silently dropped.
 *
 * <h3>Formats defined</h3>
 * <ul>
 *   <li>{@link #FINANCE_API} — custom Serilog with {@code #} field separators</li>
 *   <li>{@link #LOG4NET} — Log4Net level-first with {@code ,} millisecond separator</li>
 *   <li>{@link #ISO_SINGLE_TOKEN} — ISO-8601 single-token fallback (UTC or offset)</li>
 * </ul>
 */
public enum LogFormat {

    /**
     * FinanceAPI / EFLogger — custom Serilog layout.
     * <pre>2026-04-30 11:13:12.7154 # ClassName # Error # Message # </pre>
     * Two-token timestamp (date + time with {@code .} fractional seconds),
     * level is the third {@code #}-delimited field.
     */
    FINANCE_API(
            "FinanceAPI / EFLogger (Serilog # format)",
            Pattern.compile(
                    "^(?<date>\\d{4}-\\d{2}-\\d{2})\\s+(?<time>\\d{2}:\\d{2}:\\d{2}\\.\\d+)"
                    + "\\s+#\\s+[^#]+#\\s+(?<level>" + Const.LEVELS + ")\\s+#\\s+(?<message>.*?)\\s*#?\\s*$",
                    Pattern.CASE_INSENSITIVE)) {
        @Override
        public Optional<LogEntry> tryParse(String line, String tzOffset) {
            Matcher m = pattern().matcher(line);
            if (!m.matches()) return Optional.empty();
            String tsRaw = m.group("date") + "T" + m.group("time") + tzOffset;
            return Optional.of(LogEntry.builder()
                    .timestamp(tryParseInstant(tsRaw))
                    .level(normalizeLevel(m.group("level")))
                    .message(m.group("message"))
                    .rawLine(line)
                    .build());
        }
    },

    /**
     * WebAPI / WebUI / NHibernate SQL — Log4Net level-first layout.
     * <pre>ERROR 2025-12-04 14:08:19,681 [thread] [ctx] Class [Method] - Message</pre>
     * Level comes first, two-token timestamp with {@code ,} millisecond separator.
     */
    LOG4NET(
            "WebAPI / WebUI / NHibernate (Log4Net level-first)",
            Pattern.compile(
                    "^(?<level>" + Const.LEVELS + ")\\s+(?<date>\\d{4}-\\d{2}-\\d{2})"
                    + "\\s+(?<time>\\d{2}:\\d{2}:\\d{2},\\d+)\\s+(?<message>.*)$",
                    Pattern.CASE_INSENSITIVE)) {
        @Override
        public Optional<LogEntry> tryParse(String line, String tzOffset) {
            Matcher m = pattern().matcher(line);
            if (!m.matches()) return Optional.empty();
            String tsRaw = m.group("date") + "T" + m.group("time").replace(",", ".") + tzOffset;
            return Optional.of(LogEntry.builder()
                    .timestamp(tryParseInstant(tsRaw))
                    .level(normalizeLevel(m.group("level")))
                    .message(m.group("message"))
                    .rawLine(line)
                    .build());
        }
    },

    /**
     * ISO single-token — future-proof / generic fallback.
     * <pre>2026-05-11T10:23:45.123Z ERROR Message</pre>
     * Handles both {@code Z} (UTC) and {@code +05:30} offset forms.
     * The server timezone offset is NOT appended here — it is already embedded.
     */
    ISO_SINGLE_TOKEN(
            "ISO-8601 single-token (UTC or offset)",
            Pattern.compile(
                    "^(?<timestamp>\\S+T\\S+)\\s+(?<level>" + Const.LEVELS + ")\\s+(?<message>.*)$",
                    Pattern.CASE_INSENSITIVE)) {
        @Override
        public Optional<LogEntry> tryParse(String line, String tzOffset) {
            Matcher m = pattern().matcher(line);
            if (!m.matches()) return Optional.empty();
            return Optional.of(LogEntry.builder()
                    .timestamp(tryParseInstant(m.group("timestamp")))
                    .level(normalizeLevel(m.group("level")))
                    .message(m.group("message"))
                    .rawLine(line)
                    .build());
        }
    };

    // ── constructor ───────────────────────────────────────────────────────────

    private final String  description;
    private final Pattern pattern;

    LogFormat(String description, Pattern pattern) {
        this.description = description;
        this.pattern     = pattern;
    }

    public String  description() { return description; }
    public Pattern pattern()     { return pattern; }

    // ── abstract parse ────────────────────────────────────────────────────────

    /**
     * Attempts to parse {@code line} using this format's regex.
     *
     * @param line     trimmed log line (never null, never blank)
     * @param tzOffset server timezone offset string (e.g. {@code "+05:30"} or {@code "Z"})
     *                 appended to two-token timestamps that have no embedded offset
     * @return non-empty Optional on match, empty Optional on mismatch
     */
    public abstract Optional<LogEntry> tryParse(String line, String tzOffset);

    // ── shared utilities (called from constant bodies) ────────────────────────

    /**
     * Tries ISO-8601 UTC first, then ISO offset form, returns {@code null} on failure.
     */
    static Instant tryParseInstant(String raw) {
        try { return Instant.parse(raw); } catch (DateTimeParseException ignored) {}
        try { return OffsetDateTime.parse(raw).toInstant(); } catch (DateTimeParseException ignored) {}
        return null;
    }

    /** Normalises level aliases to a canonical uppercase form. */
    static String normalizeLevel(String raw) {
        if (raw == null) return null;
        String up = raw.toUpperCase();
        if ("INFORMATION".equals(up)) return "INFO";
        if ("WARNING".equals(up))     return "WARN";
        if ("CRITICAL".equals(up))    return "FATAL";
        return up;
    }

    // ── compile-time constants ────────────────────────────────────────────────

    /**
     * Lightweight pattern that detects the opening line of a new log entry.
     * Handles two leading layouts:
     * <ul>
     *   <li><b>Timestamp-first</b> (EFLogger / ISO): {@code 2026-05-15 11:42:08...}</li>
     *   <li><b>Level-first</b> (Log4Net): {@code DEBUG 2026-05-15 11:42:08,...}</li>
     * </ul>
     * Used by fetchers to group multi-line entries (stack traces, SQL bodies) into a
     * single block before parsing.
     */
    public static final Pattern ENTRY_START = Pattern.compile(
            "^(?:\\d{4}-\\d{2}-\\d{2}"
            + "|(?:TRACE|DEBUG|INFO|INFORMATION|WARN|WARNING|ERROR|FATAL|CRITICAL)\\s+\\d{4}-\\d{2}-\\d{2})"
            + "\\s+\\d{2}:\\d{2}:\\d{2}",
            Pattern.CASE_INSENSITIVE);

    /**
     * Holds string constants that must be available at enum-constant construction time.
     * Java initialises enum constants before any static fields on the enum class itself,
     * so a plain {@code static final String} on {@code LogFormat} would be null when the
     * Pattern arguments are compiled. An interface-nested constant has no such restriction.
     */
    interface Const {
        String LEVELS = "TRACE|DEBUG|INFO|INFORMATION|WARN|WARNING|ERROR|FATAL|CRITICAL";
    }
}
