package com.gsl.converter;

import io.github.sskorol.converters.DefaultConverter;
import java.math.BigDecimal;

public class BigDecimalConverter extends DefaultConverter<BigDecimal> {

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