package com.gsl.diagnostics.section;

import com.gsl.diagnostics.DiagnosticSection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.util.Map;

/**
 * DiagnosticSection that captures browser performance timing metrics at failure time.
 * Metrics: domReady, pageLoad, xhrPending count, networkIdle flag, JS heap (Chrome only).
 */
public final class PerformanceTimingSection implements DiagnosticSection {

    private static final Logger LOG = LogManager.getLogger(PerformanceTimingSection.class);
    private static final long HEAP_WARN_BYTES = 100L * 1024 * 1024; // 100 MB

    private final long    domReadyMs;
    private final long    pageLoadMs;
    private final long    xhrPending;
    private final boolean networkIdle;
    private final long    heapUsedBytes;
    private final long    heapTotalBytes;
    private final boolean captured;

    public PerformanceTimingSection(WebDriver driver) {
        long domR = 0, load = 0, pending = 0, heapUsed = 0, heapTotal = 0;
        boolean idle = true, ok = false;
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) js.executeScript("""
                var t   = window.performance.timing;
                var mem = window.performance.memory || {};
                var pend = 0;
                try {
                    pend = window.performance.getEntriesByType('resource')
                        .filter(function(r){ return r.responseEnd === 0; }).length;
                } catch(e) {}
                return {
                    domReady:   t.domContentLoadedEventEnd - t.navigationStart,
                    pageLoad:   t.loadEventEnd - t.navigationStart,
                    xhrPending: pend,
                    networkIdle: pend === 0,
                    heapUsed:   mem.usedJSHeapSize  || 0,
                    heapTotal:  mem.totalJSHeapSize || 0
                };
                """);
            if (data != null) {
                domR     = toLong(data.get("domReady"));
                load     = toLong(data.get("pageLoad"));
                pending  = toLong(data.get("xhrPending"));
                idle     = Boolean.TRUE.equals(data.get("networkIdle"));
                heapUsed = toLong(data.get("heapUsed"));
                heapTotal= toLong(data.get("heapTotal"));
                ok = true;
            }
        } catch (Exception e) {
            LOG.warn("[PerformanceTimingSection] Could not capture timing: {}", e.getMessage());
        }
        this.domReadyMs    = domR;
        this.pageLoadMs    = load;
        this.xhrPending    = pending;
        this.networkIdle   = idle;
        this.heapUsedBytes = heapUsed;
        this.heapTotalBytes= heapTotal;
        this.captured      = ok;
    }

    @Override
    public String sectionTitle() { return "Performance Timing"; }

    @Override
    public boolean hasData()     { return captured && domReadyMs > 0; }

    @Override
    public String renderHtml() {
        StringBuilder html = new StringBuilder();
        html.append("<table style='border-collapse:collapse;font-size:12px;font-family:monospace;width:auto;'>")
            .append("<tr style='background:#f0f0f0;font-weight:bold;'>")
            .append("<th style='").append(TH).append("'>Metric</th>")
            .append("<th style='").append(TH).append("'>Value</th>")
            .append("<th style='").append(TH).append("'>Status</th>")
            .append("</tr>");

        addRow(html, "DOM Ready",      domReadyMs + " ms",              "OK",  "#006600");
        addRow(html, "Page Load",      pageLoadMs + " ms",              "OK",  "#006600");

        String pendingStatus = xhrPending > 0 ? "&#9888; " + xhrPending + " pending" : "OK";
        String pendingColor  = xhrPending > 0 ? "#cc6600" : "#006600";
        addRow(html, "XHR Pending",    String.valueOf(xhrPending),       pendingStatus, pendingColor);

        String idleStatus = networkIdle ? "OK" : "&#9888; Not idle";
        String idleColor  = networkIdle ? "#006600" : "#cc6600";
        addRow(html, "Network Idle",   networkIdle ? "true" : "false",  idleStatus, idleColor);

        if (heapUsedBytes > 0) {
            long usedMb  = heapUsedBytes  / (1024 * 1024);
            long totalMb = heapTotalBytes / (1024 * 1024);
            String heapStatus = heapUsedBytes > HEAP_WARN_BYTES ? "&#9888; High" : "OK";
            String heapColor  = heapUsedBytes > HEAP_WARN_BYTES ? "#cc6600" : "#006600";
            addRow(html, "JS Heap Used",  usedMb  + " MB", heapStatus, heapColor);
            addRow(html, "JS Heap Total", totalMb + " MB", "OK",       "#006600");
        } else {
            addRow(html, "JS Heap", "Not available (non-Chrome)", "N/A", "#888888");
        }

        html.append("</table>");
        return html.toString();
    }

    private static void addRow(StringBuilder t, String metric, String value, String status, String statusColor) {
        t.append("<tr>")
         .append("<td style='").append(TD).append("font-weight:bold;'>").append(esc(metric)).append("</td>")
         .append("<td style='").append(TD).append("'>").append(esc(value)).append("</td>")
         .append("<td style='").append(TD).append("color:").append(statusColor).append(";'>").append(status).append("</td>")
         .append("</tr>");
    }

    private static final String TH = "padding:4px 10px;border:1px solid #ddd;text-align:left;";
    private static final String TD = "padding:3px 10px;border:1px solid #eee;";

    private static long toLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        return 0L;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
