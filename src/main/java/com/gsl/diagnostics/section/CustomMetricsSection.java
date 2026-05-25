package com.gsl.diagnostics.section;

import com.gsl.config.FrameworkConfig;
import com.gsl.diagnostics.DiagnosticSection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * DiagnosticSection that collects browser-level Custom Metrics via the
 * web.dev Performance Observer APIs and renders them as a multi-category
 * HTML block inside the "Logs &amp; Diagnostics" child node.
 *
 * <p>Categories rendered (each as a collapsible {@code <details>} block):
 * <ul>
 *   <li>User Timing Measures — dev-defined spans via performance.mark/measure</li>
 *   <li>Long Tasks / Animation Frames — JS blocking main thread &gt;50 ms</li>
 *   <li>Paint Timing — FP and FCP timestamps</li>
 *   <li>Navigation Timing — domInteractive, domContentLoaded, loadEventEnd</li>
 *   <li>Slow Resources — resources exceeding the configured threshold</li>
 *   <li>Server Timing — server-reported metrics from HTTP response headers</li>
 *   <li>Event Timing — top slow input events</li>
 * </ul>
 *
 * <p>Requires {@link com.gsl.browser.CustomMetricsObserverInjector#inject} to have
 * been called after browser launch so that Long Tasks / Animation Frames / Event
 * Timing are already buffered in {@code window.__gslMetrics}.
 */
public final class CustomMetricsSection implements DiagnosticSection {

    private static final Logger LOG = LogManager.getLogger(CustomMetricsSection.class);

    private static final String TH = "padding:4px 10px;border:1px solid #ddd;text-align:left;";
    private static final String TD = "padding:3px 10px;border:1px solid #eee;";

    private final List<Map<String, Object>> userMeasures;
    private final List<Map<String, Object>> longTasks;
    private final List<Map<String, Object>> longAnimFrames;
    private final List<Map<String, Object>> paintEntries;
    private final Map<String, Object>       navTiming;
    private final List<Map<String, Object>> slowResources;
    private final List<Map<String, Object>> serverTimings;
    private final List<Map<String, Object>> eventTimings;
    private final Map<String, Object>       memoryStats;
    private final long                      userTimingWarnMs;
    private final boolean                   serverTimingEnabled;
    private final boolean                   longAnimFrameEnabled;
    private final boolean                   captured;

    @SuppressWarnings("unchecked")
    public CustomMetricsSection(WebDriver driver, FrameworkConfig config) {
        this.userTimingWarnMs     = config.customMetricsUserTimingWarnMs();
        this.serverTimingEnabled  = config.customMetricsServerTimingEnabled();
        this.longAnimFrameEnabled = config.customMetricsLongAnimationFrameEnabled();
        long slowResourceMs       = config.customMetricsSlowResourceThresholdMs();

        List<Map<String, Object>> measures   = Collections.emptyList();
        List<Map<String, Object>> lTasks     = Collections.emptyList();
        List<Map<String, Object>> lFrames    = Collections.emptyList();
        List<Map<String, Object>> paints     = Collections.emptyList();
        Map<String, Object>       nav        = null;
        List<Map<String, Object>> slowRes    = Collections.emptyList();
        List<Map<String, Object>> srvTiming  = Collections.emptyList();
        List<Map<String, Object>> evtTimings = Collections.emptyList();
        Map<String, Object> mem = null;
        boolean ok = false;

        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // ── User Timing measures ───────────────────────────────────────────
            Object rawMeasures = js.executeScript(
                    "return performance.getEntriesByType('measure').map(function(e){" +
                    "  return {name:e.name, duration:Math.round(e.duration)};" +
                    "});");
            if (rawMeasures instanceof List) measures = (List<Map<String, Object>>) rawMeasures;

            // ── Paint timing (FP, FCP) ─────────────────────────────────────────
            Object rawPaint = js.executeScript(
                    "return performance.getEntriesByType('paint').map(function(e){" +
                    "  return {name:e.name, startTime:Math.round(e.startTime)};" +
                    "});");
            if (rawPaint instanceof List) paints = (List<Map<String, Object>>) rawPaint;

            // ── Navigation Timing v2 ───────────────────────────────────────────
            Object rawNav = js.executeScript(
                    "var e = performance.getEntriesByType('navigation')[0];" +
                    "if (!e) return null;" +
                    "var st = [];" +
                    "if (e.serverTiming) {" +
                    "  e.serverTiming.forEach(function(s){" +
                    "    st.push({name:s.name, duration:Math.round(s.duration), description:s.description||''});" +
                    "  });" +
                    "}" +
                    "return {" +
                    "  type:                  e.type," +
                    "  domInteractive:        Math.round(e.domInteractive)," +
                    "  domContentLoaded:      Math.round(e.domContentLoadedEventEnd)," +
                    "  loadEventEnd:          Math.round(e.loadEventEnd)," +
                    "  transferSize:          e.transferSize || 0," +
                    "  serverTiming:          st" +
                    "};");
            if (rawNav instanceof Map) {
                nav = (Map<String, Object>) rawNav;
                Object rawSt = nav.get("serverTiming");
                if (rawSt instanceof List) srvTiming = (List<Map<String, Object>>) rawSt;
            }

            // ── Slow resources ─────────────────────────────────────────────────
            Object rawRes = js.executeScript(
                    "var t=" + slowResourceMs + ";" +
                    "return performance.getEntriesByType('resource')" +
                    "  .filter(function(r){ return r.duration > t; })" +
                    "  .slice(0,20)" +
                    "  .map(function(r){" +
                    "    return {name:r.name, initiatorType:r.initiatorType," +
                    "            duration:Math.round(r.duration)};" +
                    "  });");
            if (rawRes instanceof List) slowRes = (List<Map<String, Object>>) rawRes;

            // ── Observer-buffered metrics (injected at test start) ─────────────
            Object rawStored = js.executeScript("return window.__gslMetrics || {};");
            if (rawStored instanceof Map) {
                Map<String, Object> stored = (Map<String, Object>) rawStored;
                Object lt = stored.get("longTasks");
                if (lt instanceof List) lTasks = (List<Map<String, Object>>) lt;
                Object laf = stored.get("longAnimationFrames");
                if (laf instanceof List) lFrames = (List<Map<String, Object>>) laf;
                Object et = stored.get("eventTimings");
                if (et instanceof List) evtTimings = ((List<Map<String, Object>>) et)
                        .stream()
                        .filter(m -> toLong(m.get("duration")) > 50)
                        .toList();
            }

            // ── JS Heap memory (Chrome only) ──────────────────────────────────
            Object rawMem = js.executeScript(
                    "if (!performance.memory) return null;" +
                    "return {" +
                    "  usedJSHeapSize:  performance.memory.usedJSHeapSize," +
                    "  totalJSHeapSize: performance.memory.totalJSHeapSize," +
                    "  jsHeapSizeLimit: performance.memory.jsHeapSizeLimit" +
                    "};");
            if (rawMem instanceof Map) mem = (Map<String, Object>) rawMem;

            ok = true;
        } catch (Exception e) {
            LOG.warn("[CustomMetricsSection] Collection failed: {}", e.getMessage());
        }

        this.userMeasures   = measures;
        this.longTasks      = lTasks;
        this.longAnimFrames = lFrames;
        this.paintEntries   = paints;
        this.navTiming      = nav;
        this.slowResources  = slowRes;
        this.serverTimings  = srvTiming;
        this.eventTimings   = evtTimings;
        this.memoryStats    = mem;
        this.captured       = ok;
    }

    @Override
    public String sectionTitle() {
        int total = userMeasures.size() + longTasks.size() + longAnimFrames.size()
                + paintEntries.size() + slowResources.size() + serverTimings.size()
                + eventTimings.size() + (navTiming != null ? 1 : 0);
        return "Custom Metrics (" + total + " entries)";
    }

    @Override
    public boolean hasData() {
        if (!captured) return false;
        return !userMeasures.isEmpty() || !longTasks.isEmpty() || !longAnimFrames.isEmpty()
                || !paintEntries.isEmpty() || navTiming != null || !slowResources.isEmpty()
                || !serverTimings.isEmpty() || !eventTimings.isEmpty() || memoryStats != null;
    }

    @Override
    public String renderHtml() {
        StringBuilder html = new StringBuilder();

        renderUserTiming(html);
        renderLongTasksAndFrames(html);
        renderPaintTiming(html);
        renderNavigationTiming(html);
        renderSlowResources(html);
        if (serverTimingEnabled) renderServerTiming(html);
        renderEventTiming(html);
        renderMemoryStats(html);

        return html.toString();
    }

    // ── Sub-section renderers ──────────────────────────────────────────────────

    private void renderUserTiming(StringBuilder html) {
        if (userMeasures.isEmpty()) return;
        openDetails(html, "User Timing Measures (" + userMeasures.size() + ")");
        html.append(tableHeader("Name", "Duration (ms)", "Status"));
        for (Map<String, Object> m : userMeasures) {
            long dur   = toLong(m.get("duration"));
            boolean hi = dur > userTimingWarnMs;
            addRow(html, esc(str(m.get("name"))), dur + " ms",
                    hi ? "&#9888; Slow" : "OK",
                    hi ? "#cc6600" : "#006600");
        }
        html.append("</table>");
        closeDetails(html);
    }

    private void renderLongTasksAndFrames(StringBuilder html) {
        boolean hasTasks  = !longTasks.isEmpty();
        boolean hasFrames = longAnimFrameEnabled && !longAnimFrames.isEmpty();
        if (!hasTasks && !hasFrames) return;

        int total = longTasks.size() + (hasFrames ? longAnimFrames.size() : 0);
        openDetails(html, "Long Tasks / Animation Frames (" + total + ")");

        if (hasTasks) {
            boolean hasAttribution = longTasks.stream()
                    .anyMatch(t -> {
                        Object s = t.get("scripts");
                        return s instanceof List<?> l && !l.isEmpty();
                    });
            html.append("<p style='font-size:11px;font-weight:bold;margin:4px 0 2px;color:#cc0000;'>")
                .append("Long Tasks (JS blocked main thread &gt;50 ms) — ")
                .append(longTasks.size()).append(" detected</p>");
            if (hasAttribution) {
                html.append(tableHeader("Name", "Start (ms)", "Duration (ms)", "Scripts (sourceURL :: fn)"));
                for (Map<String, Object> t : longTasks) {
                    String scripts = buildScriptAttribution(t.get("scripts"));
                    addRow4(html,
                            esc(str(t.get("name"))),
                            str(t.get("startTime")),
                            str(t.get("duration")) + " ms",
                            scripts.isEmpty() ? "—" : esc(scripts),
                            "#cc0000");
                }
            } else {
                html.append(tableHeader("Name", "Start (ms)", "Duration (ms)"));
                for (Map<String, Object> t : longTasks) {
                    addRow(html,
                            esc(str(t.get("name"))),
                            str(t.get("startTime")),
                            str(t.get("duration")) + " ms",
                            "#cc0000");
                }
            }
            html.append("</table>");
        }

        if (hasFrames) {
            html.append("<p style='font-size:11px;font-weight:bold;margin:8px 0 2px;color:#cc6600;'>")
                .append("Long Animation Frames (&gt;50 ms) — ")
                .append(longAnimFrames.size()).append(" detected</p>");
            html.append(tableHeader("Start (ms)", "Duration (ms)"));
            for (Map<String, Object> f : longAnimFrames) {
                addRow2(html, str(f.get("startTime")), str(f.get("duration")) + " ms", "#cc6600");
            }
            html.append("</table>");
        }

        closeDetails(html);
    }

    private void renderPaintTiming(StringBuilder html) {
        if (paintEntries.isEmpty()) return;
        openDetails(html, "Paint Timing (" + paintEntries.size() + ")");
        html.append(tableHeader("Paint Event", "Start Time (ms)", "Status"));
        for (Map<String, Object> p : paintEntries) {
            long t = toLong(p.get("startTime"));
            addRow(html, esc(str(p.get("name"))), t + " ms",
                    t > 3000 ? "&#9888; Slow" : "OK",
                    t > 3000 ? "#cc6600" : "#006600");
        }
        html.append("</table>");
        closeDetails(html);
    }

    private void renderNavigationTiming(StringBuilder html) {
        if (navTiming == null) return;
        openDetails(html, "Navigation Timing");
        html.append(tableHeader("Metric", "Value", "Status"));
        addNavRow(html, "Navigation Type",  str(navTiming.get("type")),     0);
        addNavRow(html, "DOM Interactive",  str(navTiming.get("domInteractive"))  + " ms", 3000);
        addNavRow(html, "DOM Content Loaded", str(navTiming.get("domContentLoaded")) + " ms", 3000);
        addNavRow(html, "Load Event End",   str(navTiming.get("loadEventEnd")) + " ms", 5000);
        long ts = toLong(navTiming.get("transferSize"));
        if (ts > 0) {
            addRow(html, "Transfer Size", ts + " bytes", ts > 1_000_000 ? "&#9888; Large" : "OK",
                    ts > 1_000_000 ? "#cc6600" : "#006600");
        }
        html.append("</table>");
        closeDetails(html);
    }

    private void renderSlowResources(StringBuilder html) {
        if (slowResources.isEmpty()) return;
        openDetails(html, "Slow Resources (" + slowResources.size() + ")");
        html.append(tableHeader("URL", "Type", "Duration (ms)"));
        for (Map<String, Object> r : slowResources) {
            String name = str(r.get("name"));
            // Truncate long URLs for readability
            String display = name.length() > 80 ? "…" + name.substring(name.length() - 79) : name;
            long dur = toLong(r.get("duration"));
            addRow(html, esc(display), esc(str(r.get("initiatorType"))), dur + " ms", "#cc6600");
        }
        html.append("</table>");
        closeDetails(html);
    }

    private void renderServerTiming(StringBuilder html) {
        if (serverTimings.isEmpty()) return;
        openDetails(html, "Server Timing (" + serverTimings.size() + ")");
        html.append(tableHeader("Name", "Duration (ms)", "Description"));
        for (Map<String, Object> s : serverTimings) {
            addRowPlain(html,
                    esc(str(s.get("name"))),
                    str(s.get("duration")),
                    esc(str(s.get("description"))));
        }
        html.append("</table>");
        closeDetails(html);
    }

    private void renderEventTiming(StringBuilder html) {
        if (eventTimings.isEmpty()) return;
        openDetails(html, "Slow Event Timings (" + eventTimings.size() + ")");
        html.append(tableHeader("Event", "Input Delay (ms)", "Total Duration (ms)"));
        for (Map<String, Object> e : eventTimings) {
            long start = toLong(e.get("startTime"));
            long procStart = toLong(e.get("processingStart"));
            long inputDelay = procStart - start;
            long dur = toLong(e.get("duration"));
            boolean warn = inputDelay > 100;
            addRow(html, esc(str(e.get("name"))),
                    inputDelay + " ms",
                    dur + " ms",
                    warn ? "#cc0000" : "#cc6600");
        }
        html.append("</table>");
        closeDetails(html);
    }

    private void renderMemoryStats(StringBuilder html) {
        if (memoryStats == null) return;
        long used  = toLong(memoryStats.get("usedJSHeapSize"));
        long total = toLong(memoryStats.get("totalJSHeapSize"));
        long limit = toLong(memoryStats.get("jsHeapSizeLimit"));
        openDetails(html, "Memory Monitoring — JS Heap (Chrome only)");
        html.append(tableHeader("Metric", "Value", "Status"));
        long usedMb = used / (1024 * 1024);
        addRow(html, "Used JS Heap", usedMb + " MB",
                usedMb > 500 ? "&#9888; High (&gt;500 MB)" : "OK",
                usedMb > 500 ? "#cc6600" : "#006600");
        addRow(html, "Total JS Heap", (total / (1024 * 1024)) + " MB", "—", "#555");
        addRow(html, "Heap Limit",    (limit / (1024 * 1024)) + " MB", "—", "#555");
        html.append("</table>");
        closeDetails(html);
    }

    // ── HTML helpers ───────────────────────────────────────────────────────────

    private static void openDetails(StringBuilder html, String title) {
        html.append("<details style='margin:4px 0 6px;border:1px solid #e0e0e0;border-radius:3px;'>")
            .append("<summary style='cursor:pointer;font-weight:bold;font-size:11px;")
            .append("padding:4px 8px;background:#f5f5f5;color:#333;'>")
            .append(esc(title))
            .append("</summary>")
            .append("<div style='padding:6px 8px;'>");
    }

    private static void closeDetails(StringBuilder html) {
        html.append("</div></details>");
    }

    private static String tableHeader(String... cols) {
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

    private static void addRow(StringBuilder t, String c1, String c2, String status, String color) {
        t.append("<tr>")
         .append("<td style='").append(TD).append("font-weight:bold;'>").append(c1).append("</td>")
         .append("<td style='").append(TD).append("'>").append(c2).append("</td>")
         .append("<td style='").append(TD).append("color:").append(color).append(";'>").append(status).append("</td>")
         .append("</tr>");
    }

    private static void addRow2(StringBuilder t, String c1, String c2, String color) {
        t.append("<tr>")
         .append("<td style='").append(TD).append("'>").append(c1).append("</td>")
         .append("<td style='").append(TD).append("color:").append(color).append(";font-weight:bold;'>").append(c2).append("</td>")
         .append("</tr>");
    }

    private static void addRowPlain(StringBuilder t, String c1, String c2, String c3) {
        t.append("<tr>")
         .append("<td style='").append(TD).append("font-weight:bold;'>").append(c1).append("</td>")
         .append("<td style='").append(TD).append("'>").append(c2).append("</td>")
         .append("<td style='").append(TD).append("color:#555;'>").append(c3).append("</td>")
         .append("</tr>");
    }

    private void addNavRow(StringBuilder t, String label, String value, long warnAboveMs) {
        long v = 0;
        try { v = Long.parseLong(value.replace(" ms", "").trim()); } catch (Exception ignored) {}
        boolean warn = warnAboveMs > 0 && v > warnAboveMs;
        addRow(t, esc(label), esc(value), warn ? "&#9888; Slow" : "OK",
                warn ? "#cc6600" : "#006600");
    }

    private static long toLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        return 0L;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ── HTML helpers ── additional ─────────────────────────────────────────────

    private static void addRow4(StringBuilder t,
                                 String c1, String c2, String c3, String c4, String color) {
        t.append("<tr>")
         .append("<td style='").append(TD).append("font-weight:bold;'>").append(c1).append("</td>")
         .append("<td style='").append(TD).append("'>").append(c2).append("</td>")
         .append("<td style='").append(TD).append("color:").append(color)
         .append(";'>").append(c3).append("</td>")
         .append("<td style='").append(TD).append("font-size:10px;color:#555;'>").append(c4).append("</td>")
         .append("</tr>");
    }

    @SuppressWarnings("unchecked")
    private static String buildScriptAttribution(Object scripts) {
        if (!(scripts instanceof List<?> list) || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) continue;
            String url = str(((Map<String, Object>) m).get("sourceURL"));
            String fn  = str(((Map<String, Object>) m).get("sourceFn"));
            if (url.isEmpty() && fn.isEmpty()) continue;
            // Show only the filename portion of the URL for readability
            String shortUrl = url.contains("/") ? url.substring(url.lastIndexOf('/') + 1) : url;
            if (!sb.isEmpty()) sb.append("; ");
            sb.append(shortUrl);
            if (!fn.isEmpty()) sb.append(" :: ").append(fn);
        }
        return sb.toString();
    }

    // ── Public accessors used by FailureBundleOrchestrator ────────────────────

    /** Total count of long tasks detected (Long Tasks API). Used for auto-fail logic. */
    public int longTaskCount() {
        return longTasks.size();
    }

    /** Total duration of all long tasks combined (ms). Used by RootCauseAttributionSection. */
    public long longTaskTotalMs() {
        return longTasks.stream().mapToLong(t -> toLong(t.get("duration"))).sum();
    }

    /**
     * First Contentful Paint timestamp (ms). Used as the total delay baseline in
     * RootCauseAttributionSection. Returns 0 when paint timing is unavailable.
     */
    public long firstContentfulPaintMs() {
        return paintEntries.stream()
                .filter(p -> "first-contentful-paint".equals(p.get("name")))
                .mapToLong(p -> toLong(p.get("startTime")))
                .findFirst()
                .orElse(0L);
    }
}
