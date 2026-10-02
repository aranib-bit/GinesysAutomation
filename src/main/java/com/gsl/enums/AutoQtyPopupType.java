package com.gsl.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Whether the POS UI should pop up a quantity-entry dialog automatically for an item. Populated
 * from the "Auto Qty Popup" column of the "POS Behaviour" sheet in the inventory-item Excel test
 * data (see {@code ItemPOSBehaviour.autoQtyPopup}) via
 * {@link com.gsl.converter.AutoQtyPopupConverter} / {@link #fromExcel}.
 */
public enum AutoQtyPopupType {

    YES("Yes"),
    NO("No");
    private final String label;

    AutoQtyPopupType(String label) {
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
     * Resolves {@code excelValue} to its matching {@link AutoQtyPopupType} constant by
     * case-insensitive match against each constant's {@link #label}; throws
     * {@link IllegalArgumentException} naming {@code fileName}/{@code sheetName}/
     * {@code columnName} for a blank or unrecognized value.
     */
    public static AutoQtyPopupType fromExcel(
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

        for (AutoQtyPopupType type : values()) {
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
                .map(AutoQtyPopupType::label)
                .collect(Collectors.joining(", "));

        return String.format(
                "%n❌ Invalid Auto Qty Popup value from Excel%n" +
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
                AutoQtyPopupType.class.getName()
        );
    }
}
