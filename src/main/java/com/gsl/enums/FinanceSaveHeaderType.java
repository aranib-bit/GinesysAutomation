package com.gsl.enums;

import java.util.Arrays;

/**
 * Acceptable header strings on the document-save confirmation popup for Finance vouchers.
 * The server-side header text has varied across releases (e.g., "Success" vs "Result") even
 * when the document was created successfully — record acceptable variants here so future
 * variants are an enum addition, not a code change.
 */
public enum FinanceSaveHeaderType {
    SUCCESS("Success"),
    RESULT("Result");

    private final String label;

    FinanceSaveHeaderType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static boolean isAcceptable(String header) {
        if (header == null) return false;
        String trimmed = header.trim();
        return Arrays.stream(values()).anyMatch(h -> h.label.equalsIgnoreCase(trimmed));
    }
}
