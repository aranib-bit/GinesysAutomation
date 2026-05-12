package com.gsl.config.converter;

import com.gsl.enums.RunMode;
import org.aeonbits.owner.Converter;

import java.lang.reflect.Method;

/**
 * Owner library Converter that maps a config property string to the RunMode enum.
 * Used via @Config.ConverterClass on typed runMode() method in FrameworkConfig.
 * Falls back to LOCAL on blank or null input.
 */
public final class StringToRunModeConverter implements Converter<RunMode> {

    @Override
    public RunMode convert(Method method, String input) {
        if (input == null || input.isBlank()) return RunMode.LOCAL;
        return RunMode.valueOf(input.trim().toUpperCase());
    }
}
