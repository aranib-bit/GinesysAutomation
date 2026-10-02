package com.gsl.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Granularity at which an item's pricing is maintained. Populated from the "Pricing Management"
 * column of the "Inventory Tracking" sheet in the inventory-item Excel test data (see
 * {@code ItemInventoryTracking.pricingManagement}) via
 * {@link com.gsl.converter.PricingManagementConverter} / {@link #fromExcel}.
 */
public enum PricingManagementType {
    /** A single price applies to the whole item, regardless of batch/serial. */
    ITEM_LEVEL("Item level"),
    /** Price can vary per batch/serial. */
    BATCH_SERIAL_WISE("Batch/Serial level");

    private final String label;

    PricingManagementType(String label) {
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
     * Resolves {@code excelValue} to its matching {@link PricingManagementType} constant by
     * case-insensitive match against each constant's {@link #label}; throws
     * {@link IllegalArgumentException} naming {@code fileName}/{@code sheetName}/
     * {@code columnName} for a blank or unrecognized value.
     */
    public static PricingManagementType fromExcel(
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

        for (PricingManagementType type : values()) {
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
                .map(PricingManagementType::label)
                .collect(Collectors.joining(", "));

        return String.format(
                "%n❌ Invalid Pricing Management value from Excel%n" +
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
                PricingManagementType.class.getName()
        );
    }
}
