package com.gsl.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Log {

    private static final Logger logger = LoggerFactory.getLogger("Framework");

    private Log() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void debug(String msg, Object... args) { logger.debug(msg, args); }
    public static void info(String msg, Object... args)  { logger.info(msg, args);  }
    public static void warn(String msg, Object... args)  { logger.warn(msg, args);  }
    public static void error(String msg, Object... args) { logger.error(msg, args); }
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
