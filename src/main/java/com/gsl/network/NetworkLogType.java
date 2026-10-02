package com.gsl.network;

/** Classifies a {@link NetworkLogRecord} as a completed response or a failed/errored request. */
public enum NetworkLogType {
    /** A response was received (status may still be >= 400). */
    RESPONSE,
    /** The request never completed (e.g. network error) or is treated as a failure by capture-time filtering. */
    FAILED
}
