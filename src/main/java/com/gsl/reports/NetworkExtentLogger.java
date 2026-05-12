package com.gsl.reports;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.markuputils.Markup;
import com.gsl.config.ConfigFactory;
import com.gsl.network.NetworkLogRecord;
import com.gsl.network.NetworkLogType;

import java.util.List;
import java.util.Map;

/**
 * Renders captured network traffic as a collapsible table in ExtentReport.
 *
 * Layout per row:
 *   Status | Method | URL | Duration
 *   ↳ [Request Headers]  (collapsible, if network.capture.headers=true)
 *   ↳ [Response Headers] (collapsible, if network.capture.headers=true)
 *   ↳ [Request Body]     (collapsible, if captured)
 *   ↳ [Response Body]    (collapsible, if captured)
 *
 * Status colour: 2xx=green, 3xx=blue, 4xx=orange, 5xx/0=red.
 */
public final class NetworkExtentLogger {

    private NetworkExtentLogger() {}

    public static void logNetworkLogs(List<NetworkLogRecord> logs, String protocol) {
        if (logs == null || logs.isEmpty()) return;
        ExtentTest test = ExtentManager.getExtentTest();
        if (test == null) return;

        long failed = logs.stream().filter(r -> r.type() == NetworkLogType.FAILED || r.status() >= 400).count();

        StringBuilder wrapper = new StringBuilder();
        wrapper.append("<details><summary style='cursor:pointer;font-weight:bold;'>")
               .append("Network Traffic [").append(protocol).append("] — ")
               .append(logs.size()).append(" requests");
        if (failed > 0) {
            wrapper.append(" <span style='color:red;'>(").append(failed).append(" failed)</span>");
        }
        wrapper.append("</summary><div style='margin-top:6px;overflow-x:auto;'>")
               .append(buildTable(logs))
               .append("</div></details>");

        String html = wrapper.toString();
        test.info((Markup) () -> html);
    }

    // ── Table builder ─────────────────────────────────────────────────────────

    /** Returns the rendered HTML table — used by NetworkLogSection in DiagnosticReport. */
    public static String renderTable(List<NetworkLogRecord> logs) {
        return buildTable(logs);
    }

    private static String buildTable(List<NetworkLogRecord> logs) {
        StringBuilder t = new StringBuilder();
        t.append("<table style='width:100%;border-collapse:collapse;font-size:11px;font-family:monospace;'>")
         .append("<tr style='background:#f0f0f0;font-weight:bold;text-align:left;'>")
         .append("<th style='").append(TH).append("'>Status</th>")
         .append("<th style='").append(TH).append("'>Method</th>")
         .append("<th style='").append(TH).append("width:55%'>URL</th>")
         .append("<th style='").append(TH).append("'>Duration</th>")
         .append("</tr>");

        for (NetworkLogRecord r : logs) {
            boolean isBad     = r.type() == NetworkLogType.FAILED || r.status() >= 400;
            String  rowBg     = isBad ? "#fff5f5" : "#ffffff";
            String  statusStr = r.status() == 0 ? "ERR" : String.valueOf(r.status());
            String  statusClr = statusColor(r.status());
            String  duration  = r.durationMs() > 0 ? r.durationMs() + "ms" : "—";

            t.append("<tr style='background:").append(rowBg).append(";'>")
             .append("<td style='").append(TD).append("color:").append(statusClr).append(";font-weight:bold;'>")
             .append(statusStr).append("</td>")
             .append("<td style='").append(TD).append("'>").append(esc(r.method())).append("</td>")
             .append("<td style='").append(TD).append("max-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;' title='")
             .append(esc(r.url())).append("'>").append(esc(r.url())).append("</td>")
             .append("<td style='").append(TD).append("'>").append(duration).append("</td>")
             .append("</tr>");

            // Headers sub-row
            boolean captureHeaders = ConfigFactory.getConfig().networkCaptureHeaders();
            if (captureHeaders && (!r.requestHeaders().isEmpty() || !r.responseHeaders().isEmpty())) {
                t.append("<tr style='background:#fafafa;'><td colspan='4' style='").append(TD).append("padding-left:16px;'>")
                 .append(buildHeadersDetail(r))
                 .append("</td></tr>");
            }

            // Body sub-row
            if (hasValue(r.requestBody()) || hasValue(r.responseBody())) {
                t.append("<tr style='background:#fafafa;'><td colspan='4' style='").append(TD).append("padding-left:16px;'>")
                 .append(buildBodyDetail(r))
                 .append("</td></tr>");
            }
        }

        t.append("</table>");
        return t.toString();
    }

    private static String buildHeadersDetail(NetworkLogRecord r) {
        StringBuilder s = new StringBuilder();
        if (!r.requestHeaders().isEmpty()) {
            s.append(collapsible("Request Headers", formatHeaders(r.requestHeaders()), "#555"));
        }
        if (!r.responseHeaders().isEmpty()) {
            s.append(collapsible("Response Headers", formatHeaders(r.responseHeaders()), "#555"));
        }
        return s.toString();
    }

    private static String buildBodyDetail(NetworkLogRecord r) {
        if (isStaticAsset(r)) return "";
        StringBuilder s = new StringBuilder();
        if (hasValue(r.requestBody())) {
            s.append(collapsible("Request Body",  preBlock(r.requestBody()),  "#777"));
        }
        if (hasValue(r.responseBody())) {
            s.append(collapsible("Response Body", preBlock(r.responseBody()), "#777"));
        }
        return s.toString();
    }

    private static boolean isStaticAsset(NetworkLogRecord r) {
        String url = r.url() != null ? r.url().toLowerCase() : "";
        String path = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        if (path.matches(".*\\.(css|js|mjs|woff2?|ttf|otf|eot|png|jpg|jpeg|gif|svg|ico|webp|map)$"))
            return true;
        String ct = (r.responseHeaders() != null)
                ? r.responseHeaders().getOrDefault("content-type", "").toLowerCase() : "";
        return ct.startsWith("text/css")
            || ct.startsWith("application/javascript")
            || ct.startsWith("text/javascript")
            || ct.startsWith("image/")
            || ct.startsWith("font/");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static final String TH = "padding:4px 8px;border:1px solid #ddd;";
    private static final String TD = "padding:3px 8px;border:1px solid #eee;";

    private static String statusColor(int status) {
        if (status == 0)          return "#cc0000";
        if (status >= 500)        return "#cc0000";
        if (status >= 400)        return "#ff8800";
        if (status >= 300)        return "#0066cc";
        return "#006600";
    }

    private static String collapsible(String label, String content, String labelColor) {
        return "<details style='margin:2px 0;'><summary style='cursor:pointer;font-size:10px;color:"
                + labelColor + ";'>" + label + "</summary>" + content + "</details>";
    }

    private static String formatHeaders(Map<String, String> headers) {
        StringBuilder sb = new StringBuilder("<pre style='font-size:10px;margin:2px 0;padding:4px;"
                + "background:#f8f8f8;border-left:2px solid #ddd;'>");
        headers.forEach((k, v) -> sb.append(esc(k)).append(": ").append(esc(v)).append("\n"));
        return sb.append("</pre>").toString();
    }

    private static String preBlock(String content) {
        return "<pre style='font-size:10px;margin:2px 0;padding:4px;background:#f8f8f8;"
                + "border-left:2px solid #ddd;white-space:pre-wrap;"
                + "max-height:400px;overflow-y:auto;'>"
                + esc(content) + "</pre>";
    }

    private static boolean hasValue(String s) {
        return s != null && !s.isBlank() && !"null".equalsIgnoreCase(s);
    }

    private static String esc(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
