package com.gsl.remote.formatter;

import com.gsl.remote.model.LogEntry;

import java.util.List;

/** Converts a list of log entries into a presentable string (HTML, plain text, JSON, etc.). */
public interface LogFormatter {

    String format(List<LogEntry> entries);
}
