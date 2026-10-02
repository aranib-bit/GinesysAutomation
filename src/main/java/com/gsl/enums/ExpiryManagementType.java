package com.gsl.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Whether an item tracks batch/serial expiry dates. Populated from the "Expiry Management" column
 * of the "Inventory Tracking" sheet in the inventory-item Excel test data (see
 * {@code ItemInventoryTracking.expiryManagement}) via
 * {@link com.gsl.converter.ExpiryManagementConverter} / {@link #fromExcel}.
 */
public enum ExpiryManagementType {
    YES("Yes"),
    NO("No");
    private final String label;

    ExpiryManagementType(String label) {
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
     * Resolves {@code excelValue} to its matching {@link ExpiryManagementType} constant by
     * case-insensitive match against each constant's {@link #label}; throws
     * {@link IllegalArgumentException} naming {@code fileName}/{@code sheetName}/
     * {@code columnName} for a blank or unrecognized value.
     */
    public static ExpiryManagementType fromExcel(
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

        for (ExpiryManagementType type : values()) {
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
                .map(ExpiryManagementType::label)
                .collect(Collectors.joining(", "));

        return String.format(
                "%n❌ Invalid Expiry Management value from Excel%n" +
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
                ExpiryManagementType.class.getName()
        );
    }
}
