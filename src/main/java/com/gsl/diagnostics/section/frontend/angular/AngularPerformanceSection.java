package com.gsl.diagnostics.section.frontend.angular;

import com.gsl.diagnostics.section.frontend.BaseFrontendSection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Angular-specific frontend performance section.
 *
 * <p>Reads {@code window.__gslExtDiag} (populated by {@code AngularFrontendInjector}) and
 * renders the following sub-sections inside the Extent "Logs &amp; Diagnostics" node:
 * <ul>
 *   <li>API Timings (common)</li>
 *   <li>Slow Event Handlers (common)</li>
 *   <li>DOM Complexity (common)</li>
 *   <li>User Interactions (common)</li>
 *   <li>Forced Reflows (common)</li>
 *   <li>NgZone Stability Events (Angular)</li>
 *   <li>Change Detection Time (Angular — dev mode only)</li>
 *   <li>Loading States — CDK/Material DOM-observed (Angular)</li>
 *   <li>Layout Shifts — CLS (Angular)</li>
 *   <li>Route Transitions (Angular)</li>
 * </ul>
 */
public final class AngularPerformanceSection extends BaseFrontendSection {

    private static final Logger LOG = LogManager.getLogger(AngularPerformanceSection.class);

    private final List<Map<String, Object>> ngZoneEvents;
    private final long                      changeDetectionMs;
    private final List<Map<String, Object>> loadingStates;
    private final List<Map<String, Object>> layoutShifts;
    private final List<Map<String, Object>> routeTransitions;

    @SuppressWarnings("unchecked")
    public AngularPerformanceSection(WebDriver driver) {
        super(driver);

        List<Map<String, Object>> ngZn  = Collections.emptyList();
        long                      cdMs  = -1L;
        List<Map<String, Object>> lStat = Collections.emptyList();
        List<Map<String, Object>> lShft = Collections.emptyList();
        List<Map<String, Object>> rTran = Collections.emptyList();

        if (driver != null) {
            try {
                JavascriptExecutor js = (JavascriptExecutor) driver;
                Object raw = js.executeScript("return window.__gslExtDiag || null;");
                if (raw instanceof Map<?, ?> d) {
                    Map<String, Object> diag = (Map<String, Object>) d;
                    ngZn  = asList(diag.get("ngZoneEvents"));
                    cdMs  = toLong(diag.get("changeDetectionMs"));
                    lStat = asList(diag.get("loadingStates"));
                    lShft = asList(diag.get("layoutShifts"));
                    rTran = asList(diag.get("routeTransitions"));
                }
            } catch (Exception e) {
                LOG.warn("[AngularPerformanceSection] Angular data read failed: {}", e.getMessage());
            }
        }

        this.ngZoneEvents      = ngZn;
        this.changeDetectionMs = cdMs;
        this.loadingStates     = lStat;
        this.layoutShifts      = lShft;
        this.routeTransitions  = rTran;
    }

    // ── DiagnosticSection ─────────────────────────────────────────────────────

    @Override
    public String sectionTitle() {
        int total = apiTimings.size() + slowEventHandlers.size()
                + ngZoneEvents.size() + loadingStates.size()
                + layoutShifts.size() + routeTransitions.size()
                + forcedReflows.size() + interactions.size()
                + (changeDetectionMs >= 0 ? 1 : 0);
        return "Frontend Performance — Angular (" + total + " entries)";
    }

    @Override
    public boolean hasData() {
        return super.hasData()
                || !ngZoneEvents.isEmpty() || !loadingStates.isEmpty()
                || !layoutShifts.isEmpty() || !routeTransitions.isEmpty()
                || changeDetectionMs >= 0;
    }

    @Override
    public String renderHtml() {
        StringBuilder html = new StringBuilder();
        renderCommon(html);
        renderNgZone(html);
        renderChangeDetection(html);
        renderLoadingStates(html);
        renderLayoutShifts(html);
        renderRouteTransitions(html);
        return html.toString();
    }

    // ── Public accessors (used by RootCauseAttributionSection) ───────────────

    @Override
    public long totalLayoutTimeMs() {
        return ngZoneEvents.stream().mapToLong(m -> toLong(m.get("durationMs"))).sum();
    }

    @Override
    public long totalOverlayDurationMs() {
        return loadingStates.stream().mapToLong(m -> toLong(m.get("durationMs"))).sum();
    }

    @Override
    public double totalLayoutShiftScore() {
        return layoutShifts.stream().mapToDouble(m -> toDouble(m.get("value"))).sum();
    }

    @Override
    public Map<String, Object> getSlowestOverlayEntry() {
        return loadingStates.stream()
                .max(java.util.Comparator.comparingLong(m -> toLong(m.get("durationMs"))))
                .orElse(null);
    }

    // ── Angular-specific sub-section renderers ────────────────────────────────

    private void renderNgZone(StringBuilder html) {
        if (ngZoneEvents.isEmpty()) return;
        long total = ngZoneEvents.stream().mapToLong(m -> toLong(m.get("durationMs"))).sum();
        openDetails(html, "NgZone Stability Events (" + ngZoneEvents.size()
                + ", total instability " + total + " ms)");
        html.append(tableHeader("Unstable At (ms)", "Stable At (ms)", "Duration (ms)", "Status"));
        for (Map<String, Object> z : ngZoneEvents) {
            long dur = toLong(z.get("durationMs"));
            String status = dur > 500 ? "⚠ Warning" : "OK";
            String color  = dur > 500 ? "#cc6600" : "#006600";
            addRow4(html,
                    str(z.get("unstableAt")) + " ms",
                    str(z.get("stableAt"))   + " ms",
                    dur + " ms",
                    status, color);
        }
        html.append("</table>");
        closeDetails(html);
    }

    private void renderChangeDetection(StringBuilder html) {
        openDetails(html, "Change Detection Time");
        if (changeDetectionMs < 0) {
            html.append("<p style='font-size:11px;color:#888;margin:4px 0;'>")
                .append("ng.profiler not available. This metric requires Angular dev mode ")
                .append("(ng.profiler.timeChangeDetection is absent in production builds).")
                .append("</p>");
        } else {
            html.append(tableHeader("Metric", "Value"));
            addRow2(html, "Change detection (ms/tick)", changeDetectionMs + " ms",
                    changeDetectionMs > 50 ? "#cc6600" : "#006600");
            html.append("</table>");
        }
        closeDetails(html);
    }

    private void renderLoadingStates(StringBuilder html) {
        if (loadingStates.isEmpty()) return;
        openDetails(html, "Loading States — CDK/Material (" + loadingStates.size() + ")");
        html.append(tableHeader("Selector", "Duration (ms)"));
        for (Map<String, Object> s : loadingStates) {
            long dur = toLong(s.get("durationMs"));
            addRow2(html, esc(str(s.get("selector"))), dur + " ms",
                    dur > 1000 ? "#cc6600" : "#006600");
        }
        html.append("</table>");
        closeDetails(html);
    }

    private void renderLayoutShifts(StringBuilder html) {
        if (layoutShifts.isEmpty()) return;
        double total = totalLayoutShiftScore();
        openDetails(html, "Layout Shifts — CLS (" + layoutShifts.size()
                + " shifts, total score " + String.format("%.4f", total) + ")");
        html.append(tableHeader("Shift Value", "Sources"));
        for (Map<String, Object> s : layoutShifts) {
            String val  = String.valueOf(s.get("value"));
            String srcs = str(s.get("sources"));
            double v    = toDouble(s.get("value"));
            addRow2(html, val, srcs.isEmpty() ? "—" : esc(srcs),
                    v > 0.1 ? "#cc6600" : "#006600");
        }
        html.append("</table>");
        closeDetails(html);
    }

    private void renderRouteTransitions(StringBuilder html) {
        if (routeTransitions.isEmpty()) return;
        openDetails(html, "Route Transitions (" + routeTransitions.size() + ")");
        html.append(tableHeader("Duration (ms)"));
        for (Map<String, Object> r : routeTransitions) {
            addRow2(html, toLong(r.get("durationMs")) + " ms", "", "#555555");
        }
        html.append("</table>");
        closeDetails(html);
    }
}
