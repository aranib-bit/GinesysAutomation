package com.gsl.converter;

import io.github.sskorol.converters.DefaultConverter;

public class SafeDoubleConverter extends DefaultConverter<Double> {

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
