package com.gsl.config;

import com.gsl.enums.TargetOs;
import com.gsl.utils.Log;

import java.util.OptionalInt;

/**
 * Resolves the optional cap on concurrent browser sessions for the active {@link TargetOs}.
 * Lookup order: {@code max.concurrent.sessions} (one-off override for any OS), then
 * {@code max.concurrent.sessions.<os>}. A blank or absent value means "no cap", so the pool size
 * stays whatever {@code SessionPool} derives from the UI-user count — the pre-existing behaviour.
 * A value that is not a positive integer is a configuration error and fails fast.
 */
public final class ConcurrencyCap {

    private ConcurrencyCap() {}

    /** The cap for {@code os}, or empty when none is configured. */
    public static OptionalInt forOs(TargetOs os) {
        ExecutionConfig cfg = ConfigFactory.getExecutionConfig();
        String generic = cfg.maxConcurrentSessions();
        if (generic != null && !generic.isBlank()) {
            return OptionalInt.of(parse("max.concurrent.sessions", generic));
        }
        String perOs = os == TargetOs.WINDOWS ? cfg.maxConcurrentSessionsWindows() : cfg.maxConcurrentSessionsLinux();
        if (perOs != null && !perOs.isBlank()) {
            return OptionalInt.of(parse("max.concurrent.sessions." + os.key(), perOs));
        }
        return OptionalInt.empty();
    }

    /** The cap for the run's resolved target OS. */
    public static OptionalInt current() {
        return forOs(TargetOsResolver.current());
    }

    /** Human-readable source of the cap for logs and reports, e.g. {@code max.concurrent.sessions.windows=2}; empty when no cap. */
    public static String describe() {
        OptionalInt cap = current();
        if (cap.isEmpty()) {
            return "";
        }
        ExecutionConfig cfg = ConfigFactory.getExecutionConfig();
        String key = cfg.maxConcurrentSessions() != null && !cfg.maxConcurrentSessions().isBlank()
                ? "max.concurrent.sessions" : "max.concurrent.sessions." + TargetOsResolver.name();
        return key + "=" + cap.getAsInt();
    }

    private static int parse(String key, String value) {
        try {
            int n = Integer.parseInt(value.trim());
            if (n < 1) {
                throw new NumberFormatException("must be >= 1");
            }
            return n;
        } catch (NumberFormatException e) {
            Log.error("[ConcurrencyCap] {}='{}' is not a positive integer", key, value);
            throw new IllegalArgumentException(key + "='" + value + "' must be a positive integer (or blank for no cap).", e);
        }
    }
}
