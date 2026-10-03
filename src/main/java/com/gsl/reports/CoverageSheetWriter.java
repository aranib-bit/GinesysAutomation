package com.gsl.reports;

import com.gsl.reports.CoverageStats.Counts;
import com.gsl.reports.CoverageStats.RowState;
import com.gsl.reports.CoverageWorkbookBuilder.JoinResult;
import com.gsl.reports.CoverageWorkbookBuilder.Matched;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.fixture.finance.ActiveGroupFilters;
import com.gsl.utils.dataprovider.fixture.finance.CoverageRow;
import com.gsl.utils.io.XlsxBook;
import com.gsl.utils.io.XlsxCharts;
import com.gsl.utils.io.XlsxColumn;
import com.gsl.utils.io.XlsxColumn.Kind;
import com.gsl.utils.io.XlsxLink;
import com.gsl.utils.io.XlsxPivots;
import com.gsl.utils.io.XlsxStyle;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lays out the sheets of {@code Voucher_Coverage_Results.xlsx} on top of the domain-free
 * {@link XlsxBook}. This class decides WHAT each sheet shows (columns, ordering, colours, which
 * numbers feed which chart); the generic book/chart/pivot mechanics live in {@code utils.io}.
 * Package-private: only {@link CoverageWorkbookBuilder} drives it.
 *
 * <p>Every count, duration and percentage is written as a numeric cell with a number format;
 * the only text in numeric columns is a header. Each sheet's job is stated in its banner.
 */
final class CoverageSheetWriter {

    // palette ------------------------------------------------------------------------------------
    static final String NAVY = "1F3864";
    static final String BLUE = "2F5597";
    static final String PASS_BG = "C6EFCE", PASS_FG = "006100";
    static final String FAIL_BG = "FFC7CE", FAIL_FG = "9C0006";
    static final String SKIP_BG = "FFEB9C", SKIP_FG = "9C5700";
    static final String GREY_BG = "EDEDED", GREY_FG = "595959";
    static final String SNR_BG = "FCE4D6", SNR_FG = "833C0B";
    static final String NR_BG = "D9D9D9", NR_FG = "404040";
    static final String DBX_BG = "DDEBF7", DBX_FG = "1F4E79";
    static final String C_PASS = "4CAF50", C_FAIL = "E15759", C_SKIP = "F2B134", C_BLUE = "4E79A7", C_GREY = "9AA5B1";

    /** Row index of the table header on every data sheet (banner, note, spacer above it). */
    static final int HDR = 3;

    static final String SHEET_DASH = "Dashboard";
    static final String SHEET_EXEC = "Execution Results";
    static final String SHEET_DETAILS = "Test Details";
    static final String SHEET_FAILS = "Failure Details";
    static final String SHEET_PIVOT = "Pivot";
    static final String SHEET_GV = "GV Coverage";
    static final String SHEET_ARAP = "AR AP Coverage";
    static final String SHEET_MATRIX = "Group Matrix";
    static final String SHEET_NOTES = "Notes";

    /** System property that switches the native PivotTables off ({@code -Dcoverage.workbook.pivots=false}). */
    static final String PIVOT_PROPERTY = "coverage.workbook.pivots";

    private final XlsxBook book;
    private final Path outputPath;
    private final JoinResult join;
    private final CoverageStats stats;
    private final RunMeta meta;
    private final ActiveGroupFilters filters;
    private final String reportFileName;
    /** Results in display order: failures first, then skips, then passes (stable). */
    private final List<CoverageResult> ordered;

    // Execution Results geometry, referenced by the Pivot sheet's formulas and PivotTables.
    private int erFirstRow, erLastRow;
    private static final int ER_COLS = 22;
    private static final int COL_MODULE = 1, COL_FLOW = 2, COL_ID = 3, COL_GROUP = 6, COL_STATUS = 9, COL_CATEGORY = 17;

    CoverageSheetWriter(XlsxBook book, Path outputPath, JoinResult join, List<CoverageResult> results,
                        CoverageStats stats, RunMeta meta, ActiveGroupFilters filters, String reportFileName) {
        this.book = book;
        this.outputPath = outputPath;
        this.join = join;
        this.stats = stats;
        this.meta = meta == null ? RunMeta.builder().build() : meta;
        this.filters = filters == null ? ActiveGroupFilters.none() : filters;
        this.reportFileName = reportFileName;
        List<CoverageResult> sorted = new ArrayList<>(results);
        sorted.sort(Comparator.comparingInt(r -> statusRank(r.getStatus())));
        this.ordered = sorted;
    }

    static int statusRank(String status) {
        return switch (status) {
            case "FAIL" -> 0;
            case "SKIP" -> 1;
            default -> 2;
        };
    }

    /** Writes every sheet in tab order. Each optional visual is isolated so one failure cannot lose the others. */
    void writeAll() {
        dashboard();            // sheet order matters; Dashboard is created first, filled after the data sheets
        executionResults();
        testDetails();
        failureDetails();
        pivot();
        gvCoverage();
        arApCoverage();
        groupMatrix();
        notes();
        fillDashboard();
    }

    // ============================================================================================
    // Dashboard
    // ============================================================================================

    private XSSFSheet dash;

    private void dashboard() {
        dash = book.sheet(SHEET_DASH, "2E7D32", false);
    }

    private void fillDashboard() {
        XSSFSheet s = dash;
        book.setWidths(s, 2, 24, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 2);
        XlsxStyle banner = XlsxStyle.builder().fillRgb(NAVY).fontRgb("FFFFFF").bold(true).fontSize(18)
                .align(XlsxStyle.Align.LEFT).build();
        book.merged(s, 0, 1, 0, 12, "Voucher Test Coverage & Results  |  General Voucher  -  AR  -  AP", banner);
        book.rowHeight(s, 0, 38f);
        XlsxStyle sub = XlsxStyle.builder().fillRgb(BLUE).fontRgb("FFFFFF").fontSize(10).align(XlsxStyle.Align.LEFT).build();
        book.merged(s, 1, 1, 1, 12, subtitle(), sub);
        book.rowHeight(s, 1, 22f);
        XlsxStyle filt = XlsxStyle.builder().fillRgb("FFF8E1").fontRgb("7A5C00").italic(true).wrap(true)
                .align(XlsxStyle.Align.LEFT).border(true).borderRgb("F0D98A").build();
        book.merged(s, 2, 1, 2, 12, filterLine(), filt);
        book.rowHeight(s, 2, 34f);

        Counts t = stats.total;
        book.kpiTile(s, 4, 1, 2, "PLANNED", t.planned, "#,##0", "EAF1FB", NAVY);
        book.kpiTile(s, 4, 3, 2, "SELECTED (after filter)", t.selected(), "#,##0", "EAF1FB", NAVY);
        book.kpiTile(s, 4, 5, 2, "EXECUTED", t.executed, "#,##0", "EAF1FB", NAVY);
        book.kpiTile(s, 4, 7, 2, "PASSED", t.pass, "#,##0", PASS_BG, PASS_FG);
        book.kpiTile(s, 4, 9, 2, "FAILED", t.fail, "#,##0", FAIL_BG, FAIL_FG);
        book.kpiTile(s, 4, 11, 2, "SKIPPED", t.skip, "#,##0", SKIP_BG, SKIP_FG);

        double avg = t.executed == 0 ? 0 : t.durationMs / 1000.0 / t.executed;
        Object wall = meta.wall() == null ? null : meta.wallSeconds() / 86400.0;
        book.kpiTile(s, 7, 1, 2, "PASS RATE", t.passRate(), "0.0%", PASS_BG, PASS_FG);
        book.kpiTile(s, 7, 3, 2, "COVERAGE OF SELECTED", t.coverage(), "0.0%", "EAF1FB", NAVY);
        book.kpiTile(s, 7, 5, 2, "PASSED ON RETRY (flaky)", t.flaky, "#,##0", SKIP_BG, SKIP_FG);
        book.kpiTile(s, 7, 7, 2, "WALL TIME", wall, "[h]:mm:ss", "EAF1FB", NAVY);
        book.kpiTile(s, 7, 9, 2, "AVG TEST (s)", avg, "0.0", "EAF1FB", NAVY);
        book.kpiTile(s, 7, 11, 2, "NOT IN CATALOG", stats.uncatalogued, "#,##0", GREY_BG, GREY_FG);

        // --- by module and flow ---------------------------------------------------------------
        int row = 10;
        sectionTitle(s, row++, "By module and flow");
        List<XlsxColumn> cols = List.of(XlsxColumn.of("Module / Flow", 24), XlsxColumn.of("Planned", 11, Kind.INT),
                XlsxColumn.of("Not selected", 11, Kind.INT), XlsxColumn.of("Selected, not run", 11, Kind.INT),
                XlsxColumn.of("Executed", 11, Kind.INT), XlsxColumn.of("Pass", 11, Kind.INT),
                XlsxColumn.of("Fail", 11, Kind.INT), XlsxColumn.of("Skip", 11, Kind.INT),
                XlsxColumn.of("Pass %", 11, Kind.PERCENT), XlsxColumn.of("Coverage %", 11, Kind.PERCENT));
        List<Object[]> rows = new ArrayList<>();
        for (Map.Entry<String, Counts> e : stats.byModuleFlow.entrySet()) {
            Counts c = e.getValue();
            rows.add(new Object[]{e.getKey().replace('|', ' '), c.planned, c.notSelected, c.selectedNotRun, c.executed,
                    c.pass, c.fail, c.skip, c.passRate(), c.selected() <= 0 ? null : c.coverage()});
        }
        int mfHeader = row;
        int[] mf = book.writeTable(s, mfHeader, 1, cols, rows, noFreezeNoFilter());
        row = mf[1] + 1;
        totalRow(s, row, "TOTAL", t, cols.size());
        row += 3;

        // --- by group ---------------------------------------------------------------------------
        sectionTitle(s, row++, "By execution group");
        List<XlsxColumn> gcols = List.of(XlsxColumn.of("Group", 24), XlsxColumn.of("Planned", 11, Kind.INT),
                XlsxColumn.of("Not selected", 11, Kind.INT), XlsxColumn.of("Executed", 11, Kind.INT),
                XlsxColumn.of("Passed", 11, Kind.INT), XlsxColumn.of("Failed", 11, Kind.INT),
                XlsxColumn.of("Skipped", 11, Kind.INT));
        List<Object[]> grows = new ArrayList<>();
        for (Map.Entry<String, Counts> e : stats.byGroup.entrySet()) {
            Counts c = e.getValue();
            grows.add(new Object[]{groupLabel(e.getKey()), c.planned, c.notSelected, c.executed, c.pass, c.fail, c.skip});
        }
        int gHeader = row;
        int[] g = book.writeTable(s, gHeader, 1, gcols, grows, noFreezeNoFilter());
        row = g[1] + 4;

        // --- top failure categories ------------------------------------------------------------
        sectionTitle(s, row++, "Top failure / skip categories");
        List<XlsxColumn> ccols = List.of(XlsxColumn.of("Category", 24), XlsxColumn.of("Count", 11, Kind.INT),
                XlsxColumn.of("% of non-pass", 13, Kind.PERCENT));
        List<Object[]> crows = new ArrayList<>();
        int nonPass = t.fail + t.skip;
        for (Map.Entry<FailureCategory, Integer> e : stats.topCategories()) {
            crows.add(new Object[]{e.getKey().label(), e.getValue(), nonPass == 0 ? 0.0 : e.getValue() / (double) nonPass});
        }
        if (crows.isEmpty()) crows.add(new Object[]{"(no failures or skips)", 0, 0.0});
        int cHeader = row;
        int[] c = book.writeTable(s, cHeader, 1, ccols, crows, noFreezeNoFilter());
        row = c[1] + 3;

        // --- charts (each isolated) -----------------------------------------------------------
        sectionTitle(s, row++, "Charts");
        int chartTop = row;
        if (mf[1] >= mf[0]) {
            safeChart("Pass / Fail / Skip by module and flow", () -> XlsxCharts.bar(s, new int[]{1, chartTop, 7, chartTop + 17},
                    "Pass / Fail / Skip by module and flow", s, new CellRangeAddress(mf[0], mf[1], 1, 1),
                    List.of(new XlsxCharts.Series("Pass", new CellRangeAddress(mf[0], mf[1], 6, 6), C_PASS),
                            new XlsxCharts.Series("Fail", new CellRangeAddress(mf[0], mf[1], 7, 7), C_FAIL),
                            new XlsxCharts.Series("Skip", new CellRangeAddress(mf[0], mf[1], 8, 8), C_SKIP)),
                    true, false));
        }
        if (g[1] >= g[0]) {
            safeChart("Planned vs executed vs passed by group", () -> XlsxCharts.bar(s, new int[]{7, chartTop, 13, chartTop + 17},
                    "Planned vs executed vs passed, by group", s, new CellRangeAddress(g[0], g[1], 1, 1),
                    List.of(new XlsxCharts.Series("Planned", new CellRangeAddress(g[0], g[1], 2, 2), C_GREY),
                            new XlsxCharts.Series("Executed", new CellRangeAddress(g[0], g[1], 4, 4), C_BLUE),
                            new XlsxCharts.Series("Passed", new CellRangeAddress(g[0], g[1], 5, 5), C_PASS)),
                    false, false));
        }
        if (!stats.topCategories().isEmpty() && c[1] >= c[0]) {
            safeChart("Failures by category", () -> XlsxCharts.bar(s, new int[]{1, chartTop + 18, 7, chartTop + 35},
                    "Failures and skips by category", s, new CellRangeAddress(c[0], c[1], 1, 1),
                    List.of(new XlsxCharts.Series("Count", new CellRangeAddress(c[0], c[1], 2, 2), C_FAIL)),
                    false, true));
        }
        if (mf[1] >= mf[0]) {
            safeChart("Planned vs executed by module and flow", () -> XlsxCharts.bar(s,
                    new int[]{7, chartTop + 18, 13, chartTop + 35}, "Planned vs executed, by module and flow",
                    s, new CellRangeAddress(mf[0], mf[1], 1, 1),
                    List.of(new XlsxCharts.Series("Planned", new CellRangeAddress(mf[0], mf[1], 2, 2), C_GREY),
                            new XlsxCharts.Series("Executed", new CellRangeAddress(mf[0], mf[1], 5, 5), C_BLUE)),
                    false, false));
        }
    }

    private String subtitle() {
        StringBuilder sb = new StringBuilder();
        append(sb, "Run", meta.getRunId());
        append(sb, "Env", meta.getEnv());
        append(sb, "Browser", meta.getBrowser());
        append(sb, "OS", meta.getTargetOs());
        append(sb, "Branch", meta.getBranch());
        append(sb, "Suite", meta.getSuiteFile().isBlank() ? meta.getSuiteName() : meta.getSuiteFile());
        append(sb, "User", meta.getUser());
        if (meta.getStart() != null) {
            append(sb, "Started", fmt(meta.getStart()));
        }
        if (meta.getEnd() != null) {
            append(sb, "Finished", fmt(meta.getEnd()));
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) return;
        if (sb.length() > 0) sb.append("   |   ");
        sb.append(label).append(": ").append(value);
    }

    private static String fmt(Instant i) {
        return LocalDateTime.ofInstant(i, ZoneId.systemDefault()).toString().replace('T', ' ').replaceAll("\\.\\d+$", "");
    }

    /** "Filter in effect" line: active filters and how many catalog rows they intentionally excluded. */
    String filterLine() {
        List<String> active = meta.getActiveFilters().isEmpty() ? filters.describe() : meta.getActiveFilters();
        int notSelected = stats.total.notSelected;
        if (active.isEmpty()) {
            return "Filter in effect: none - the full catalog was selected. Rows without a result are 'Selected but not run' or 'Not run'.";
        }
        return "Filter in effect: " + String.join(", ", active) + "  ->  " + notSelected
                + " catalog combination(s) intentionally not run (Not selected). They are excluded from Coverage % "
                + "and shown separately from genuine gaps.";
    }

    private void sectionTitle(XSSFSheet s, int row, String text) {
        XlsxStyle st = XlsxStyle.builder().bold(true).fontSize(12).fontRgb(NAVY).build();
        book.put(s, row, 1, text, st);
        book.rowHeight(s, row, 20f);
    }

    private void totalRow(XSSFSheet s, int row, String label, Counts c, int cols) {
        XlsxStyle txt = XlsxStyle.builder().bold(true).fillRgb("D9E2F3").border(true).build();
        XlsxStyle num = txt.toBuilder().numFmt("#,##0").align(XlsxStyle.Align.RIGHT).build();
        XlsxStyle pct = txt.toBuilder().numFmt("0.0%").align(XlsxStyle.Align.RIGHT).build();
        book.put(s, row, 1, label, txt);
        Object[] v = {c.planned, c.notSelected, c.selectedNotRun, c.executed, c.pass, c.fail, c.skip, c.passRate(),
                c.selected() <= 0 ? null : c.coverage()};
        for (int i = 0; i < v.length && i + 2 < cols + 1; i++) {
            book.put(s, row, 2 + i, v[i], i >= 7 ? pct : num);
        }
    }

    private static XlsxBook.TableOptions noFreezeNoFilter() {
        XlsxBook.TableOptions o = new XlsxBook.TableOptions();
        o.freeze = false;
        o.autoFilter = false;
        return o;
    }

    private static String groupLabel(String group) {
        return switch (group) {
            case "MANDATORY" -> "Mandatory";
            case "REGRESSION_PHASE1" -> "Regression Phase 1";
            case "REGRESSION_PHASE2" -> "Regression Phase 2";
            case "REGRESSION_PHASE3" -> "Regression Phase 3";
            case CoverageRow.GROUP_ALWAYS -> "Always (not group-filtered)";
            case "VARIES" -> "Varies (DB-expanded)";
            default -> group;
        };
    }

    private static void safeChart(String name, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            Log.warn("[CoverageWorkbook] Chart '{}' skipped: {}", name, t.toString());
        }
    }

    // ============================================================================================
    // Execution Results
    // ============================================================================================

    private void executionResults() {
        XSSFSheet s = book.sheet(SHEET_EXEC, "1F3864", true);
        banner(s, "Execution Results  -  failures first", ER_COLS - 1,
                ordered.size() + " executed  |  " + stats.total.pass + " pass  |  " + stats.total.fail + " fail  |  "
                        + stats.total.skip + " skip  |  Test Case ID and wording are exactly as in the HTML report title");
        List<XlsxColumn> cols = List.of(
                XlsxColumn.builder().header("#").width(5).kind(Kind.INT).center(true).build(),
                XlsxColumn.of("Module", 8), XlsxColumn.of("Flow", 13),
                XlsxColumn.of("Test Case ID", 28),
                XlsxColumn.builder().header("Run #").width(8).center(true).build(),
                XlsxColumn.of("Variant ID", 30), XlsxColumn.of("Group", 19),
                XlsxColumn.wrapped("Test Case Description", 62), XlsxColumn.wrapped("Scenario Summary", 52),
                XlsxColumn.builder().header("Status").width(10).center(true).build(),
                XlsxColumn.builder().header("Retries").width(8).kind(Kind.INT).center(true).build(),
                XlsxColumn.builder().header("Passed on retry").width(10).center(true).build(),
                XlsxColumn.of("Voucher No.", 22),
                XlsxColumn.of("Start", 19, Kind.DATETIME), XlsxColumn.of("End", 19, Kind.DATETIME),
                XlsxColumn.of("Duration (s)", 11, Kind.DECIMAL1),
                XlsxColumn.of("Thread", 16), XlsxColumn.of("Failure Category", 25),
                XlsxColumn.wrapped("Failure Summary", 72), XlsxColumn.wrapped("Failing Step", 32),
                XlsxColumn.of("Screenshot", 12, Kind.LINK), XlsxColumn.of("Report", 12, Kind.LINK));
        List<Object[]> rows = new ArrayList<>();
        int n = 0;
        for (CoverageResult r : ordered) {
            CoverageRow cat = join.rowByResult.get(r);
            rows.add(new Object[]{++n, r.getModule(), r.getFlow(), r.displayTestCaseId(), r.runLabel(), nz(r.getVariantId()),
                    cat != null ? CoverageStats.groupOf(cat) : "(not in catalog)",
                    nz(r.getTitleDescription()).isBlank() ? nz(r.getDescription()) : r.getTitleDescription(),
                    nz(r.getScenarioSummary()), r.getStatus(), r.getRetries(), r.isPassedOnRetry() ? "Yes" : "",
                    nz(r.getVoucherNo()), ts(r.getStartMs()), ts(r.getEndMs()), r.getDurationMs() / 1000.0,
                    nz(r.getThread()), r.getFailureCategory().label(), nz(r.getReason()), nz(r.getFailingStep()),
                    screenshotLink(r), reportLink(r)});
        }
        XlsxBook.TableOptions o = new XlsxBook.TableOptions();
        o.freezeColumns = 4;
        int[] range = book.writeTable(s, HDR, 0, cols, rows, o);
        erFirstRow = range[0];
        erLastRow = Math.max(range[1], range[0]);
        statusColours(s, range, COL_STATUS);
        if (range[1] >= range[0]) {
            book.dataBars(s, new CellRangeAddress(range[0], range[1], 15, 15), "9DC3E6");
            book.highlightEquals(s, new CellRangeAddress(range[0], range[1], 11, 11), "Yes", SKIP_BG, SKIP_FG);
        }
    }

    private XlsxLink screenshotLink(CoverageResult r) {
        String f = r.getScreenshotFile();
        if (f == null || f.isBlank()) return null;
        return new XlsxLink("Screenshot", relativeOrUri(f));
    }

    private XlsxLink reportLink(CoverageResult r) {
        if (reportFileName == null || reportFileName.isBlank()) return null;
        return new XlsxLink("Open report", reportFileName);
    }

    /** Path relative to the workbook's folder when the file lives under it (so a run folder stays portable), else a file: URI. */
    String relativeOrUri(String file) {
        try {
            Path p = Path.of(file).toAbsolutePath().normalize();
            Path base = outputPath.toAbsolutePath().normalize().getParent();
            if (base != null && p.startsWith(base)) {
                return base.relativize(p).toString().replace('\\', '/');
            }
            return p.toUri().toString();
        } catch (Exception e) {
            return file;
        }
    }

    private void statusColours(XSSFSheet s, int[] range, int col) {
        if (range[1] < range[0]) return;
        CellRangeAddress r = new CellRangeAddress(range[0], range[1], col, col);
        book.highlightEquals(s, r, "PASS", PASS_BG, PASS_FG);
        book.highlightEquals(s, r, "FAIL", FAIL_BG, FAIL_FG);
        book.highlightEquals(s, r, "SKIP", SKIP_BG, SKIP_FG);
        book.highlightEquals(s, r, RowState.NOT_SELECTED.label(), GREY_BG, GREY_FG);
        book.highlightEquals(s, r, RowState.SELECTED_NOT_RUN.label(), SNR_BG, SNR_FG);
        book.highlightEquals(s, r, RowState.NOT_RUN.label(), NR_BG, NR_FG);
        book.highlightEquals(s, r, RowState.DB_EXPANDED.label(), DBX_BG, DBX_FG);
    }

    private void banner(XSSFSheet s, String title, int lastCol, String note) {
        XlsxStyle b = XlsxStyle.builder().fillRgb(NAVY).fontRgb("FFFFFF").bold(true).fontSize(14)
                .align(XlsxStyle.Align.LEFT).build();
        book.merged(s, 0, 0, 0, lastCol, title, b);
        book.rowHeight(s, 0, 28f);
        XlsxStyle n = XlsxStyle.builder().italic(true).fontRgb("595959").align(XlsxStyle.Align.LEFT).build();
        book.merged(s, 1, 0, 1, lastCol, note, n);
    }

    // ============================================================================================
    // Test Details
    // ============================================================================================

    /** Labels shown first and in this order in the Test Details header block; others follow in encounter order. */
    private static final List<String> PREFERRED = List.of("Scenario", "TestCase ID", "TestCase Name", "Owner Site",
            "Cash/Bank GL", "Doc Scheme", "Doc Date", "Current Date", "Reference", "Remarks", "Voucher No.",
            "Cheque No.", "Cheque Known/Unknown", "Entry Method", "Current Doc Date", "Expected Doc Date",
            "On Account", "TDS Provided", "Payment Info Warning");

    /** Strips a trailing {@code " (Row N)"} so per-row cheque labels fold into one column. */
    static String foldLabel(String label) {
        return label == null ? "" : label.replaceAll("\\s*\\(Row \\d+\\)$", "").strip();
    }

    private void testDetails() {
        XSSFSheet s = book.sheet(SHEET_DETAILS, "2F5597", true);
        List<String> headerLabels = headerLabels();
        List<XlsxColumn> cols = new ArrayList<>(List.of(
                XlsxColumn.of("Test Case ID", 28),
                XlsxColumn.builder().header("Run #").width(8).center(true).build(),
                XlsxColumn.of("Module", 8), XlsxColumn.of("Flow", 13), XlsxColumn.of("Variant ID", 30),
                XlsxColumn.builder().header("Status").width(10).center(true).build(),
                XlsxColumn.wrapped("Test Case Description", 62), XlsxColumn.wrapped("Scenario Summary", 52)));
        for (String l : headerLabels) {
            cols.add(XlsxColumn.wrapped(l, widthFor(l)));
        }
        cols.add(XlsxColumn.wrapped("Expected Results", 48));
        cols.add(XlsxColumn.wrapped("Expected Results (detail)", 70));
        cols.add(XlsxColumn.of("Resulting Voucher No.", 22));
        cols.add(XlsxColumn.of("Login User", 16));
        cols.add(XlsxColumn.of("Author", 14));
        cols.add(XlsxColumn.of("Thread", 16));
        cols.add(XlsxColumn.of("Start", 19, Kind.DATETIME));
        cols.add(XlsxColumn.of("End", 19, Kind.DATETIME));
        cols.add(XlsxColumn.of("Duration (s)", 11, Kind.DECIMAL1));
        cols.add(XlsxColumn.builder().header("Retries").width(8).kind(Kind.INT).center(true).build());
        cols.add(XlsxColumn.of("Env", 12));
        cols.add(XlsxColumn.of("Browser", 12));
        cols.add(XlsxColumn.of("OS", 10));
        cols.add(XlsxColumn.of("Browser Build", 34));
        cols.add(XlsxColumn.of("Failure Category", 25));
        cols.add(XlsxColumn.wrapped("Failure Message (full)", 80));

        List<Object[]> rows = new ArrayList<>();
        for (CoverageResult r : ordered) {
            List<Object> row = new ArrayList<>(List.of(r.displayTestCaseId(), r.runLabel(), r.getModule(), r.getFlow(),
                    nz(r.getVariantId()), r.getStatus(),
                    nz(r.getTitleDescription()).isBlank() ? nz(r.getDescription()) : r.getTitleDescription(),
                    nz(r.getScenarioSummary())));
            Map<String, String> fields = foldedFields(r);
            for (String l : headerLabels) row.add(fields.getOrDefault(l, ""));
            row.add(nz(r.getExpectedSummary()));
            row.add(VoucherReportRows.expectedDetail(r.getExpectedRows()));
            row.add(nz(r.getVoucherNo()));
            row.add(nz(r.getUser()));
            row.add(nz(r.getAuthor()));
            row.add(nz(r.getThread()));
            row.add(ts(r.getStartMs()));
            row.add(ts(r.getEndMs()));
            row.add(r.getDurationMs() / 1000.0);
            row.add(r.getRetries());
            row.add(nz(meta.getEnv()));
            row.add(nz(meta.getBrowser()));
            row.add(nz(meta.getTargetOs()));
            row.add(String.join("; ", meta.getBrowserVersions()));
            row.add(r.getFailureCategory().label());
            row.add(nz(r.getFailureMessage()));
            rows.add(row.toArray());
        }
        banner(s, "Test Details  -  header data and expected results per test, as shown in the HTML report",
                cols.size() - 1, ordered.size() + " test(s)  |  Header columns are the report's 'Test Data - Header' rows; "
                        + "Expected Results mirror the report's 'Expected Results' table");
        XlsxBook.TableOptions o = new XlsxBook.TableOptions();
        o.freezeColumns = 1;
        int[] range = book.writeTable(s, HDR, 0, cols, rows, o);
        statusColours(s, range, 5);
    }

    private static int widthFor(String label) {
        return switch (label) {
            case "Remarks", "Reference", "TestCase Name" -> 34;
            case "Scenario" -> 30;
            default -> 18;
        };
    }

    private List<String> headerLabels() {
        Map<String, Integer> encounter = new LinkedHashMap<>();
        for (CoverageResult r : ordered) {
            for (String[] f : r.getHeaderFields()) {
                encounter.putIfAbsent(foldLabel(f[0]), encounter.size());
            }
        }
        List<String> labels = new ArrayList<>(encounter.keySet());
        labels.sort(Comparator.comparingInt((String l) -> {
            int i = PREFERRED.indexOf(l);
            return i >= 0 ? i : PREFERRED.size() + encounter.get(l);
        }));
        return labels;
    }

    /** Header rows as label to value; per-row labels folded into one cell joined with " | ". */
    private static Map<String, String> foldedFields(CoverageResult r) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String[] f : r.getHeaderFields()) {
            if (f == null || f.length < 2) continue;
            String base = foldLabel(f[0]);
            String v = f[1] == null ? "" : f[1];
            String prefix = f[0].equals(base) ? "" : f[0].substring(base.length()).strip() + ": ";
            m.merge(base, prefix.isEmpty() && !m.containsKey(base) ? v : prefix + v, (a, b) -> a + " | " + b);
        }
        return m;
    }

    // ============================================================================================
    // Failure Details
    // ============================================================================================

    private void failureDetails() {
        XSSFSheet s = book.sheet(SHEET_FAILS, "C00000", true);
        List<XlsxColumn> cols = List.of(XlsxColumn.of("Test Case ID", 28),
                XlsxColumn.builder().header("Run #").width(8).center(true).build(),
                XlsxColumn.of("Module", 8), XlsxColumn.of("Flow", 13), XlsxColumn.of("Variant ID", 30),
                XlsxColumn.of("Failure Category", 25), XlsxColumn.of("Area", 11), XlsxColumn.wrapped("Location", 44),
                XlsxColumn.wrapped("Component / Field", 30), XlsxColumn.wrapped("Property", 20),
                XlsxColumn.wrapped("Expected", 30), XlsxColumn.wrapped("Actual", 30),
                XlsxColumn.wrapped("Detail", 70), XlsxColumn.wrapped("Failing Step", 30),
                XlsxColumn.of("Screenshot", 12, Kind.LINK));
        List<Object[]> rows = new ArrayList<>();
        for (CoverageResult r : ordered) {
            if (!"FAIL".equals(r.getStatus())) continue;
            List<FailureDetail> details = r.getFailures();
            if (details.isEmpty()) {
                details = List.of(FailureDetail.builder().area("Test flow").location("").component("").property("")
                        .expected("").actual("").detail(nz(r.getReason())).build());
            }
            for (FailureDetail d : details) {
                rows.add(new Object[]{r.displayTestCaseId(), r.runLabel(), r.getModule(), r.getFlow(), nz(r.getVariantId()),
                        r.getFailureCategory().label(), d.getArea(), d.getLocation(), d.getComponent(), d.getProperty(),
                        d.getExpected(), d.getActual(), d.getDetail(), nz(r.getFailingStep()), screenshotLink(r)});
            }
        }
        banner(s, "Failure Details  -  one row per UI / DB mismatch", cols.size() - 1,
                rows.size() + " mismatch row(s) from " + stats.total.fail + " failed test(s)");
        XlsxBook.TableOptions o = new XlsxBook.TableOptions();
        o.freezeColumns = 1;
        book.writeTable(s, HDR, 0, cols, rows, o);
    }

    // ============================================================================================
    // Pivot
    // ============================================================================================

    private void pivot() {
        XSSFSheet s = book.sheet(SHEET_PIVOT, "7030A0", true);
        XlsxStyle b = XlsxStyle.builder().fillRgb(NAVY).fontRgb("FFFFFF").bold(true).fontSize(14).align(XlsxStyle.Align.LEFT).build();
        book.merged(s, 0, 0, 0, 22, "Pivot  -  slice the Execution Results", b);
        book.rowHeight(s, 0, 28f);
        XlsxStyle n = XlsxStyle.builder().italic(true).fontRgb("595959").wrap(true).align(XlsxStyle.Align.LEFT).vCenter(false).build();
        book.merged(s, 1, 0, 1, 22,
                "Top: native PivotTables (Excel rebuilds them when the file opens - drag fields, use the report filters; select a PivotTable "
                        + "then Insert > PivotChart for a native PivotChart, which the writer library cannot create). Below: formula-driven "
                        + "pivot-style summaries with ordinary bar charts that update when the Execution Results are edited.", n);
        book.rowHeight(s, 1, 44f);
        book.setWidths(s, 2, 22, 14, 12, 12, 12, 12, 12, 2, 22, 14, 12, 12, 12, 12, 2, 28, 12, 12, 12, 12, 12);

        boolean havePivotData = erLastRow >= erFirstRow && !ordered.isEmpty();
        boolean pivotsOn = !"false".equalsIgnoreCase(System.getProperty(PIVOT_PROPERTY, "true"));
        if (havePivotData && pivotsOn) {
            XSSFSheet src = book.raw().getSheet(SHEET_EXEC);
            try {   // each pivot isolated: a POI quirk must not cost the whole workbook
                XlsxPivots.countPivot(s, 5, 1, src, HDR, erLastRow, 0, ER_COLS - 1, List.of(COL_MODULE, COL_FLOW),
                        List.of(COL_STATUS), List.of(COL_GROUP, COL_CATEGORY), COL_ID, "Tests");
            } catch (Throwable t) {
                Log.warn("[CoverageWorkbook] PivotTable 1 skipped: {}", t.toString());
            }
            try {
                XlsxPivots.countPivot(s, 5, 9, src, HDR, erLastRow, 0, ER_COLS - 1, List.of(COL_GROUP),
                        List.of(COL_STATUS), List.of(COL_MODULE), COL_ID, "Tests");
            } catch (Throwable t) {
                Log.warn("[CoverageWorkbook] PivotTable 2 skipped: {}", t.toString());
            }
            try {
                XlsxPivots.countPivot(s, 5, 16, src, HDR, erLastRow, 0, ER_COLS - 1, List.of(COL_CATEGORY),
                        List.of(COL_MODULE), List.of(COL_STATUS), COL_ID, "Tests");
            } catch (Throwable t) {
                Log.warn("[CoverageWorkbook] PivotTable 3 skipped: {}", t.toString());
            }
        } else if (!havePivotData) {
            book.put(s, 5, 1, "(no executed tests - nothing to pivot)", n);
        }

        // --- pivot-style, formula-driven summaries + charts ----------------------------------
        int row = 34;
        XlsxStyle sec = XlsxStyle.builder().bold(true).fontSize(12).fontRgb(NAVY).build();
        book.put(s, row++, 1, "Pivot-style summary: module / flow x status (formulas over Execution Results)", sec);
        List<XlsxColumn> cols = List.of(XlsxColumn.of("Module / Flow", 22), XlsxColumn.of("Module", 8),
                XlsxColumn.of("Flow", 13), XlsxColumn.of("PASS", 9, Kind.INT), XlsxColumn.of("FAIL", 9, Kind.INT),
                XlsxColumn.of("SKIP", 9, Kind.INT), XlsxColumn.of("Total", 9, Kind.INT));
        List<String[]> mfKeys = new ArrayList<>();
        for (String key : stats.byModuleFlow.keySet()) {
            Counts c = stats.byModuleFlow.get(key);
            if (c.executed > 0) mfKeys.add(key.split("\\|"));
        }
        int hdr1 = row;
        writeFormulaTable(s, hdr1, cols, mfKeys.size(), i -> new String[]{mfKeys.get(i)[0] + " " + mfKeys.get(i)[1],
                mfKeys.get(i)[0], mfKeys.get(i)[1]}, true);
        int first1 = hdr1 + 1, last1 = hdr1 + mfKeys.size();
        if (!mfKeys.isEmpty()) {
            safeChart("Pivot module/flow", () -> XlsxCharts.bar(s, new int[]{9, hdr1 - 1, 16, hdr1 + 15},
                    "Status by module and flow (from Execution Results)", s, new CellRangeAddress(first1, last1, 0 + 1, 1),
                    List.of(new XlsxCharts.Series("PASS", new CellRangeAddress(first1, last1, 4, 4), C_PASS),
                            new XlsxCharts.Series("FAIL", new CellRangeAddress(first1, last1, 5, 5), C_FAIL),
                            new XlsxCharts.Series("SKIP", new CellRangeAddress(first1, last1, 6, 6), C_SKIP)),
                    true, false));
        }

        row = Math.max(last1 + 3, hdr1 + 18);
        book.put(s, row++, 1, "Pivot-style summary: group x status", sec);
        List<XlsxColumn> gcols = List.of(XlsxColumn.of("Group", 22), XlsxColumn.of("Group key", 8),
                XlsxColumn.of("", 13), XlsxColumn.of("PASS", 9, Kind.INT), XlsxColumn.of("FAIL", 9, Kind.INT),
                XlsxColumn.of("SKIP", 9, Kind.INT), XlsxColumn.of("Total", 9, Kind.INT));
        List<String> groups = new ArrayList<>();
        stats.byGroup.forEach((g, c) -> {
            if (c.executed > 0) groups.add(g);
        });
        boolean hasUncat = stats.uncatalogued > 0;
        if (hasUncat) groups.add("(not in catalog)");
        int hdr2 = row;
        writeFormulaTable(s, hdr2, gcols, groups.size(), i -> new String[]{groupLabel(groups.get(i)), groups.get(i), ""}, false);
        int first2 = hdr2 + 1, last2 = hdr2 + groups.size();
        if (!groups.isEmpty()) {
            safeChart("Pivot group", () -> XlsxCharts.bar(s, new int[]{9, hdr2 - 1, 16, hdr2 + 13},
                    "Status by execution group (from Execution Results)", s, new CellRangeAddress(first2, last2, 1, 1),
                    List.of(new XlsxCharts.Series("PASS", new CellRangeAddress(first2, last2, 4, 4), C_PASS),
                            new XlsxCharts.Series("FAIL", new CellRangeAddress(first2, last2, 5, 5), C_FAIL),
                            new XlsxCharts.Series("SKIP", new CellRangeAddress(first2, last2, 6, 6), C_SKIP)),
                    true, false));
        }

        row = Math.max(last2 + 3, hdr2 + 16);
        book.put(s, row++, 1, "Pivot-style summary: failure / skip category", sec);
        List<XlsxColumn> ccols = List.of(XlsxColumn.of("Category", 28), XlsxColumn.of("Count", 9, Kind.INT));
        List<FailureCategory> cats = new ArrayList<>();
        for (Map.Entry<FailureCategory, Integer> e : stats.topCategories()) cats.add(e.getKey());
        int hdr3 = row;
        XlsxBook.TableOptions o = new XlsxBook.TableOptions();
        o.freeze = false;
        o.autoFilter = false;
        List<Object[]> catRows = new ArrayList<>();
        for (FailureCategory c : cats) catRows.add(new Object[]{c.label(), null});
        if (catRows.isEmpty()) catRows.add(new Object[]{"(no failures or skips)", 0});
        int[] cr = book.writeTable(s, hdr3, 1, ccols, catRows, o);
        if (!cats.isEmpty()) {
            for (int r = cr[0]; r <= cr[1]; r++) {
                String label = "$B" + (r + 1);
                book.formula(s, r, 2, "COUNTIFS(" + erRange(COL_CATEGORY) + "," + label + ")",
                        XlsxStyle.builder().numFmt("#,##0").border(true).align(XlsxStyle.Align.RIGHT).build());
            }
            safeChart("Pivot category", () -> XlsxCharts.bar(s, new int[]{9, hdr3 - 1, 16, hdr3 + 14},
                    "Failures and skips by category (from Execution Results)", s, new CellRangeAddress(cr[0], cr[1], 1, 1),
                    List.of(new XlsxCharts.Series("Count", new CellRangeAddress(cr[0], cr[1], 2, 2), C_FAIL)),
                    false, true));
        }
        book.recalcOnOpen();
    }

    /** Writes a header + one COUNTIFS row per label; columns: label, key1, key2, PASS, FAIL, SKIP, Total (starting at column B). */
    private void writeFormulaTable(XSSFSheet s, int hdrRow, List<XlsxColumn> cols, int n,
                                   java.util.function.IntFunction<String[]> labels, boolean moduleFlow) {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String[] l = labels.apply(i);
            rows.add(new Object[]{l[0], l[1], l[2], null, null, null, null});
        }
        if (rows.isEmpty()) rows.add(new Object[]{"(none)", "", "", 0, 0, 0, 0});
        XlsxBook.TableOptions o = new XlsxBook.TableOptions();
        o.freeze = false;
        o.autoFilter = false;
        int[] r = book.writeTable(s, hdrRow, 1, cols, rows, o);
        if (n == 0) return;
        XlsxStyle num = XlsxStyle.builder().numFmt("#,##0").border(true).align(XlsxStyle.Align.RIGHT).build();
        String[] statusHdr = {"E", "F", "G"};   // header cells hold PASS / FAIL / SKIP (columns E,F,G)
        for (int row = r[0]; row <= r[1]; row++) {
            int x = row + 1;   // 1-based Excel row
            for (int k = 0; k < 3; k++) {
                // module/flow table: module in C, flow in D; group table: the group key in C
                String crit = moduleFlow
                        ? erRange(COL_MODULE) + ",$C" + x + "," + erRange(COL_FLOW) + ",$D" + x + ","
                        : erRange(COL_GROUP) + ",$C" + x + ",";
                book.formula(s, row, 4 + k, "COUNTIFS(" + crit + erRange(COL_STATUS) + "," + statusHdr[k] + "$" + (hdrRow + 1) + ")", num);
            }
            book.formula(s, row, 7, "SUM(E" + x + ":G" + x + ")", num);
        }
    }

    private String erRange(int col) {
        String letter = CellReference.convertNumToColString(col);
        return "'" + SHEET_EXEC + "'!$" + letter + "$" + (erFirstRow + 1) + ":$" + letter + "$" + (erLastRow + 1);
    }

    // ============================================================================================
    // Coverage sheets
    // ============================================================================================

    private void gvCoverage() {
        XSSFSheet s = book.sheet(SHEET_GV, "ED7D31", true);
        List<XlsxColumn> cols = List.of(XlsxColumn.of("Flow", 10), XlsxColumn.of("Variant ID", 36),
                XlsxColumn.of("Group", 19), XlsxColumn.of("Scenario Type", 16), XlsxColumn.wrapped("Key Fields", 46),
                XlsxColumn.wrapped("Catalog Description", 60),
                XlsxColumn.builder().header("Status").width(26).center(true).build(),
                XlsxColumn.of("Test Case ID", 28), XlsxColumn.builder().header("Run #").width(8).center(true).build(),
                XlsxColumn.of("Duration (s)", 11, Kind.DECIMAL1), XlsxColumn.of("Failure Category", 25),
                XlsxColumn.wrapped("Failure / Skip Reason", 70));
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < join.catalogRows.size(); i++) {
            Matched m = join.catalogRows.get(i);
            if (!"GV".equals(m.row.getModule())) continue;
            RowState st = stats.rowStates.get(i);
            CoverageResult r = m.result;
            rows.add(new Object[]{m.row.getFlow(), m.row.getVariantId(), CoverageStats.groupOf(m.row),
                    nz(m.row.getScenarioType()), nz(m.row.getKeyFields()), nz(m.row.getDescription()), st.label(),
                    r == null ? "" : r.displayTestCaseId(), r == null ? "" : r.runLabel(),
                    r == null ? null : r.getDurationMs() / 1000.0, r == null ? "" : r.getFailureCategory().label(),
                    r == null ? notRunHint(st) : nz(r.getReason())});
        }
        coverageSheet(s, "GV Coverage  -  every planned General Voucher combination and what happened to it", "GV", cols, rows, 6);
    }

    private void arApCoverage() {
        XSSFSheet s = book.sheet(SHEET_ARAP, "ED7D31", true);
        List<XlsxColumn> cols = List.of(XlsxColumn.of("Module", 8), XlsxColumn.of("Flow", 10),
                XlsxColumn.of("Variant ID", 40), XlsxColumn.of("Group", 19), XlsxColumn.of("Scenario Type", 26),
                XlsxColumn.wrapped("Catalog Description", 60),
                XlsxColumn.builder().header("Status").width(26).center(true).build(),
                XlsxColumn.of("Test Case ID", 28), XlsxColumn.builder().header("Run #").width(8).center(true).build(),
                XlsxColumn.of("Duration (s)", 11, Kind.DECIMAL1), XlsxColumn.of("Failure Category", 25),
                XlsxColumn.wrapped("Failure / Skip Reason", 70));
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < join.catalogRows.size(); i++) {
            Matched m = join.catalogRows.get(i);
            String module = m.row.getModule();
            if (!"AR".equals(module) && !"AP".equals(module)) continue;
            RowState st = stats.rowStates.get(i);
            if (m.row.isDbExpanded()) {
                List<CoverageResult> family = join.expandedByRow.getOrDefault(m.row, List.of());
                rows.add(new Object[]{module, m.row.getFlow(), m.row.getVariantId(), "VARIES", nz(m.row.getScenarioType()),
                        nz(m.row.getDescription()), st.label(), "", "", null, "",
                        family.size() + " concrete row(s) executed this run - listed below as (DB-expanded)"});
                continue;
            }
            CoverageResult r = m.result;
            String id = r != null ? r.displayTestCaseId()
                    : (m.row.getTestCaseId() == null || m.row.getTestCaseId().isBlank() ? "" : "[" + m.row.getTestCaseId() + "]");
            rows.add(new Object[]{module, m.row.getFlow(), m.row.getVariantId(), CoverageStats.groupOf(m.row),
                    nz(m.row.getScenarioType()), nz(m.row.getDescription()), st.label(), id,
                    r == null ? "" : r.runLabel(), r == null ? null : r.getDurationMs() / 1000.0,
                    r == null ? "" : r.getFailureCategory().label(), r == null ? notRunHint(st) : nz(r.getReason())});
        }
        for (List<CoverageResult> family : join.expandedByRow.values()) {
            for (CoverageResult r : family) rows.add(executedRow(r, "(DB-expanded)"));
        }
        for (CoverageResult r : join.unmatched) {
            if ("AR".equals(r.getModule()) || "AP".equals(r.getModule())) rows.add(executedRow(r, "(not in catalog)"));
        }
        coverageSheet(s, "AR / AP Coverage  -  planned scenarios, DB-expanded rows that ran, and what happened to each", null, cols, rows, 6);
    }

    private Object[] executedRow(CoverageResult r, String group) {
        return new Object[]{r.getModule(), r.getFlow(), nz(r.getVariantId()), group, nz(r.getScenarioType()),
                nz(r.getTitleDescription()).isBlank() ? nz(r.getDescription()) : r.getTitleDescription(), r.getStatus(),
                r.displayTestCaseId(), r.runLabel(), r.getDurationMs() / 1000.0, r.getFailureCategory().label(),
                nz(r.getReason())};
    }

    private static String notRunHint(RowState st) {
        return switch (st) {
            case NOT_SELECTED -> "Excluded by the active group/tag filter - intentionally not run.";
            case SELECTED_NOT_RUN -> "Selected, yet produced no result: data-provider shortage, skip before the row was bound, or not reached.";
            case NOT_RUN -> "No test of this module/flow ran in this run (flow not in this suite).";
            default -> "";
        };
    }

    /**
     * Group subtotal block (rows 3..), then the detail table; no frozen panes here (the subtotal
     * block above the table would freeze a dozen rows) - the table still has its autofilter.
     */
    private void coverageSheet(XSSFSheet s, String title, String moduleOnly, List<XlsxColumn> cols,
                               List<Object[]> rows, int statusCol) {
        banner(s, title, cols.size() - 1, "Status split: 'Not selected (group filter)' = excluded on purpose by the active filter; "
                + "'Selected but not run' = a real gap; 'Not run' = flow not part of this suite.");
        // subtotals
        XlsxStyle sec = XlsxStyle.builder().bold(true).fontSize(11).fontRgb(NAVY).build();
        book.put(s, 2, 0, "Subtotals by group", sec);
        List<XlsxColumn> sub = List.of(XlsxColumn.of("Group", 19), XlsxColumn.of("Planned", 10, Kind.INT),
                XlsxColumn.of("Not selected", 12, Kind.INT), XlsxColumn.of("Executed", 10, Kind.INT),
                XlsxColumn.of("Pass", 8, Kind.INT), XlsxColumn.of("Fail", 8, Kind.INT), XlsxColumn.of("Skip", 8, Kind.INT),
                XlsxColumn.of("Selected, not run", 16, Kind.INT));
        List<Object[]> subRows = new ArrayList<>();
        for (String g : CoverageStats.GROUPS) {
            Counts sum = new Counts();
            boolean any = false;
            for (String mod : CoverageStats.MODULES) {
                if (moduleOnly != null && !moduleOnly.equals(mod)) continue;
                if (moduleOnly == null && "GV".equals(mod)) continue;
                Counts c = stats.byModuleGroup.get(CoverageStats.key(mod, g));
                if (c == null) continue;
                any = true;
                sum.planned += c.planned; sum.notSelected += c.notSelected; sum.executed += c.executed;
                sum.pass += c.pass; sum.fail += c.fail; sum.skip += c.skip; sum.selectedNotRun += c.selectedNotRun;
            }
            if (any) subRows.add(new Object[]{groupLabel(g), sum.planned, sum.notSelected, sum.executed, sum.pass,
                    sum.fail, sum.skip, sum.selectedNotRun});
        }
        XlsxBook.TableOptions so = new XlsxBook.TableOptions();
        so.freeze = false;
        so.autoFilter = false;
        so.headerHeight = 20f;
        int[] sr = book.writeTable(s, 3, 0, sub, subRows, so);
        int hdr = Math.max(sr[1], 3) + 3;
        book.put(s, hdr - 1, 0, "Detail", sec);
        // detail table sets its own widths: keep the wider of the two layouts per column
        int[] widths = new int[cols.size()];
        for (int c = 0; c < cols.size(); c++) widths[c] = cols.get(c).getWidth();
        XlsxBook.TableOptions o = new XlsxBook.TableOptions();
        o.freeze = false;
        int[] range = book.writeTable(s, hdr, 0, cols, rows, o);
        statusColours(s, range, statusCol);
        for (int c = 0; c < sub.size() && c < widths.length; c++) {
            s.setColumnWidth(c, Math.max(widths[c], sub.get(c).getWidth()) * 256);
        }
    }

    // ============================================================================================
    // Group Matrix
    // ============================================================================================

    private void groupMatrix() {
        XSSFSheet s = book.sheet(SHEET_MATRIX, "FFC000", true);
        XlsxStyle b = XlsxStyle.builder().fillRgb(NAVY).fontRgb("FFFFFF").bold(true).fontSize(14).align(XlsxStyle.Align.LEFT).build();
        book.merged(s, 0, 0, 0, 12, "Group Matrix  -  Mandatory vs Regression phases by module (planned / executed / passed)", b);
        book.rowHeight(s, 0, 28f);
        XlsxStyle n = XlsxStyle.builder().italic(true).fontRgb("595959").align(XlsxStyle.Align.LEFT).build();
        book.merged(s, 1, 0, 1, 12, "Planned = catalog combinations in the group; Executed/Passed = results matched to them. "
                + "With a group filter active, the other groups show planned > 0 and executed = 0 by design.", n);
        book.setWidths(s, 24, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9);

        XlsxStyle h = XlsxBook.HEADER;
        book.merged(s, 3, 0, 4, 0, "Group", h);
        List<String> mods = new ArrayList<>(CoverageStats.MODULES);
        for (int i = 0; i < mods.size(); i++) {
            book.merged(s, 3, 1 + i * 3, 3, 3 + i * 3, mods.get(i), h);
            book.put(s, 4, 1 + i * 3, "Planned", h);
            book.put(s, 4, 2 + i * 3, "Executed", h);
            book.put(s, 4, 3 + i * 3, "Passed", h);
        }
        int tc = 1 + mods.size() * 3;
        book.merged(s, 3, tc, 3, tc + 2, "All modules", h);
        book.put(s, 4, tc, "Planned", h);
        book.put(s, 4, tc + 1, "Executed", h);
        book.put(s, 4, tc + 2, "Passed", h);
        book.rowHeight(s, 3, 20f);
        book.rowHeight(s, 4, 20f);

        XlsxStyle lab = XlsxStyle.builder().border(true).bold(true).build();
        XlsxStyle num = XlsxStyle.builder().border(true).numFmt("#,##0").align(XlsxStyle.Align.RIGHT).build();
        XlsxStyle tot = XlsxStyle.builder().border(true).bold(true).fillRgb("D9E2F3").numFmt("#,##0").align(XlsxStyle.Align.RIGHT).build();
        int row = 5;
        int[] colTotals = new int[mods.size() * 3 + 3];
        for (String g : CoverageStats.GROUPS) {
            boolean any = false;
            for (String m : mods) if (stats.byModuleGroup.containsKey(CoverageStats.key(m, g))) any = true;
            if (!any) continue;
            book.put(s, row, 0, groupLabel(g), lab);
            int rp = 0, re = 0, rpass = 0;
            for (int i = 0; i < mods.size(); i++) {
                Counts c = stats.byModuleGroup.get(CoverageStats.key(mods.get(i), g));
                int p = c == null ? 0 : c.planned, e = c == null ? 0 : c.executed, ps = c == null ? 0 : c.pass;
                book.put(s, row, 1 + i * 3, p, num);
                book.put(s, row, 2 + i * 3, e, num);
                book.put(s, row, 3 + i * 3, ps, num);
                colTotals[i * 3] += p; colTotals[i * 3 + 1] += e; colTotals[i * 3 + 2] += ps;
                rp += p; re += e; rpass += ps;
            }
            book.put(s, row, tc, rp, tot);
            book.put(s, row, tc + 1, re, tot);
            book.put(s, row, tc + 2, rpass, tot);
            colTotals[mods.size() * 3] += rp; colTotals[mods.size() * 3 + 1] += re; colTotals[mods.size() * 3 + 2] += rpass;
            row++;
        }
        book.put(s, row, 0, "TOTAL", lab.toBuilder().fillRgb("D9E2F3").build());
        for (int i = 0; i < colTotals.length; i++) book.put(s, row, 1 + i, colTotals[i], tot);
        if (stats.uncatalogued > 0) {
            book.put(s, row + 2, 0, stats.uncatalogued + " executed test(s) match no catalog row and are not counted above "
                    + "(AR Edit/Delete, UI validation, negatives/boundaries - see Execution Results).", n);
        }
    }

    // ============================================================================================
    // Notes / Legend
    // ============================================================================================

    private void notes() {
        XSSFSheet s = book.sheet(SHEET_NOTES, "7F7F7F", true);
        banner(s, "Notes & Legend", 1, "How to read this workbook, and what it cannot tell you");
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{"Run", nz(meta.getRunId())});
        rows.add(new Object[]{"Generated", fmt(Instant.now())});
        rows.add(new Object[]{"Environment / browser", nz(meta.getEnv()) + " / " + nz(meta.getBrowser())});
        rows.add(new Object[]{"OS (Grid node)", nz(meta.getTargetOs())});
        rows.add(new Object[]{"Browser build(s) used", meta.getBrowserVersions().isEmpty()
                ? "not recorded (no browser session was created)" : String.join("; ", meta.getBrowserVersions())});
        if (!nz(meta.getVersionDrift()).isBlank()) {
            rows.add(new Object[]{"WARNING: browser version drift", "More than one major version ran in this run ("
                    + meta.getVersionDrift() + "). Results from different builds are mixed."});
        }
        rows.add(new Object[]{"Concurrent-session cap", nz(meta.getSessionCap()).isBlank()
                ? "none (pool-derived)" : meta.getSessionCap() + "  -  lowers parallelism, so wall-clock durations are not comparable with an uncapped run"});
        rows.add(new Object[]{"Branch / user / machine", nz(meta.getBranch()) + " / " + nz(meta.getUser()) + " / " + nz(meta.getMachine())});
        rows.add(new Object[]{"Suite", nz(meta.getSuiteFile()).isBlank() ? nz(meta.getSuiteName()) : meta.getSuiteFile()});
        rows.add(new Object[]{"Filter in effect", filterLine()});
        rows.add(new Object[]{"Purpose", "Document-only view of planned test combinations (catalog) versus what ran this run. It does not "
                + "drive execution; the Extent report remains the detailed source of truth."});
        rows.add(new Object[]{"Colour legend", "PASS green | FAIL red | SKIP amber | Not selected (group filter) light grey | "
                + "Selected but not run orange | Not run dark grey | DB-EXPANDED blue."});
        rows.add(new Object[]{"Not selected (group filter)", "A catalog row the active gv.group / gv.tag / gv.edit.group / arv.group / apv.group "
                + "filter excludes. The run did not intend to execute it, so it does not lower Coverage %."});
        rows.add(new Object[]{"Selected but not run", "Selected by the filter and its module/flow did run, yet it produced no result: data-provider "
                + "shortage (e.g. no matching document in the DB), a skip raised before the row was bound, or not reached."});
        rows.add(new Object[]{"Not run", "Selected, but nothing of its module/flow ran at all (the flow was not part of this suite)."});
        rows.add(new Object[]{"Coverage %", "Executed catalog-matched results divided by planned combinations that the filter selected."});
        rows.add(new Object[]{"Test Case ID / Run #", "Test Case ID is the bracketed id from the HTML report title, e.g. [FIN-GV-ADD-COMBO-01]. Its "
                + "-NN suffix is positional within that run's filtered row list, so it differs between runs/groups; join across runs "
                + "by Variant ID. Run # (e.g. 1/9) is the position within the data provider's list."});
        rows.add(new Object[]{"Retries", "Retries counts superseded attempts before the final result; 'Passed on retry' flags a flaky pass."});
        rows.add(new Object[]{"Failure categories", "UI/DB/Snapshot validation, Application rejected action, Timeout, Click blocked, Element not "
                + "found, Database error, Data shortage / skipped, Suite circuit breaker, Assertion mismatch, Other."});
        rows.add(new Object[]{"Charts", "Dashboard and Pivot-sheet charts are native Excel bar charts bound to cell ranges."});
        rows.add(new Object[]{"PivotTables / PivotCharts", "The Pivot sheet holds real PivotTables that Excel refreshes when the file opens. Apache POI 5.1.0 "
                + "cannot create native PivotCharts (a chart bound to a pivot table); the pivot-style summary tables with ordinary charts stand "
                + "in for them. To get a PivotChart, click a PivotTable and use Insert > PivotChart. Set -Dcoverage.workbook.pivots=false to omit "
                + "the PivotTables if Excel ever reports a repair prompt - the summary tables and charts remain."});
        rows.add(new Object[]{"AR/AP DB-expanded rows", "Select-Document variants driven by classification types discovered in the DB cannot be "
                + "enumerated statically; they appear as '(DB-expanded)' rows when executed."});
        rows.add(new Object[]{"AR/AP groups", "Groups for AR/AP rows not backed by a shared static list are transcribed from the Db*VoucherDataSource.load() "
                + "source and kept in sync by hand; apv.tag is reported but cannot be applied per row."});
        rows.add(new Object[]{"Not catalogued", "AR Edit/Delete, GV/AR/AP UI-validation and GV negative/boundary tests have no static catalog; when they "
                + "run they appear in Execution Results only ('(not in catalog)')."});
        rows.add(new Object[]{"Data-provider skips", "When a DataSupplier throws a skip the whole test method is skipped as ONE result with no variant id; "
                + "the method-level SKIP row in Execution Results carries the reason."});
        rows.add(new Object[]{"Freeze panes", "Coverage sheets carry a group-subtotal block above the table, so they have autofilter but no frozen header."});
        XlsxBook.TableOptions o = new XlsxBook.TableOptions();
        o.autoFilter = false;
        o.zebra = true;
        book.writeTable(s, HDR, 0, List.of(XlsxColumn.of("Topic", 30), XlsxColumn.wrapped("Detail", 130)), rows, o);
    }

    // -- helpers -------------------------------------------------------------------------------

    private static LocalDateTime ts(long ms) {
        return ms <= 0 ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
