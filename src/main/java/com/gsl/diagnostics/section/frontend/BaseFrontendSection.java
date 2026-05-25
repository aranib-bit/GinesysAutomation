package com.gsl.diagnostics.section.frontend;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Abstract base for all framework-specific frontend performance sections.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Read common data from {@code window.__gslExtDiag}: apiTimings, slowEventHandlers,
 *       forcedReflows, interactions.</li>
 *   <li>Read DOM node count via native {@code document.querySelectorAll('*').length}.</li>
 *   <li>Provide shared HTML rendering helpers used by all sub-classes.</li>
 *   <li>Render the four common sub-sections: API Timings, Slow Event Handlers,
 *       DOM Complexity, User Interactions, Forced Reflows.</li>
 * </ul>
 *
 * <p>Sub-classes call {@link #renderCommon(StringBuilder)} then add their own
 * framework-specific sub-sections.
 */
public abstract class BaseFrontendSection implements FrontendPerformanceSection {

    private static final Logger LOG = LogManager.getLogger(BaseFrontendSection.class);

    protected static final String TH = "padding:4px 10px;border:1px solid #ddd;text-align:left;";
    protected static final String TD = "padding:3px 10px;border:1px solid #eee;";

    // ── common captured data ──────────────────────────────────────────────────

    protected final List<Map<String, Object>> apiTimings;
    protected final List<Map<String, Object>> slowEventHandlers;
    protected final List<Map<String, Object>> forcedReflows;
    protected final List<Map<String, Object>> interactions;
    protected final long                      domNodeCount;
    protected final boolean                   captured;
    protected final String                    framework;

    @SuppressWarnings("unchecked")
    protected BaseFrontendSection(WebDriver driver) {
        String  fw    = "unknown";
        boolean ok    = false;
        long    nodes = -1L;

        List<Map<String, Object>> api   = Collections.emptyList();
        List<Map<String, Object>> evts  = Collections.emptyList();
        List<Map<String, Object>> rfls  = Collections.emptyList();
        List<Map<String, Object>> acts  = Collections.emptyList();

        if (driver != null) {
            try {
                JavascriptExecutor js = (JavascriptExecutor) driver;

                // ── Read window.__gslExtDiag (framework-injected state) ──────────
                Object raw = js.executeScript("return window.__gslExtDiag || null;");
                if (raw instanceof Map<?, ?> diag) {
                    Map<String, Object> d = (Map<String, Object>) diag;
                    fw   = str(d.get("framework"));
                    api  = asList(d.get("apiTimings"));
                    evts = asList(d.get("slowEventHandlers"));
                    rfls = asList(d.get("forcedReflows"));
                    acts = asList(d.get("interactions"));
                    ok   = true;
                }

                // ── Fallback: native Resource Timing for apiTimings when injected list empty ─
                if (api.isEmpty()) {
                    Object fallback = js.executeScript(
                            "return (performance.getEntriesByType('resource') || [])" +
                            ".filter(function(r){" +
                            "  return r.initiatorType==='xmlhttprequest'||r.initiatorType==='fetch';" +
                            "})" +
                            ".map(function(r){" +
                            "  return {url:r.name,durationMs:Math.round(r.duration),status:0};" +
                            "});");
                    if (fallback instanceof List<?> list && !list.isEmpty()) {
                        api = (List<Map<String, Object>>) fallback;
                        ok  = true;
                    }
                }

                // ── DOM node count (always available from native DOM API) ─────────
                Object nc = js.executeScript("return document.querySelectorAll('*').length;");
                if (nc instanceof Number n) nodes = n.longValue();

            } catch (Exception e) {
                LOG.warn("[BaseFrontendSection] Collection failed: {}", e.getMessage());
            }
        }

        // Fallback framework name from config
        if ("unknown".equals(fw) || fw.isBlank()) {
            try {
                String cfg = com.gsl.config.ConfigFactory.getConfig().applicationType();
                fw = (cfg != null && !cfg.isBlank()) ? cfg.toLowerCase().trim() : "extjs";
            } catch (Exception ignored) {
                fw = "extjs";
            }
        }

        this.framework         = fw;
        this.apiTimings        = api;
        this.slowEventHandlers = evts;
        this.forcedReflows     = rfls;
        this.interactions      = acts;
        this.domNodeCount      = nodes;
        this.captured          = ok;
    }

    // ── FrontendPerformanceSection interface — default implementations ────────

    @Override public String  getFramework()    { return framework; }
    @Override public boolean isCaptured()      { return captured; }

    @Override
    public long totalOverlayDurationMs()       { return 0L; }
    @Override
    public long longestComponentRenderMs()     { return 0L; }
    @Override
    public long totalLayoutTimeMs()            { return 0L; }
    @Override
    public double totalLayoutShiftScore()      { return 0.0; }

    @Override
    public List<Map<String, Object>> getApiTimings()       { return apiTimings; }
    @Override
    public List<Map<String, Object>> getLoadMaskEvents()   { return Collections.emptyList(); }
    @Override
    public List<Map<String, Object>> getComponentRenders() { return Collections.emptyList(); }
    @Override
    public Map<String, Object> getSlowestComponentRender() { return null; }
    @Override
    public Map<String, Object> getSlowestReactRender()     { return null; }
    @Override
    public Map<String, Object> getSlowestOverlayEntry()    { return null; }

    // ── DiagnosticSection ─────────────────────────────────────────────────────

    @Override
    public boolean hasData() {
        return captured
                || domNodeCount >= 0
                || !forcedReflows.isEmpty()
                || !interactions.isEmpty();
    }

    // ── Shared render entry-point ─────────────────────────────────────────────

    /** Renders the four common sub-sections into the given builder. */
    protected void renderCommon(StringBuilder html) {
        renderApiTimings(html);
        renderSlowEventHandlers(html);
        renderDomStats(html);
        renderUserInteractions(html);
        renderForcedReflows(html);
    }

    // ── Common sub-section renderers ──────────────────────────────────────────

    protected void renderApiTimings(StringBuilder html) {
        if (apiTimings.isEmpty()) return;
        List<Map<String, Object>> sorted = apiTimings.stream()
                .sorted(Comparator.comparingLong(m -> -toLong(m.get("durationMs"))))
                .limit(20).toList();
        openDetails(html, "API Timings (" + apiTimings.size() + ")");
        html.append(tableHeader("URL", "Duration (ms)", "HTTP Status"));
        for (Map<String, Object> a : sorted) {
            long dur  = toLong(a.get("durationMs"));
            long stat = toLong(a.get("status"));
            boolean warn = dur > 2000 || stat >= 400;
            String url = str(a.get("url"));
            String display = url.length() > 90 ? "…" + url.substring(url.length() - 89) : url;
            addRow(html, esc(display), dur + " ms", String.valueOf(stat),
                    warn ? "#cc6600" : "#006600");
        }
        html.append("</table>");
        closeDetails(html);
    }

    protected void renderSlowEventHandlers(StringBuilder html) {
        if (slowEventHandlers.isEmpty()) return;
        openDetails(html, "Slow Event Handlers (" + slowEventHandlers.size() + ")");
        html.append(tableHeader("Event", "Duration (ms)"));
        for (Map<String, Object> e : slowEventHandlers) {
            long dur = toLong(e.get("durationMs"));
            addRow2(html, esc(str(e.get("event"))), dur + " ms", "#cc6600");
        }
        html.append("</table>");
        closeDetails(html);
    }

    protected void renderDomStats(StringBuilder html) {
        if (domNodeCount < 0) return;
        boolean warn  = domNodeCount > 5000;
        String  color = warn ? "#cc6600" : "#006600";
        String  label = warn ? "⚠ DOM explosion (>5 000)" : "OK";
        openDetails(html, "DOM Complexity");
        html.append(tableHeader("Metric", "Value", "Status"));
        html.append("<tr>")
            .append("<td style='").append(TD).append("font-weight:bold;'>Total DOM Nodes</td>")
            .append("<td style='").append(TD).append("'>").append(domNodeCount).append("</td>")
            .append("<td style='").append(TD).append("color:").append(color).append(";'>")
            .append(label).append("</td>")
            .append("</tr>");
        html.append("</table>");
        closeDetails(html);
    }

    protected void renderUserInteractions(StringBuilder html) {
        if (interactions.isEmpty()) return;
        List<Map<String, Object>> recent = interactions.stream()
                .sorted(Comparator.comparingLong((Map<String, Object> m) -> toLong(m.get("timestamp"))).reversed())
                .limit(20).toList();
        openDetails(html, "User Interactions (" + interactions.size() + " clicks recorded)");
        html.append(tableHeader("Tag", "Classes", "Timestamp (ms)"));
        for (Map<String, Object> i : recent) {
            addRowPlain(html,
                    esc(str(i.get("tagName"))),
                    esc(str(i.get("className"))),
                    str(i.get("timestamp")));
        }
        html.append("</table>");
        closeDetails(html);
    }

    protected void renderForcedReflows(StringBuilder html) {
        if (forcedReflows.isEmpty()) return;
        long worst = forcedReflows.stream().mapToLong(m -> toLong(m.get("durationMs"))).max().orElse(0);
        openDetails(html, "Forced Reflows — getBoundingClientRect ("
                + forcedReflows.size() + " calls >5ms)");
        html.append("<p style='font-size:11px;margin:2px 0 4px;color:#555;'>")
            .append("Total calls: ").append(forcedReflows.size())
            .append(" | Slowest: ").append(worst).append(" ms</p>");
        html.append(tableHeader("Tag", "Duration (ms)", "Status"));
        for (Map<String, Object> r : forcedReflows) {
            long dur = toLong(r.get("durationMs"));
            addRow(html, esc(str(r.get("tagName"))), dur + " ms",
                    dur > 50 ? "⚠ Slow" : "OK",
                    dur > 50 ? "#cc6600" : "#006600");
        }
        html.append("</table>");
        closeDetails(html);
    }

    // ── HTML helpers ──────────────────────────────────────────────────────────

    protected static void openDetails(StringBuilder html, String title) {
        html.append("<details style='margin:4px 0 6px;border:1px solid #e0e0e0;border-radius:3px;'>")
            .append("<summary style='cursor:pointer;font-weight:bold;font-size:11px;")
            .append("padding:4px 8px;background:#f5f5f5;color:#333;'>")
            .append(esc(title))
            .append("</summary>")
            .append("<div style='padding:6px 8px;'>");
    }

    protected static void closeDetails(StringBuilder html) {
        html.append("</div></details>");
    }

    protected static String tableHeader(String... cols) {
        StringBuilder t = new StringBuilder(
                "<table style='border-collapse:collapse;font-size:12px;font-family:monospace;" +
                "width:100%;margin-top:4px;'>" +
                "<tr style='background:#f0f0f0;font-weight:bold;'>");
        for (String c : cols) {
            t.append("<th style='").append(TH).append("'>").append(esc(c)).append("</th>");
        }
        t.append("</tr>");
        return t.toString();
    }

    protected static void addRow(StringBuilder t,
                                  String c1, String c2, String status, String color) {
        t.append("<tr>")
         .append("<td style='").append(TD).append("font-weight:bold;'>").append(c1).append("</td>")
         .append("<td style='").append(TD).append("'>").append(c2).append("</td>")
         .append("<td style='").append(TD).append("color:").append(color).append(";'>")
         .append(status).append("</td>")
         .append("</tr>");
    }

    protected static void addRow2(StringBuilder t, String c1, String c2, String color) {
        t.append("<tr>")
         .append("<td style='").append(TD).append("'>").append(c1).append("</td>")
         .append("<td style='").append(TD).append("color:").append(color)
         .append(";font-weight:bold;'>").append(c2).append("</td>")
         .append("</tr>");
    }

    protected static void addRow4(StringBuilder t,
                                   String c1, String c2, String c3, String status, String color) {
        t.append("<tr>")
         .append(td(c1)).append(td(c2)).append(td(c3))
         .append("<td style='").append(TD).append("color:").append(color).append(";'>")
         .append(status).append("</td>")
         .append("</tr>");
    }

    protected static void addRow4Plain(StringBuilder t,
                                        String c1, String c2, String c3, String c4) {
        t.append("<tr>")
         .append(td(c1)).append(td(c2)).append(td(c3)).append(td(c4))
         .append("</tr>");
    }

    protected static void addRowPlain(StringBuilder t,
                                       String c1, String c2, String c3) {
        t.append("<tr>")
         .append(td(c1)).append(td(c2)).append(td(c3))
         .append("</tr>");
    }

    protected static String td(String val) {
        return "<td style='" + TD + "'>" + val + "</td>";
    }

    // ── Static label builders (used by RootCauseAttributionSection) ───────────

    public static String componentRenderLabel(Map<String, Object> entry) {
        if (entry == null) return "component.render()";
        String itemId = str(entry.get("itemId"));
        String xtype  = str(entry.get("xtype"));
        String name   = !itemId.isEmpty() ? itemId : (!xtype.isEmpty() ? xtype : "component");
        return name + ".render()";
    }

    public static String reactRenderLabel(Map<String, Object> entry) {
        if (entry == null) return "component.render()";
        String name = str(entry.get("componentName"));
        return (!name.isEmpty() ? name : "component") + ".render()";
    }

    public static String overlayLabel(Map<String, Object> entry, String fw) {
        if (entry == null) return "loading overlay";
        String component = str(entry.get("component"));
        String selector  = str(entry.get("selector"));
        if ("extjs".equals(fw)) {
            return (!component.isEmpty() ? component : "overlay") + " loadmask";
        }
        return (!selector.isEmpty() ? selector : "loading element") + " loading state";
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> asList(Object o) {
        if (o instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map) {
            return (List<Map<String, Object>>) list;
        }
        return Collections.emptyList();
    }

    protected static long toLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        return 0L;
    }

    protected static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    protected static String str(Object o)  { return o == null ? "" : o.toString(); }

    protected static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
