package com.gsl.reports;

import com.gsl.reports.FoldableSectionRenderer.Status;
import com.gsl.validation.ValidationSwitches;
import com.gsl.validation.api.ApiCheckRow;
import com.gsl.validation.ui.UiValidationReporter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders API read-back checks as one collapsed sub-section per group (Document, Row N, Audit · ...), each with the same header the
 * UI validation tree uses (title chips, counts, status pill) and a Check | Expected | Actual | Status table, in the "API Validation"
 * step; and deposits the same rows on {@link ActualResultsHolder} so Actual Results shows the identical tree. Honours
 * {@code report.validation.api.enabled}: no = only the failed rows are drawn (the step still fails on them).
 */
public final class ApiValidationReporter {

    private ApiValidationReporter() {}

    /** Replaces the rows held for Actual Results and logs the tree.
     *  @param titles group key to report title (chips); may be null
     *  @param source what was read back, e.g. {@code "GET rootview?docNo=VG/00000046/26-27"}; may be null */
    public static void report(List<ApiCheckRow> rows, Map<String, String> titles, String source) {
        ActualResultsHolder h = ActualResultsHolder.get();
        // Audit groups appended earlier in the flow survive a later rootview report.
        List<ApiCheckRow> all = new ArrayList<>(rows);
        if (h.getApiValidationRows() != null) h.getApiValidationRows().stream().filter(r -> r.group().startsWith("Audit")).forEach(all::add);
        h.setApiValidationRows(all);
        Map<String, String> merged = titles == null ? new LinkedHashMap<>() : new LinkedHashMap<>(titles);
        if (h.getApiValidationTitles() != null) h.getApiValidationTitles().forEach((k, v) -> { if (k.startsWith("Audit")) merged.put(k, v); });
        h.setApiValidationTitles(merged);
        h.setApiValidationSource(source);
        log(rows, titles, source);
    }

    /** Adds rows (e.g. the audit groups) to those already held for Actual Results and logs just these. */
    public static void append(List<ApiCheckRow> rows, Map<String, String> titles, String source) {
        ActualResultsHolder h = ActualResultsHolder.get();
        List<ApiCheckRow> all = h.getApiValidationRows() == null ? new ArrayList<>() : new ArrayList<>(h.getApiValidationRows());
        all.addAll(rows);
        h.setApiValidationRows(all);
        Map<String, String> t = h.getApiValidationTitles() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(h.getApiValidationTitles());
        if (titles != null) t.putAll(titles);
        h.setApiValidationTitles(t);
        if (h.getApiValidationSource() == null) h.setApiValidationSource(source);
        log(rows, titles, source);
    }

    private static void log(List<ApiCheckRow> rows, Map<String, String> titles, String source) {
        String html = html(rows, titles, source, ValidationSwitches.mode(ValidationSwitches.Area.API));
        if (!html.isEmpty()) ExtentLogger.infoHtml(html);
    }

    /** The group header: title with chips, "N checked, M failed", status pill — same look as a UI validation row node. */
    public static String groupHeader(String title, long checked, long failed) {
        Status roll = failed > 0 ? Status.FAIL : checked == 0 ? Status.SKIP : Status.PASS;
        String counts = checked + " checked, " + failed + " failed";
        return "<span style='display:flex;align-items:center;gap:8px;width:100%'>"
                + "<span style='flex:1 1 auto;min-width:0;display:flex;align-items:center;gap:8px;overflow:hidden;"
                + "font-weight:500;text-transform:none;letter-spacing:0'>"
                + UiValidationReporter.titleWithChips(title) + "</span>"
                + "<span style='font-size:11px;color:#64748B;white-space:nowrap'>" + esc(counts) + "</span>"
                + FoldableSectionRenderer.badge(roll) + "</span>";
    }

    /** Package-visible for tests; empty when there is nothing to draw. */
    static String html(List<ApiCheckRow> rows, Map<String, String> titles, String source, ValidationSwitches.Mode mode) {
        if (mode == ValidationSwitches.Mode.OFF || rows == null || rows.isEmpty()) return "";
        boolean failuresOnly = mode == ValidationSwitches.Mode.FAILURES_ONLY;
        long failed = rows.stream().filter(r -> !r.passed()).count();
        if (failuresOnly && failed == 0) return "";

        Map<String, List<ApiCheckRow>> all = new LinkedHashMap<>();
        for (ApiCheckRow r : rows) all.computeIfAbsent(r.group(), k -> new ArrayList<>()).add(r);

        StringBuilder sb = new StringBuilder();
        sb.append(FoldableSectionRenderer.CSS).append("<div class='rs-wrap'>");
        sb.append("<div style='padding:6px 14px 2px;font-size:11px;color:#64748B'>")
          .append(rows.size()).append(" checks &middot; ").append(rows.size() - failed).append(" passed &middot; ")
          .append(failed).append(" failed")
          .append(source == null ? "" : " &middot; " + esc(source))
          .append(failuresOnly ? " &middot; showing failures only" : "").append("</div>");
        for (Map.Entry<String, List<ApiCheckRow>> g : all.entrySet()) {
            long gFailed = g.getValue().stream().filter(r -> !r.passed()).count();
            if (failuresOnly && gFailed == 0) continue;
            List<ApiCheckRow> shown = failuresOnly ? g.getValue().stream().filter(r -> !r.passed()).toList() : g.getValue();
            String title = titles != null && titles.get(g.getKey()) != null ? titles.get(g.getKey()) : g.getKey();
            String[][] table = new String[shown.size() + 1][];
            table[0] = new String[]{"Check", "Expected", "Actual", "Status"};
            for (int i = 0; i < shown.size(); i++) {
                ApiCheckRow r = shown.get(i);
                table[i + 1] = new String[]{esc(r.check()), esc(r.expected()), esc(r.actual()),
                        FoldableSectionRenderer.badge(r.passed() ? Status.PASS : Status.FAIL)};
            }
            // Collapsed unless something in the group failed.
            sb.append(FoldableSectionRenderer.sectionOpen(gFailed > 0 ? "open" : "", "rs-sub", "",
                    groupHeader(title, g.getValue().size(), gFailed)));
            sb.append(FoldableSectionRenderer.tableHtml(table, new String[]{"22%", "34%", "34%", "10%"}));
            sb.append(FoldableSectionRenderer.sectionClose());
        }
        return sb.append("</div>").toString();
    }

    private static String esc(String s) {
        if (s == null) return "—";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
