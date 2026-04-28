package com.gsl.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum PaymentSelectionType {

    CHEQUE("Cheque",       "C"),
    NEFT_RTGS("NEFT/RTGS", "R"),
    CASH("Cash",           "H");

    private final String label;
    private final String dbCode;

    PaymentSelectionType(String label, String dbCode) {
        this.label  = label;
        this.dbCode = dbCode;
    }

    /** UI dropdown label — used for selecting options in the Payment Information popup. */
    public String label() {
        return label;
    }

    /** Single-character code stored in finvchdet.payment_mode column. */
    public String dbCode() {
        return dbCode;
    }

    @Override
    public String toString() {
        return label;
    }

    public static PaymentSelectionType fromExcel(
            String excelValue,
            String fileName,
            String sheetName,
            String columnName
    ) {
        if (excelValue == null || excelValue.trim().isEmpty()) {
            return null;
        }

        String cleaned = excelValue.trim();

        for (PaymentSelectionType type : values()) {
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
                .map(PaymentSelectionType::label)
                .collect(Collectors.joining(", "));

        return String.format(
                "%n❌ Invalid Payment Selection value from Excel%n" +
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
                PaymentSelectionType.class.getName()
        );
    }
}
