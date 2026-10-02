package com.gsl.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test method as a pre-execution rule/config validation check rather than an ordinary
 * functional test — {@code TestListener} detects it via
 * {@code Method.isAnnotationPresent(FrameworkValidation.class)} in {@code onTestStart} and gives
 * it a distinct ExtentReports node ("Rule Validation [&lt;domain&gt;]", labeled from the method's
 * first/second parameters) instead of the normal per-test node, so these checks are visibly
 * grouped apart from real business-flow tests in the report.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FrameworkValidation {
}
