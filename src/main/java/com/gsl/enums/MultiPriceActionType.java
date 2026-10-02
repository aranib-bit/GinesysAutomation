package com.gsl.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * How POS billing should resolve an item's price when more than one price is applicable (e.g.
 * multiple active price lists). Populated from the "Multi Price Action" column of the "POS
 * Behaviour" sheet in the inventory-item Excel test data (see
 * {@code ItemPOSBehaviour.multiPriceAction}) via
 * {@link com.gsl.converter.MultiPriceActionConverter} / {@link #fromExcel}.
 */
public enum MultiPriceActionType {
    /** Always use the price defined on the item master, ignoring price lists. */
    APPLY_PRICE_FROM_MASTER("Apply Price from Master"),
    /** Let the operator pick from a popup listing each applicable price-list price. */
    POP_UP_PRICE_FROM_LIST("Popup Price from list"),
    /** Auto-apply whichever price-list price was last used, without prompting. */
    APPLY_LAST_PRICE_FROM_LIST("Apply Last Price from list");

    private final String label;

    MultiPriceActionType(String label) {
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
     * Resolves {@code excelValue} to its matching {@link MultiPriceActionType} constant by
     * case-insensitive match against each constant's {@link #label}; throws
     * {@link IllegalArgumentException} naming {@code fileName}/{@code sheetName}/
     * {@code columnName} for a blank or unrecognized value.
     */
    public static MultiPriceActionType fromExcel(
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

        for (MultiPriceActionType type : values()) {
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
                .map(MultiPriceActionType::label)
                .collect(Collectors.joining(", "));

        return String.format(
                "%n❌ Invalid Multi Price Action value from Excel%n" +
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
                MultiPriceActionType.class.getName()
        );
    }
}
