package com.gsl.enums;

/**
 * Policy applied after atomic reservation when choosing which pending documents are
 * eligible for a test scenario. SQL already enforces {@code debit_credit='D' AND pending>0};
 * this mode is a per-SL trimming policy on the already-validated pool.
 */
public enum DocumentSelectionMode {

    /** Only SLs with at least one un-reserved positive-pending doc (current behavior, hardened). */
    POSITIVE,

    /** Allow any non-zero pending doc. Useful when intentionally testing credit notes. */
    MIXED,

    /** Prefer POSITIVE; fall back to MIXED per-SL only if positive pool is exhausted. SL is never dropped. */
    HYBRID;

    public static DocumentSelectionMode fromString(String value) {
        if (value == null || value.isBlank()) return POSITIVE;
        try {
            return valueOf(value.toUpperCase().strip());
        } catch (IllegalArgumentException e) {
            return POSITIVE;
        }
    }
}
