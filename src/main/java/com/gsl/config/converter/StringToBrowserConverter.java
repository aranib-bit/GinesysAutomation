package com.gsl.config.converter;

import com.gsl.enums.Browser;
import org.aeonbits.owner.Converter;

import java.lang.reflect.Method;

/**
 * Owner library Converter that maps a config property string to the Browser enum.
 * Used via @Config.ConverterClass on typed browserType() method in FrameworkConfig.
 * Falls back to CHROME on blank or null input.
 */
public final class StringToBrowserConverter implements Converter<Browser> {

    /** Converts {@code input} (case-insensitive) to a {@link Browser}, defaulting to {@link Browser#CHROME} when blank/null. */
    @Override
    public Browser convert(Method method, String input) {
        if (input == null || input.isBlank()) return Browser.CHROME;
        return Browser.valueOf(input.trim().toUpperCase());
    }
}
