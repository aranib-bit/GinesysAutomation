package com.gsl.diagnostics.section.frontend.react;

import com.gsl.diagnostics.section.frontend.BaseFrontendSection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * React-specific frontend performance section.
 *
 * <p>Reads {@code window.__gslExtDiag} (populated by {@code ReactFrontendInjector}) and
 * renders the following sub-sections inside the Extent "Logs &amp; Diagnostics" node:
 * <ul>
 *   <li>API Timings (common)</li>
 *   <li>Slow Event Handlers (common)</li>
 *   <li>DOM Complexity (common)</li>
 *   <li>User Interactions (common)</li>
 *   <li>Forced Reflows (common)</li>
 *   <li>React Component Renders — User Timing marks (React)</li>
 *   <li>Loading States — DOM-observed (React)</li>
 *   <li>Layout Shifts — CLS (React)</li>
 *   <li>Route Transitions (React)</li>
 * </ul>
 */
public final class ReactPerformanceSection extends BaseFrontendSection {

    private static final Logger LOG = LogManager.getLogger(ReactPerformanceSection.class);

    private final List<Map<String, Object>> reactRenders;
    private final List<Map<String, Object>> loadingStates;
    private final List<Map<String, Object>> layoutShifts;
    private final List<Map<String, Object>> routeTransitions;

    @SuppressWarnings("unchecked")
    public ReactPerformanceSection(WebDriver driver) {
        super(driver);

        List<Map<String, Object>> rRend = Collections.emptyList();
        List<Map<String, Object>> lStat = Collections.emptyList();
        List<Map<String, Object>> lShft = Collections.emptyList();
        List<Map<String, Object>> rTran = Collections.emptyList();

        if (driver != null) {
            try {
                JavascriptExecutor js = (JavascriptExecutor) driver;

                // React User Timing marks — harvested at collection time (dev/profiling builds only)
                Object rawRenders = js.executeScript("""
                        return (performance.getEntriesByType('measure') || [])
                            .filter(function(e) {
                                return e.name.indexOf('\\u269b') !== -1 ||
                                       e.name.indexOf('\\u26db') !== -1 ||
                                       e.name.indexOf('React') !== -1;
                            })
                            .filter(function(e) { return Math.round(e.duration) > 16; })
                            .map(function(e) {
                                var raw = e.name
                                    .replace(/^[\\u269b\\u26db]\\s*/,'')
                                    .replace(/\\s*\\[.*\\]$/,'')
                                    .trim();
                                var lower = raw.toLowerCase();
                                var phase = lower.indexOf('update') !== -1
                                    ? 'update'
                                    : lower.indexOf('mount') !== -1 ? 'mount' : '';
                                var compName = raw.replace(/\\s*(render|update|mount)\\s*/i,'').trim()
                                    || 'component';
                                return {
                                    componentName: compName,
                                    phase:         phase,
                                    durationMs:    Math.round(e.duration)
                                };
                            });
                        """);
                rRend = asList(rawRenders);

                // Load from window.__gslExtDiag (populated by ReactFrontendInjector)
                Object raw = js.executeScript("return window.__gslExtDiag || null;");
                if (raw instanceof Map<?, ?> d) {
                    Map<String, Object> diag = (Map<String, Object>) d;
                    lStat = asList(diag.get("loadingStates"));
                    lShft = asList(diag.get("layoutShifts"));
                    rTran = asList(diag.get("routeTransitions"));
                }
            } catch (Exception e) {
                LOG.warn("[ReactPerformanceSection] React data read failed: {}", e.getMessage());
            }
        }

        this.reactRenders      = rRend;
        this.loadingStates     = lStat;
        this.layoutShifts      = lShft;
        this.routeTransitions  = rTran;
    }

    // ── DiagnosticSection ─────────────────────────────────────────────────────

    @Override
    public String sectionTitle() {
        int total = apiTimings.size() + slowEventHandlers.size()
                + reactRenders.size() + loadingStates.size()
                + layoutShifts.size() + routeTransitions.size()
                + forcedReflows.size() + interactions.size();
        return "Frontend Performance — React (" + total + " entries)";
    }

    @Override
    public boolean hasData() {
        return super.hasData()
                || !reactRenders.isEmpty() || !loadingStates.isEmpty()
                || !layoutShifts.isEmpty() || !routeTransitions.isEmpty();
    }

    @Override
    public String renderHtml() {
        StringBuilder html = new StringBuilder();
        renderCommon(html);
        renderReactRenders(html);
        renderLoadingStates(html);
        renderLayoutShifts(html);
        renderRouteTransitions(html);
        return html.toString();
    }

    // ── Public accessors (used by RootCauseAttributionSection) ───────────────

    @Override
    public long longestComponentRenderMs() {
        return reactRenders.stream().mapToLong(m -> toLong(m.get("durationMs"))).max().orElse(0L);
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
    public Map<String, Object> getSlowestReactRender() {
        return reactRenders.stream()
                .max(Comparator.comparingLong(m -> toLong(m.get("durationMs"))))
                .orElse(null);
    }

    @Override
    public Map<String, Object> getSlowestOverlayEntry() {
        return loadingStates.stream()
                .max(Comparator.comparingLong(m -> toLong(m.get("durationMs"))))
                .orElse(null);
    }

    // ── React-specific sub-section renderers ──────────────────────────────────

    private void renderReactRenders(StringBuilder html) {
        openDetails(html, "React Component Renders (" + reactRenders.size() + ")");
        if (reactRenders.isEmpty()) {
            html.append("<p style='font-size:11px;color:#888;margin:4px 0;'>")
                .append("No ⦿ User Timing marks found. ")
                .append("React User Timing measures (⚛ Component render) are only present in ")
                .append("development or profiling builds, not in production bundles.")
                .append("</p>");
        } else {
            List<Map<String, Object>> sorted = reactRenders.stream()
                    .sorted(Comparator.comparingLong(m -> -toLong(m.get("durationMs"))))
                    .limit(20).toList();
            html.append(tableHeader("Component", "Phase", "Duration (ms)"));
            for (Map<String, Object> r : sorted) {
                long dur = toLong(r.get("durationMs"));
                addRow(html, esc(str(r.get("componentName"))),
                        esc(str(r.get("phase"))),
                        dur + " ms",
                        dur > 100 ? "#cc6600" : "#006600");
            }
            html.append("</table>");
        }
        closeDetails(html);
    }

    private void renderLoadingStates(StringBuilder html) {
        if (loadingStates.isEmpty()) return;
        openDetails(html, "Loading States — DOM-observed (" + loadingStates.size() + ")");
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
        boolean hasFrom = routeTransitions.stream().anyMatch(r -> !str(r.get("from")).isEmpty());
        if (hasFrom) {
            html.append(tableHeader("From", "To", "Duration (ms)"));
            for (Map<String, Object> r : routeTransitions) {
                long dur = toLong(r.get("durationMs"));
                addRow(html, esc(str(r.get("from"))), esc(str(r.get("to"))),
                        dur + " ms", "#555555");
            }
        } else {
            html.append(tableHeader("Duration (ms)"));
            for (Map<String, Object> r : routeTransitions) {
                addRow2(html, toLong(r.get("durationMs")) + " ms", "", "#555555");
            }
        }
        html.append("</table>");
        closeDetails(html);
    }
}
