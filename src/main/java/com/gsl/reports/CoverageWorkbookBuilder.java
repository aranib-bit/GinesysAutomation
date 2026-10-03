package com.gsl.reports;

import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.fixture.finance.CoverageCatalog;
import com.gsl.utils.dataprovider.fixture.finance.CoverageRow;
import com.gsl.utils.io.XlsxFill;
import com.gsl.utils.io.XlsxSheet;
import com.gsl.utils.io.XlsxWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Builds the per-run, document-only "Coverage &amp; Results" workbook
 * ({@code reports/runs/<runId>/Voucher_Coverage_Results.xlsx}) for GV + AR + AP — an at-a-glance,
 * non-technical companion to the detailed HTML report. It never drives execution: it joins the
 * static {@link CoverageCatalog} (what exists) to the {@link ResultCollector} snapshot (what ran).
 *
 * <p>Sheets: <b>Summary</b> (per module/flow counts), <b>Results</b> (one row per executed test),
 * <b>GV Coverage</b> (full GV matrix with last-run status), <b>AR AP Coverage</b> (scenario-level
 * catalog plus the concrete DB-expanded rows that actually ran) and <b>Notes</b> (limitations).
 *
 * <p><b>Join rule</b> ({@link #join}): a catalog row matches a result in the same module+flow by
 * {@code variantId} first, then by full {@code testCaseId} (for rows that carry none, e.g. AP
 * Revert/Delete). The positional {@code -NN} suffix of a test-case id is NOT a stable key for
 * variant-keyed rows, so it is only used where the catalog row declares one explicitly.
 * DB-expanded placeholder rows ({@link CoverageRow#isDbExpanded()}) claim every still-unmatched
 * result of their module/flow whose scenario type equals the placeholder's id.
 *
 * <p>Mechanics of writing the file live in {@link XlsxWriter} (domain-free); this class only
 * decides what the sheets contain. Called failure-safe from {@code TestListener.onFinish(ISuite)}.
 */
public final class CoverageWorkbookBuilder {

    /** File name written into the run's report directory. */
    public static final String FILE_NAME = "Voucher_Coverage_Results.xlsx";

    static final String NOT_RUN = "NOT RUN";
    static final String DB_EXPANDED = "DB-EXPANDED";

    private static final Map<String, XlsxFill> STATUS_FILLS = Map.of(
            "PASS", XlsxFill.GREEN, "FAIL", XlsxFill.RED, "SKIP", XlsxFill.AMBER,
            NOT_RUN, XlsxFill.GREY, DB_EXPANDED, XlsxFill.BLUE);

    private CoverageWorkbookBuilder() {}

    /** One catalog row together with the result it matched ({@code null} when it did not run). */
    public static final class Matched {
        public final CoverageRow row;
        public final CoverageResult result;

        Matched(CoverageRow row, CoverageResult result) {
            this.row = row;
            this.result = result;
        }
    }

    /** Output of {@link #join}: per-catalog-row matches plus the results no row claimed. */
    public static final class JoinResult {
        /** Catalog order preserved; DB-expanded placeholders carry a {@code null} result. */
        public final List<Matched> catalogRows = new ArrayList<>();
        /** Result → the catalog row it matched (identity-keyed); absent when unmatched. */
        public final Map<CoverageResult, CoverageRow> rowByResult = new IdentityHashMap<>();
        /** Results matched by a DB-expanded placeholder, per placeholder. */
        public final Map<CoverageRow, List<CoverageResult>> expandedByRow = new IdentityHashMap<>();
        /** Results claimed by no catalog row at all. */
        public final List<CoverageResult> unmatched = new ArrayList<>();
    }

    /**
     * Generates the workbook for the current run from {@link CoverageCatalog#all()} and the
     * {@link ResultCollector} snapshot. Failure-safe: logs and returns on any error, never throws —
     * a reporting problem must not change the suite's outcome.
     *
     * @param reportDir      the run-scoped report directory ({@code FrameworkConstants.getReportDir()})
     * @param reportFileName file name of the HTML report in that directory, shown in the Results sheet
     */
    public static void buildSafely(Path reportDir, String reportFileName) {
        try {
            Path out = reportDir.resolve(FILE_NAME);
            build(out, CoverageCatalog.all(), ResultCollector.snapshot(), reportFileName);
            Log.info("[CoverageWorkbook] Written to: {}", out);
        } catch (Throwable t) {
            Log.warn("[CoverageWorkbook] Generation failed (ignored): {}", t.toString());
        }
    }

    /**
     * Builds and writes the workbook from explicit inputs (the unit-testable core).
     *
     * @param outputPath     where to write the {@code .xlsx}
     * @param catalog        planned rows
     * @param results        executed results
     * @param reportFileName HTML report file name for the Results sheet's "Report" column; may be {@code null}
     */
    public static void build(Path outputPath, List<CoverageRow> catalog, List<CoverageResult> results,
                             String reportFileName) throws IOException {
        JoinResult join = join(catalog, results);
        List<XlsxSheet> sheets = new ArrayList<>();
        sheets.add(summarySheet(join, results));
        sheets.add(resultsSheet(join, results, reportFileName));
        sheets.add(gvCoverageSheet(join));
        sheets.add(arApCoverageSheet(join));
        sheets.add(notesSheet(outputPath));
        XlsxWriter.write(outputPath, sheets);
    }

    // ── Join ─────────────────────────────────────────────────────────────────────────────────

    /** Joins {@code catalog} to {@code results} — see the class Javadoc's join rule. */
    public static JoinResult join(List<CoverageRow> catalog, List<CoverageResult> results) {
        Map<String, List<CoverageResult>> byVariant = new LinkedHashMap<>();
        Map<String, List<CoverageResult>> byTestCase = new LinkedHashMap<>();
        for (CoverageResult r : results) {
            if (notBlank(r.getVariantId())) {
                byVariant.computeIfAbsent(key(r.getModule(), r.getFlow(), r.getVariantId()), k -> new ArrayList<>()).add(r);
            }
            byTestCase.computeIfAbsent(key(r.getModule(), r.getFlow(), r.getTestCaseId()), k -> new ArrayList<>()).add(r);
        }
        JoinResult out = new JoinResult();
        Set<CoverageResult> claimed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (CoverageRow row : catalog) {
            CoverageResult hit = null;
            if (!row.isDbExpanded()) {
                hit = firstUnclaimed(byVariant.get(key(row.getModule(), row.getFlow(), row.getVariantId())), claimed);
                if (hit == null && notBlank(row.getTestCaseId())) {
                    hit = firstUnclaimed(byTestCase.get(key(row.getModule(), row.getFlow(), row.getTestCaseId())), claimed);
                }
            }
            if (hit != null) {
                claimed.add(hit);
                out.rowByResult.put(hit, row);
            }
            out.catalogRows.add(new Matched(row, hit));
        }
        for (CoverageRow row : catalog) {
            if (!row.isDbExpanded()) continue;
            List<CoverageResult> family = new ArrayList<>();
            for (CoverageResult r : results) {
                if (!claimed.contains(r) && r.getModule().equals(row.getModule()) && r.getFlow().equals(row.getFlow())
                        && row.getVariantId().equals(r.getScenarioType())) {
                    family.add(r);
                    claimed.add(r);
                    out.rowByResult.put(r, row);
                }
            }
            out.expandedByRow.put(row, family);
        }
        for (CoverageResult r : results) {
            if (!claimed.contains(r)) out.unmatched.add(r);
        }
        return out;
    }

    private static CoverageResult firstUnclaimed(List<CoverageResult> candidates, Set<CoverageResult> claimed) {
        if (candidates == null) return null;
        for (CoverageResult r : candidates) {
            if (!claimed.contains(r)) return r;
        }
        return null;
    }

    // ── Sheets ───────────────────────────────────────────────────────────────────────────────

    private static XlsxSheet summarySheet(JoinResult join, List<CoverageResult> results) {
        // module|flow → counters; TreeMap gives a stable module/flow order.
        Map<String, int[]> counts = new TreeMap<>();
        // idx: 0 planned, 1 planned mand, 2 planned reg, 3 executed, 4 pass, 5 fail, 6 skip, 7 not run,
        //      8 executed mand, 9 executed reg
        for (Matched m : join.catalogRows) {
            if (m.row.isDbExpanded()) continue;
            int[] c = counts.computeIfAbsent(key(m.row.getModule(), m.row.getFlow(), ""), k -> new int[10]);
            c[0]++;
            c[m.row.isMandatoryTier() ? 1 : 2]++;
            if (m.result == null) c[7]++;
        }
        for (CoverageResult r : results) {
            int[] c = counts.computeIfAbsent(key(r.getModule(), r.getFlow(), ""), k -> new int[10]);
            c[3]++;
            switch (r.getStatus()) {
                case "PASS" -> c[4]++;
                case "FAIL" -> c[5]++;
                default -> c[6]++;
            }
            CoverageRow row = join.rowByResult.get(r);
            if (row != null) c[row.isMandatoryTier() ? 8 : 9]++;
        }
        List<String[]> rows = new ArrayList<>();
        int[] total = new int[10];
        for (Map.Entry<String, int[]> e : counts.entrySet()) {
            String[] mf = e.getKey().split("\\|", -1);
            int[] c = e.getValue();
            rows.add(summaryRow(mf[0], mf[1], c));
            for (int i = 0; i < total.length; i++) total[i] += c[i];
        }
        rows.add(summaryRow("TOTAL", "", total));
        return XlsxSheet.builder().name("Summary")
                .header(new String[]{"Module", "Flow", "Planned", "Planned (Mandatory)", "Planned (Regression)",
                        "Executed", "Pass", "Fail", "Skip", "Not Run", "Executed (Mandatory)", "Executed (Regression)"})
                .rows(rows).autoFilter(false).build();
    }

    private static String[] summaryRow(String module, String flow, int[] c) {
        String[] row = new String[2 + c.length];
        row[0] = module;
        row[1] = flow;
        for (int i = 0; i < c.length; i++) row[2 + i] = String.valueOf(c[i]);
        return row;
    }

    private static XlsxSheet resultsSheet(JoinResult join, List<CoverageResult> results, String reportFileName) {
        List<String[]> rows = new ArrayList<>();
        for (CoverageResult r : results) {
            CoverageRow row = join.rowByResult.get(r);
            String description = row != null && notBlank(row.getDescription()) ? row.getDescription() : r.getDescription();
            rows.add(new String[]{r.getModule(), r.getFlow(), r.getTestCaseId(), r.getVariantId(),
                    row != null ? row.getGroup() : "", description, r.getStatus(),
                    String.format("%.1f", r.getDurationMs() / 1000.0), r.getReason(),
                    reportFileName == null ? "" : reportFileName});
        }
        return XlsxSheet.builder().name("Results")
                .header(new String[]{"Module", "Flow", "Test Case ID", "Variant ID", "Group", "Scenario", "Status",
                        "Duration (s)", "Failure / Skip Reason", "Report"})
                .rows(rows).cellFills(STATUS_FILLS).fillColumns(new int[]{6}).build();
    }

    private static XlsxSheet gvCoverageSheet(JoinResult join) {
        List<String[]> rows = new ArrayList<>();
        for (Matched m : join.catalogRows) {
            if (!"GV".equals(m.row.getModule())) continue;
            rows.add(new String[]{m.row.getFlow(), m.row.getVariantId(), m.row.getGroup(), m.row.getScenarioType(),
                    nz(m.row.getKeyFields()), nz(m.row.getDescription()),
                    m.result == null ? NOT_RUN : m.result.getStatus(),
                    m.result == null ? "" : m.result.getTestCaseId(),
                    m.result == null ? "" : m.result.getReason()});
        }
        return XlsxSheet.builder().name("GV Coverage")
                .header(new String[]{"Flow", "Variant ID", "Group", "Scenario Type", "Key Fields", "Description",
                        "Last Run Status", "Test Case ID", "Failure / Skip Reason"})
                .rows(rows).cellFills(STATUS_FILLS).fillColumns(new int[]{6}).build();
    }

    private static XlsxSheet arApCoverageSheet(JoinResult join) {
        List<String[]> rows = new ArrayList<>();
        for (Matched m : join.catalogRows) {
            String module = m.row.getModule();
            if (!"AR".equals(module) && !"AP".equals(module)) continue;
            if (m.row.isDbExpanded()) {
                List<CoverageResult> family = join.expandedByRow.getOrDefault(m.row, List.of());
                rows.add(new String[]{module, m.row.getFlow(), m.row.getVariantId(), "", m.row.getGroup(),
                        m.row.getScenarioType(), nz(m.row.getDescription()), DB_EXPANDED,
                        family.size() + " row(s) executed this run - see Results", ""});
            } else {
                rows.add(new String[]{module, m.row.getFlow(), m.row.getVariantId(), nz(m.row.getTestCaseId()),
                        m.row.getGroup(), m.row.getScenarioType(), nz(m.row.getDescription()),
                        m.result == null ? NOT_RUN : m.result.getStatus(),
                        m.result == null ? "" : m.result.getReason(),
                        m.result == null ? "" : m.result.getTestCaseId()});
            }
        }
        // The concrete rows the DB-expanded placeholders stand for, and any AR/AP result no row claimed.
        for (List<CoverageResult> family : join.expandedByRow.values()) {
            for (CoverageResult r : family) rows.add(executedRow(r, "(DB-expanded)"));
        }
        for (CoverageResult r : join.unmatched) {
            if ("AR".equals(r.getModule()) || "AP".equals(r.getModule())) rows.add(executedRow(r, "(not in catalog)"));
        }
        return XlsxSheet.builder().name("AR AP Coverage")
                .header(new String[]{"Module", "Flow", "Variant ID", "Test Case ID", "Group", "Scenario Type",
                        "Description", "Status", "Failure / Skip Reason", "Executed As"})
                .rows(rows).cellFills(STATUS_FILLS).fillColumns(new int[]{7}).build();
    }

    private static String[] executedRow(CoverageResult r, String group) {
        return new String[]{r.getModule(), r.getFlow(), nz(r.getVariantId()), r.getTestCaseId(), group,
                nz(r.getScenarioType()), nz(r.getDescription()), r.getStatus(), nz(r.getReason()), r.getTestCaseId()};
    }

    private static XlsxSheet notesSheet(Path outputPath) {
        Path dir = outputPath.getParent();
        String runId = dir != null && dir.getFileName() != null ? dir.getFileName().toString() : "";
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Run", runId});
        rows.add(new String[]{"Generated", Instant.now().toString()});
        rows.add(new String[]{"Purpose", "Document-only view of planned test combinations (catalog) versus what ran this run. "
                + "It does not drive execution; the Extent report (index.html) remains the detailed source of truth."});
        rows.add(new String[]{"NOT RUN", "A catalog row with no matching result in THIS run - usually filtered out by the active "
                + "execution group/tag, or the whole data-provider skipped (see Results for a method-level SKIP and its reason)."});
        rows.add(new String[]{"Join key", "Catalog rows join to results by module + flow + variant id, falling back to the full "
                + "test-case id for rows that carry no variant id (AP Revert/Delete, AP Edit header rows)."});
        rows.add(new String[]{"Test-case index", "The -NN suffix of a test-case id is positional within the filtered row list of that run, "
                + "so it differs between runs/groups; join and reading by Variant ID, not index."});
        rows.add(new String[]{"Data-provider skips", "When a DataSupplier throws a skip (e.g. no matching document in the DB) the whole "
                + "test method is skipped as ONE result with no variant id, so every variant of that method shows NOT RUN here; "
                + "the method-level SKIP row in Results carries the reason."});
        rows.add(new String[]{"AR/AP DB-expanded rows", "Select-Document variants driven by classification types discovered in the DB "
                + "cannot be enumerated statically; they appear only if executed (AR AP Coverage sheet, 'Executed As' rows)."});
        rows.add(new String[]{"AR/AP groups", "Groups for AR/AP rows not backed by a shared static list are transcribed from the "
                + "Db*VoucherDataSource.load() source and kept in sync by hand."});
        rows.add(new String[]{"Not catalogued", "AR Edit/Delete, GV/AR/AP UI-validation and GV negative/boundary tests have no static "
                + "catalog; when they run they appear in Results only."});
        rows.add(new String[]{"Expected outcome", "No structured expected-outcome field exists for variants; the workbook shows the "
                + "scenario description plus the failure/skip reason, not expected-versus-actual."});
        rows.add(new String[]{"Summary columns", "Executed counts every result recorded for the flow; Executed (Mandatory/Regression) "
                + "counts only results matched to a catalog row, so the two may sum to less than Executed. Rows with group ALWAYS "
                + "(not group-filtered) are counted as Mandatory."});
        return XlsxSheet.builder().name("Notes").header(new String[]{"Topic", "Detail"})
                .rows(rows).autoFilter(false).build();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────

    private static String key(String module, String flow, String id) {
        return module + "|" + flow + "|" + id;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
