package com.gsl.api.core;

/**
 * Generates collision-safe item/batch/serial identifiers matching k6's isolation scheme
 * (timestamp + thread + iteration, base36-encoded, length-capped) so parallel API-driven
 * data creation never collides with itself or with concurrent k6 runs.
 */
public final class UniqueNameGenerator {

    private UniqueNameGenerator() {}

    /** e.g. "APITEST-kx3f8a1-i9-2" — capped to 40 chars (ItemMaster/Create's cname1 limit). */
    public static String itemName(String prefix, int iterationId) {
        String raw = prefix + "-" + Long.toString(System.currentTimeMillis(), 36)
                + Long.toString(Thread.currentThread().getId(), 36)
                + Integer.toString(iterationId, 36);
        return raw.length() > 40 ? raw.substring(0, 40) : raw;
    }

    /** e.g. "SERkx3f8a1-i9-2-0" — capped to 20 chars (batch/serial code limit). */
    public static String batchOrSerialCode(int lineIndex) {
        String raw = "SER" + Long.toString(System.currentTimeMillis(), 36)
                + Long.toString(Thread.currentThread().getId(), 36)
                + Integer.toString(lineIndex, 36);
        return raw.length() > 20 ? raw.substring(0, 20) : raw;
    }
}
