package com.tests.config;

import com.gsl.reports.CoverageResult;
import com.gsl.reports.CoverageStats;
import com.gsl.reports.CoverageWorkbookBuilder;
import com.gsl.reports.FailureCategory;
import com.gsl.reports.FailureClassifier;
import com.gsl.reports.GeneralVoucherExtentDescriptionLogger;
import com.gsl.reports.RunMeta;
import com.gsl.reports.TestTitle;
import com.gsl.reports.VoucherReportRows;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.dataprovider.fixture.finance.ActiveGroupFilters;
import com.gsl.utils.dataprovider.fixture.finance.CoverageRow;
import com.gsl.validation.CombinedValidationError;
import com.gsl.validation.ui.UiValidationFailure;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Browser-free, DB-free checks of the redesigned coverage workbook: numeric cells, pass %, the
 * failure categoriser (incl. HTML unescape), not-selected classification, title splitting and the
 * header-row extraction shared with the Extent logger. Run with {@code -Dtest=CoverageWorkbookTest}.
 */
public class CoverageWorkbookTest {

    // -- title split -------------------------------------------------------------------------------------

    @Test
    public void titleSplitsIntoBracketedIdDescriptionAndRunPosition() {
        TestTitle t = TestTitle.parse("[FIN-GV-ADD-COMBO-01] Verify that a General Voucher is created successfully using "
                + "a Income GL with Site allocation only, Cheque payment. [1/9]");
        assertEquals(t.bracketedId(), "[FIN-GV-ADD-COMBO-01]");
        assertEquals(t.id(), "FIN-GV-ADD-COMBO-01");
        assertEquals(t.description(), "Verify that a General Voucher is created successfully using a Income GL with "
                + "Site allocation only, Cheque payment.");
        assertEquals(t.runIndex(), 1);
        assertEquals(t.runTotal(), 9);
        assertEquals(t.runLabel(), "1/9");
    }

    @Test
    public void titleWithoutRunSuffixOrIdStillParses() {
        TestTitle noRun = TestTitle.parse("[FIN-AP-REVERT] Revert a posted voucher");
        assertEquals(noRun.bracketedId(), "[FIN-AP-REVERT]");
        assertEquals(noRun.description(), "Revert a posted voucher");
        assertEquals(noRun.runLabel(), "");
        TestTitle plain = TestTitle.parse("Just a sentence [x]");
        assertEquals(plain.bracketedId(), "");
        assertEquals(TestTitle.parse(null).description(), "");
    }

    // -- failure classifier ------------------------------------------------------------------------------

    @Test
    public void htmlEntitiesAreUnescapedIncludingNumeric() {
        assertEquals(FailureClassifier.unescapeHtml("a &lt;b&gt; &amp; &quot;c&quot; &#39;d&#39; &#x41; &unknown; x"),
                "a <b> & \"c\" 'd' A &unknown; x");
        assertEquals(FailureClassifier.unescapeHtml(null), "");
        assertEquals(FailureClassifier.unescapeHtml("no entities"), "no entities");
    }

    @Test
    public void combinedUiAndDbErrorIsSummarisedFromItsAreasNotItsFirstLine() {
        UiValidationFailure ui = new UiValidationFailure("OWNER_SITE_LABEL", "font-size", "12px", "13px");
        CombinedValidationError e = new CombinedValidationError(List.of(
                CombinedValidationError.uiArea(List.of(ui)),
                new CombinedValidationError.Area(CombinedValidationError.Kind.DATABASE, "Database Validation",
                        List.of("finvchmain.refno expected 'A &amp; B' but was 'A'"))));
        FailureClassifier.Outcome o = FailureClassifier.classify(e, "FAIL", List.of(ui));
        assertEquals(o.category(), FailureCategory.DB_VALIDATION);          // DB outranks UI
        assertFalse(o.summary().startsWith("2 validation areas failed"), o.summary());
        assertTrue(o.summary().contains("UI Validation (1)"), o.summary());
        assertTrue(o.summary().contains("finvchmain.refno expected 'A & B'"), o.summary());   // unescaped
        assertEquals(o.details().get(0).getArea(), "UI");
        assertEquals(o.details().get(0).getComponent(), "OWNER_SITE_LABEL");
        assertEquals(o.details().get(0).getExpected(), "12px");
        assertEquals(o.details().get(0).getActual(), "13px");
        assertEquals(o.details().size(), 2);
        assertEquals(o.details().get(1).getArea(), "Database");
    }

    @Test
    public void plainThrowablesAreCategorisedByTypeAndMessage() {
        assertEquals(FailureClassifier.classify(new org.openqa.selenium.TimeoutException("Expected condition failed"),
                "FAIL", List.of()).category(), FailureCategory.TIMEOUT);
        assertEquals(FailureClassifier.classify(new org.openqa.selenium.ElementClickInterceptedException("x"),
                "FAIL", List.of()).category(), FailureCategory.CLICK_BLOCKED);
        assertEquals(FailureClassifier.classify(new org.testng.SkipException("No matching document in DB"),
                "SKIP", List.of()).category(), FailureCategory.DATA_SHORTAGE);
        assertEquals(FailureClassifier.classify(new org.testng.SkipException("Suite circuit breaker tripped"),
                "SKIP", List.of()).category(), FailureCategory.CIRCUIT_BREAKER);
        assertEquals(FailureClassifier.classify(new AssertionError("Application rejected the save with a validation error"),
                "FAIL", List.of()).category(), FailureCategory.APP_REJECTION);
        assertEquals(FailureClassifier.classify(null, "PASS", List.of()).category(), FailureCategory.NONE);
        FailureClassifier.Outcome o = FailureClassifier.classify(new IllegalStateException("boom &lt;x&gt;\nsecond line"),
                "FAIL", List.of());
        assertEquals(o.summary(), "boom <x>");
        assertTrue(o.fullMessage().contains("second line"));
    }

    // -- header rows == what the Extent logger emits -----------------------------------------------------

    @Test
    public void headerRowsAreTheRowsTheExtentLoggerRenders() {
        MergedFinanceGeneralVoucherData data = gvData();
        List<String[]> rows = GeneralVoucherExtentDescriptionLogger.headerRowsOf(data);
        // The Extent logger renders exactly headerRows(data) via ExtentLogger.infoTable - same labels, same order.
        List<String> labels = new ArrayList<>();
        rows.forEach(r -> labels.add(r[0]));
        assertEquals(labels, List.of("Field", "Scenario", "TestCase ID", "TestCase Name", "Owner Site", "Cash/Bank GL",
                "Doc Scheme", "Doc Date", "Current Date", "Reference", "Remarks", "Payment Info Warning"));
        assertEquals(rows.get(1)[1], "GV_INCOME_T2_CR");
        assertEquals(rows.get(6)[1], "SCH-1");
        assertEquals(VoucherReportRows.withoutHeaderRow(rows).size(), rows.size() - 1);
    }

    @Test
    public void expectedRowsFeedTheSummaryLine() {
        List<String[]> expected = VoucherReportRows.withoutHeaderRow(
                GeneralVoucherExtentDescriptionLogger.expectedRowsOf(gvData()));
        assertEquals(VoucherReportRows.summariseExpected(expected),
                "UI Save; DB finvchmain, finvchdet, finpost, fincosttag, Audit Trail");
        assertEquals(VoucherReportRows.summariseExpected(List.of(new String[]{"Flag — On Account", "x"},
                new String[]{"Flag — TDS Provided", "y"}, new String[]{"Solo", "z"})),
                "Flag On Account, TDS Provided; Solo");
        assertTrue(VoucherReportRows.expectedDetail(expected).contains("UI — Save: Document saved"));
    }

    // -- not-selected classification ---------------------------------------------------------------------

    @Test
    public void notRunRowsAreSplitIntoNotSelectedSelectedNotRunAndNotRun() {
        List<CoverageRow> catalog = List.of(
                row("GV", "Create", "V_MAND", "MANDATORY"),
                row("GV", "Create", "V_REG", "REGRESSION_PHASE2"),
                row("GV", "Create", "V_ALWAYS", CoverageRow.GROUP_ALWAYS),
                row("GV", "Revert", "V_REV_MAND", "MANDATORY"),
                row("GV", "Edit", "E_MAND", "MANDATORY"),
                row("GV", "Edit", "E_REG", "REGRESSION_PHASE1"));
        List<CoverageResult> results = List.of(res("GV", "Create", "FIN-GV-ADD-COMBO-01", "V_MAND", "PASS"));
        CoverageWorkbookBuilder.JoinResult join = CoverageWorkbookBuilder.join(catalog, results);
        // gv.group=MANDATORY: Regression rows are filtered out by design.
        ActiveGroupFilters f = new ActiveGroupFilters("MANDATORY", Set.of(), null, null, null, null);
        CoverageStats s = CoverageStats.compute(join, results, f);
        assertEquals(s.rowStates, List.of(
                CoverageStats.RowState.PASS,
                CoverageStats.RowState.NOT_SELECTED,        // REGRESSION_PHASE2 under gv.group=MANDATORY
                CoverageStats.RowState.SELECTED_NOT_RUN,    // ALWAYS row, GV Create ran, but no result
                CoverageStats.RowState.NOT_RUN,             // selected, but GV Revert never ran at all
                CoverageStats.RowState.NOT_RUN,             // selected, GV Edit never ran
                CoverageStats.RowState.NOT_RUN));           // E_REG: gv.edit.group unset -> selected, Edit never ran
    }

    @Test
    public void editGroupFilterIsIndependentOfCreateGroupFilter() {
        ActiveGroupFilters f = new ActiveGroupFilters("MANDATORY", Set.of(), null, null, null, null);
        // gv.edit.group is unset, so an Edit REGRESSION row is still selected even though gv.group=MANDATORY.
        assertTrue(f.isSelected(row("GV", "Edit", "E_REG", "REGRESSION_PHASE1")));
        assertFalse(f.isSelected(row("GV", "Create", "V_REG", "REGRESSION_PHASE1")));
        ActiveGroupFilters e = new ActiveGroupFilters(null, Set.of(), "MANDATORY", null, null, null);
        assertFalse(e.isSelected(row("GV", "Edit", "E_REG", "REGRESSION_PHASE1")));
        assertTrue(ActiveGroupFilters.none().describe().isEmpty());
        assertEquals(f.describe(), List.of("gv.group=MANDATORY"));
    }

    // -- numeric cells + pass % in the written workbook --------------------------------------------------

    @Test
    public void countsDurationsAndPercentsAreNumericCells() throws Exception {
        List<CoverageResult> results = List.of(
                res("GV", "Create", "FIN-GV-ADD-COMBO-01", "V1", "PASS"),
                res("GV", "Create", "FIN-GV-ADD-COMBO-02", "V2", "PASS"),
                res("GV", "Create", "FIN-GV-ADD-COMBO-03", "V3", "FAIL"),
                res("GV", "Edit", "FIN-GV-EDIT-HEADER-01", "E1", "SKIP"));
        List<CoverageRow> catalog = List.of(row("GV", "Create", "V1", "MANDATORY"), row("GV", "Create", "V2", "MANDATORY"),
                row("GV", "Create", "V3", "MANDATORY"), row("GV", "Create", "V4", "REGRESSION_PHASE1"));
        Path out = Files.createTempFile("coverage-numeric", ".xlsx");
        try {
            CoverageWorkbookBuilder.build(out, catalog, results, "index.html", RunMeta.builder().env("qa").browser("chrome").build(),
                    new ActiveGroupFilters("MANDATORY", Set.of(), null, null, null, null));
            try (InputStream in = Files.newInputStream(out); XSSFWorkbook wb = new XSSFWorkbook(in)) {
                List<String> names = new ArrayList<>();
                wb.forEach(s -> names.add(s.getSheetName()));
                assertEquals(names, List.of("Dashboard", "Execution Results", "Test Details", "Failure Details", "Pivot",
                        "GV Coverage", "AR AP Coverage", "Group Matrix", "Notes"));

                Sheet er = wb.getSheet("Execution Results");
                // failures first: header row is index 3, first data row index 4 is the FAIL
                assertEquals(er.getRow(4).getCell(9).getStringCellValue(), "FAIL");
                assertEquals(er.getRow(5).getCell(9).getStringCellValue(), "SKIP");
                assertEquals(er.getRow(3).getCell(3).getStringCellValue(), "Test Case ID");
                assertEquals(er.getRow(4).getCell(3).getStringCellValue(), "[FIN-GV-ADD-COMBO-03]");
                assertEquals(er.getRow(4).getCell(0).getCellType(), CellType.NUMERIC);          // #
                assertEquals(er.getRow(4).getCell(10).getCellType(), CellType.NUMERIC);         // Retries
                assertEquals(er.getRow(4).getCell(15).getCellType(), CellType.NUMERIC);         // Duration (s)
                assertEquals(er.getRow(4).getCell(15).getNumericCellValue(), 1.5, 1e-9);

                Sheet dash = wb.getSheet("Dashboard");
                // KPI value rows: planned/selected/executed/pass/fail/skip are numeric
                assertEquals(dash.getRow(5).getCell(1).getCellType(), CellType.NUMERIC);
                assertEquals(dash.getRow(5).getCell(1).getNumericCellValue(), 4.0);   // planned
                assertEquals(dash.getRow(5).getCell(3).getNumericCellValue(), 3.0);   // selected = 4 - 1 not selected
                assertEquals(dash.getRow(5).getCell(7).getNumericCellValue(), 2.0);   // pass
                // Pass % tile = 2 pass / 4 executed
                assertEquals(dash.getRow(8).getCell(1).getNumericCellValue(), 0.5, 1e-9);
                assertEquals(dash.getRow(8).getCell(1).getCellStyle().getDataFormatString(), "0.0%");
                assertTrue(dash.getRow(2).getCell(1).getStringCellValue().contains("gv.group=MANDATORY"));

                Sheet gv = wb.getSheet("GV Coverage");
                boolean sawNotSelected = false;
                for (int r = 0; r <= gv.getLastRowNum(); r++) {
                    if (gv.getRow(r) != null && gv.getRow(r).getCell(6) != null && gv.getRow(r).getCell(6).getCellType() == CellType.STRING
                            && "Not selected (group filter)".equals(gv.getRow(r).getCell(6).getStringCellValue())) {
                        sawNotSelected = true;
                    }
                }
                assertTrue(sawNotSelected, "regression row must be split out as 'Not selected (group filter)'");
            }
        } finally {
            Files.deleteIfExists(out);
        }
    }

    @Test
    public void passRateAndCoverageAreFractions() {
        CoverageStats.Counts c = new CoverageStats.Counts();
        assertEquals(c.passRate(), 0.0);
        assertEquals(c.coverage(), 0.0);
        c.executed = 8;
        c.pass = 6;
        c.planned = 10;
        c.notSelected = 2;
        c.executedCatalogued = 4;
        assertEquals(c.passRate(), 0.75, 1e-9);
        assertEquals(c.selected(), 8);
        assertEquals(c.coverage(), 0.5, 1e-9);
    }

    @Test
    public void emptyRunStillProducesAValidWorkbook() throws Exception {
        Path out = Files.createTempFile("coverage-empty", ".xlsx");
        try {
            CoverageWorkbookBuilder.build(out, CoverageCatalogFixtures.small(), List.of(), null, null, null);
            try (InputStream in = Files.newInputStream(out); XSSFWorkbook wb = new XSSFWorkbook(in)) {
                assertEquals(wb.getNumberOfSheets(), 9);
            }
        } finally {
            Files.deleteIfExists(out);
        }
    }

    @Test
    public void foldingPerRowChequeLabelsCollapsesToOneColumn() throws Exception {
        java.lang.reflect.Method m = Class.forName("com.gsl.reports.CoverageSheetWriter").getDeclaredMethod("foldLabel", String.class);
        m.setAccessible(true);
        assertEquals(m.invoke(null, "Cheque No. (Row 2)"), "Cheque No.");
        assertEquals(m.invoke(null, "Cheque No."), "Cheque No.");
    }

    // -- fixtures ----------------------------------------------------------------------------------------


    private static final class CoverageCatalogFixtures {
        static List<CoverageRow> small() {
            return List.of(row("GV", "Create", "V1", "MANDATORY"), row("AR", "Create", "AR1", "REGRESSION_PHASE1"));
        }
    }

    private static MergedFinanceGeneralVoucherData gvData() {
        MasterTestData master = new MasterTestData();
        master.setTestcaseid("TC1");
        master.setTestcasename("GV | Income GL | Site allocation only");
        DocumentInformation doc = new DocumentInformation("TC1", "SCH-1", LocalDate.of(2026, 1, 2), "REF-1", "", "remark");
        return MergedFinanceGeneralVoucherData.builder().masterTestData(master).documentInformation(doc)
                .scenarioTypeLabel("GV_INCOME_T2_CR").build();
    }

    private static CoverageRow row(String module, String flow, String variant, String group) {
        return CoverageRow.builder().module(module).flow(flow).variantId(variant).group(group)
                .description("d").scenarioType("COMBINATION").build();
    }

    private static CoverageResult res(String module, String flow, String testCaseId, String variant, String status) {
        return CoverageResult.builder().module(module).flow(flow).testCaseId(testCaseId).variantId(variant)
                .scenarioType("").description("").methodName("m").status(status).durationMs(1500).reason("")
                .titleDescription("Verify something").failureCategory("FAIL".equals(status) ? FailureCategory.DB_VALIDATION
                        : "SKIP".equals(status) ? FailureCategory.DATA_SHORTAGE : FailureCategory.NONE).build();
    }
}
