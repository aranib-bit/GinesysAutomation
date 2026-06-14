package com.gsl.remote.filter;

import com.gsl.config.ConfigFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides whether a log filename should be included based on any date embedded in its name.
 *
 * Rule: if the filename contains a recognisable date token (yyyyMMdd or yyyy-MM-dd) AND
 * that token does not fall in the valid date set, the file is skipped.
 * Files whose names contain no recognisable date are always included (rolling logs).
 */
public final class LogFileNameDateFilter {

    private static final Pattern DATE_IN_NAME = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}|\\d{8}|\\d{2}-\\d{2}-\\d{4}");
    private static final DateTimeFormatter COMPACT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DASHED  = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DMY     = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private LogFileNameDateFilter() {}

    /**
     * Returns true when the file should be read.
     * Always returns true when {@code validTokens} is empty (no time window configured).
     */
    public static boolean include(String filename, Set<String> validTokens) {
        if (filename == null || validTokens == null || validTokens.isEmpty()) return true;
        Matcher m = DATE_IN_NAME.matcher(filename);
        if (!m.find()) return true;
        do {
            if (validTokens.contains(m.group())) return true;
        } while (m.find());
        return false;
    }

    /**
     * Computes date tokens (both yyyyMMdd and yyyy-MM-dd) for every calendar date in
     * [{@code start}, {@code end}] using the server's configured timezone offset.
     * Returns an empty set when either instant is null.
     */
    public static Set<String> validDateTokens(Instant start, Instant end) {
        if (start == null || end == null) return Set.of();
        ZoneOffset zone = serverZoneOffset();
        LocalDate from = start.atOffset(zone).toLocalDate();
        LocalDate to   = end.atOffset(zone).toLocalDate();
        Set<String> tokens = new LinkedHashSet<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            tokens.add(COMPACT.format(d));
            tokens.add(DASHED.format(d));
            tokens.add(DMY.format(d));
        }
        return tokens;
    }

    /**
     * Builds a PowerShell Where-Object pipeline fragment that filters files by name-embedded date.
     * Returns an empty string when no time window is set (no filtering performed).
     *
     * The returned string begins with a pipe character and is safe to insert directly into
     * a Get-ChildItem pipeline before Sort-Object.
     */
    public static String buildPsDateFilterClause(Instant start, Instant end) {
        Set<String> tokens = validDateTokens(start, end);
        if (tokens.isEmpty()) return "";
        // Tokens are plain date strings — no regex metacharacters, safe to join with |
        String tokenPattern = String.join("|", tokens);
        return " | Where-Object { $_.Name -notmatch '\\d{8}|\\d{4}-\\d{2}-\\d{2}|\\d{2}-\\d{2}-\\d{4}'"
                + " -or $_.Name -match '" + tokenPattern + "' }";
    }

    /**
     * Builds a bash snippet that skips files whose name-embedded date is outside the valid set.
     * Returns an empty string when no time window is set.
     *
     * The snippet references the shell variable {@code _fname} (basename of the current file)
     * and uses {@code continue} to skip the file in the enclosing for-loop.
     */
    public static String buildBashDateGuard(Instant start, Instant end) {
        Set<String> tokens = validDateTokens(start, end);
        if (tokens.isEmpty()) return "";
        // Build an alternation pattern for grep -E (tokens are plain date strings, no metacharacters)
        String tokenPattern = String.join("|", tokens);
        return "_fname=$(basename \"$ff\"); "
                + "if echo \"$_fname\" | grep -qE '[0-9]{8}|[0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{2}-[0-9]{2}-[0-9]{4}'; then "
                + "if ! echo \"$_fname\" | grep -qE '" + tokenPattern + "'; then continue; fi; fi; ";
    }

    /**
     * Formats an instant as {@code yyyy-MM-dd HH:mm:ss} in the server's configured timezone.
     * Used to embed time-window boundaries directly in remote scripts (bash / PowerShell).
     * Returns {@code null} when {@code instant} is null.
     */
    public static String serverTimeString(Instant instant) {
        if (instant == null) return null;
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(serverZoneOffset())
                .format(instant);
    }

    private static ZoneOffset serverZoneOffset() {
        try {
            String raw = ConfigFactory.getRemoteLogConfig().remoteLogsServerTimezoneOffset();
            return (raw != null && !raw.isBlank()) ? ZoneOffset.of(raw) : ZoneOffset.UTC;
        } catch (Exception e) {
            return ZoneOffset.UTC;
        }
    }
}
