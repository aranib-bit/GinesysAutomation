package com.gsl.utils;

import java.math.BigDecimal;

/**
 * Generic parsing helper for turning a UI-rendered numeric string (grid cell, summary field,
 * exported report value) into a {@link BigDecimal} without throwing on the formatting noise
 * real UI text carries — thousands separators, currency symbols, non-breaking spaces, and
 * accounting-style parenthesized negatives. Never throws: any unparseable input resolves to
 * {@link BigDecimal#ZERO} rather than propagating a {@link NumberFormatException}, so callers can
 * use this directly in assertions/reconciliation math without their own try/catch.
 */
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
