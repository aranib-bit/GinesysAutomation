package com.gsl.diagnostics.section;

import com.gsl.config.ConfigFactory;
import com.gsl.config.FrameworkConfig;
import com.gsl.diagnostics.DiagnosticSection;
import com.gsl.model.network.CallFrameEntry;
import com.gsl.model.network.NetworkTimingSnapshot;
import com.gsl.model.network.RequestCookieEntry;
import com.gsl.model.network.ServerTimingEntry;
import com.gsl.network.NetworkTimingCapture;

import java.util.List;
import java.util.Map;

public final class NetworkTimingSection implements DiagnosticSection {

    private final List<NetworkTimingSnapshot> snapshots;
    private final boolean showCookies;
    private final boolean showCallStack;

    public NetworkTimingSection(boolean isFailure) {
        FrameworkConfig config = ConfigFactory.getConfig();
        this.snapshots     = NetworkTimingCapture.stopAndCollect();
        this.showCookies   = shouldShow(config.networkCookiesLogOn(),   isFailure);
        this.showCallStack = shouldShow(config.networkCallstackLogOn(),  isFailure);
    }

    private static boolean shouldShow(String setting, boolean isFailure) {
        return "always".equalsIgnoreCase(setting) || isFailure;
    }

    @Override
    public String sectionTitle() {
        return "Network Timing — " + snapshots.size() + " request(s) captured";
    }

    @Override
    public boolean hasData() {
        return !snapshots.isEmpty();
    }

    @Override
    public String renderHtml() {
        StringBuilder sb = new StringBuilder();
        for (NetworkTimingSnapshot snap : snapshots) {
            String url = snap.getUrl();
            if (url != null && url.length() > 100) url = "…" + url.substring(url.length() - 100);

            sb.append("<div style='margin-bottom:14px;border:1px solid #ddd;border-radius:4px;padding:8px;'>")
              .append("<div style='font-weight:600;font-size:12px;margin-bottom:6px;word-break:break-all;'>")
              .append(esc(url))
              .append("</div>")
              .append("<div style='font-size:11px;color:#555;margin-bottom:6px;'>")
              .append("<b>Status:</b> ").append(snap.getStatusCode())
              .append(" &nbsp; <b>Type:</b> ").append(esc(snap.getMimeType()))
              .append("</div>");

            sb.append(buildTimingTable(snap));

            if (snap.getServerTiming() != null && !snap.getServerTiming().isEmpty()) {
                sb.append(buildServerTimingTable(snap.getServerTiming()));
            }

            if (showCookies && snap.getRequestCookies() != null && !snap.getRequestCookies().isEmpty()) {
                sb.append(buildCookiesTable(snap.getRequestCookies()));
            }

            if (showCallStack && snap.getCallStack() != null && !snap.getCallStack().isEmpty()) {
                sb.append(buildCallStackTable(snap.getCallStack()));
            }

            sb.append("</div>");
        }
        return sb.toString();
    }

    // ── Timing table ──────────────────────────────────────────────────────────

    private static String buildTimingTable(NetworkTimingSnapshot snap) {
        Map<String, Double> t = snap.getTimings();
        StringBuilder rows = new StringBuilder();

        addTimingRow(rows, "Queueing",        snap.getQueueingMs());
        addTimingRow(rows, "Stalled",         snap.getStalledMs());

        double dnsStart = t.getOrDefault("dnsStart", -1.0);
        double dnsEnd   = t.getOrDefault("dnsEnd",   -1.0);
        if (dnsStart >= 0 && dnsEnd >= 0) addTimingRow(rows, "DNS Lookup", dnsEnd - dnsStart);

        double sslStart = t.getOrDefault("sslStart", -1.0);
        double sslEnd   = t.getOrDefault("sslEnd",   -1.0);
        if (sslStart >= 0 && sslEnd >= 0) addTimingRow(rows, "SSL", sslEnd - sslStart);

        double connectStart = t.getOrDefault("connectStart", -1.0);
        double connectEnd   = t.getOrDefault("connectEnd",   -1.0);
        if (connectStart >= 0 && connectEnd >= 0) addTimingRow(rows, "Connect", connectEnd - connectStart);

        double sendStart = t.getOrDefault("sendStart", -1.0);
        double sendEnd   = t.getOrDefault("sendEnd",   -1.0);
        if (sendStart >= 0 && sendEnd >= 0) {
            rows.append(tr(td("Request sent"), td(String.format("%.2f ms", Math.max(0, sendEnd - sendStart)))));
        }

        double receiveHeadersEnd = t.getOrDefault("receiveHeadersEnd", -1.0);
        if (sendStart >= 0 && receiveHeadersEnd >= 0) addTimingRow(rows, "Waiting (TTFB)", receiveHeadersEnd - sendStart);

        addTimingRow(rows, "Content Download", snap.getContentDownloadMs());

        if (snap.getEncodedDataBytes() > 0) {
            rows.append(tr(td("Downloaded"), td(formatBytes(snap.getEncodedDataBytes()))));
        }

        if (rows.length() == 0) return "";
        return tableWrap("Timing", new String[]{"Phase", "Value"}, rows.toString());
    }

    private static void addTimingRow(StringBuilder rows, String label, double value) {
        if (value > 0) rows.append(tr(td(label), td(String.format("%.2f ms", value))));
    }

    // ── Server-Timing table ───────────────────────────────────────────────────

    private static String buildServerTimingTable(List<ServerTimingEntry> entries) {
        StringBuilder rows = new StringBuilder();
        for (ServerTimingEntry e : entries) {
            rows.append(tr(td(esc(e.getMetricName())),
                           td(e.getDescription() != null ? esc(e.getDescription()) : ""),
                           td(String.format("%.2f ms", e.getDuration()))));
        }
        return tableWrap("Server-Timing", new String[]{"Metric", "Description", "Duration"}, rows.toString());
    }

    // ── Cookies table ─────────────────────────────────────────────────────────

    private static String buildCookiesTable(List<RequestCookieEntry> cookies) {
        StringBuilder rows = new StringBuilder();
        for (RequestCookieEntry c : cookies) {
            String val = c.getValue();
            if (val != null && val.length() > 40) val = val.substring(0, 40) + "…";
            rows.append(tr(
                td(esc(c.getName())),
                td(esc(val)),
                td(esc(c.getDomain())),
                td(esc(c.getPath())),
                td(esc(c.getExpires())),
                td(String.valueOf(c.getSize())),
                td(String.valueOf(c.isHttpOnly())),
                td(String.valueOf(c.isSecure())),
                td(esc(c.getSameSite())),
                td(esc(c.getPriority()))
            ));
        }
        return tableWrap("Request Cookies",
                new String[]{"Name","Value","Domain","Path","Expires","Size","HttpOnly","Secure","SameSite","Priority"},
                rows.toString());
    }

    // ── Call stack table ──────────────────────────────────────────────────────

    private static String buildCallStackTable(List<CallFrameEntry> frames) {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < frames.size(); i++) {
            CallFrameEntry f = frames.get(i);
            rows.append(tr(
                td(String.valueOf(i + 1)),
                td(esc(f.getFunctionName())),
                td(esc(f.getScriptUrl())),
                td(String.valueOf(f.getLineNumber())),
                td(String.valueOf(f.getColumnNumber()))
            ));
        }
        return tableWrap("Request Initiator (Call Stack)",
                new String[]{"#","Function","File","Line","Col"},
                rows.toString());
    }

    // ── HTML helpers ──────────────────────────────────────────────────────────

    private static String tableWrap(String title, String[] headers, String bodyRows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='margin-top:8px;'>")
          .append("<div style='font-size:11px;font-weight:600;margin-bottom:4px;color:#444;'>")
          .append(title).append("</div>")
          .append("<table style='font-size:11px;border-collapse:collapse;width:100%;'>")
          .append("<thead><tr style='background:#f0f0f0;'>");
        for (String h : headers) {
            sb.append("<th style='border:1px solid #ccc;padding:3px 6px;text-align:left;'>")
              .append(h).append("</th>");
        }
        sb.append("</tr></thead><tbody>").append(bodyRows).append("</tbody></table></div>");
        return sb.toString();
    }

    private static String tr(String... cells) {
        return "<tr>" + String.join("", cells) + "</tr>";
    }

    private static String td(String content) {
        return "<td style='border:1px solid #ddd;padding:2px 6px;vertical-align:top;'>"
                + (content != null ? content : "") + "</td>";
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}
