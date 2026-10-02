package com.gsl.enums;

/**
 * Target browser for a WebDriver session, selected via the {@code browserType()} framework
 * config property (mapped from its string value by
 * {@link com.gsl.config.converter.StringToBrowserConverter}, defaulting to {@link #CHROME}) and
 * consumed by {@code DriverManager}/the driver factories to launch the matching WebDriver.
 */
public enum Browser {
    CHROME, FIREFOX, EDGE, SAFARI
}
