package com.gsl.remote.model;

/**
 * When remote log collection runs relative to a test, as configured by
 * {@code remote.logs.capture.mode} in {@link com.gsl.config.RemoteLogConfig}.
 */
public enum CaptureMode {
    /** Collect remote logs only when a test fails. */
    ON_FAILURE,
    /** Collect remote logs after every test regardless of outcome. */
    ALWAYS,
    /** Disable remote log collection entirely. */
    NEVER
}
