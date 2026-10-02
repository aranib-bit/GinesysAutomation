package com.gsl.remote.formatter;

import com.gsl.remote.model.LogEntry;

import java.util.List;

/** Converts a list of log entries into a presentable string (HTML, plain text, JSON, etc.). */
public interface LogFormatter {

    /** Renders {@code entries} as a single string; implementations must handle a null or empty list. */
    String format(List<LogEntry> entries);
}
