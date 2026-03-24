package com.gsl.converter;

import io.github.sskorol.converters.IConverter;

public class SafeIntegerConverter implements IConverter<Integer> {

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

    @Override
    public Class<Integer> getType() {
        return Integer.class;
    }
}
