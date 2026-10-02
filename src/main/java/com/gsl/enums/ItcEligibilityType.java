package com.gsl.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * GST Input Tax Credit (ITC) eligibility classification for an item. Populated from the "ITC
 * Eligibility" column of the "General" sheet in the inventory-item Excel test data (see
 * {@code ItemGeneral.itcEligibility}) via {@link com.gsl.converter.ItcEligibilityConverter} /
 * {@link #fromExcel}.
 */
public enum ItcEligibilityType {

    /** ITC on this item's inward supply is claimable against output tax (revenue item). */
    INPUT_APPLICABLE("Input Applicable"),
    /** Item is capitalized (capital goods) — ITC follows the capital-goods reversal rules. */
    CAPITAL_GOODS("Capital Goods"),
    /** No ITC is claimable on this item. */
    NOT_APPLICABLE("Not Applicable");

    private final String label;

    ItcEligibilityType(String label) {
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
     * Resolves {@code excelValue} to its matching {@link ItcEligibilityType} constant by
     * case-insensitive match against each constant's {@link #label}; throws
     * {@link IllegalArgumentException} naming {@code fileName}/{@code sheetName}/
     * {@code columnName} for a blank or unrecognized value.
     */
    public static ItcEligibilityType fromExcel(
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

        for (ItcEligibilityType type : values()) {
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
                .map(ItcEligibilityType::label)
                .collect(Collectors.joining(", "));

        return String.format(
                "%n❌ Invalid ITC Eligibility value from Excel%n" +
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
                ItcEligibilityType.class.getName()
        );
    }
}
