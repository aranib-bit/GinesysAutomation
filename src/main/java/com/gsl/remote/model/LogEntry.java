package com.gsl.remote.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * A single parsed log line from a remote server.
 *
 * <p>Produced by {@link com.gsl.remote.parser.LogParser} implementations, screened by
 * {@link com.gsl.remote.filter.LogFilter}, and ultimately rendered by
 * {@link com.gsl.remote.formatter.LogFormatter} implementations for the test report.
 */
@Value
@Builder
public class LogEntry {
    /** Parsed timestamp; null when the line format is unrecognised. */
    Instant timestamp;
    /** Log level string e.g. ERROR, WARN, INFO; null when unparseable. */
    String  level;
    /** Message portion of the log line. */
    String  message;
    /** The original unmodified raw line from the remote log file. */
    String  rawLine;
    /** Absolute path of the source file on the remote server; null when not tracked. */
    String  sourceFile;
}
