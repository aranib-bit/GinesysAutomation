package com.gsl.converter;

import io.github.sskorol.converters.IConverter;

/**
 * ZeroCell "safe" numeric converter for Excel test-data columns bound to an {@link Integer}
 * field — never throws: a blank or unparseable cell resolves to {@code null} instead of failing
 * the data load. Unlike {@link SafeDoubleConverter}/{@link BigDecimalConverter}, which default to
 * zero on the same kind of bad input, this deliberately defaults to {@code null} so a missing
 * integer value stays distinguishable from an explicit zero.
 */
public class SafeIntegerConverter implements IConverter<Integer> {

    /** Parses {@code data} to an {@link Integer}, returning {@code null} for blank or malformed input. */
    @Override
    public Integer convert(String data) {
        if (data == null || data.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(data);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Registers this converter's target type as {@link Integer}. */
    @Override
    public Class<Integer> getType() {
        return Integer.class;
    }
}
