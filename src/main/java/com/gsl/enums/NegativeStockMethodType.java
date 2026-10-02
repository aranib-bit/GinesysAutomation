package com.gsl.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * How the application should behave when a transaction would drive an item's stock negative.
 * Populated from the "Negative Stock Method" column of the "Others" sheet in the inventory-item
 * Excel test data (see {@code ItemOthers.negativeStockMethod}) via
 * {@link com.gsl.converter.NegativeStockMethodConverter} / {@link #fromExcel}.
 */
public enum NegativeStockMethodType {
    /** Defer to the site/user profile's own negative-stock setting rather than an item-level override. */
    PROFILE("Profile"),
    /** Allow the transaction; negative stock is not flagged. */
    IGNORE("Ignore"),
    /** Allow the transaction but surface a warning. */
    WARNING("Warning"),
    /** Block the transaction outright. */
    STOP("Stop");

    private final String label;

    NegativeStockMethodType(String label) {
        this.label = label;
    }

    /** Excel parse key / display label — same string for this enum (see {@link #toString}). */
    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }


    /**
     * Resolves {@code excelValue} to its matching {@link NegativeStockMethodType} constant by
     * case-insensitive match against each constant's {@link #label}; throws
     * {@link IllegalArgumentException} naming {@code fileName}/{@code sheetName}/
     * {@code columnName} for a blank or unrecognized value.
     */
    public static NegativeStockMethodType fromExcel(
            String excelValue,
            String fileName,
            String sheetName,
            String columnName
    ) {
        if (excelValue == null || excelValue.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    buildErrorMessage("BLANK VALUE", fileName, sheetName, columnName)
            );
        }

        String cleaned = excelValue.trim(); // ONLY trim start/end spaces

        for (NegativeStockMethodType type : values()) {
            if (type.label.equalsIgnoreCase(cleaned)) {
                return type;
            }
        }

        throw new IllegalArgumentException(
                buildErrorMessage(excelValue, fileName, sheetName, columnName)
        );
    }


    private static String buildErrorMessage(
            String excelValue,
            String fileName,
            String sheetName,
            String columnName
    ) {
        String allowedValues = Arrays.stream(values())
                .map(NegativeStockMethodType::label)
                .collect(Collectors.joining(", "));

        return String.format(
                "%n❌ Invalid Negative Stock Method Type value from Excel%n" +
                        "--------------------------------------------%n" +
                        "📄 File      : %s%n" +
                        "📑 Sheet     : %s%n" +
                        "📌 Column    : %s%n" +
                        "🔴 Found     : '%s'%n" +
                        "✅ Allowed   : [%s]%n" +
                        "🧩 Enum      : %s%n",
                fileName,
                sheetName,
                columnName,
                excelValue,
                allowedValues,
                NegativeStockMethodType.class.getName()
        );
    }
}
