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
}
