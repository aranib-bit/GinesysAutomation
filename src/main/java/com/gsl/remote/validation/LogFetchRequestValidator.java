package com.gsl.remote.validation;

import com.gsl.remote.model.LogFetchRequest;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Centralised validation for {@link LogFetchRequest} before any network call is made.
 * Extracted from the inline {@code validate()} that was previously duplicated per fetcher.
 */
public final class LogFetchRequestValidator {

    // Backslash is intentionally excluded: it is the standard Windows path separator and
    // is safe inside PowerShell single-quoted strings (PS has no backslash escape in '...').
    private static final Pattern SHELL_UNSAFE  = Pattern.compile("[;&|><$`]");
    private static final Pattern HOSTNAME_SAFE = Pattern.compile(
            "^[a-zA-Z0-9][a-zA-Z0-9.\\-]{0,253}$");

    private LogFetchRequestValidator() {}

    /**
     * Validates the request and throws {@link IllegalArgumentException} with a descriptive
     * message on the first violation found.
     */
    public static void validate(LogFetchRequest request) {
        requireNonBlank(request.getHost(), "host");
        requireHostnameSafe(request.getHost());
        requireInRange(request.getPort(), 1, 65535, "port");
        requireNonNull(request.getCredentials(), "credentials");
        requireNonNull(request.getStartUtc(), "startUtc");
        requireNonNull(request.getEndUtc(), "endUtc");
        requireBefore(request.getStartUtc(), request.getEndUtc());
        requireNonEmpty(request.getLogPaths(), "logPaths");
        requireNoShellChars(request.getLogPaths(), "logPaths");
        if (request.getSearchKeywords() != null) {
            requireNoShellChars(request.getSearchKeywords(), "searchKeywords");
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Throws when {@code value} is null or blank. */
    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("LogFetchRequest." + field + " must not be blank");
    }

    /** Throws when {@code host} doesn't match {@link #HOSTNAME_SAFE} (blocks shell-unsafe hostnames). */
    private static void requireHostnameSafe(String host) {
        if (!HOSTNAME_SAFE.matcher(host).matches())
            throw new IllegalArgumentException(
                    "LogFetchRequest.host contains invalid characters: " + host);
    }

    /** Throws when {@code value} falls outside [{@code min}, {@code max}] inclusive. */
    private static void requireInRange(int value, int min, int max, String field) {
        if (value < min || value > max)
            throw new IllegalArgumentException(
                    "LogFetchRequest." + field + " must be between " + min + " and " + max
                            + ", got: " + value);
    }

    /** Throws when {@code value} is null. */
    private static void requireNonNull(Object value, String field) {
        if (value == null)
            throw new IllegalArgumentException("LogFetchRequest." + field + " must not be null");
    }

    /** Throws when {@code start} is not strictly before {@code end}. */
    private static void requireBefore(Instant start, Instant end) {
        if (!start.isBefore(end))
            throw new IllegalArgumentException(
                    "LogFetchRequest.startUtc must be before endUtc");
    }

    /** Throws when {@code list} is null or empty. */
    private static void requireNonEmpty(List<?> list, String field) {
        if (list == null || list.isEmpty())
            throw new IllegalArgumentException(
                    "LogFetchRequest." + field + " must not be empty");
    }

    /** Throws when any entry in {@code values} matches {@link #SHELL_UNSAFE}. */
    private static void requireNoShellChars(List<String> values, String field) {
        for (String v : values) {
            if (SHELL_UNSAFE.matcher(v).find())
                throw new IllegalArgumentException(
                        "LogFetchRequest." + field + " contains shell-unsafe characters: " + v);
        }
    }
}
