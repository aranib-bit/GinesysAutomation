package com.gsl.diagnostics.section.frontend.extjs;

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
 * ExtJS-specific frontend performance section.
 *
 * <p>Reads {@code window.__gslExtDiag} (populated by {@code ExtJsFrontendInjector}) and
 * renders the following sub-sections inside the Extent "Logs &amp; Diagnostics" node:
 * <ul>
 *   <li>API Timings (common)</li>
 *   <li>Slow Event Handlers (common)</li>
 *   <li>DOM Complexity (common)</li>
 *   <li>User Interactions (common)</li>
 *   <li>Forced Reflows (common)</li>
 *   <li>LoadMask / Overlay Timing (ExtJS)</li>
 *   <li>MessageBox Events (ExtJS)</li>
 *   <li>Component Render Timing (ExtJS)</li>
 *   <li>Layout Calculation Runs (ExtJS)</li>
 *   <li>Grid Render Timing (ExtJS)</li>
 * </ul>
 */
public final class ExtJsPerformanceSection extends BaseFrontendSection {

    private static final Logger LOG = LogManager.getLogger(ExtJsPerformanceSection.class);

    private final List<Map<String, Object>> loadMaskEvents;
    private final List<Map<String, Object>> messageBoxEvents;
    private final List<Map<String, Object>> componentRenders;
    private final List<Map<String, Object>> layoutRuns;
    private final List<Map<String, Object>> gridRenders;

    @SuppressWarnings("unchecked")
    public ExtJsPerformanceSection(WebDriver driver) {
        super(driver);

        List<Map<String, Object>> masks  = Collections.emptyList();
        List<Map<String, Object>> msgBx  = Collections.emptyList();
        List<Map<String, Object>> cRend  = Collections.emptyList();
        List<Map<String, Object>> lRuns  = Collections.emptyList();
        List<Map<String, Object>> gRends = Collections.emptyList();

        if (driver != null && captured) {
            try {
                JavascriptExecutor js = (JavascriptExecutor) driver;
                Object raw = js.executeScript("return window.__gslExtDiag || null;");
                if (raw instanceof Map<?, ?> d) {
                    Map<String, Object> diag = (Map<String, Object>) d;
                    masks  = asList(diag.get("loadMaskEvents"));
                    msgBx  = asList(diag.get("messageBoxEvents"));
                    cRend  = asList(diag.get("componentRenders"));
                    lRuns  = asList(diag.get("layoutRuns"));
                    gRends = asList(diag.get("gridRenders"));
                }
            } catch (Exception e) {
                LOG.warn("[ExtJsPerformanceSection] ExtJS data read failed: {}", e.getMessage());
            }
        }

        this.loadMaskEvents  = masks;
        this.messageBoxEvents = msgBx;
        this.componentRenders = cRend;
        this.layoutRuns       = lRuns;
        this.gridRenders      = gRends;
    }

    // ── DiagnosticSection ─────────────────────────────────────────────────────

    @Override
    public String sectionTitle() {
        int total = apiTimings.size() + slowEventHandlers.size()
                + loadMaskEvents.size() + messageBoxEvents.size()
                + componentRenders.size() + layoutRuns.size()
                + gridRenders.size() + forcedReflows.size() + interactions.size();
        return "Frontend Performance — ExtJS (" + total + " entries)";
    }

    @Override
    public boolean hasData() {
        return super.hasData()
                || !loadMaskEvents.isEmpty() || !messageBoxEvents.isEmpty()
                || !componentRenders.isEmpty() || !layoutRuns.isEmpty()
                || !gridRenders.isEmpty();
    }

    @Override
    public String renderHtml() {
        StringBuilder html = new StringBuilder();
        renderCommon(html);
        renderLoadMask(html);
        renderMessageBox(html);
        renderComponentRenders(html);
        renderLayoutRuns(html);
        renderGridRenders(html);
        return html.toString();
    }

    // ── Public accessors (used by RootCauseAttributionSection) ───────────────

    @Override
    public List<Map<String, Object>> getLoadMaskEvents()   { return loadMaskEvents; }
    @Override
    public List<Map<String, Object>> getComponentRenders() { return componentRenders; }

    @Override
    public long totalOverlayDurationMs() {
        return loadMaskEvents.stream().mapToLong(m -> toLong(m.get("durationMs"))).sum();
    }

    @Override
    public long longestComponentRenderMs() {
        return componentRenders.stream().mapToLong(m -> toLong(m.get("durationMs"))).max().orElse(0L);
    }

    @Override
    public long totalLayoutTimeMs() {
        return layoutRuns.stream().mapToLong(m -> toLong(m.get("durationMs"))).sum();
    }

    @Override
    public Map<String, Object> getSlowestComponentRender() {
        return componentRenders.stream()
                .max(Comparator.comparingLong(m -> toLong(m.get("durationMs"))))
                .orElse(null);
    }

    @Override
    public Map<String, Object> getSlowestOverlayEntry() {
        return loadMaskEvents.stream()
                .max(Comparator.comparingLong(m -> toLong(m.get("durationMs"))))
                .orElse(null);
    }

    // ── ExtJS-specific sub-section renderers ──────────────────────────────────

    private void renderLoadMask(StringBuilder html) {
        if (loadMaskEvents.isEmpty()) return;
        openDetails(html, "LoadMask / Overlay Timing (" + loadMaskEvents.size() + ")");
        html.append(tableHeader("Component", "Start (ms)", "Duration (ms)", "Status"));
        for (Map<String, Object> m : loadMaskEvents) {
            long dur = toLong(m.get("durationMs"));
            String status; String color;
            if      (dur > 3000) { status = "⚠ Critical"; color = "#cc0000"; }
            else if (dur > 1000) { status = "⚠ Warning";  color = "#cc6600"; }
            else                 { status = "OK";          color = "#006600"; }
            addRow4(html,
                    esc(str(m.get("component"))),
                    str(m.get("timestamp")) + " ms",
                    dur + " ms",
                    status, color);
        }
        html.append("</table>");
        closeDetails(html);
    }

    private void renderMessageBox(StringBuilder html) {
        if (messageBoxEvents.isEmpty()) return;
        openDetails(html, "MessageBox Events (" + messageBoxEvents.size() + ")");
        html.append(tableHeader("Title", "Message", "Progress", "Timestamp (ms)"));
        for (Map<String, Object> m : messageBoxEvents) {
            addRow4Plain(html,
                    esc(str(m.get("title"))),
                    esc(str(m.get("msg"))),
                    Boolean.TRUE.equals(m.get("progress")) ? "yes" : "no",
                    str(m.get("timestamp")) + " ms");
        }
        html.append("</table>");
        closeDetails(html);
    }

    private void renderComponentRenders(StringBuilder html) {
        if (componentRenders.isEmpty()) return;
        List<Map<String, Object>> sorted = componentRenders.stream()
                .sorted(Comparator.comparingLong(m -> -toLong(m.get("durationMs"))))
                .limit(20).toList();
        openDetails(html, "Component Render Timing — top " + sorted.size() + " slowest");
        html.append(tableHeader("xtype", "itemId", "Duration (ms)"));
        for (Map<String, Object> c : sorted) {
            long dur = toLong(c.get("durationMs"));
            addRow(html, esc(str(c.get("xtype"))), esc(str(c.get("itemId"))),
                    dur + " ms", dur > 500 ? "#cc0000" : "#cc6600");
        }
        html.append("</table>");
        closeDetails(html);
    }

    private void renderLayoutRuns(StringBuilder html) {
        if (layoutRuns.isEmpty()) return;
        long total = layoutRuns.stream().mapToLong(m -> toLong(m.get("durationMs"))).sum();
        long worst = layoutRuns.stream().mapToLong(m -> toLong(m.get("durationMs"))).max().orElse(0);
        openDetails(html, "Layout Calculation Runs ("
                + layoutRuns.size() + " runs, total " + total + " ms)");
        html.append("<p style='font-size:11px;margin:2px 0 4px;color:#555;'>")
            .append("Total: ").append(total)
            .append(" ms | Runs: ").append(layoutRuns.size())
            .append(" | Slowest: ").append(worst).append(" ms</p>");
        html.append(tableHeader("Run #", "Duration (ms)", "Status"));
        int i = 1;
        for (Map<String, Object> r : layoutRuns) {
            long dur = toLong(r.get("durationMs"));
            addRow(html, String.valueOf(i++), dur + " ms",
                    dur > 500 ? "⚠ Slow" : "OK",
                    dur > 500 ? "#cc6600" : "#006600");
        }
        html.append("</table>");
        closeDetails(html);
    }

    private void renderGridRenders(StringBuilder html) {
        if (gridRenders.isEmpty()) return;
        List<Map<String, Object>> sorted = gridRenders.stream()
                .sorted(Comparator.comparingLong(m -> -toLong(m.get("durationMs"))))
                .toList();
        openDetails(html, "Grid Render Timing (" + gridRenders.size() + " grids)");
        html.append(tableHeader("Grid ID / itemId", "Rows", "Columns", "Duration (ms)", "Status"));
        for (Map<String, Object> g : sorted) {
            long dur = toLong(g.get("durationMs"));
            String id = str(g.get("itemId")).isBlank() ? str(g.get("gridId")) : str(g.get("itemId"));
            String status; String color;
            if      (dur > 300) { status = "⚠ Slow"; color = "#cc0000"; }
            else if (dur > 100) { status = "⚠ Warn";  color = "#cc6600"; }
            else                { status = "OK";       color = "#006600"; }
            html.append("<tr>")
                .append("<td style='").append(TD).append("font-weight:bold;'>").append(esc(id)).append("</td>")
                .append("<td style='").append(TD).append("'>").append(str(g.get("rows"))).append("</td>")
                .append("<td style='").append(TD).append("'>").append(str(g.get("columns"))).append("</td>")
                .append("<td style='").append(TD).append("'>").append(dur).append(" ms</td>")
                .append("<td style='").append(TD).append("color:").append(color).append(";'>")
                .append(status).append("</td>")
                .append("</tr>");
        }
        html.append("</table>");
        closeDetails(html);
    }
}
