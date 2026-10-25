package com.gsl.validation.ui;

import com.gsl.pages.enums.Finance.Operations.Common.FormSection;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.FoldableSectionRenderer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared rendering for every {@code validateUiContract(...)} call across GV/AR/AP (and, going
 * forward, any other page adopting this framework). Builds the "UI Validation" step-tree —
 * mirrors the existing "Database Snapshot Validation" step's Validation Summary / Exceptions /
 * Full Validation Details shape ({@code SnapshotValidationReporter#render}), swapping in only the
 * genuinely UI-contract-equivalent children — populates {@link ActualResultsHolder} for the
 * Actual Results merge ({@code ActualResultsLogger}'s own per-field row appends reuse
 * {@link #rowsFor}), and soft-asserts only the FAIL entries.
 *
 * <p>"UI Full Validation Details" is a nested tree built from each result's
 * {@link UiValidationFailure#path()} (see {@link UiValidationScope}); results created outside any
 * scope fall back to a name-prefix group ({@link #fallbackPath}). Every node is collapsed by
 * default. Every node header uses the same fixed right-hand columns (counts / section state /
 * Pass-Fail) at every nesting level — nesting only indents the title — so the Pass/Fail badge
 * lines up whether a node is open or collapsed.
 */
public final class UiValidationReporter {

    private UiValidationReporter() {}

    private static final String[] HEADER = {"Component", "Property", "Expected", "Actual", "Status"};

    /** Fixed column widths shared by every node header and every table (see class Javadoc). */
    private static final String HEADER_GRID =
            "display:grid;grid-template-columns:minmax(0,1fr) 200px 70px 92px;gap:8px;align-items:center;"
            + "flex:1 1 auto;min-width:0;text-transform:none;letter-spacing:0;font-size:12px;"
            + "color:#1E293B;word-break:keep-all";
    private static final String[] TABLE_WIDTHS = {"26%", "15%", "23%", "23%", "92px"};

    /** Scoped CSS for the "UI Full Validation Details" tree (emitted once): left chevron that follows
     *  `details[open]`, the renderer's own right chevron hidden, normal-case titles and table headers,
     *  and chips. Everything short is `keep-all` so Extent's global `td{word-break:break-all}` cannot
     *  split a badge or chip across lines. */
    private static final String UV_CSS = "<style>"
            + ".uv-root summary.rs-sub.uv{text-transform:none;letter-spacing:0;color:#1E293B;font-size:12px;padding:6px 12px}"
            + ".uv-root summary.uv .rs-chev{display:none}"
            + ".uv-root summary.uv::before{content:'\\25B8';display:inline-block;width:12px;margin-right:4px;font-size:11px;color:#64748B;flex:none}"
            + ".uv-root details[open]>summary.uv::before{content:'\\25BE'}"
            + ".uv-root .rs-tbl th{text-transform:none;letter-spacing:0;word-break:keep-all}"
            + ".uv-root .rs-tbl th:last-child{text-align:right!important}"
            + ".uv-root .uv-kids{margin-left:14px;padding-left:8px;border-left:2px solid #CBD5E1}"
            + ".uv-root .uv-kids .uv-kids .uv-kids{border-left-color:#E2E8F0}"
            + ".uv-root .uv-title{display:flex;align-items:center;gap:8px;min-width:0;overflow:hidden}"
            + ".uv-root .uv-title .t{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-weight:500}"
            + ".uv-root .uv-chips{display:flex;gap:6px;flex-wrap:wrap;padding:4px 12px 6px 28px;background:#F8FAFC}"
            + ".uv-root .uv-chip{font-size:11px;padding:1px 8px;border-radius:99px;white-space:nowrap;word-break:keep-all;overflow-wrap:normal;flex:none;background:#E6F1FB;color:#0C447C}"
            + ".uv-root .uv-chip.n{background:#F0F7FF;color:#5B84B1}"
            + "</style>";

    private static final java.util.regex.Pattern ROW_TAGS =
            java.util.regex.Pattern.compile("^(.*?)((?: · (?:Sub Ledger|Site|Payment): (?:yes|no))+)$");
    private static final java.util.regex.Pattern ONE_TAG =
            java.util.regex.Pattern.compile(" · (Sub Ledger|Site|Payment): (yes|no)");

    /** Renders the "UI Validation" step-tree (must be called from inside a
     *  {@code StepExecutor.step("UI Validation", ...)} lambda — the child nodes this creates land
     *  under whatever step is currently active) and parks the FAIL entries in {@link UiFailureCollector}.
     *
     *  <p><b>UI validation is ALWAYS soft.</b> This method deliberately never throws: a UI defect must
     *  not stop Save / DB validation / snapshot validation. The flow's single final assertion
     *  ({@code VoucherSaveFlowSteps#finalizeAsserts} for business flows, {@link UiFailureCollector#assertNoPending}
     *  for UI-only tests) reports the parked failures together with any DB failure. The name is kept so
     *  existing callers stay safe without edits; new code should call {@link #renderDeferred} directly.
     *  {@code UiSoftAssertGuardTest} fails the build if anything else soft-asserts UI results. */
    public static void renderAndAssert(List<UiValidationFailure> results) {
        renderDeferred(results);
    }

    /** Renders the report nodes; the Actual Results rows are
     *  produced identically, and the FAIL entries are parked in {@link UiFailureCollector} so the
     *  flow's later steps (Save, DB validation, snapshot validation) still run. The collector is
     *  asserted once at the flow's final assertion point, combined with any DB/snapshot failure.
     *  Results are APPENDED to the Actual Results holder across several calls (checkpoints). */
    public static void renderDeferred(List<UiValidationFailure> results) {
        List<UiValidationFailure> held = drainHeld();
        List<UiValidationFailure> merged = new ArrayList<>(held);
        merged.addAll(results);
        renderAndRecord(merged);
        UiFailureCollector.addAll(results);   // held results were parked in the collector when they were held
    }

    private static void renderAndRecord(List<UiValidationFailure> results) {
        renderStepTree(results);
        List<UiValidationFailure> all = new ArrayList<>();
        List<UiValidationFailure> prior = ActualResultsHolder.get().getUiValidationResults();
        if (prior != null) all.addAll(prior);
        all.addAll(results);
        ActualResultsHolder.get().setUiValidationResults(all);
    }

    private static final ThreadLocal<List<UiValidationFailure>> HELD = ThreadLocal.withInitial(ArrayList::new);

    /**
     * Holds pre-fill results so the Create flow reports ONE consolidated tree: the next {@link #renderDeferred}
     * prepends them. Failures are parked in {@link UiFailureCollector} immediately, so nothing is lost if the
     * flow dies before the rendering step ({@link #flushHeld} then still shows them).
     */
    public static void holdForFill(List<UiValidationFailure> results) {
        HELD.get().addAll(results);
        UiFailureCollector.addAll(results);
    }

    /** Returns and clears the held results (package-visible for tests). */
    static List<UiValidationFailure> drainHeld() {
        List<UiValidationFailure> held = new ArrayList<>(HELD.get());
        HELD.get().clear();
        return held;
    }

    /** Renders anything still held (flow ended before the consolidated render); no-op otherwise. */
    public static void flushHeld() {
        List<UiValidationFailure> held = drainHeld();
        if (!held.isEmpty()) renderAndRecord(held);
    }

    /** Drops held results (per-test reset). */
    public static void clearHeld() {
        HELD.get().clear();
    }

    // ── Step tree ──────────────────────────────────────────────────────────────────────────

    private static void renderStepTree(List<UiValidationFailure> results) {
        long failCount = results.stream().filter(UiValidationFailure::isFail).count();
        long skipCount = results.stream().filter(UiValidationFailure::isSkip).count();
        long knownCount = results.stream().filter(UiValidationFailure::isKnownIssue).count();
        long passCount = results.size() - failCount - skipCount - knownCount;

        // One summary node (counts + which sections failed) — the former separate "Exceptions"
        // table is gone: every failure is already highlighted in the tree below and listed once in
        // the test's final combined failure message.
        ExtentLogger.infoHtmlOnChildNode("UI Validation Summary",
                buildSummaryHtml(results.size(), passCount, failCount, skipCount, knownCount)
                        + failingSectionsHtml(results)
                        + "<div style='padding:0 14px 8px;font-size:11px;color:#64748B'>Style baseline — "
                        + esc(UiStyleBaseline.describe()) + "</div>");

        // Known issues (registered in UiKnownIssues): amber, visible, non-blocking — same shape as the
        // DB snapshot validator's "Known Issues" card. Also lists registered issues whose check now
        // passes so the registry gets cleaned up.
        List<UiValidationFailure> known = results.stream().filter(UiValidationFailure::isKnownIssue).toList();
        List<UiKnownIssue> resolved = resolvedKnownIssues(results);
        if (!known.isEmpty() || !resolved.isEmpty()) {
            ExtentLogger.infoHtmlOnChildNode("Known Issues",
                    FoldableSectionRenderer.foldableTableWithSummary(FoldableSectionRenderer.ICO_WARNING,
                            "⚠️ Known Issues", true,
                            known.size() + " known issue(s) — visible, non-blocking"
                                    + (resolved.isEmpty() ? "" : "; " + resolved.size()
                                    + " now resolved — remove from UiKnownIssues"),
                            buildKnownIssuesTable(known, resolved), KNOWN_WIDTHS));
        }

        String summaryLine = "<div style='padding:6px 14px 2px;font-size:11px;color:#64748B'>"
                + "Checks: " + results.size() + " | Passed: " + passCount + " | Failed: " + failCount
                + (skipCount > 0 ? " | Skipped: " + skipCount : "")
                + (knownCount > 0 ? " | Known issues: " + knownCount : "") + "</div>";
        ExtentLogger.infoHtmlOnChildNode("UI Full Validation Details",
                FoldableSectionRenderer.foldableSection(FoldableSectionRenderer.ICO_TABLE,
                        "UI Full Validation Details", false,
                        UV_CSS + "<div class='uv-root'>" + summaryLine + buildTreeHtml(results) + "</div>"));
    }

    // ── Tree ───────────────────────────────────────────────────────────────────────────────

    private static final class Node {
        final String name;
        final Map<String, Node> children = new LinkedHashMap<>();
        final List<UiValidationFailure> direct = new ArrayList<>();

        Node(String name) { this.name = name; }

        void collect(List<UiValidationFailure> out) {
            out.addAll(direct);
            children.values().forEach(c -> c.collect(out));
        }
    }

    private static String buildTreeHtml(List<UiValidationFailure> results) {
        Node root = new Node("");
        for (UiValidationFailure r : results) {
            List<String> path = r.path().isEmpty() ? fallbackPath(r.component()) : r.path();
            Node n = root;
            for (String segment : path) {
                n = n.children.computeIfAbsent(segment, Node::new);
            }
            n.direct.add(r);
        }
        StringBuilder sb = new StringBuilder();
        orderNodes(root.children.values(), n -> n.name, true).forEach(top -> renderNode(sb, top, 0));
        return sb.toString();
    }

    /**
     * Display order for sibling report nodes, shared by both report trees. Results are produced in the order the
     * flow runs its checks (e.g. the blank grid footer before any row exists), so layout is decided here, not by
     * emission order: top-level sections run Document Information, the grid section, Payment Information, Page
     * Footer; inside any node a "Grid footer" comes last. Everything else keeps first-appearance order (stable sort).
     */
    public static <T> List<T> orderNodes(java.util.Collection<T> nodes, java.util.function.Function<T, String> name,
            boolean topLevel) {
        List<T> ordered = new ArrayList<>(nodes);
        ordered.sort(java.util.Comparator.comparingInt(n -> nodeRank(name.apply(n), topLevel)));
        return ordered;
    }

    private static int nodeRank(String rawName, boolean topLevel) {
        String n = rawName == null ? "" : rawName.toLowerCase(java.util.Locale.ROOT);
        if (!topLevel) return n.startsWith("grid footer") ? 1 : 0;
        if (n.contains("document information")) return 0;
        if (n.contains("payment information")) return 2;
        if (n.contains("page footer")) return 3;
        return 1; // grid section (Expense/Income Detail, Sub Ledger Information, ...)
    }

    /** One collapsed {@code <details>} per node: fixed-column header, then this node's own table
     *  (if it has direct results), then its child nodes. */
    private static void renderNode(StringBuilder sb, Node node, int depth) {
        List<UiValidationFailure> all = new ArrayList<>();
        node.collect(all);
        long fail = all.stream().filter(UiValidationFailure::isFail).count();
        long skip = all.stream().filter(UiValidationFailure::isSkip).count();
        long known = all.stream().filter(UiValidationFailure::isKnownIssue).count();
        long checked = all.size() - skip;

        String counts = checked + " checked, " + fail + " failed"
                + (known > 0 ? ", " + known + " known issue" + (known > 1 ? "s" : "") : "")
                + (skip > 0 ? ", " + skip + " skipped" : "");
        // A node with only known issues (no real failure) is amber, not red.
        String resultBadge = fail > 0 ? FoldableSectionRenderer.badge(FoldableSectionRenderer.Status.FAIL)
                : known > 0 ? FoldableSectionRenderer.badge(FoldableSectionRenderer.Status.KNOWN_ISSUE)
                : checked == 0 ? FoldableSectionRenderer.badge(FoldableSectionRenderer.Status.SKIP)
                : FoldableSectionRenderer.badge(FoldableSectionRenderer.Status.PASS);

        String headerHtml = "<span style='" + HEADER_GRID + "'>"
                + "<span title='" + esc(node.name).replace("'", "&#39;") + "' class='uv-title'"
                + (depth == 0 ? " style='text-transform:uppercase;letter-spacing:.04em'" : "") + ">"
                + titleWithChips(node.name)
                + "</span>"
                + "<span style='text-align:right;font-size:11px;color:#64748B;white-space:nowrap'>" + esc(counts) + "</span>"
                + "<span style='text-align:center'>" + stateBadge(node) + "</span>"
                + "<span style='text-align:right'>" + resultBadge + "</span>"
                + "</span>";

        sb.append(FoldableSectionRenderer.sectionOpen("", "rs-sub uv", "", headerHtml));
        if (!node.direct.isEmpty()) {
            sb.append(FoldableSectionRenderer.tableHtml(toTable(node.direct), TABLE_WIDTHS));
        }
        // Children render inside an indented, guide-lined block: header bar, table and deeper children of a
        // child all move together, so the hierarchy is readable from position alone.
        if (!node.children.isEmpty()) {
            sb.append("<div class='uv-kids'>");
            orderNodes(node.children.values(), n -> n.name, false).forEach(c -> renderNode(sb, c, depth + 1));
            sb.append("</div>");
        }
        sb.append(FoldableSectionRenderer.sectionClose());
    }

    /** Escaped node title; trailing " · Sub Ledger: yes · Site: no · Payment: yes" tags become inline chips in the
     *  SAME header line (used by both report trees). */
    public static String titleWithChips(String name) {
        java.util.regex.Matcher tags = ROW_TAGS.matcher(name);
        if (!tags.matches()) return "<span class='t'>" + esc(name) + "</span>";
        StringBuilder sb = new StringBuilder("<span class='t'>" + esc(tags.group(1)) + "</span>");
        java.util.regex.Matcher one = ONE_TAG.matcher(tags.group(2));
        while (one.find()) {
            sb.append(chip(one.group(1) + ": " + one.group(2), "yes".equals(one.group(2))));
        }
        return sb.toString();
    }

    private static String chip(String text, boolean active) {
        return "<span class='uv-chip" + (active ? "" : " n") + "' style='flex:none;font-size:11px;padding:1px 8px;border-radius:99px;"
                + "white-space:nowrap;background:" + (active ? "#E6F1FB;color:#0C447C" : "#F0F7FF;color:#5B84B1") + "'>" + esc(text) + "</span>";
    }

    /** OPEN/CLOSED badge from this node's own "section state" result (empty when it has none). */
    private static String stateBadge(Node node) {
        for (UiValidationFailure f : node.direct) {
            String c = f.component() == null ? "" : f.component().toUpperCase();
            if (c.startsWith("SECTION_") && !c.startsWith("SECTION_HEADER_") && "section state".equals(f.property())) {
                String actual = f.actual() == null ? "?" : f.actual().trim().toUpperCase();
                boolean open = "OPEN".equals(actual);
                return "<span style='display:inline-block;font-size:10px;padding:1px 6px;border-radius:4px;"
                        + (open ? "background:#EAF3DE;color:#27500A" : "background:#FEF3C7;color:#92400E") + "'>"
                        + esc(actual) + "</span>";
            }
        }
        return "";
    }

    /** Tree path for a result created outside any {@link UiValidationScope} node, derived from its
     *  component-name prefix — keeps Document Information / Page Footer / legacy callers grouped.
     *  Unknown prefixes fall back to "Other". */
    private static List<String> fallbackPath(String component) {
        String c = component == null ? "" : component.toUpperCase();
        if (c.startsWith("SECTION_HEADER_DOCUMENT_INFORMATION") || c.startsWith("SECTION_DOCUMENT_INFORMATION")
                || c.startsWith("OWNER_SITE") || c.startsWith("CASH_BANK") || c.startsWith("DATE")
                || c.startsWith("REMARKS") || c.startsWith("GV_NET_AMOUNT") || c.startsWith("AP_ON_ACCOUNT")) {
            return List.of(FormSection.DOCUMENT_INFORMATION.title());
        }
        if (c.startsWith("SECTION_EXPENSE_INCOME") || c.startsWith("SECTION_HEADER_EXPENSE_INCOME")) {
            return List.of(FormSection.EXPENSE_INCOME_DETAIL.title());
        }
        if (c.startsWith("SECTION_PAYMENT_INFORMATION")) {
            return List.of(FormSection.PAYMENT_INFORMATION.title());
        }
        if (c.startsWith("SECTION_SUB_LEDGER") || c.startsWith("SECTION_HEADER_SUB_LEDGER")
                || c.startsWith("SUB_LEDGER_")) {
            return List.of(FormSection.SUB_LEDGER_INFORMATION.title());
        }
        if (c.startsWith("SECTION_ADJUSTMENT") || c.startsWith("ADJUSTMENT_INFORMATION")) {
            return List.of("Adjustment Information");
        }
        if (c.startsWith("GRID_FOOTER_") || c.startsWith("GV_GRID_FOOTER_")) {
            return List.of("Grid Footer");
        }
        if (c.startsWith("GRID_")) {
            return List.of("Grid");
        }
        if (c.startsWith("SAVE_BUTTON") || c.startsWith("FOOTER_BUTTON_")) {
            return List.of("Page Footer");
        }
        if (c.startsWith("ALLOCATE_SITES_")) {
            return List.of("Allocate Sites");
        }
        if (c.startsWith("PAYMENT_INFO_")) {
            return List.of(FormSection.PAYMENT_INFORMATION.title());
        }
        if (c.startsWith("ADJUSTMENT_ENTRY_")) {
            return List.of("Adjustment Entry");
        }
        return List.of("Other");
    }

    /** "Failing sections: Grid header and toolbar (2), Page Footer (1)" — empty when nothing failed. */
    private static String failingSectionsHtml(List<UiValidationFailure> results) {
        Map<String, Integer> bySection = new LinkedHashMap<>();
        for (UiValidationFailure r : results) {
            if (!r.isFail()) continue;
            List<String> path = r.path().isEmpty() ? fallbackPath(r.component()) : r.path();
            bySection.merge(path.get(0), 1, Integer::sum);
        }
        if (bySection.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("<div style='padding:0 14px 8px;font-size:12px;color:#B91C1C'>"
                + "<b>Failing sections:</b> ");
        boolean first = true;
        for (Map.Entry<String, Integer> e : bySection.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(esc(e.getKey())).append(" (").append(e.getValue()).append(")");
            first = false;
        }
        return sb.append("</div>").toString();
    }

    private static final String[] KNOWN_WIDTHS = {"20%", "12%", "17%", "17%", "26%", "92px"};

    /** Registered known issues whose component+property was checked in {@code results} and PASSED
     *  (and none of those checks is itself still a known issue) — the app is fixed, so the entry is stale. */
    private static List<UiKnownIssue> resolvedKnownIssues(List<UiValidationFailure> results) {
        List<UiKnownIssue> resolved = new ArrayList<>();
        for (UiKnownIssue e : UiKnownIssues.all()) {
            boolean passed = results.stream().anyMatch(r -> r.isPass() && e.sameCheck(r.component(), r.property()));
            boolean stillKnown = results.stream().anyMatch(r -> r.isKnownIssue() && e.sameCheck(r.component(), r.property()));
            if (passed && !stillKnown) resolved.add(e);
        }
        return resolved;
    }

    private static String[][] buildKnownIssuesTable(List<UiValidationFailure> known, List<UiKnownIssue> resolved) {
        String[][] table = new String[known.size() + resolved.size() + 1][];
        table[0] = new String[]{"Component", "Property", "Expected", "Actual", "Note", "Status"};
        int i = 1;
        for (UiValidationFailure f : known) {
            table[i++] = amber(new String[]{
                    esc(humanize(f.component())), esc(f.property()),
                    colorAwareValue(f.property(), f.expected()), colorAwareValue(f.property(), f.actual()),
                    esc(f.note()),
                    "<div style='text-align:right;white-space:nowrap;word-break:keep-all'>"
                            + FoldableSectionRenderer.badge(FoldableSectionRenderer.Status.KNOWN_ISSUE) + "</div>"});
        }
        for (UiKnownIssue e : resolved) {
            table[i++] = new String[]{
                    esc(humanize(e.component())), esc(e.property()), "—", "now passes",
                    "Known issue now resolved — remove it from UiKnownIssues",
                    "<div style='text-align:right;white-space:nowrap;word-break:keep-all'>"
                            + FoldableSectionRenderer.badge(FoldableSectionRenderer.Status.PASS) + "</div>"};
        }
        return table;
    }

    /** Amber text for a known-issue row (matches the DB snapshot report's {@code amberRow}). */
    private static String[] amber(String[] row) {
        String[] styled = new String[row.length];
        for (int j = 0; j < row.length; j++) {
            styled[j] = row[j] == null ? null : "<span style='color:#e65100'>" + row[j] + "</span>";
        }
        return styled;
    }

    private static String buildSummaryHtml(int total, long passed, long failed, long skipped, long known) {
        String overall = failed == 0 ? "✓ Pass" : "✗ Fail";
        return FoldableSectionRenderer.STATUS_LEGEND
                + "<div style='padding:8px 14px;font-size:12px;color:#1E293B'>"
                + "<b>Fields Checked:</b> " + total
                + " &nbsp; <b>Passed:</b> " + passed
                + " &nbsp; <b>Failed:</b> " + failed
                + (known > 0 ? " &nbsp; <b>Known issues:</b> " + known : "")
                + (skipped > 0 ? " &nbsp; <b>Skipped:</b> " + skipped : "")
                + " &nbsp; <b>Overall Result:</b> " + overall
                + "</div>";
    }

    /** One group per component: a bold component heading row (name shown once, with the group's
     *  checked/failed counts), then one row per property — so a component with seven style
     *  properties is not repeated seven times. */
    private static String[][] toTable(List<UiValidationFailure> results) {
        // Group by component, ordered by first appearance (a field's label/input/state checks are
        // emitted interleaved with its marker/style checks, so grouping must not need adjacency).
        Map<String, List<UiValidationFailure>> groups = groupByComponent(results);
        List<String[]> rows = new ArrayList<>();
        rows.add(HEADER);
        for (Map.Entry<String, List<UiValidationFailure>> g : groups.entrySet()) {
            rows.add(groupHeadingRow(displayName(g.getKey()), g.getValue()));
            for (UiValidationFailure f : g.getValue()) {
                String actualCell = colorAwareValue(f.property(), f.actual());
                if (f.isKnownIssue()) {
                    actualCell += "<div style='font-size:11px;margin-top:2px'>" + esc(f.note()) + "</div>";
                }
                String[] row = new String[]{
                        "<span style='padding-left:14px;color:#64748B'>&#8627;</span>", esc(propertyLabel(f)),
                        colorAwareValue(f.property(), f.expected()),
                        actualCell,
                        "<div style='text-align:right;white-space:nowrap;word-break:keep-all'>" + FoldableSectionRenderer.badge(toRendererStatus(f.status())) + "</div>"
                };
                rows.add(f.isKnownIssue() ? amber(row) : row);
            }
        }
        return rows.toArray(new String[0][]);
    }

    private static String[] groupHeadingRow(String name, List<UiValidationFailure> group) {
        long fail = group.stream().filter(UiValidationFailure::isFail).count();
        long skip = group.stream().filter(UiValidationFailure::isSkip).count();
        String counts = (group.size() - skip) + " checked, " + fail + " failed" + (skip > 0 ? ", " + skip + " skipped" : "");
        return new String[]{
                "<b style='color:#1E293B'>" + esc(name) + "</b>",
                "<span style='font-size:11px;color:#64748B'>" + esc(counts) + "</span>", "", "", ""};
    }

    // ── Actual Results merge support ──────────────────────────────────────────────────────

    /** Every result whose component name starts with (any of) {@code componentPrefixes},
     *  case-insensitively — e.g. {@code rowsFor(results, "OWNER_SITE")} pulls Owner Site's own
     *  label/input/state checks together, for {@code ActualResultsLogger} to append directly
     *  under that field's existing business-match row. */
    public static List<UiValidationFailure> rowsFor(List<UiValidationFailure> results, String... componentPrefixes) {
        return results.stream()
                .filter(r -> {
                    String c = r.component().toUpperCase();
                    for (String prefix : componentPrefixes) {
                        if (c.startsWith(prefix.toUpperCase())) return true;
                    }
                    return false;
                })
                .toList();
    }

    /** Human field label for one contract-check row, e.g. "Owner site — font-size" — component
     *  name humanized (title case, underscores to spaces) + the raw property name. A result created
     *  inside a "Row N …" scope node is prefixed with "Row N / " so its row is identifiable. */
    public static String fieldLabel(UiValidationFailure f) {
        StringBuilder prefix = new StringBuilder();
        for (String segment : f.path()) {
            if (segment.equals(FormSection.EXPENSE_INCOME_DETAIL.title())) continue; // implied by the section header
            int cut = segment.indexOf(" ·");
            prefix.append(cut > 0 ? segment.substring(0, cut) : segment).append(" / ");
        }
        return prefix + humanize(f.component()) + " — " + f.property();
    }

    /** Label without the path prefix — for views that already show the path as enclosing nodes. */
    public static String leafLabel(UiValidationFailure f) {
        return humanize(f.component()) + " — " + f.property();
    }

    /**
     * Results grouped per FIELD, ordered by first appearance: a field's label and required-marker results
     * ({@code X_LABEL}, {@code X_UI_MANDATORY}) fold into the group of {@code X} when {@code X} is also present, so
     * one field = one group (as in the model sample) instead of three. {@link #propertyLabel} keeps the folded rows
     * distinguishable ("label font-size", "required marker color").
     */
    public static Map<String, List<UiValidationFailure>> groupByComponent(List<UiValidationFailure> results) {
        java.util.Set<String> present = new java.util.HashSet<>();
        for (UiValidationFailure f : results) present.add(f.component());
        Map<String, List<UiValidationFailure>> groups = new LinkedHashMap<>();
        for (UiValidationFailure f : results) {
            groups.computeIfAbsent(groupKey(f.component(), present), k -> new ArrayList<>()).add(f);
        }
        return groups;
    }

    private static String groupKey(String component, java.util.Set<String> present) {
        for (String suffix : new String[]{"_UI_MANDATORY", "_LABEL"}) {
            if (component.endsWith(suffix)) {
                String base = component.substring(0, component.length() - suffix.length());
                if (present.contains(base)) return groupKey(base, present);
            }
        }
        return component;
    }

    /** Property text for a row inside its (possibly folded) group. */
    public static String propertyLabel(UiValidationFailure f) {
        String c = f.component() == null ? "" : f.component();
        String p = f.property();
        if (c.endsWith("_UI_MANDATORY")) {
            return "mandatory".equals(p) ? p : "required marker " + p;
        }
        if (c.endsWith("_LABEL") && !"label text".equals(p) && !p.startsWith("label ")) {
            return "label " + p;
        }
        return p;
    }

    /** Group heading: the baseline workbook's {@code DisplayName} for the component, else the humanised name. */
    public static String displayName(String component) {
        try {
            String d = UiStyleBaseline.attribute(component, UiStyleBaseline.DISPLAY_NAME);
            if (d != null && !d.isBlank()) return d;
        } catch (RuntimeException ignored) {
            // baseline unavailable - fall back to the humanised name
        }
        return humanize(component);
    }

    /** Group heading text for a component (workbook DisplayName, else humanised). */
    public static String humanizeComponent(String component) {
        return displayName(component);
    }

    /** "3 checked, 1 failed, 1 skipped" for one group of results. */
    public static String groupCounts(List<UiValidationFailure> group) {
        long fail = group.stream().filter(UiValidationFailure::isFail).count();
        long skip = group.stream().filter(UiValidationFailure::isSkip).count();
        return (group.size() - skip) + " checked, " + fail + " failed" + (skip > 0 ? ", " + skip + " skipped" : "");
    }

    private static String humanize(String component) {
        String[] words = component.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            if (w.equals("ui")) {
                sb.append("UI");
            } else {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return sb.toString();
    }

    public static FoldableSectionRenderer.Status toRendererStatus(UiValidationFailure.Status status) {
        return switch (status) {
            case PASS -> FoldableSectionRenderer.Status.PASS;
            case SKIP -> FoldableSectionRenderer.Status.SKIP;
            case FAIL -> FoldableSectionRenderer.Status.FAIL;
            case KNOWN_ISSUE -> FoldableSectionRenderer.Status.KNOWN_ISSUE;
        };
    }

    /** For a color property ({@code color}/{@code background-color}/{@code border-color}),
     *  prefixes a small colored square swatch before the raw {@code rgb(...)} text — pass or fail
     *  alike, per the user's explicit ask. Non-color properties return the plain escaped value.
     *  Callers must treat the return value as already-safe HTML (do not re-escape it). */
    public static String colorAwareValue(String property, String value) {
        String escaped = esc(value);
        if (property == null || value == null) return escaped;
        String p = property.toLowerCase();
        boolean isColor = p.equals("color") || p.equals("background-color") || p.equals("border-color");
        if (!isColor || !value.trim().toLowerCase().startsWith("rgb")) return escaped;
        return "<span style='display:inline-block;width:12px;height:12px;border:1px solid #CBD5E1;"
                + "border-radius:2px;margin-right:5px;vertical-align:middle;background:"
                + value.trim() + "'></span>" + escaped;
    }

    private static String esc(String s) {
        if (s == null) return "—";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
