package com.gsl.browser;

public record JsErrorClassification(
        JsErrorCategory category,
        String          probableCause,
        String          confidence    // "HIGH" | "MEDIUM" | "LOW"
) {
    public static JsErrorClassification unknown() {
        return new JsErrorClassification(JsErrorCategory.UNKNOWN, "Could not determine root cause", "LOW");
    }
}
