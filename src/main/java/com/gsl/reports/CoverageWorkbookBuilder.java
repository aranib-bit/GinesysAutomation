package com.gsl.reports;

import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.fixture.finance.ActiveGroupFilters;
import com.gsl.utils.dataprovider.fixture.finance.CoverageCatalog;
import com.gsl.utils.dataprovider.fixture.finance.CoverageRow;
import com.gsl.utils.io.XlsxBook;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the per-run "Coverage &amp; Results" workbook
 * ({@code reports/runs/<runId>/Voucher_Coverage_Results.xlsx}) for GV + AR + AP - a report-style,
 * non-technical companion to the detailed HTML report. It never drives execution: it joins the
 * static {@link CoverageCatalog} (what exists) to the {@link ResultCollector} snapshot (what ran).
 *
 * <p>Sheets (tab order): <b>Dashboard</b> (KPI tiles, module x flow and group tables, native bar
 * charts, top failure categories, the "filter in effect" line), <b>Execution Results</b> (failures
 * first), <b>Test Details</b> (the report's header data and expected results per test),
 * <b>Failure Details</b> (one row per UI/DB mismatch), <b>Pivot</b> (native PivotTables plus
 * formula-driven pivot-style tables and charts), <b>GV Coverage</b>, <b>AR AP Coverage</b>
 * (not-run rows split into Not selected / Selected but not run / Not run), <b>Group Matrix</b> and
 * <b>Notes</b>. All counts, durations and percentages are numeric cells.
 *
 * <p><b>Join rule</b> ({@link #join}): a catalog row matches a result in the same module+flow by
 * {@code variantId} first, then by full {@code testCaseId} (for rows that carry none, e.g. AP
 * Revert/Delete). The positional {@code -NN} suffix of a test-case id is NOT a stable key for
 * variant-keyed rows, so it is only used where the catalog row declares one explicitly.
 * DB-expanded placeholder rows ({@link CoverageRow#isDbExpanded()}) claim every still-unmatched
 * result of their module/flow whose scenario type equals the placeholder's id.
 *
 * <p>Mechanics of writing the file live in {@code com.gsl.utils.io} (domain-free: {@link XlsxBook},
 * {@code XlsxCharts}, {@code XlsxPivots}); {@link CoverageSheetWriter} decides what each sheet
 * shows. Called failure-safe from {@code TestListener.onFinish(ISuite)}.
 */
public final class CoverageWorkbookBuilder {

    /** File name written into the run's report directory. */
    public static final String FILE_NAME = "Voucher_Coverage_Results.xlsx";

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
        /** Result to the catalog row it matched (identity-keyed); absent when unmatched. */
        public final Map<CoverageResult, CoverageRow> rowByResult = new IdentityHashMap<>();
        /** Results matched by a DB-expanded placeholder, per placeholder. */
        public final Map<CoverageRow, List<CoverageResult>> expandedByRow = new IdentityHashMap<>();
        /** Results claimed by no catalog row at all. */
        public final List<CoverageResult> unmatched = new ArrayList<>();
    }

    /**
     * Generates the workbook for the current run from {@link CoverageCatalog#all()} and the
     * {@link ResultCollector} snapshot. Failure-safe: logs and returns on any error, never throws -
     * a reporting problem must not change the suite's outcome.
     *
     * @param reportDir      the run-scoped report directory ({@code FrameworkConstants.getReportDir()})
     * @param reportFileName file name of the HTML report in that directory, linked from Execution Results
     * @param meta           run-level facts (env, browser, times, filters); may be {@code null}
     * @param filters        the group/tag filters in effect; {@code null} = none
     */
    public static void buildSafely(Path reportDir, String reportFileName, RunMeta meta, ActiveGroupFilters filters) {
        try {
            Path out = reportDir.resolve(FILE_NAME);
            build(out, CoverageCatalog.all(), ResultCollector.snapshot(), reportFileName, meta, filters);
            Log.info("[CoverageWorkbook] Written to: {}", out);
        } catch (Throwable t) {
            Log.warn("[CoverageWorkbook] Generation failed (ignored): {}", t.toString());
        }
    }

    /** Convenience overload without run metadata or filters (kept for existing callers and tests). */
    public static void build(Path outputPath, List<CoverageRow> catalog, List<CoverageResult> results,
                             String reportFileName) throws IOException {
        build(outputPath, catalog, results, reportFileName, null, null);
    }

    /**
     * Builds and writes the workbook from explicit inputs (the unit-testable core).
     *
     * @param outputPath     where to write the {@code .xlsx}
     * @param catalog        planned rows
     * @param results        executed results
     * @param reportFileName HTML report file name for the Report column; may be {@code null}
     * @param meta           run-level facts; may be {@code null}
     * @param filters        group/tag filters in effect; {@code null} = none (every catalog row counts as selected)
     */
    public static void build(Path outputPath, List<CoverageRow> catalog, List<CoverageResult> results,
                             String reportFileName, RunMeta meta, ActiveGroupFilters filters) throws IOException {
        JoinResult join = join(catalog, results);
        ActiveGroupFilters f = filters == null ? ActiveGroupFilters.none() : filters;
        CoverageStats stats = CoverageStats.compute(join, results, f);
        RunMeta m = meta;
        if (m != null && (m.getRunId() == null || m.getRunId().isBlank())) {
            Path dir = outputPath.getParent();
            m = m.toBuilder().runId(dir != null && dir.getFileName() != null ? dir.getFileName().toString() : "").build();
        }
        try (XlsxBook book = new XlsxBook()) {
            new CoverageSheetWriter(book, outputPath, join, results, stats, m, f, reportFileName).writeAll();
            book.evaluateAllQuietly();
            book.write(outputPath);
        }
    }

    // -- Join -------------------------------------------------------------------------------------

    /** Joins {@code catalog} to {@code results} - see the class Javadoc's join rule. */
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

    // -- helpers ----------------------------------------------------------------------------------

    private static String key(String module, String flow, String id) {
        return module + "|" + flow + "|" + id;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
