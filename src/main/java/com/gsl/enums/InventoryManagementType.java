package com.gsl.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Granularity at which an item's stock is tracked. Populated from the "Inventory Management"
 * column of the "Inventory Tracking" sheet in the inventory-item Excel test data (see
 * {@code ItemInventoryTracking.inventoryManagement}) via
 * {@link com.gsl.converter.InventoryManagementConverter} / {@link #fromExcel}.
 */
public enum InventoryManagementType {
    /** Tracked at plain item level — no per-unit serial or batch identity. */
    ITEM_WISE("Item Wise"),
    /** Tracked per individual unit serial number. */
    SERIAL_WISE("Serial Wise"),
    /** Tracked per batch/lot. */
    BATCH_WISE("Batch Wise");
    private final String label;

    InventoryManagementType(String label) {
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
     * Resolves {@code excelValue} to its matching {@link InventoryManagementType} constant by
     * case-insensitive match against each constant's {@link #label}; throws
     * {@link IllegalArgumentException} naming {@code fileName}/{@code sheetName}/
     * {@code columnName} for a blank or unrecognized value.
     */
    public static InventoryManagementType fromExcel(
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

        for (InventoryManagementType type : values()) {
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
                .map(InventoryManagementType::label)
                .collect(Collectors.joining(", "));

        return String.format(
                "%n❌ Invalid Inventory Management value from Excel%n" +
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
                InventoryManagementType.class.getName()
        );
    }
}
