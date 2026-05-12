package com.gsl.diagnostics.section;

import com.gsl.config.ConfigFactory;
import com.gsl.diagnostics.DiagnosticSection;
import com.gsl.utils.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 * Captures recent error-level entries from the application server's event log.
 *
 * Windows: queries the Application event log via wevtutil (Error + Critical level).
 * Linux:   queries systemd journal via journalctl, or falls back to /var/log/syslog.
 *
 * Config:
 *   diagnostics.event.log.enabled = false
 *   diagnostics.event.log.lines   = 30
 *   (server type/host read from diagnostics.app.server.*)
 */
public final class EventLogSection implements DiagnosticSection {

    private String  output;
    private boolean collected;

    public EventLogSection() {
        collect();
    }

    private void collect() {
        if (!ConfigFactory.getConfig().diagnosticsEventLogEnabled()) return;
        int    lines = ConfigFactory.getConfig().diagnosticsEventLogLines();
        String type  = ConfigFactory.getConfig().diagnosticsAppServerType();
        try {
            if ("LINUX".equalsIgnoreCase(type)) {
                output = exec("sh", "-c",
                        "journalctl -p err -n " + lines + " --no-pager 2>/dev/null " +
                        "|| tail -n " + lines + " /var/log/syslog 2>/dev/null");
            } else {
                output = exec("wevtutil", "qe", "Application",
                        "/c:" + lines, "/rd:true", "/f:text",
                        "/q:*[System[(Level=1 or Level=2)]]");
            }
            collected = (output != null && !output.isBlank());
        } catch (Exception e) {
            Log.warn("[EventLogSection] Collection failed: {}", e.getMessage());
        }
    }

    @Override
    public String sectionTitle() {
        return "Event Log / System Journal";
    }

    @Override
    public String renderHtml() {
        return "<pre style='font-size:10px;background:#fafafa;padding:6px;" +
               "border-left:3px solid #e0a000;white-space:pre-wrap;overflow:auto;max-height:300px;'>"
               + esc(output) + "</pre>";
    }

    @Override
    public boolean hasData() {
        return collected;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String exec(String... cmd) {
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String out = r.lines().collect(Collectors.joining("\n")).trim();
                return out.isBlank() ? null : out;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
