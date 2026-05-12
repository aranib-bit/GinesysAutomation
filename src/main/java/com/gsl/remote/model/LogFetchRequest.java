package com.gsl.remote.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

/** Describes a single remote log-collection request. Build via {@link LogFetchRequest#builder()}. */
@Value
@Builder
public class LogFetchRequest {

    /** Target server hostname or IP address. */
    String       host;

    /** SSH port (usually 22) or WinRM port (usually 5985/5986). */
    int          port;

    /** Operating system of the target server. */
    OSType       osType;

    /** Authentication credentials (sourced from environment variables). */
    Credentials  credentials;

    /** Collect log lines written at or after this instant (UTC). */
    Instant      startUtc;

    /** Collect log lines written at or before this instant (UTC). */
    Instant      endUtc;

    /**
     * Absolute paths of log files to search on the remote server.
     * Accepts glob patterns only when validated safe (no shell metacharacters).
     */
    List<String> logPaths;

    /**
     * Keywords to grep for (case-insensitive substring match).
     * Empty list = time-window filter only, no keyword restriction.
     */
    List<String> searchKeywords;

    /** Seconds to wait when establishing the remote connection. */
    int          connectTimeoutSeconds;

    /** Seconds to wait for remote data to arrive. */
    int          readTimeoutSeconds;

    /** Seconds after which a running remote command is forcibly terminated. */
    int          commandTimeoutSeconds;

    /** Max retry attempts on transient connection failures (exponential backoff). */
    int          retryCount;

    /** Maximum number of log lines to return in the response. */
    long         maxResultLines;

    /** When true, WinRM connects over HTTPS (port 5986). Ignored by SshLogFetcher. */
    boolean      winrmUseHttps;
}
