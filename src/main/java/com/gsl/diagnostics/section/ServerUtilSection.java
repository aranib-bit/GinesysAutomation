package com.gsl.diagnostics.section;

import com.gsl.config.ConfigFactory;
import com.gsl.diagnostics.DiagnosticSection;
import com.gsl.utils.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 * Captures a CPU + memory snapshot from the application server or database server
 * at the moment of test failure.
 *
 * For localhost, executes OS commands directly via Runtime.exec().
 * Remote server support (SSH/WinRM) is a future Phase 2 addition.
 *
 * Config keys (prefix = diagnostics.app.server or diagnostics.db.server):
 *   diagnostics.app.server.util.enabled = false
 *   diagnostics.app.server.type         = WINDOWS   # WINDOWS | LINUX
 *   diagnostics.app.server.host         = localhost
 */
public final class ServerUtilSection implements DiagnosticSection {

    private final ServerRole role;
    private String           cpuOutput;
    private String           memOutput;
    private boolean          collected;

    public ServerUtilSection(ServerRole role) {
        this.role = role;
        collect();
    }

    private void collect() {
        if (!enabled()) return;
        String type = serverType();
        try {
            if ("LINUX".equalsIgnoreCase(type)) {
                cpuOutput = exec("sh", "-c", "top -bn1 | grep 'Cpu(s)'");
                memOutput = exec("sh", "-c", "free -m");
            } else {
                cpuOutput = exec("powershell", "-NoProfile", "-NonInteractive", "-Command",
                        "Get-WmiObject Win32_Processor | Select-Object -ExpandProperty LoadPercentage | ForEach-Object { \"CPU Load: $_%%\" }");
                memOutput = exec("powershell", "-NoProfile", "-NonInteractive", "-Command",
                        "Get-WmiObject Win32_OperatingSystem | ForEach-Object { " +
                        "\"Total MB: \" + [math]::Round($_.TotalVisibleMemorySize/1024) + \" | Free MB: \" + [math]::Round($_.FreePhysicalMemory/1024) }");
            }
            collected = (cpuOutput != null || memOutput != null);
        } catch (Exception e) {
            Log.warn("[ServerUtilSection] {} collection failed: {}", role.label, e.getMessage());
        }
    }

    @Override
    public String sectionTitle() {
        return role.label + " Utilization";
    }

    @Override
    public String renderHtml() {
        StringBuilder html = new StringBuilder();
        html.append("<div style='font-family:monospace;font-size:11px;'>");
        appendBlock(html, "CPU", cpuOutput);
        appendBlock(html, "Memory", memOutput);
        html.append("</div>");
        return html.toString();
    }

    @Override
    public boolean hasData() {
        return collected;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private boolean enabled() {
        return role == ServerRole.APP
                ? ConfigFactory.getConfig().diagnosticsAppServerUtilEnabled()
                : ConfigFactory.getConfig().diagnosticsDbServerUtilEnabled();
    }

    private String serverType() {
        return role == ServerRole.APP
                ? ConfigFactory.getConfig().diagnosticsAppServerType()
                : ConfigFactory.getConfig().diagnosticsDbServerType();
    }

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

    private static void appendBlock(StringBuilder html, String label, String content) {
        if (content == null || content.isBlank()) return;
        html.append("<div style='margin-bottom:6px;'>")
            .append("<b>").append(label).append(":</b>")
            .append("<pre style='margin:2px 0;padding:4px;background:#f5f5f5;")
            .append("border-left:3px solid #bbb;font-size:10px;white-space:pre-wrap;'>")
            .append(esc(content)).append("</pre></div>");
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
