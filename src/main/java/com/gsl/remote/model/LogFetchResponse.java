package com.gsl.remote.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/** Result of a remote log-collection attempt. */
@Value
@Builder
public class LogFetchResponse {

    /** Parsed and filtered log entries. May be empty but never null. */
    List<LogEntry> entries;

    /** Stderr captured from the remote command; empty string when none. */
    String         stderr;

    /** Exit code of the remote command; -1 when the command timed out. */
    int            exitCode;

    /** Number of retry attempts consumed (0 = succeeded on first try). */
    int            retriesUsed;

    /** Wall-clock duration of the entire fetch operation in milliseconds. */
    long           durationMs;

    /** Returns true when entries is non-empty. */
    public boolean hasEntries() {
        return entries != null && !entries.isEmpty();
    }
}
