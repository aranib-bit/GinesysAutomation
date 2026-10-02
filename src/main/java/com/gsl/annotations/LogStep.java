package com.gsl.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method whose invocation should be logged as a named test step. {@code LoggingAspect}
 * advises every {@code @LogStep}-annotated method call, logging {@link #value()} to both the
 * ExtentReports test (when one is active) and Log4j2 at INFO, and logging a failure message to
 * the report if the method throws.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LogStep {
    /** Human-readable step description shown in the report and log line. */
    String value();
}
