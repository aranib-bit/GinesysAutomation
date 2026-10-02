package com.gsl.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin static facade over a single, framework-wide SLF4J {@link Logger} (category {@code
 * "Framework"}) — utility/helper code that isn't itself a page object or test class logs through
 * here instead of each declaring its own {@code LoggerFactory.getLogger(...)}, so all such
 * diagnostic output lands under one predictable logger name/category in the log config.
 * Message/args follow SLF4J's own {@code "{}"} placeholder convention, not {@link String#format}.
 */
public final class Log {

    private static final Logger logger = LoggerFactory.getLogger("Framework");

    private Log() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Logs at DEBUG, SLF4J {@code "{}"}-placeholder style. */
    public static void debug(String msg, Object... args) { logger.debug(msg, args); }
    /** Logs at INFO, SLF4J {@code "{}"}-placeholder style. */
    public static void info(String msg, Object... args)  { logger.info(msg, args);  }
    /** Logs at WARN, SLF4J {@code "{}"}-placeholder style. */
    public static void warn(String msg, Object... args)  { logger.warn(msg, args);  }
    /** Logs at ERROR, SLF4J {@code "{}"}-placeholder style. */
    public static void error(String msg, Object... args) { logger.error(msg, args); }
    /** Logs at ERROR with a full stack trace for {@code t}. */
    public static void error(String msg, Throwable t)    { logger.error(msg, t);    }

    /**
     * Logs current JVM heap usage at INFO. Useful at suite start/finish and on OOM
     * post-mortem to correlate growth against test progress.
     */
    public static void logHeap(String label) {
        Runtime r = Runtime.getRuntime();
        long used  = (r.totalMemory() - r.freeMemory()) / 1048576L;
        long total = r.totalMemory() / 1048576L;
        long max   = r.maxMemory() / 1048576L;
        logger.info("[HEAP {}] used={} MB / total={} MB / max={} MB", label, used, total, max);
    }
}
