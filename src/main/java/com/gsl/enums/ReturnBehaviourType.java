package com.gsl.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Item-level return policy, as configured in the "Return Behavior" column of the "POS Behaviour"
 * sheet in the inventory-item Excel test data ({@code ItemPOSBehaviour.returnBehavior}) — resolved
 * from the raw cell text by {@link #fromExcel} via {@code ReturnBehaviourConverter}.
 */
public enum ReturnBehaviourType {
    /** Return eligibility follows whatever policy is configured at the user/role level. */
    BASED_ON_USER_POLICY("Based on User Policy"),
    /** The item can only be returned when the original bill/invoice is presented. */
    ONLY_AGAINST_BILL("Only against Bill"),
    /** The item can always be returned, with no bill/policy restriction. */
    ALWAYS("Always"),
    /** The item can never be returned. */
    DO_NOT_ALLOW("Do not allow");

    private final String label;

    ReturnBehaviourType(String label) {
        this.label = label;
    }

    /** Excel/UI label for this return behaviour. */
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
    public static ReturnBehaviourType fromExcel(
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

        for (ReturnBehaviourType type : values()) {
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
                .map(ReturnBehaviourType::label)
                .collect(Collectors.joining(", "));

        return String.format(
                "%n❌ Invalid Return Behaviour value from Excel%n" +
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
                ReturnBehaviourType.class.getName()
        );
    }
}
