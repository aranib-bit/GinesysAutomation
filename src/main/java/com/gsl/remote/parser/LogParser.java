package com.gsl.remote.parser;

import com.gsl.remote.model.LogEntry;

import java.util.Optional;

/**
 * Parses a single raw log line into a structured {@link LogEntry}.
 * Implementations must never throw on unrecognised formats — return a raw-line
 * LogEntry instead so no data is silently dropped.
 */
public interface LogParser {

    /**
     * Attempts to parse {@code line} into a structured log entry.
     * Returns empty only when {@code line} is null or blank.
     */
    Optional<LogEntry> parse(String line);
}
