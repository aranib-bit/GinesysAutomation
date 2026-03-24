package com.gsl.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum ReturnBehaviourType {
    BASED_ON_USER_POLICY("Based on User Policy"),
    ONLY_AGAINST_BILL("Only against Bill"),
    ALWAYS("Always"),
    DO_NOT_ALLOW("Do not allow");

    private final String label;

    ReturnBehaviourType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }


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
