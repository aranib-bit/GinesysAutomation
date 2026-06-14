package com.gsl.diagnostics.section;

import com.gsl.config.ConfigFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.diagnostics.DiagnosticSection;
import com.gsl.utils.Log;

import java.util.List;
import java.util.Map;

/**
 * Runs a configurable diagnostic SQL query against the test database on failure and
 * renders the result as an HTML table in the DiagnosticReport.
 *
 * Useful for capturing the DB state of application records (e.g. last 5 vouchers
 * created during the run) alongside browser and network evidence.
 *
 * Config keys:
 *   diagnostics.db.state.enabled = false
 *   diagnostics.db.state.query   = SELECT vchcode,vchdate,slcode,amount
 *                                   FROM finvchmain ORDER BY creation_dt DESC LIMIT 5
 *
 * IMPORTANT: Keep the query read-only (SELECT only). Never configure DML statements here.
 */
public final class DbStateSection implements DiagnosticSection {

    private List<Map<String, Object>> rows;
    private List<String>              columns;

    public DbStateSection() {
        if (!ConfigFactory.getDiagnosticsConfig().diagnosticsDbStateEnabled()) return;
        collect();
    }

    @Override
    public String sectionTitle() {
        int count = (rows != null) ? rows.size() : 0;
        return "DB State Snapshot (" + count + " rows)";
    }

    @Override
    public String renderHtml() {
        if (rows == null || rows.isEmpty()) {
            return "<div style='color:#999;font-size:11px;'>Query returned no rows.</div>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div style='overflow-x:auto;'>")
          .append("<table style='border-collapse:collapse;font-size:11px;font-family:monospace;width:100%;'>");

        // Header
        sb.append("<tr style='background:#f0f0f0;font-weight:bold;'>");
        for (String col : columns) {
            sb.append("<th style='padding:3px 8px;border:1px solid #ddd;'>").append(esc(col)).append("</th>");
        }
        sb.append("</tr>");

        // Rows
        for (Map<String, Object> row : rows) {
            sb.append("<tr>");
            for (String col : columns) {
                Object val = row.get(col);
                sb.append("<td style='padding:3px 8px;border:1px solid #eee;'>")
                  .append(val != null ? esc(val.toString()) : "<span style='color:#bbb;'>NULL</span>")
                  .append("</td>");
            }
            sb.append("</tr>");
        }

        sb.append("</table></div>");
        return sb.toString();
    }

    @Override
    public boolean hasData() {
        return rows != null && !rows.isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void collect() {
        String sql = ConfigFactory.getDiagnosticsConfig().diagnosticsDbStateQuery();
        if (sql == null || sql.isBlank()) {
            Log.warn("[DbStateSection] diagnostics.db.state.query is not configured");
            return;
        }

        try {
            rows = DatabaseManager.getJdbi().withHandle(handle ->
                    handle.createQuery(sql).mapToMap().list());

            if (rows != null && !rows.isEmpty()) {
                columns = List.copyOf(rows.get(0).keySet());
            }
        } catch (Exception e) {
            Log.warn("[DbStateSection] Diagnostic query failed: {}", e.getMessage());
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
