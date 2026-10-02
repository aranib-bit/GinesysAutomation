package com.gsl.remote.filter;

import com.gsl.remote.model.LogEntry;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Accepts log entries that fall inside the configured time window AND contain
 * at least one of the supplied keywords (case-insensitive substring match).
 *
 * <p>The {@link com.gsl.remote.fetcher.LogFetcher} implementations construct this per-request
 * from {@link com.gsl.remote.model.LogFetchRequest}'s time window and keyword list.
 *
 * <p>Rules:
 * <ul>
 *   <li>If startUtc/endUtc are null the time-window check is skipped.</li>
 *   <li>If keywords list is empty the keyword check is skipped.</li>
 *   <li>Entries with a null timestamp pass the time-window check (can't be excluded by time).</li>
 * </ul>
 */
public final class KeywordTimeWindowFilter implements LogFilter {

    private final Instant      startUtc;
    private final Instant      endUtc;
    private final List<String> keywords;

    /**
     * @param startUtc inclusive lower bound; null disables the time-window check together with a null {@code endUtc}
     * @param endUtc   inclusive upper bound; null disables the time-window check together with a null {@code startUtc}
     * @param keywords case-insensitive substrings to match; null or empty disables the keyword check
     */
    public KeywordTimeWindowFilter(Instant startUtc, Instant endUtc, List<String> keywords) {
        this.startUtc = startUtc;
        this.endUtc   = endUtc;
        this.keywords = keywords == null ? List.of() : keywords;
    }

    /** {@inheritDoc} Accepts only entries satisfying both the time-window and keyword rules. */
    @Override
    public boolean matches(LogEntry entry) {
        return inTimeWindow(entry) && containsKeyword(entry);
    }

    /** True when {@code entry}'s timestamp (if any) falls within [{@code startUtc}, {@code endUtc}]. */
    private boolean inTimeWindow(LogEntry entry) {
        if (startUtc == null && endUtc == null) return true;
        if (entry.getTimestamp() == null) return true;

        Instant ts = entry.getTimestamp();
        if (startUtc != null && ts.isBefore(startUtc)) return false;
        if (endUtc   != null && ts.isAfter(endUtc))    return false;
        return true;
    }

    /** True when {@code entry}'s raw line (or message, if no raw line) contains any configured keyword. */
    private boolean containsKeyword(LogEntry entry) {
        if (keywords.isEmpty()) return true;

        String haystack = (entry.getRawLine() != null
                ? entry.getRawLine()
                : entry.getMessage()).toLowerCase(Locale.ROOT);

        return keywords.stream()
                .anyMatch(kw -> haystack.contains(kw.toLowerCase(Locale.ROOT)));
    }
}
