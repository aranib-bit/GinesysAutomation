package com.gsl.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum AdjustmentType {

    SELECT_DOCUMENT("SelectDocument",                          "Select Document"),
    FIFO_SETOFF("FIFO Set-Off",                               "FIFO Set-Off"),
    REFERENCE_SITEWISE_FIFO_SETOFF("Reference Site wise FIFO set-off", "Reference Site wise FIFO set-off");

    private final String label;
    private final String displayLabel;

    AdjustmentType(String label, String displayLabel) {
        this.label        = label;
        this.displayLabel = displayLabel;
    }

    /** Excel parse key — used by {@link #fromExcel} and {@link #toString}. */
    public String label() {
        return label;
    }

    /** Human-readable label for reports (e.g. "Select Document" vs parse key "SelectDocument"). */
    public String displayLabel() {
        return displayLabel;
    }

    @Override
    public String toString() {
        return label;
    }

    public static AdjustmentType fromExcel(
            String excelValue,
            String fileName,
            String sheetName,
            String columnName
    ) {
        if (excelValue == null || excelValue.trim().isEmpty()) {
            return null;
        }

        String cleaned = excelValue.trim();

        for (AdjustmentType type : values()) {
            if (type.label.equalsIgnoreCase(cleaned)) {
                return type;
            }
        }

        throw new IllegalArgumentException(buildErrorMessage(excelValue, fileName, sheetName, columnName));
    }

    private static String buildErrorMessage(
            String excelValue,
            String fileName,
            String sheetName,
            String columnName
    ) {
        String allowedValues = Arrays.stream(values())
                .map(AdjustmentType::label)
                .collect(Collectors.joining(", "));

        return String.format(
                "%n❌ Invalid Adjustment Type value from Excel%n" +
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
                AdjustmentType.class.getName()
        );
    }
}
