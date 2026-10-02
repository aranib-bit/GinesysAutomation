package com.gsl.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The payment mode selected in a voucher's Payment Information popup, stored in {@code
 * finvchdet.payment_mode}. Bridges three directions: {@link #fromExcel} resolves curated test
 * input (throws on an unrecognized label), {@link #fromDbCode} resolves live DB data read back for
 * report display (never throws), and {@link #label()} supplies the UI dropdown text used to drive
 * the popup itself.
 */
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

    /** Reverse lookup — resolves a raw {@code finvchdet.payment_mode} DB value ('C'/'R'/'H') back
     *  to its enum/label, for report display. {@code null} for a blank/unrecognized code (never
     *  throws — unlike {@link #fromExcel}, this reads live DB data, not curated test input). */
    public static PaymentSelectionType fromDbCode(String dbCode) {
        if (dbCode == null || dbCode.isBlank()) return null;
        String trimmed = dbCode.trim();
        for (PaymentSelectionType type : values()) {
            if (type.dbCode.equalsIgnoreCase(trimmed)) return type;
        }
        return null;
    }

    @Override
    public String toString() {
        return label;
    }

    /** Resolves a raw Excel cell value to its matching constant (case-insensitive label match);
     *  {@code null} for a blank value, or throws {@link IllegalArgumentException} naming the
     *  offending file/sheet/column and the allowed values for an unrecognized one. */
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
