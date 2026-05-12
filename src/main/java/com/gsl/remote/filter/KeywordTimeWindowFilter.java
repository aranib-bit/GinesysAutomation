package com.gsl.remote.filter;

import com.gsl.remote.model.LogEntry;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Accepts log entries that fall inside the configured time window AND contain
 * at least one of the supplied keywords (case-insensitive substring match).
 *
 * Rules:
 * - If startUtc/endUtc are null the time-window check is skipped.
 * - If keywords list is empty the keyword check is skipped.
 * - Entries with a null timestamp pass the time-window check (can't be excluded by time).
 */
public final class KeywordTimeWindowFilter implements LogFilter {

    private final Instant      startUtc;
    private final Instant      endUtc;
    private final List<String> keywords;

    public KeywordTimeWindowFilter(Instant startUtc, Instant endUtc, List<String> keywords) {
        this.startUtc = startUtc;
        this.endUtc   = endUtc;
        this.keywords = keywords == null ? List.of() : keywords;
    }

    @Override
    public boolean matches(LogEntry entry) {
        return inTimeWindow(entry) && containsKeyword(entry);
    }

    private boolean inTimeWindow(LogEntry entry) {
        if (startUtc == null && endUtc == null) return true;
        if (entry.getTimestamp() == null) return true;

        Instant ts = entry.getTimestamp();
        if (startUtc != null && ts.isBefore(startUtc)) return false;
        if (endUtc   != null && ts.isAfter(endUtc))    return false;
        return true;
    }

    private boolean containsKeyword(LogEntry entry) {
        if (keywords.isEmpty()) return true;

        String haystack = (entry.getRawLine() != null
                ? entry.getRawLine()
                : entry.getMessage()).toLowerCase(Locale.ROOT);

        return keywords.stream()
                .anyMatch(kw -> haystack.contains(kw.toLowerCase(Locale.ROOT)));
    }
}
