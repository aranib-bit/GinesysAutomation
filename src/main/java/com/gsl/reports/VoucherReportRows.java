package com.gsl.reports;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure helpers over the Extent report's table rows (as built by
 * {@link VoucherExtentDescriptionLogger#headerRows} / {@link VoucherExtentDescriptionLogger#expectedRows})
 * that the coverage workbook needs: dropping the header row, and condensing the Expected Results
 * table into one readable line.
 */
public final class VoucherReportRows {

    private VoucherReportRows() {}

    private static final String SEP = " — ";   // the em dash the Extent labels use: "DB — finvchmain"

    /** {@code rows} without its first (column-title) row; never {@code null}. */
    public static List<String[]> withoutHeaderRow(List<String[]> rows) {
        if (rows == null || rows.size() <= 1) return List.of();
        return new ArrayList<>(rows.subList(1, rows.size()));
    }

    /**
     * Condenses Expected Results rows (header row already removed) into one line: labels sharing a
     * prefix before the em dash are grouped, e.g. {@code UI Save; DB finvchmain, finvchdet, finpost,
     * fincosttag, Audit Trail}. A label with no prefix stands alone.
     */
    public static String summariseExpected(List<String[]> rows) {
        if (rows == null || rows.isEmpty()) return "";
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String[] r : rows) {
            if (r == null || r.length == 0 || r[0] == null) continue;
            String label = r[0].strip();
            int i = label.indexOf(SEP);
            String prefix = i > 0 ? label.substring(0, i).strip() : label;
            String item = i > 0 ? label.substring(i + SEP.length()).strip() : "";
            groups.computeIfAbsent(prefix, k -> new ArrayList<>());
            if (!item.isEmpty()) groups.get(prefix).add(item);
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : groups.entrySet()) {
            parts.add(e.getValue().isEmpty() ? e.getKey() : e.getKey() + " " + String.join(", ", e.getValue()));
        }
        return String.join("; ", parts);
    }

    /** {@code label: details} lines (one per row) for a detail cell. */
    public static String expectedDetail(List<String[]> rows) {
        if (rows == null) return "";
        List<String> lines = new ArrayList<>();
        for (String[] r : rows) {
            if (r == null || r.length < 2) continue;
            lines.add(r[0] + ": " + (r[1] == null ? "" : r[1]));
        }
        return String.join("\n", lines);
    }
}
