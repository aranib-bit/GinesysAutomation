package com.gsl.validation;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class ExcelEnumValidator {

    private ExcelEnumValidator() {}

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

    @FunctionalInterface
    public interface LabelExtractor<E> {
        String getLabel(E e);
    }
}
