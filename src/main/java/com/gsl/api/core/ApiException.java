package com.gsl.api.core;

/** Thrown when a WebAPI/PublicAPI call returns a non-2xx status or an envelope with success=false. */
public class ApiException extends RuntimeException {

    private final int statusCode;

    /** @param statusCode the HTTP status code (or the last-known one, for a non-HTTP failure) the call returned. */
    public ApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    /** Same as {@link #ApiException(String, int)}, chaining an underlying cause (e.g. a parse failure). */
    public ApiException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
