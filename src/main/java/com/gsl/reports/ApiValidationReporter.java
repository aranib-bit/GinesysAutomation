package com.gsl.reports;

import com.gsl.reports.FoldableSectionRenderer.Status;
import com.gsl.validation.ValidationSwitches;
import com.gsl.validation.api.ApiCheckRow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders API read-back checks as a Check | Expected | Actual | Status table (one sub-table per group) in the "API Validation" step, and
 * deposits the same rows on {@link ActualResultsHolder} so Actual Results shows the identical table. Honours
 * {@code report.validation.api.enabled}: no = only the failed rows are drawn (the step still fails on them).
 */
public final class ApiValidationReporter {

    private ApiValidationReporter() {}

    /** @param source what was read back, e.g. {@code "GET rootview?docNo=VG/00000046/26-27"}; may be null */
    public static void report(List<ApiCheckRow> rows, String source) {
        ActualResultsHolder h = ActualResultsHolder.get();
        h.setApiValidationRows(rows);
        h.setApiValidationSource(source);
        ValidationSwitches.Mode mode = ValidationSwitches.mode(ValidationSwitches.Area.API);
        String html = html(rows, source, mode);
        if (!html.isEmpty()) ExtentLogger.infoHtml(html);
    }

    /** Package-visible for tests; empty when there is nothing to draw. */
    static String html(List<ApiCheckRow> rows, String source, ValidationSwitches.Mode mode) {
        if (mode == ValidationSwitches.Mode.OFF || rows == null || rows.isEmpty()) return "";
        boolean failuresOnly = mode == ValidationSwitches.Mode.FAILURES_ONLY;
        long failed = rows.stream().filter(r -> !r.passed()).count();
        if (failuresOnly && failed == 0) return "";

        Map<String, List<ApiCheckRow>> groups = new LinkedHashMap<>();
        for (ApiCheckRow r : rows) {
            if (failuresOnly && r.passed()) continue;
            groups.computeIfAbsent(r.group(), k -> new ArrayList<>()).add(r);
        }
        StringBuilder inner = new StringBuilder();
        inner.append("<div style='padding:6px 14px 2px;font-size:11px;color:#64748B'>")
             .append(rows.size()).append(" checks &middot; ").append(rows.size() - failed).append(" passed &middot; ")
             .append(failed).append(" failed")
             .append(source == null ? "" : " &middot; " + esc(source))
             .append(failuresOnly ? " &middot; showing failures only" : "").append("</div>");
        for (Map.Entry<String, List<ApiCheckRow>> g : groups.entrySet()) {
            String[][] table = new String[g.getValue().size() + 1][];
            table[0] = new String[]{"Check", "Expected", "Actual", "Status"};
            for (int i = 0; i < g.getValue().size(); i++) {
                ApiCheckRow r = g.getValue().get(i);
                table[i + 1] = new String[]{esc(r.check()), esc(r.expected()), esc(r.actual()),
                        FoldableSectionRenderer.badge(r.passed() ? Status.PASS : Status.FAIL)};
            }
            inner.append(FoldableSectionRenderer.foldableTable(FoldableSectionRenderer.ICO_TABLE, g.getKey(), true, table,
                    new String[]{"22%", "34%", "34%", "10%"}));
        }
        return inner.toString();
    }

    private static String esc(String s) {
        if (s == null) return "—";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
