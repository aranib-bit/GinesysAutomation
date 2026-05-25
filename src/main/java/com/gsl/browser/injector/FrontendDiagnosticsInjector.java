package com.gsl.browser.injector;

import org.openqa.selenium.WebDriver;

/**
 * Contract for framework-specific frontend performance instrumentation.
 *
 * <p>Each implementation injects JavaScript into the page that buffers timing
 * data into {@code window.__gslExtDiag}.  The injected scripts are safe to call
 * multiple times — a guard variable prevents double-injection.
 *
 * <p>To add a new framework: implement this interface and add one {@code case} in
 * {@link FrontendDiagnosticsInjectorFactory}.
 */
public interface FrontendDiagnosticsInjector {

    /**
     * Injects framework-specific performance monitoring JS into the page.
     * Safe to call multiple times — guard prevents re-injection.
     * No-op when driver is null or does not support JS execution.
     */
    void inject(WebDriver driver);

    /** Display name for log messages — e.g. "ExtJS", "React", "Angular". */
    String frameworkName();
}
