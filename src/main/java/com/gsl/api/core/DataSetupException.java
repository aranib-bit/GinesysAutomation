package com.gsl.api.core;

/**
 * Thrown when hybrid API+DB test data setup fails — e.g. an API-created record isn't found
 * in the DB on validation, or a bulk k6 backfill process exits non-zero. Distinct from
 * {@link ApiException} (a single call's response was bad) and from a data-availability
 * {@code SkipException} (genuinely no data exists, not an error).
 */
public class DataSetupException extends RuntimeException {

    /** Signals a data-setup failure with no underlying cause (e.g. a post-creation DB lookup came back empty). */
    public DataSetupException(String message) {
        super(message);
    }

    /** Signals a data-setup failure wrapping an underlying cause (e.g. the k6 backfill subprocess's own exception). */
    public DataSetupException(String message, Throwable cause) {
        super(message, cause);
    }
}
