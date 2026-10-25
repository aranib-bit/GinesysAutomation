package com.gsl.validation.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * UI checks of an ExtJS grid-style picker (the floating, paged boundlist that opens from a General Ledger /
 * Sub Ledger cell): header columns, rows, per-cell tooltips and the paging footer, before typing and after
 * typing, plus the change between the two.
 *
 * <p>Reading is one inspection script over the visible {@code .x-boundlist-floating} (header
 * {@code .headerBoundList b}, rows {@code .x-boundlist-item td[data-qtip]}, footer
 * {@code .x-grid-paging-toolbar} - DOM confirmed from captured pages). Nothing is clicked. The checks themselves are
 * pure functions of two {@link Snapshot}s, so the page objects only take snapshots while the picker is open and the
 * results are produced later under the row's report node.
 */
public final class PickerUi {

    private PickerUi() {}

    /** One read of the open picker. {@code shown=false} when no picker was visible. */
    public record Snapshot(boolean shown, List<String> headers, List<List<String>> rows, int qtipMismatches,
                           Integer page, Integer totalPages, Map<String, Boolean> buttonsEnabled) {
        public static Snapshot none() {
            return new Snapshot(false, List.of(), List.of(), 0, null, null, Map.of());
        }
    }

    /**
     * What the business fill did with one picker.
     *
     * @param label           report label, e.g. "Sub Ledger picker"
     * @param expectedColumns header texts in order
     * @param typed           text typed to filter
     * @param targetName      option that must be selectable (Name column)
     * @param targetId        its Id when the data has one, else {@code null}
     * @param targetCells     further expected values of the target row by column (e.g. Site Applicable=No)
     */
    public record Observation(String label, List<String> expectedColumns, String typed, String targetName,
                              String targetId, Map<String, String> targetCells, Snapshot before, Snapshot after) {}

    private static final String[] BUTTONS = {"first", "prev", "next", "last", "refresh"};

    private static final String READ_JS =
            "var ps=Array.from(document.querySelectorAll('.x-boundlist-floating')).filter(function(p){"
            + "return p.style.display!=='none'&&p.offsetParent!==null;});"
            + "if(!ps.length)return JSON.stringify({shown:false});var p=ps[ps.length-1];"
            + "var h=Array.from(p.querySelectorAll('.headerBoundList b')).map(function(b){return b.textContent.trim();});"
            + "var mism=0;var rows=Array.from(p.querySelectorAll('.x-boundlist-item')).map(function(it){"
            + "return Array.from(it.querySelectorAll('td[data-qtip]')).map(function(td){var t=td.textContent.trim();"
            + "if((td.getAttribute('data-qtip')||'').trim()!==t)mism++;return t;});});"
            + "var tb=p.querySelector('.x-grid-paging-toolbar');var b={};"
            + "var cls={first:'x-tbar-page-first',prev:'x-tbar-page-prev',next:'x-tbar-page-next',last:'x-tbar-page-last',refresh:'x-tbar-loading'};"
            + "['first','prev','next','last','refresh'].forEach(function(n){var ic=tb?tb.querySelector('.'+cls[n]):null;"
            + "var a=ic?ic.closest('a'):null;b[n]=a?(a.getAttribute('aria-disabled')!=='true'):null;});"
            + "var inp=tb?tb.querySelector('input'):null;var of=null;if(tb){tb.querySelectorAll('.x-toolbar-text').forEach(function(t){"
            + "var m=(t.textContent||'').match(/of\\s*(\\d+)/);if(m)of=parseInt(m[1],10);});}"
            + "return JSON.stringify({shown:true,headers:h,rows:rows,mism:mism,page:inp?parseInt(inp.value,10):null,of:of,b:b});";

    /** Reads the currently visible picker; {@link Snapshot#none()} when none is open or the read fails. */
    public static Snapshot snapshot() {
        try {
            Object raw = JavascriptExecutorUtils.executeScript(READ_JS);
            return parse(raw == null ? null : raw.toString());
        } catch (RuntimeException e) {
            return Snapshot.none();
        }
    }

    /** Parses the script's JSON (package-visible for tests). */
    public static Snapshot parse(String json) {
        try {
            if (json == null) return Snapshot.none();
            JsonNode n = new ObjectMapper().readTree(json);
            if (!n.path("shown").asBoolean(false)) return Snapshot.none();
            List<String> headers = new ArrayList<>();
            n.path("headers").forEach(h -> headers.add(h.asText()));
            List<List<String>> rows = new ArrayList<>();
            n.path("rows").forEach(r -> {
                List<String> cells = new ArrayList<>();
                r.forEach(c -> cells.add(c.asText()));
                rows.add(cells);
            });
            Map<String, Boolean> b = new LinkedHashMap<>();
            for (String k : BUTTONS) {
                JsonNode v = n.path("b").path(k);
                b.put(k, v.isNull() || v.isMissingNode() ? null : v.asBoolean());
            }
            Integer page = n.path("page").isInt() ? n.path("page").asInt() : null;
            Integer of = n.path("of").isInt() ? n.path("of").asInt() : null;
            return new Snapshot(true, headers, rows, n.path("mism").asInt(0), page, of, b);
        } catch (Exception e) {
            return Snapshot.none();
        }
    }

    // ── Checks ──────────────────────────────────────────────────────────────────────────────

    /** All results for one observation, as "Before fill" / "After fill" / "Before -> after fill" nodes. */
    public static List<UiValidationFailure> validate(Observation o) {
        String comp = "PICKER_" + o.label().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("_+$", "");
        List<UiValidationFailure> f = new ArrayList<>();
        f.addAll(UiValidationScope.node("Before fill", () -> loaded(o.before())
                ? state(comp, o, o.before())
                : UiComponentValidator.skip(comp, "picker list", "loaded before typing",
                        o.before() != null && o.before().shown()
                                ? "list not loaded until typing (Page " + o.before().page() + " of "
                                        + o.before().totalPages() + ", no rows)"
                                : "not shown before typing (the list opens on typing)")));
        f.addAll(UiValidationScope.node("After fill · typed '" + o.typed() + "'", () -> {
            List<UiValidationFailure> r = new ArrayList<>(state(comp, o, o.after()));
            if (o.after().shown()) r.addAll(filtered(comp, o));
            return r;
        }));
        if (loaded(o.before()) && o.after().shown()) {
            f.addAll(UiValidationScope.node("Before -> after fill", () -> change(comp, o.before(), o.after())));
        }
        return f;
    }

    /** A picker whose store has loaded: shown, with rows or a real page number (an unloaded list reads Page 0 of 0). */
    public static boolean loaded(Snapshot s) {
        return s != null && s.shown() && (!s.rows().isEmpty() || (s.page() != null && s.page() > 0));
    }

    /** Picker, header, rows and footer at one point. */
    public static List<UiValidationFailure> state(String comp, Observation o, Snapshot s) {
        List<UiValidationFailure> r = new ArrayList<>();
        r.add(row(comp, "element type", "Floating grid", s.shown() ? "Floating grid" : "not shown", s.shown()));
        if (!s.shown()) return r;
        String hc = comp + "_HEADER";
        r.add(row(hc, "columns", String.join(" | ", o.expectedColumns()), String.join(" | ", s.headers()),
                s.headers().equals(o.expectedColumns())));
        String rc = comp + "_ROWS";
        r.add(pass(rc, "rows on this page", String.valueOf(s.rows().size())));
        for (int i = 0; i < Math.min(3, s.rows().size()); i++) {
            r.add(pass(rc, "row " + (i + 1), String.join(" | ", s.rows().get(i))));
        }
        r.add(row(rc, "every cell tooltip equals its text", "0 mismatches", s.qtipMismatches() + " mismatches",
                s.qtipMismatches() == 0));
        r.addAll(footer(comp + "_FOOTER", s));
        return r;
    }

    public static List<UiValidationFailure> footer(String fc, Snapshot s) {
        List<UiValidationFailure> r = new ArrayList<>();
        for (String k : BUTTONS) {
            Boolean en = s.buttonsEnabled().get(k);
            r.add(row(fc, k + " button", "present", en == null ? "missing" : (en ? "present, enabled" : "present, disabled"),
                    en != null));
        }
        r.add(row(fc, "page", "Page n of M", "Page " + s.page() + " of " + s.totalPages(),
                s.page() != null && s.totalPages() != null && s.page() >= 1 && s.page() <= Math.max(1, s.totalPages())));
        if (s.page() != null && s.totalPages() != null) {
            boolean more = s.page() < s.totalPages();
            boolean first = s.page() > 1;
            r.add(enabledRule(fc, "first", first, s));
            r.add(enabledRule(fc, "prev", first, s));
            r.add(enabledRule(fc, "next", more, s));
            r.add(enabledRule(fc, "last", more, s));
        }
        return r;
    }

    private static UiValidationFailure enabledRule(String fc, String k, boolean expected, Snapshot s) {
        Boolean en = s.buttonsEnabled().get(k);
        return row(fc, k + " enabled (page " + s.page() + " of " + s.totalPages() + ")",
                expected ? "enabled" : "disabled", en == null ? "missing" : (en ? "enabled" : "disabled"),
                en != null && en == expected);
    }

    /** After typing: every row matches the text; the target row is present with its expected values. */
    public static List<UiValidationFailure> filtered(String comp, Observation o) {
        List<UiValidationFailure> r = new ArrayList<>();
        Snapshot s = o.after();
        int nameIdx = Math.max(0, s.headers().indexOf("Name"));
        String typed = o.typed() == null ? "" : o.typed().toLowerCase(Locale.ROOT);
        long misses = s.rows().stream().filter(c -> c.size() <= nameIdx
                || !c.get(nameIdx).toLowerCase(Locale.ROOT).contains(typed)).count();
        r.add(row(comp + "_ROWS", "every Name contains '" + o.typed() + "'", "0 rows without it", misses + " rows without it",
                misses == 0));
        int idIdx = s.headers().indexOf("Id");
        List<String> target = s.rows().stream()
                .filter(c -> c.size() > nameIdx && c.get(nameIdx).equalsIgnoreCase(o.targetName()))
                .filter(c -> o.targetId() == null || o.targetId().isBlank() || (idIdx >= 0 && c.size() > idIdx
                        && c.get(idIdx).equalsIgnoreCase(o.targetId())))
                .findFirst().orElse(null);
        String want = o.targetName() + (o.targetId() == null || o.targetId().isBlank() ? "" : " (Id " + o.targetId() + ")");
        if (target == null) {
            boolean morePages = s.totalPages() != null && s.page() != null && s.page() < s.totalPages();
            r.add(morePages
                    ? new UiValidationFailure(comp + "_TARGET", "target row", want, "not on page " + s.page()
                            + " of " + s.totalPages() + " (the fill pages forward to it)", UiValidationFailure.Status.SKIP)
                    : row(comp + "_TARGET", "target row", want, "not in the list", false));
            return r;
        }
        r.add(row(comp + "_TARGET", "target row", want, String.join(" | ", target), true));
        if (o.targetCells() != null) {
            for (Map.Entry<String, String> e : o.targetCells().entrySet()) {
                int i = s.headers().indexOf(e.getKey());
                String actual = i >= 0 && i < target.size() ? target.get(i) : "(column missing)";
                r.add(row(comp + "_TARGET", e.getKey(), e.getValue(), actual, actual.equalsIgnoreCase(e.getValue())));
            }
        }
        return r;
    }

    /** Before vs after typing: still on page 1, fewer or equal pages and rows, paging buttons follow the pages. */
    public static List<UiValidationFailure> change(String comp, Snapshot before, Snapshot after) {
        List<UiValidationFailure> r = new ArrayList<>();
        String fc = comp + "_FOOTER";
        r.add(row(fc, "page before -> after", "1 -> page holding the target", before.page() + " -> " + after.page(),
                Integer.valueOf(1).equals(before.page()) && after.page() != null && after.page() >= 1));
        boolean pagesOk = before.totalPages() != null && after.totalPages() != null
                && after.totalPages() <= before.totalPages();
        r.add(row(fc, "total pages before -> after", "after <= before", before.totalPages() + " -> " + after.totalPages(),
                pagesOk));
        r.add(row(comp + "_ROWS", "rows before -> after", "after <= before",
                before.rows().size() + " -> " + after.rows().size(), after.rows().size() <= before.rows().size()));
        return r;
    }

    private static UiValidationFailure row(String comp, String prop, String expected, String actual, boolean ok) {
        return new UiValidationFailure(comp, prop, expected, actual,
                ok ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL);
    }

    private static UiValidationFailure pass(String comp, String prop, String actual) {
        return new UiValidationFailure(comp, prop, "(recorded)", actual, UiValidationFailure.Status.PASS);
    }
}
