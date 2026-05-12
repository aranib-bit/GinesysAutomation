package com.gsl.remote.model;

public enum CaptureMode {
    /** Collect remote logs only when a test fails. */
    ON_FAILURE,
    /** Collect remote logs after every test regardless of outcome. */
    ALWAYS,
    /** Disable remote log collection entirely. */
    NEVER
}
