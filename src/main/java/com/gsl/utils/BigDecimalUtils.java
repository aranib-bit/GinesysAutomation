package com.gsl.utils;

import java.math.BigDecimal;

public class BigDecimalUtils {

    private BigDecimalUtils() {}

    /**
     * Safely parses a BigDecimal from a UI-sourced string.
     * Handles commas, non-breaking spaces, currency symbols, and
     * accounting-style negatives in parentheses e.g. (1,23,456.78) → -123456.78.
     */
    public static BigDecimal safeParse(String str) {
        if (str == null || str.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            boolean isNegative = str.contains("(") && str.contains(")");
            String normalized = str.replaceAll("[^\\d.eE+\\-]", "").trim();
            if (normalized.isEmpty()) {
                return BigDecimal.ZERO;
            }
            BigDecimal result = new BigDecimal(normalized);
            return isNegative ? result.negate() : result;
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
