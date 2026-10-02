package com.gsl.remote.exception;

/**
 * Thrown when a remote shell command exits non-zero, times out, or fails after all retries.
 *
 * <p>Raised by {@link com.gsl.remote.fetcher.LogFetcher} implementations and deliberately treated
 * as non-retryable by {@link com.gsl.remote.retry.RetryPolicy} — it represents a command that ran
 * and failed, not a transient transport error worth retrying.
 */
public class RemoteCommandException extends RuntimeException {

    private final int    exitCode;
    private final String stderr;

    /**
     * Builds an exception for a remote command that ran but exited non-zero or timed out.
     *
     * @param exitCode the remote command's exit status, or -1 when it timed out
     * @param stderr   captured stderr output; empty string when none was produced
     */
    public RemoteCommandException(String message, int exitCode, String stderr) {
        super(message + " [exitCode=" + exitCode + ", stderr=" + stderr + "]");
        this.exitCode = exitCode;
        this.stderr   = stderr;
    }

    /** Wraps a lower-level failure (e.g. connection error) where no exit code/stderr is available. */
    public RemoteCommandException(String message, Throwable cause) {
        super(message, cause);
        this.exitCode = -1;
        this.stderr   = "";
    }

    /** Remote command's exit status, or -1 when it timed out or never ran. */
    public int    getExitCode() { return exitCode; }
    /** Captured stderr output; empty string when none was produced. */
    public String getStderr()   { return stderr; }
}
