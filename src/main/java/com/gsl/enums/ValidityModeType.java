package com.gsl.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The unit an item's shelf-life/validity period is expressed in, as configured in the "Validity
 * Mode" column of the "Inventory Tracking" sheet in the inventory-item Excel test data ({@code
 * ItemInventoryTracking.validityMode}, paired with the free-text {@code validityPeriod} value) —
 * resolved from the raw cell text by {@link #fromExcel} via {@code ValidityModeConverter}.
 */
public enum ValidityModeType {
    /** No expiry/validity tracking applies. */
    NONE("None"),
    DAY("Day"),
    MONTH("Month"),
    YEAR("Year");

    private final String label;

    ValidityModeType(String label) {
        this.label = label;
    }

    /** Excel/UI label for this validity mode. */
    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }


    /** Resolves a raw Excel cell value to its matching constant (case-insensitive label match);
     *  throws {@link IllegalArgumentException} naming the offending file/sheet/column and the
     *  allowed values for a blank or unrecognized value. */
    public static ValidityModeType fromExcel(
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

        for (ValidityModeType type : values()) {
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
                .map(ValidityModeType::label)
                .collect(Collectors.joining(", "));

        return String.format(
                "%n❌ Invalid Validity Mode value from Excel%n" +
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
                ValidityModeType.class.getName()
        );
    }
}
