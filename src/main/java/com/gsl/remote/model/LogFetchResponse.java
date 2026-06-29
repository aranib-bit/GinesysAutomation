package com.gsl.remote.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Result of a remote log-collection attempt. */
@Value
@Builder(toBuilder = true)
public class LogFetchResponse {

    /** Reason a collection attempt produced no entries. Null on a normal fetch. */
    public enum SkipReason {
        CREDS_MISSING_WINRM,
        CREDS_MISSING_SSH,
        FETCH_FAILED
    }

    /**
     * Non-null when collection was skipped or failed before producing entries. The
     * orchestrator uses this to render a precise reason in the failure-bundle report
     * instead of guessing from a null response.
     */
    SkipReason skipReason;

    /** Parsed and filtered log entries. May be empty but never null. */
    List<LogEntry> entries;

    /**
     * Absolute paths of every file that was read on the remote server, in the order
     * they were processed. Populated only when the fetcher emits {@code ###FILE:###}
     * markers (WinRM + SSH file-based fetches). Empty for EventLog and legacy fetches.
     */
    @Singular("fileRead")
    List<String> filesRead;

    /**
     * Per-file errors keyed by absolute remote path. Populated when a specific file
     * could not be read (access denied, locked, etc.) but collection continued for
     * remaining files. Empty map when no file-level errors occurred.
     */
    @Builder.Default
    Map<String, String> fileErrors = new LinkedHashMap<>();

    /** Stderr captured from the remote command; empty string when none. */
    String         stderr;

    /** Exit code of the remote command; -1 when the command timed out. */
    int            exitCode;

    /** Number of retry attempts consumed (0 = succeeded on first try). */
    int            retriesUsed;

    /** Wall-clock duration of the entire fetch operation in milliseconds. */
    long           durationMs;

    /** Log path patterns that were configured for this fetch (from remote-log.properties). */
    List<String>   configuredPaths;

    /** Start of the test time window used to filter entries (UTC). */
    Instant        timeWindowStart;

    /** End of the test time window used to filter entries (UTC). */
    Instant        timeWindowEnd;

    /** Returns true when entries is non-empty. */
    public boolean hasEntries() {
        return entries != null && !entries.isEmpty();
    }

    /**
     * Returns log entries grouped by their source file path, preserving the order
     * files were read. Entries with no sourceFile are grouped under "Unknown".
     */
    public Map<String, List<LogEntry>> getEntriesByFile() {
        if (entries == null || entries.isEmpty()) return new LinkedHashMap<>();
        return entries.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getSourceFile() != null ? e.getSourceFile() : "Unknown",
                        LinkedHashMap::new,
                        Collectors.toList()));
    }
}
