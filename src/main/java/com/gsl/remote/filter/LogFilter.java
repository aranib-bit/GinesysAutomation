package com.gsl.remote.filter;

import com.gsl.remote.model.LogEntry;

/** Decides whether a parsed log entry should be included in the final result set. */
@FunctionalInterface
public interface LogFilter {

    /** Returns true when the entry satisfies the filter criteria. */
    boolean matches(LogEntry entry);
}
