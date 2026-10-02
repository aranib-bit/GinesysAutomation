package com.gsl.browser;

/** Result of {@link JsErrorClassificationEngine}'s pattern-matching over a CRITICAL {@link BrowserLogRecord}. */
public record JsErrorClassification(
        JsErrorCategory category,
        String          probableCause,
        String          confidence    // "HIGH" | "MEDIUM" | "LOW"
) {
    /** @return the fallback classification for a record that matched none of the engine's rules. */
    public static JsErrorClassification unknown() {
        return new JsErrorClassification(JsErrorCategory.UNKNOWN, "Could not determine root cause", "LOW");
    }
}
