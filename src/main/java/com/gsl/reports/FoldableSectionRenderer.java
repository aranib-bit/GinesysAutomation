package com.gsl.reports;

import java.util.List;

/**
 * Shared foldable-section HTML — plan §4I. Extracted out of {@link ActualResultsLogger}, which
 * used to hand-append the same {@code <details>/<summary class='rs-sub'>/<span class='rs-chev'>}
 * boilerplate at 13 separate call sites, each with its own inlined {@code CSS} constant copy.
 * {@code SnapshotValidationReporter} (in {@code com.gsl.validation.snapshot}, which already has an
 * established one-way dependency on this package via {@code DatabaseLogger}/{@code ExtentLogger})
 * and {@code DbGatheringExtentLogger} now converge on this one renderer instead of each rendering
 * its sections a different way.
 *
 * <p>Table cells are inserted as-is, never HTML-escaped — the same contract
 * {@code ExtentLogger.infoLabeledTable}/{@code buildScrollableTableHtml} has always had, so a
 * caller's pre-styled {@code <span style=...>} cells (e.g. {@code SnapshotValidationReporter}'s
 * red/amber highlighted rows) keep rendering correctly. Callers are responsible for escaping any
 * cell content that isn't already deliberately-embedded markup.
 */
public final class FoldableSectionRenderer {

    private FoldableSectionRenderer() {}

    // ─── CSS — moved verbatim from ActualResultsLogger's former private CSS constant ─────────

    public static final String CSS =
        "<style>" +
        ".rs-top{list-style:none;cursor:pointer;display:flex;align-items:center;" +
            "justify-content:space-between;gap:8px;" +
            "font-size:12px;font-weight:500;letter-spacing:.04em;text-transform:uppercase;" +
            "padding:9px 14px;user-select:none;border-radius:8px 8px 0 0;margin-top:10px}" +
        ".rs-ui{background:#EAF2FB;color:#1F4E79;border:0.5px solid #B8D4EC}" +
        ".rs-db{background:#E6F4F4;color:#2C7A7B;border:0.5px solid #B2D8D8}" +
        ".rs-wrap details[open] .rs-top,.rs-sub{border-radius:0}" +
        ".rs-sub{list-style:none;cursor:pointer;display:flex;align-items:center;" +
            "justify-content:space-between;gap:6px;" +
            "font-size:11px;font-weight:500;color:#64748B;padding:6px 14px;" +
            "background:#F8FAFC;text-transform:uppercase;letter-spacing:.05em;" +
            "border:0.5px solid #E2E8F0;border-top:none}" +
        ".rs-wrap details{border:0.5px solid #E2E8F0;border-radius:8px;overflow:hidden;margin-bottom:2px}" +
        ".rs-wrap details.rs-inner{border:none;border-bottom:0.5px solid #E2E8F0;border-radius:0}" +
        ".rs-tbl{width:100%;min-width:540px;border-collapse:collapse;border:none;table-layout:fixed}" +
        ".rs-tbl tbody tr:last-child td{border-bottom:none}" +
        ".rs-wrap summary::-webkit-details-marker{display:none}" +
        ".rs-lookup-sum{list-style:none;cursor:pointer;display:flex;align-items:center;" +
            "justify-content:space-between;gap:6px;font-size:11px;font-weight:500;" +
            "color:#475569;padding:6px 14px;background:#F8FAFC;text-transform:uppercase;" +
            "letter-spacing:.04em;border:0.5px solid #E2E8F0;border-top:none;user-select:none}" +
        ".rs-chev{font-size:9px;opacity:0.55;flex-shrink:0;display:inline-block;transition:transform .15s}" +
        ".rs-wrap details:not([open]) .rs-chev{transform:rotate(180deg)}" +
        // Hide the Status and Timestamp columns of the Extent log row that wraps the widget.
        // Use > (direct-child combinator) so only the outer Extent <tr>'s own <td>s are hidden,
        // not the <td>s inside the nested tables within .rs-wrap.
        // :has() supported: Chrome 105+, Edge 105+, Firefox 121+
        "tr:has(.rs-wrap)>td:not(:last-child){display:none!important;width:0!important;padding:0!important}" +
        "tr:has(.rs-wrap)>td:last-child{padding:0!important}" +
        ".rs-legend{display:flex;align-items:center;flex-wrap:nowrap;overflow-x:auto;gap:6px 10px;" +
            "padding:8px 14px;background:#F8FAFC;border:0.5px solid #E2E8F0;" +
            "border-radius:8px;margin-bottom:8px}" +
        ".rs-leg-lbl{font-size:10px;font-weight:600;color:#64748B;text-transform:uppercase;" +
            "letter-spacing:.06em;margin-right:2px;white-space:nowrap}" +
        ".rs-leg-sep{color:#94A3B8;font-size:11px;white-space:nowrap}" +
        "</style>";

    // ─── Low-level building blocks — exactly the boilerplate every call site used to hand-append ──

    /**
     * @param openAttr "open", "", or a placeholder token to be string-replaced later (e.g.
     *        {@code ActualResultsLogger}'s {@code SECTION_OPEN_PLACEHOLDER})
     * @param summaryClass e.g. "rs-top rs-ui", "rs-sub", "rs-lookup-sum"
     * @param summaryStyle extra inline style for {@code <summary>}, or "" (e.g. Exceptions' red tint)
     * @param headerHtml pre-built icon+title span, typically from {@link #headerLabel}
     */
    public static String sectionOpen(String openAttr, String summaryClass, String summaryStyle, String headerHtml) {
        return "<details " + openAttr + "><summary class='" + summaryClass + "'"
             + (summaryStyle == null || summaryStyle.isEmpty() ? "" : " style='" + summaryStyle + "'") + ">"
             + headerHtml + "<span class='rs-chev'>&#9650;</span></summary>";
    }

    public static String sectionClose() {
        return "</details>";
    }

    public static String headerLabel(String icon, String title, int gapPx) {
        return "<span style='display:inline-flex;align-items:center;gap:" + gapPx + "px'>"
             + (icon != null && !icon.isEmpty() ? icon + " " : "") + title + "</span>";
    }

    // ─── High-level — a fully self-contained fragment for one ExtentLogger.infoHtml(...) call ──

    /** CSS + {@code <div class='rs-wrap'>} + one {@code rs-sub} {@code <details>} wrapping
     *  {@code innerHtml}. {@code icon} may be {@code null}/empty when the title carries its own
     *  emoji already. */
    public static String foldableSection(String icon, String title, boolean openByDefault, String innerHtml) {
        return CSS + "<div class='rs-wrap'>"
             + sectionOpen(openByDefault ? "open" : "", "rs-sub", "", headerLabel(icon, title, 6))
             + innerHtml + sectionClose() + "</div>";
    }

    /** Convenience: {@code table[0]} is the header row, every subsequent row is data. Cells are
     *  inserted as-is (see class Javadoc). Columns render at even width — use the
     *  {@code widths} overload below to hint otherwise (e.g. a Note/long-text column). */
    public static String foldableTable(String icon, String title, boolean openByDefault, String[][] table) {
        return foldableSection(icon, title, openByDefault, tableHtml(table, null));
    }

    /** Same as {@link #foldableTable(String, String, boolean, String[][])} but with an explicit
     *  per-column width hint (plan §10 — {@code null}/percentage strings e.g.
     *  {@code {"12%","22%","14%","18%","18%","16%"}}, one entry per {@code table[0]} column;
     *  {@code widths} itself may be {@code null} for even distribution, same as the simpler
     *  overload). Lets a wide table like Known Issues' {Table, Row, Column, Before, After, Note}
     *  give its Note column real room instead of fighting evenly with Table/Column. */
    public static String foldableTable(String icon, String title, boolean openByDefault, String[][] table, String[] widths) {
        return foldableSection(icon, title, openByDefault, tableHtml(table, widths));
    }

    /** Same as {@link #foldableTable(String, String, boolean, String[][], String[])} but with an
     *  extra one-line summary rendered above the table, inside the same foldable body (plan §10 —
     *  "a summary for all sections and each section"). */
    public static String foldableTableWithSummary(String icon, String title, boolean openByDefault,
            String summaryLine, String[][] table, String[] widths) {
        String summaryHtml = "<div style='padding:6px 14px 2px;font-size:11px;color:#64748B'>"
                + summaryLine + "</div>";
        return foldableSection(icon, title, openByDefault, summaryHtml + tableHtml(table, widths));
    }

    /** Renders an {@code rs-tbl}-styled table body (header row + data rows) — the same visual
     *  shape {@code ActualResultsLogger}'s own sections use. {@code widths} (one entry per
     *  header column, e.g. {@code "18%"}) is optional — {@code null} leaves columns at their
     *  browser-default (roughly even) width. */
    private static String tableHtml(String[][] table, String[] widths) {
        if (table == null || table.length == 0) {
            return "<div style='padding:8px 14px;color:#64748B;font-size:12px'>(no data)</div>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='overflow-x:auto;width:100%'><table class='rs-tbl'>");
        sb.append("<thead><tr>");
        for (int c = 0; c < table[0].length; c++) {
            String h = table[0][c];
            String wStyle = widths != null && c < widths.length && widths[c] != null
                    ? "width:" + widths[c] + ";" : "";
            sb.append("<th style='font-size:11px;font-weight:500;color:#64748B;padding:6px 12px;"
                    + "background:#fff;border-bottom:0.5px solid #E2E8F0;white-space:nowrap;"
                    + "vertical-align:top;" + wStyle + "text-align:left'>")
              .append(h == null ? "—" : h).append("</th>");
        }
        sb.append("</tr></thead><tbody>");
        for (int r = 1; r < table.length; r++) {
            sb.append("<tr>");
            for (String cell : table[r]) {
                sb.append("<td style='padding:7px 12px;border-bottom:0.5px solid #E2E8F0;"
                        + "word-break:break-word;vertical-align:top;color:#1E293B'>")
                  .append(cell == null ? "—" : cell).append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    // ─── Shared Status/badge/icon — moved here (plan §10) from ActualResultsLogger so
    // SnapshotValidationReporter's own Result/Status columns and section headers can render the
    // same badge pills and inline-SVG icons instead of plain text and no icons at all. ──

    /** Shared result classification for a report row. {@code KNOWN_ISSUE} is new here (plan §10)
     *  — {@code SnapshotValidationReporter} previously rendered this case as plain "⚠ KNOWN
     *  ISSUE" text with no badge; every other value is exactly {@code ActualResultsLogger}'s
     *  original private enum, promoted unchanged. */
    public enum Status { PASS, FAIL, SKIP, KNOWN_ISSUE }

    /** Renders a colored pill for {@code s}. */
    public static String badge(Status s) {
        return switch (s) {
            case PASS -> "<span style='display:inline-flex;align-items:center;gap:3px;"
                    + "background:#EAF3DE;color:#27500A;font-size:11px;padding:2px 7px;"
                    + "border-radius:99px;font-weight:500;white-space:nowrap'>&#10003; Pass</span>";
            case FAIL -> "<span style='display:inline-flex;align-items:center;gap:3px;"
                    + "background:#FEE2E2;color:#991B1B;font-size:11px;padding:2px 7px;"
                    + "border-radius:99px;font-weight:500;white-space:nowrap'>&#10007; Fail</span>";
            case SKIP -> "<span style='display:inline-flex;align-items:center;gap:3px;"
                    + "background:#F1EFE8;color:#5F5E5A;font-size:11px;padding:2px 7px;"
                    + "border-radius:99px;font-weight:500;white-space:nowrap'>&#8722; Skip</span>";
            case KNOWN_ISSUE -> "<span style='display:inline-flex;align-items:center;gap:3px;"
                    + "background:#FEF3C7;color:#92400E;font-size:11px;padding:2px 7px;"
                    + "border-radius:99px;font-weight:500;white-space:nowrap'>&#9888; Known Issue</span>";
        };
    }

    /** Inline-SVG icon helper. */
    public static String icon(String path, int size) {
        return "<svg xmlns='http://www.w3.org/2000/svg' width='" + size + "' height='" + size
             + "' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' "
             + "stroke-linecap='round' stroke-linejoin='round' "
             + "style='vertical-align:middle;flex-shrink:0'>" + path + "</svg>";
    }

    public static final String ICO_MONITOR   = icon(
        "<rect x='2' y='3' width='20' height='14' rx='2'/>"
        + "<line x1='8' y1='21' x2='16' y2='21'/><line x1='12' y1='17' x2='12' y2='21'/>", 14);
    public static final String ICO_DOC       = icon(
        "<path d='M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z'/>"
        + "<polyline points='14 2 14 8 20 8'/>"
        + "<line x1='16' y1='13' x2='8' y2='13'/><line x1='16' y1='17' x2='8' y2='17'/>", 12);
    public static final String ICO_FLAG      = icon(
        "<path d='M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z'/>"
        + "<line x1='4' y1='22' x2='4' y2='15'/>", 12);
    public static final String ICO_SUBLEDGER = icon(
        "<path d='M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2'/><circle cx='12' cy='7' r='4'/>", 12);
    public static final String ICO_DB        = icon(
        "<ellipse cx='12' cy='5' rx='9' ry='3'/>"
        + "<path d='M21 12c0 1.66-4 3-9 3s-9-1.34-9-3'/>"
        + "<path d='M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5'/>", 14);
    public static final String ICO_TABLE     = icon(
        "<rect x='3' y='3' width='18' height='18' rx='2'/>"
        + "<line x1='3' y1='9' x2='21' y2='9'/><line x1='9' y1='9' x2='9' y2='21'/>", 12);
    public static final String ICO_LOOKUP    = icon(
        "<circle cx='11' cy='11' r='8'/><line x1='21' y1='21' x2='16.65' y2='16.65'/>", 12);
    /** New here (plan §10) for Exceptions/Known Issues — a warning triangle, matching the same
     *  visual language a "⚠️" section title already implies. */
    public static final String ICO_WARNING   = icon(
        "<path d='M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z'/>"
        + "<line x1='12' y1='9' x2='12' y2='13'/><line x1='12' y1='17' x2='12.01' y2='17'/>", 12);

    // ─── SQL-queries table — extracted from ActualResultsLogger.buildDbLookupsSection's body,
    // shared verbatim by that section AND DbGatheringExtentLogger's "SQL Queries Executed"
    // section, since both consume the identical "[DB Query ⏱ Nms] SQL..." string format. ──

    /** Renders the SQL/Time table body for a list of {@code "[DB Query ⏱ Nms] SELECT ..."}
     *  strings — just the inner {@code <div>/<table>}, not wrapped in {@code <details>} (callers
     *  compose that via {@link #sectionOpen}/{@link #foldableSection}). */
    public static String sqlQueriesTableHtml(List<String> queries) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='padding:6px 12px 10px;background:#fff'>");
        sb.append("<table style='width:100%;border-collapse:collapse;font-size:11px;font-family:monospace'>");
        sb.append("<thead><tr>")
          .append("<th style='text-align:left;padding:4px 8px;color:#64748B;font-weight:500;border-bottom:0.5px solid #E2E8F0;font-family:sans-serif;font-size:11px'>SQL</th>")
          .append("<th style='text-align:right;padding:4px 8px;color:#64748B;font-weight:500;border-bottom:0.5px solid #E2E8F0;font-family:sans-serif;font-size:11px;white-space:nowrap'>Time (ms)</th>")
          .append("</tr></thead><tbody>");
        for (String q : queries) {
            // Format: "[DB Query ⏱ 71ms] SELECT ..." — split on "] "
            String timing = "";
            String sql = q;
            int bracket = q.indexOf("] ");
            if (bracket >= 0) {
                String prefix = q.substring(0, bracket); // e.g. "[DB Query ⏱ 71ms"
                sql = q.substring(bracket + 2);
                int msIdx = prefix.lastIndexOf(' ');
                if (msIdx >= 0) timing = prefix.substring(msIdx + 1).replace("ms", "");
            }
            sb.append("<tr>")
              .append("<td style='padding:4px 8px;border-bottom:0.5px solid #F1F5F9;color:#334155;word-break:break-all'>")
              .append(esc(sql)).append("</td>")
              .append("<td style='padding:4px 8px;border-bottom:0.5px solid #F1F5F9;color:#64748B;text-align:right;white-space:nowrap'>")
              .append(esc(timing)).append("</td>")
              .append("</tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) return "—";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
