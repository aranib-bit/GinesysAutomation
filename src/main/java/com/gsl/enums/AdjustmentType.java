package com.gsl.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Reconciliation strategy for an AR voucher's Sub-Ledger Information row — how a debit/credit
 * pair should be squared off against outstanding documents on the sub-ledger. Populated from the
 * "AdjustmentType" column of the "SubLedgerInformation" sheet in the AR voucher Excel test data
 * via {@link com.gsl.converter.AdjustmentTypeConverter} / {@link #fromExcel}.
 */
public enum AdjustmentType {

    /** Manually pick the specific outstanding document(s) to adjust against. */
    SELECT_DOCUMENT("SelectDocument",                          "Select Document"),
    /** Auto-adjust against outstanding documents oldest-first (First In, First Out). */
    FIFO_SETOFF("FIFO Set-Off",                               "FIFO Set-Off"),
    /** FIFO set-off scoped to documents sharing the same reference site. */
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

    /**
     * Resolves {@code excelValue} to its matching {@link AdjustmentType} constant by
     * case-insensitive match against each constant's {@link #label}; returns {@code null} for a
     * blank/missing cell (unlike most sibling {@code fromExcel} methods, which throw on blank —
     * this column is optional), and throws {@link IllegalArgumentException} naming
     * {@code fileName}/{@code sheetName}/{@code columnName} for an unrecognized value.
     */
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
