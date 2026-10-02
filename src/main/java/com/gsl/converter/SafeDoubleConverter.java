package com.gsl.converter;

import io.github.sskorol.converters.DefaultConverter;

/**
 * ZeroCell "safe" numeric converter for Excel test-data columns bound to a {@code double}/
 * {@link Double} field — never throws: a blank or unparseable cell resolves to {@code 0.0}
 * instead of failing the data load. Unlike {@link SafeIntegerConverter}, which defaults to
 * {@code null} on the same kind of bad input, this defaults to zero.
 */
public class SafeDoubleConverter extends DefaultConverter<Double> {

    /** Parses {@code data} to a {@link Double}, returning {@code 0.0} for blank or malformed input. */
    @Override
    public Double convert(String data) {
        if (data == null || data.trim().isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(data);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
