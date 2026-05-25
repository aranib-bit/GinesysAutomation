package com.gsl.diagnostics.section.frontend;

import com.gsl.diagnostics.DiagnosticSection;

import java.util.List;
import java.util.Map;

/**
 * Marker interface for all framework-specific frontend performance diagnostic sections.
 *
 * <p>Concrete implementations live in sub-packages:
 * <ul>
 *   <li>{@code extjs.ExtJsPerformanceSection}</li>
 *   <li>{@code react.ReactPerformanceSection}</li>
 *   <li>{@code angular.AngularPerformanceSection}</li>
 * </ul>
 *
 * <p>Use {@link FrontendPerformanceSectionFactory#create} to obtain the correct
 * instance based on {@code application.type} in config.properties.
 */
public interface FrontendPerformanceSection extends DiagnosticSection {

    /** Framework label: "extjs" | "react" | "angular". */
    String getFramework();

    /** True when window.__gslExtDiag was successfully read from the page. */
    boolean isCaptured();

    /** Sum of all loadmask / loading-state durations (ms). */
    long totalOverlayDurationMs();

    /** Longest single component render (ms) — ExtJS afterRender or React User Timing. */
    long longestComponentRenderMs();

    /** Total layout time — ExtJS layoutRuns sum or Angular ngZone instability sum (ms). */
    long totalLayoutTimeMs();

    /** Sum of all CLS layout-shift scores (React / Angular). */
    double totalLayoutShiftScore();

    List<Map<String, Object>> getApiTimings();

    List<Map<String, Object>> getLoadMaskEvents();

    List<Map<String, Object>> getComponentRenders();

    /** Slowest ExtJS component render entry, or null. */
    Map<String, Object> getSlowestComponentRender();

    /** Slowest React User Timing render entry, or null. */
    Map<String, Object> getSlowestReactRender();

    /** Slowest overlay entry across all frameworks, or null. */
    Map<String, Object> getSlowestOverlayEntry();
}
