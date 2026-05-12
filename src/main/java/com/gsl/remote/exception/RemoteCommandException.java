package com.gsl.remote.exception;

/** Thrown when a remote shell command exits non-zero, times out, or fails after all retries. */
public class RemoteCommandException extends RuntimeException {

    private final int    exitCode;
    private final String stderr;

    public RemoteCommandException(String message, int exitCode, String stderr) {
        super(message + " [exitCode=" + exitCode + ", stderr=" + stderr + "]");
        this.exitCode = exitCode;
        this.stderr   = stderr;
    }

    public RemoteCommandException(String message, Throwable cause) {
        super(message, cause);
        this.exitCode = -1;
        this.stderr   = "";
    }

    public int    getExitCode() { return exitCode; }
    public String getStderr()   { return stderr; }
}
