package com.gsl.converter;

import io.github.sskorol.converters.IConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/**
 * ZeroCell "safe" date converter for Excel test-data columns bound to a {@link LocalDate} field —
 * tries each of {@link #SUPPORTED_FORMATS} in turn (day-first, US month-first, and ISO, each with
 * {@code -} or {@code /} separators) and never throws: a blank cell or a value that matches none
 * of them resolves to {@code null} (with a WARN logged so the unparseable cell is still visible in
 * test output) instead of failing the data load.
 */
public class SafeDateConverter implements IConverter<LocalDate> {

    private static final Logger log = LogManager.getLogger(SafeDateConverter.class);

    // Allowed date formats — add or remove patterns as needed
    private static final List<DateTimeFormatter> SUPPORTED_FORMATS = Arrays.asList(
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    /**
     * Parses {@code data} against {@link #SUPPORTED_FORMATS} in order, returning the first
     * successful match.
     *
     * @return the parsed date, or {@code null} if {@code data} is blank or matches no supported format
     */
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

        // No matching pattern — log so the bad cell value is visible in test output
        log.warn("SafeDateConverter: could not parse '{}' with any known format — documentdate will be null", value);
        return null;
    }

    /** Registers this converter's target type as {@link LocalDate}. */
    @Override
    public Class<LocalDate> getType() {
        return LocalDate.class;
    }
}
