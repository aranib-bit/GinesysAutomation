package com.gsl.validation;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Resolves a raw Excel cell string to its matching enum constant via a caller-supplied {@link
 * LabelExtractor}, so test-data DTOs can map a human-readable column value (e.g. "Multi Price
 * Action") onto the enum the framework actually works with, instead of relying on {@code
 * Enum#valueOf} and its exact-name-matching. On a blank cell or an unrecognized value, throws an
 * {@link IllegalArgumentException} whose message names the offending file/sheet/column and lists
 * every allowed label, so a bad Excel row fails loudly and specifically rather than as a generic
 * enum-mapping error.
 */
public final class ExcelEnumValidator {

    private ExcelEnumValidator() {}

    /**
     * Matches {@code excelValue} (case-insensitively, after trimming) against {@code
     * extractor}'s label for each constant of {@code enumClass}.
     *
     * @param displayName human-readable field name used in the error message (e.g. "Multi Price
     *                    Action", "Allow Discount") — not necessarily the enum's own name
     * @param fileName    Excel file name, folded into the error message for a blank/unmatched value
     * @param sheetName   Excel sheet name, folded into the error message for a blank/unmatched value
     * @param columnName  Excel column name, folded into the error message for a blank/unmatched value
     * @return the matching enum constant
     * @throws IllegalArgumentException if {@code excelValue} is blank or matches no constant's label
     */
    public static <E extends Enum<E>> E validate(
            String excelValue,
            Class<E> enumClass,
            String displayName,   // 👈 "Multi Price Action", "Allow Discount", etc.
            String fileName,
            String sheetName,
            String columnName,
            LabelExtractor<E> extractor
    ) {
        if (excelValue == null || excelValue.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    buildError(displayName, "BLANK VALUE", enumClass, fileName, sheetName, columnName, extractor)
            );
        }

        String cleaned = excelValue.trim();

        for (E constant : enumClass.getEnumConstants()) {
            if (extractor.getLabel(constant).equalsIgnoreCase(cleaned)) {
                return constant;
            }
        }

        throw new IllegalArgumentException(
                buildError(displayName, excelValue, enumClass, fileName, sheetName, columnName, extractor)
        );
    }

    /** Builds the multi-line, emoji-flagged error message reported for a blank or unmatched Excel value. */
    private static <E extends Enum<E>> String buildError(
            String displayName,
            String excelValue,
            Class<E> enumClass,
            String fileName,
            String sheetName,
            String columnName,
            LabelExtractor<E> extractor
    ) {
        String allowedValues = Arrays.stream(enumClass.getEnumConstants())
                .map(extractor::getLabel)
                .collect(Collectors.joining(", "));

        return String.format(
                "%n❌ Invalid %s value from Excel%n" +
                        "--------------------------------------------%n" +
                        "📄 File      : %s%n" +
                        "📑 Sheet     : %s%n" +
                        "📌 Column    : %s%n" +
                        "🔴 Found     : '%s'%n" +
                        "✅ Allowed   : [%s]%n" +
                        "🧩 Enum      : %s%n",
                displayName,
                fileName,
                sheetName,
                columnName,
                excelValue,
                allowedValues,
                enumClass.getName()
        );
    }

    /** Supplies the human-readable label an enum constant should match against an Excel cell value. */
    @FunctionalInterface
    public interface LabelExtractor<E> {
        String getLabel(E e);
    }
}
