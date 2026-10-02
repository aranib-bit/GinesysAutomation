package com.gsl.browser;

import com.gsl.config.ConfigFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Matches browser log messages against built-in and configurable pattern lists.
 * Evaluation order: IGNORE → CRITICAL → WARNING.
 */
public final class PatternMatcherEngine {

    private static final List<String> FAIL_PATTERNS = List.of(
            "Uncaught SyntaxError",
            "Uncaught TypeError",
            "Uncaught ReferenceError",
            "net::ERR_",
            "Script error",
            "window.onerror",
            "Failed to fetch",
            "500 (Internal Server",
            "React Error",
            "Angular Error"
    );

    private static final List<String> WARN_PATTERNS_DEFAULT = List.of(
            "Failed to load resource",
            "favicon.ico",
            "404",
            "403",
            "deprecated",
            "Deprecation",
            "SameSite",
            "analytics"
    );

    private static final List<String> IGNORE_PATTERNS_DEFAULT = List.of(
            "google-analytics",
            "gtag"
    );

    private PatternMatcherEngine() {}

    /** @return true if {@code message} contains any of the built-in {@link #FAIL_PATTERNS}. */
    public static boolean isCritical(String message) {
        return FAIL_PATTERNS.stream().anyMatch(message::contains);
    }

    /** @return true if {@code message} matches a built-in warning pattern or a configured {@code browser.logs.warning.patterns} entry. */
    public static boolean isWarning(String message) {
        List<String> configPatterns = splitConfig(ConfigFactory.getNetworkConfig().browserLogsWarningPatterns());
        return Stream.concat(WARN_PATTERNS_DEFAULT.stream(), configPatterns.stream())
                .anyMatch(message::contains);
    }

    /** @return true if {@code message} matches a built-in ignore pattern or a configured {@code browser.logs.ignore.patterns} entry. */
    public static boolean isIgnored(String message) {
        List<String> configPatterns = splitConfig(ConfigFactory.getNetworkConfig().browserLogsIgnorePatterns());
        return Stream.concat(IGNORE_PATTERNS_DEFAULT.stream(), configPatterns.stream())
                .anyMatch(message::contains);
    }

    /** Splits a comma-separated config value into trimmed, non-empty pattern strings; {@code null}/blank yields an empty list. */
    private static List<String> splitConfig(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
