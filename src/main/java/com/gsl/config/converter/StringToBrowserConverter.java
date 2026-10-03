package com.gsl.config.converter;

import com.gsl.enums.Browser;
import com.gsl.utils.Log;
import org.aeonbits.owner.Converter;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owner library Converter that maps a config property string to the Browser enum.
 * Used via @Config.ConverterClass on the typed browserType() method in BrowserConfig.
 * A blank or missing value falls back to CHROME with a WARN (once per JVM), and an unknown value
 * fails with an error naming the value and every valid browser — so a fallback or a typo can
 * never pass silently.
 */
public final class StringToBrowserConverter implements Converter<Browser> {

    private static final AtomicBoolean BLANK_WARNED = new AtomicBoolean();

    /** Converts {@code input} (case-insensitive) to a {@link Browser}; blank/null falls back to {@link Browser#CHROME} with a WARN. */
    @Override
    public Browser convert(Method method, String input) {
        if (input == null || input.isBlank()) {
            if (BLANK_WARNED.compareAndSet(false, true)) {
                Log.warn("[Browser] No browser value supplied (-Dbrowser empty or missing, nothing in browser.properties)"
                        + " - falling back to CHROME.");
            }
            return Browser.CHROME;
        }
        return Browser.from(input, "-Dbrowser or browser.properties");
    }
}
