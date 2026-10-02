package com.gsl.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Whether a POS operator is allowed to override an item's selling price at the till. Populated
 * from the "Allow Price Change" column of the "POS Behaviour" sheet in the inventory-item Excel
 * test data (see {@code ItemPOSBehaviour.allowPriceChange}) via
 * {@link com.gsl.converter.AllowPriceChangeConverter} / {@link #fromExcel}.
 */
public enum AllowPriceChangeType {
    YES("Yes"),
    NO("No");
    private final String label;

    AllowPriceChangeType(String label) {
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
     * Resolves {@code excelValue} to its matching {@link AllowPriceChangeType} constant by
     * case-insensitive match against each constant's {@link #label}; throws
     * {@link IllegalArgumentException} naming {@code fileName}/{@code sheetName}/
     * {@code columnName} for a blank or unrecognized value.
     */
    public static AllowPriceChangeType fromExcel(
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

        for (AllowPriceChangeType type : values()) {
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
                .map(AllowPriceChangeType::label)
                .collect(Collectors.joining(", "));

        return String.format(
                "%n❌ Invalid Allow Price Change value from Excel%n" +
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
                AllowPriceChangeType.class.getName()
        );
    }
}
