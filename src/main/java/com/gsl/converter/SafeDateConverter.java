package com.gsl.converter;

import io.github.sskorol.converters.IConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

public class SafeDateConverter implements IConverter<LocalDate> {

    // Allowed date formats — add or remove patterns as needed
    private static final List<DateTimeFormatter> SUPPORTED_FORMATS = Arrays.asList(
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    );

    @Override
    public LocalDate convert(String data) {
        if (data == null || data.trim().isEmpty()) {
            return null;
        }

        String value = data.trim();

        for (DateTimeFormatter formatter : SUPPORTED_FORMATS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // try next pattern
            }
        }

        // No matching pattern
        return null;
    }

    @Override
    public Class<LocalDate> getType() {
        return LocalDate.class;
    }
}
