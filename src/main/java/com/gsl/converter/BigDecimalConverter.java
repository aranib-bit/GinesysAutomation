package com.gsl.converter;

import io.github.sskorol.converters.DefaultConverter;
import java.math.BigDecimal;

/**
 * ZeroCell "safe" numeric converter for Excel test-data columns bound to a {@link BigDecimal}
 * field — strips Excel's thousands-separator commas (e.g. {@code "1,234.56"}) before parsing, and
 * never throws: a blank or unparseable cell resolves to {@link BigDecimal#ZERO} instead of
 * failing the data load.
 */
public class BigDecimalConverter extends DefaultConverter<BigDecimal> {

    /** Parses {@code data} to a {@link BigDecimal}, returning {@link BigDecimal#ZERO} for blank or malformed input. */
    @Override
    public BigDecimal convert(String data) {
        if (data == null || data.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }

        try {
            // Remove commas (Excel often exports numbers like "1,234.56")
            String normalized = data.replace(",", "").trim();
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}