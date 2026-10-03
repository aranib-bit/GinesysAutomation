package com.gsl.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Target browser for a WebDriver session, selected via the {@code browserType()} framework
 * config property (mapped from its string value by
 * {@link com.gsl.config.converter.StringToBrowserConverter}, defaulting to {@link #CHROME}) and
 * consumed by {@code DriverManager}/the driver factories to launch the matching WebDriver.
 */
public enum Browser {
    CHROME, FIREFOX, EDGE, SAFARI;

    /**
     * Maps a configured value (case-insensitive) to its constant.
     *
     * @param value  raw value, e.g. {@code firefox}
     * @param source where it came from, used in the error ("-Dbrowser", "browser.properties", ...)
     * @throws IllegalArgumentException for an unknown value, naming the value, its source and every valid browser
     */
    public static Browser from(String value, String source) {
        String wanted = value.trim();
        for (Browser b : values()) {
            if (b.name().equalsIgnoreCase(wanted)) {
                return b;
            }
        }
        String valid = Arrays.stream(values()).map(b -> b.name().toLowerCase()).collect(Collectors.joining(", "));
        throw new IllegalArgumentException("Unknown browser '" + value + "' (from " + source
                + "). Valid browsers: " + valid + ".");
    }
}
