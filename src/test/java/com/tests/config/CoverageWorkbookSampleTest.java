package com.tests.config;

import com.gsl.reports.CoverageResult;
import com.gsl.reports.CoverageWorkbookBuilder;
import com.gsl.reports.FailureClassifier;
import com.gsl.reports.GeneralVoucherExtentDescriptionLogger;
import com.gsl.reports.RunMeta;
import com.gsl.reports.VoucherReportRows;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.dataprovider.fixture.finance.ActiveGroupFilters;
import com.gsl.utils.dataprovider.fixture.finance.CoverageCatalog;
import com.gsl.utils.dataprovider.fixture.finance.CoverageRow;
import com.gsl.validation.CombinedValidationError;
import com.gsl.validation.ui.UiValidationFailure;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Offline harness: generates a sample {@code Voucher_Coverage_Results.xlsx} from realistic FAKE
 * results (no Selenium, no DB) into {@code target/coverage-sample/} (override with
 * {@code -Dcoverage.sample.dir=...}) and re-opens it with Apache POI to prove it is well-formed -
 * sheets, native charts and pivot-table parts present. Run with {@code -Dtest=CoverageWorkbookSampleTest}.
 */
public class CoverageWorkbookSampleTest {

    @Test
    public void generatesAReopenableSampleWorkbookWithChartsAndPivots() throws Exception {
        Path dir = Paths.get(System.getProperty("coverage.sample.dir", "target/coverage-sample")).toAbsolutePath();
        Files.createDirectories(dir.resolve("screenshots"));
        Path shot = dir.resolve("screenshots").resolve("Save_voucher_1700000000000_T21.png");
        Files.write(shot, new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        Path out = dir.resolve(CoverageWorkbookBuilder.FILE_NAME);

        List<CoverageResult> results = fakeResults(shot);
        Instant start = Instant.now().minusSeconds(3 * 3600 + 17 * 60);
        RunMeta meta = RunMeta.builder().runId("20261004_101500").env("stage-ideal").browser("chrome")
                .branch("GinesysAutomation").user("arani.barman").machine("QA-LAPTOP-07")
                .suiteName("GV Mandatory").suiteFile("testng-gv-ui.xml").start(start).end(Instant.now())
                .activeFilters(List.of("gv.group=MANDATORY", "gv.edit.group=MANDATORY")).build();
        ActiveGroupFilters filters = new ActiveGroupFilters("MANDATORY", Set.of(), "MANDATORY", "MANDATORY", "MANDATORY", Set.of());

        CoverageWorkbookBuilder.build(out, CoverageCatalog.all(), results, "index.html", meta, filters);

        assertTrue(Files.size(out) > 20_000, "workbook should not be trivially small");
        try (InputStream in = Files.newInputStream(out); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            assertEquals(wb.getNumberOfSheets(), 9);
            int charts = 0;
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                XSSFSheet s = wb.getSheetAt(i);
                if (s.getDrawingPatriarch() != null) charts += s.getDrawingPatriarch().getCharts().size();
            }
            assertTrue(charts >= 6, "expected Dashboard + Pivot charts, found " + charts);
            assertEquals(wb.getSheet("Pivot").getPivotTables().size(), 3, "three native PivotTables expected");
            assertTrue(wb.getSheet("Execution Results").getLastRowNum() >= results.size() + 3);
        }
        Set<String> parts = new HashSet<>();
        try (ZipFile zip = new ZipFile(out.toFile())) {
            zip.stream().map(ZipEntry::getName).forEach(parts::add);
        }
        assertTrue(parts.stream().anyMatch(p -> p.startsWith("xl/charts/chart")), "chart parts: " + parts);
        assertTrue(parts.stream().anyMatch(p -> p.startsWith("xl/pivotTables/pivotTable")), "pivot table parts");
        assertTrue(parts.stream().anyMatch(p -> p.startsWith("xl/pivotCache/pivotCacheDefinition")), "pivot cache parts");
        System.out.println("[CoverageWorkbookSample] " + out + " (" + Files.size(out) + " bytes), parts: " + parts.size());
    }

    // -- fake data ------------------------------------------------------------------------------------

    private static List<CoverageResult> fakeResults(Path screenshot) {
        List<CoverageResult> out = new ArrayList<>();
        long t0 = System.currentTimeMillis() - 3 * 3600_000L;
        int[] n = {0};
        MergedFinanceGeneralVoucherData gv = gvData();
        List<String[]> header = VoucherReportRows.withoutHeaderRow(GeneralVoucherExtentDescriptionLogger.headerRowsOf(gv));
        List<String[]> expected = VoucherReportRows.withoutHeaderRow(GeneralVoucherExtentDescriptionLogger.expectedRowsOf(gv));

        List<CoverageRow> catalog = CoverageCatalog.all();
        int gvCreate = 0, gvEdit = 0, gvRevert = 0, gvDelete = 0, ar = 0, ap = 0;
        for (CoverageRow row : catalog) {
            if (row.isDbExpanded() || !"MANDATORY".equals(row.getGroup()) && !CoverageRow.GROUP_ALWAYS.equals(row.getGroup())) continue;
            n[0]++;
            String status = "PASS";
            String testCaseId;
            switch (row.getModule() + row.getFlow()) {
                case "GVCreate" -> testCaseId = String.format("FIN-GV-ADD-COMBO-%02d", ++gvCreate);
                case "GVEdit" -> testCaseId = String.format("FIN-GV-EDIT-HEADER-%02d", ++gvEdit);
                case "GVRevert" -> testCaseId = String.format("FIN-GV-REVERT-%02d", ++gvRevert);
                case "GVDelete" -> testCaseId = String.format("FIN-GV-DELETE-%02d", ++gvDelete);
                case "ARCreate" -> testCaseId = String.format("FIN-AR-ADD-DOC-%02d", ++ar);
                default -> testCaseId = row.getTestCaseId() != null ? row.getTestCaseId() : String.format("FIN-AP-ADD-DOC-%02d", ++ap);
            }
            CoverageResult.CoverageResultBuilder b = CoverageResult.builder().module(row.getModule()).flow(row.getFlow())
                    .testCaseId(testCaseId).variantId(row.getVariantId()).scenarioType(row.getScenarioType() == null ? "" : row.getScenarioType())
                    .description(row.getDescription() == null ? "" : row.getDescription()).methodName("sample")
                    .titleDescription("Verify that a " + (row.getModule().equals("GV") ? "General Voucher" : row.getModule() + " Voucher")
                            + " is " + row.getFlow().toLowerCase() + "d successfully for " + row.getDescription())
                    .scenarioSummary(row.getModule() + " | " + row.getScenarioType() + " | " + nz(row.getKeyFields()))
                    .runIndex(n[0] % 9 + 1).runTotal(9).startMs(t0 + n[0] * 41_000L).endMs(t0 + n[0] * 41_000L + 38_000L)
                    .durationMs(20_000L + (n[0] * 7919L) % 40_000L).thread("TestNG-test-" + (n[0] % 3 + 1))
                    .voucherNo("GV/0" + (2500 + n[0]) + "/26-27").user("webapi" + (n[0] % 5 + 1)).author("arani")
                    .headerFields(header).expectedRows(expected)
                    .expectedSummary(VoucherReportRows.summariseExpected(expected));
            if (n[0] == 3 || n[0] == 11) {
                // UI + DB validation failure (the case the old workbook reduced to "1 validation area failed:")
                UiValidationFailure ui = new UiValidationFailure("GV_OWNER_SITE_LABEL", "font-size", "12px", "13px");
                CombinedValidationError err = new CombinedValidationError(List.of(
                        CombinedValidationError.uiArea(List.of(ui)),
                        new CombinedValidationError.Area(CombinedValidationError.Kind.DATABASE, "Database Validation",
                                List.of("finvchdet[1].damount expected 1,000.00 but was 1,100.00",
                                        "finpost.vmg entries not balanced"))));
                FailureClassifier.Outcome o = FailureClassifier.classify(err, "FAIL", List.of(ui));
                b.status("FAIL").reason(o.summary()).failureCategory(o.category()).failureMessage(o.fullMessage())
                        .failures(o.details()).failingStep("Validate Database").screenshotFile(screenshot.toString());
            } else if (n[0] == 7) {
                FailureClassifier.Outcome o = FailureClassifier.classify(
                        new org.openqa.selenium.TimeoutException("Expected condition failed: waiting for visibility of Save button"),
                        "FAIL", List.of());
                b.status("FAIL").reason(o.summary()).failureCategory(o.category()).failureMessage(o.fullMessage())
                        .failures(o.details()).failingStep("Save voucher").screenshotFile(screenshot.toString());
            } else if (n[0] == 14) {
                FailureClassifier.Outcome o = FailureClassifier.classify(
                        new org.testng.SkipException("No Posted voucher found for owner site in open period"), "SKIP", List.of());
                b.status("SKIP").reason("No Posted voucher found for owner site in open period").failureCategory(o.category());
            } else {
                b.status(status);
                if (n[0] == 5) b.retries(1).passedOnRetry(true);
            }
            out.add(b.build());
        }
        // Results with no catalog row: AR UI validation + a GV negative.
        for (int i = 1; i <= 3; i++) {
            out.add(CoverageResult.builder().module("AR").flow("UI Validation").testCaseId("FIN-AR-UI-0" + i).variantId("")
                    .scenarioType("").description("AR UI contract").methodName("ui").status("PASS").durationMs(9_000)
                    .reason("").titleDescription("Verify the AR voucher form's styles and states").runIndex(i).runTotal(3)
                    .startMs(t0 + i * 1000L).endMs(t0 + i * 1000L + 9000L).thread("TestNG-test-1").build());
        }
        return out;
    }

    private static MergedFinanceGeneralVoucherData gvData() {
        MasterTestData master = new MasterTestData();
        master.setTestcaseid("TC1");
        master.setTestcasename("GV | Income GL | Site allocation only | 1 GL(s) x 1 Site(s) | Credit | Reversal:No | Payment:CHEQUE");
        DocumentInformation doc = new DocumentInformation("TC1", "SCH-GV", LocalDate.now(), "REF-20261004", "", "Auto remark");
        return MergedFinanceGeneralVoucherData.builder().masterTestData(master).documentInformation(doc)
                .scenarioTypeLabel("GV_INCOME_T2_CR_REV_N_BC").build();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

}
