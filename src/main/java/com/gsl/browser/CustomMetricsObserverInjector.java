package com.gsl.browser;

import com.gsl.reports.ReportLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

/**
 * Injects PerformanceObserver listeners into the browser page at test start.
 * Buffers Long Tasks, Long Animation Frames, and Event Timing entries in
 * window.__gslMetrics__ so CustomMetricsSection can read them at test end.
 *
 * Guarded by window.__gslMetricsInjected to survive SPA navigations without
 * double-injection (same pattern as AdvancedJsLogger).
 */
public final class CustomMetricsObserverInjector {

    private static final Logger LOG = LogManager.getLogger(CustomMetricsObserverInjector.class);

    private static final String INJECTION_SCRIPT = """
            if (!window.__gslMetricsInjected) {
                window.__gslMetricsInjected = true;
                window.__gslMetrics = {
                    longTasks:           [],
                    longAnimationFrames: [],
                    eventTimings:        []
                };

                var supported = (typeof PerformanceObserver !== 'undefined' &&
                                 PerformanceObserver.supportedEntryTypes) ?
                                 PerformanceObserver.supportedEntryTypes : [];

                if (supported.indexOf('longtask') !== -1) {
                    try {
                        new PerformanceObserver(function(list) {
                            list.getEntries().forEach(function(e) {
                                // Capture LoafTimingAttribution (Chrome 122+) when available
                                var scripts = [];
                                if (e.scripts && e.scripts.length) {
                                    e.scripts.forEach(function(s) {
                                        scripts.push({
                                            invoker:   s.invoker             || '',
                                            sourceURL: s.sourceURL           || '',
                                            sourceFn:  s.sourceFunctionName  || ''
                                        });
                                    });
                                }
                                window.__gslMetrics.longTasks.push({
                                    name:      e.name,
                                    startTime: Math.round(e.startTime),
                                    duration:  Math.round(e.duration),
                                    scripts:   scripts
                                });
                            });
                        }).observe({ type: 'longtask', buffered: true });
                    } catch(ex) {}
                }

                if (supported.indexOf('long-animation-frame') !== -1) {
                    try {
                        new PerformanceObserver(function(list) {
                            list.getEntries().forEach(function(e) {
                                window.__gslMetrics.longAnimationFrames.push({
                                    startTime: Math.round(e.startTime),
                                    duration:  Math.round(e.duration)
                                });
                            });
                        }).observe({ type: 'long-animation-frame', buffered: true });
                    } catch(ex) {}
                }

                if (supported.indexOf('event') !== -1) {
                    try {
                        new PerformanceObserver(function(list) {
                            list.getEntries().forEach(function(e) {
                                window.__gslMetrics.eventTimings.push({
                                    name:            e.name,
                                    startTime:       Math.round(e.startTime),
                                    processingStart: Math.round(e.processingStart),
                                    duration:        Math.round(e.duration)
                                });
                            });
                        }).observe({ type: 'event', buffered: true, durationThreshold: 16 });
                    } catch(ex) {}
                }
            }
            """;

    private CustomMetricsObserverInjector() {}

    /**
     * Injects the PerformanceObserver listeners. Safe to call multiple times —
     * the JS guard prevents double-injection. No-op when driver is null or
     * does not support JS execution.
     */
    public static void inject(WebDriver driver) {
        if (driver == null) return;
        try {
            ((JavascriptExecutor) driver).executeScript(INJECTION_SCRIPT);
        } catch (Exception e) {
            LOG.debug("[CustomMetricsObserverInjector] Injection skipped: {}", e.getMessage());
            ReportLogger.perf("[CustomMetricsObserverInjector] Injection skipped: {}", e.getMessage());
        }
    }
}
