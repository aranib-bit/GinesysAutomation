package com.tests.config;

import com.gsl.reports.CoverageResult;
import com.gsl.reports.CoverageWorkbookBuilder;
import com.gsl.utils.dataprovider.fixture.finance.CoverageCatalog;
import com.gsl.utils.dataprovider.fixture.finance.CoverageRow;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherCombinations;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Browser-free, DB-free checks of the voucher coverage catalog and the catalog/results join that
 * feeds {@code Voucher_Coverage_Results.xlsx}. Run with {@code -Dtest=CoverageCatalogTest}.
 */
public class CoverageCatalogTest {

    @Test
    public void gvEditCatalogHasThirtyScenariosSplitNineToTwentyOne() {
        List<CoverageRow> edit = CoverageCatalog.gvEdit();
        assertEquals(edit.size(), 30);
        assertEquals(edit.stream().filter(r -> "MANDATORY".equals(r.getGroup())).count(), 9);
        assertEquals(edit.stream().filter(r -> "REGRESSION_PHASE1".equals(r.getGroup())).count(), 21);
        assertTrue(edit.stream().allMatch(r -> r.getDescription() != null && !r.getDescription().isBlank()),
                "every GV Edit variant must carry a description");
        CoverageRow closed = edit.stream()
                .filter(r -> "GV_EDIT_HEADER_POSTED_MONTH_CLOSED_NEGATIVE".equals(r.getVariantId())).findFirst().orElseThrow();
        assertEquals(closed.getGroup(), "REGRESSION_PHASE1");
    }

    @Test
    public void gvCreateRevertAndDeleteCountsFollowTheirSources() {
        long all = GeneralVoucherCombinations.allCombinations().size();
        long multiRow = GeneralVoucherCombinations.allCombinations().stream().filter(v -> v.isMultiRowPayment()).count();
        assertEquals(CoverageCatalog.gvCreate().size(), all + 1);                 // + MIXED_TYPES
        assertEquals(CoverageCatalog.gvRevert().size(), all - multiRow + 1);      // - multi-row + closed-period negative
        assertEquals(CoverageCatalog.gvDelete().size(), 2);
    }

    @Test
    public void everyCatalogRowHasAUniqueJoinKeyAndAGroup() {
        Set<String> keys = new HashSet<>();
        for (CoverageRow r : CoverageCatalog.all()) {
            assertNotNull(r.getGroup(), r.getVariantId());
            assertTrue(keys.add(r.getModule() + "|" + r.getFlow() + "|" + r.getVariantId()),
                    "duplicate catalog key: " + r.getModule() + "|" + r.getFlow() + "|" + r.getVariantId());
        }
        assertTrue(CoverageCatalog.ar().size() > 9);
        assertEquals(CoverageCatalog.ap().stream().filter(r -> "Revert".equals(r.getFlow())).count(), 2);
        assertEquals(CoverageCatalog.ap().stream().filter(r -> "Delete".equals(r.getFlow())).count(), 2);
    }

    @Test
    public void joinMatchesByVariantIdThenTestCaseIdAndKeepsFlowsApart() {
        List<CoverageRow> catalog = List.of(
                row("GV", "Create", "V1", null, "MANDATORY", false),
                row("GV", "Revert", "V1", null, "REGRESSION_PHASE1", false),   // same variant id, different flow
                row("AP", "Revert", "AP_REVERT_UNTAGGED", "FIN-AP-REVERT-01", "MANDATORY", false),
                row("AR", "Create", "SELECT_DOCUMENT", null, "VARIES", true),
                row("GV", "Edit", "NEVER_RAN", null, "MANDATORY", false));
        List<CoverageResult> results = List.of(
                result("GV", "Create", "FIN-GV-ADD-COMBO-03", "V1", "", "PASS"),
                result("AP", "Revert", "FIN-AP-REVERT-01", "", "", "FAIL"),
                result("AR", "Create", "FIN-AR-ADD-DOC-04", "SD_X", "SELECT_DOCUMENT", "PASS"),
                result("GV", "Edit", "FIN-GV-EDIT-HEADER", "", "", "SKIP"));

        CoverageWorkbookBuilder.JoinResult join = CoverageWorkbookBuilder.join(catalog, results);

        assertEquals(join.catalogRows.get(0).result, results.get(0));   // GV Create V1 -> by variant id
        assertEquals(join.catalogRows.get(1).result, null);             // GV Revert V1 did not run
        assertEquals(join.catalogRows.get(2).result, results.get(1));   // AP Revert -> by test-case id fallback
        assertEquals(join.catalogRows.get(3).result, null);             // DB-expanded placeholder has no 1:1 result
        assertEquals(join.expandedByRow.get(catalog.get(3)), List.of(results.get(2)));
        assertEquals(join.catalogRows.get(4).result, null);             // not run
        assertEquals(join.unmatched, List.of(results.get(3)));          // method-level skip claimed by no row
    }

    @Test
    public void workbookIsWrittenWithAllFiveSheets() throws Exception {
        Path out = Files.createTempFile("coverage", ".xlsx");
        try {
            CoverageWorkbookBuilder.build(out, CoverageCatalog.all(),
                    List.of(result("GV", "Edit", "FIN-GV-EDIT-HEADER-01", "GV_EDIT_HEADER_UNPOSTED_FIRSTDAY", "HEADER_ONLY", "PASS")),
                    "index.html");
            try (InputStream in = Files.newInputStream(out); Workbook wb = new XSSFWorkbook(in)) {
                List<String> names = new java.util.ArrayList<>();
                wb.forEach(s -> names.add(s.getSheetName()));
                assertEquals(names, List.of("Summary", "Results", "GV Coverage", "AR AP Coverage", "Notes"));
                Sheet results = wb.getSheet("Results");
                assertEquals(results.getRow(1).getCell(6).getStringCellValue(), "PASS");
                assertEquals(results.getRow(1).getCell(4).getStringCellValue(), "REGRESSION_PHASE1");
                Sheet gv = wb.getSheet("GV Coverage");
                String statuses = java.util.stream.IntStream.rangeClosed(1, gv.getLastRowNum())
                        .mapToObj(i -> gv.getRow(i).getCell(6).getStringCellValue()).distinct().sorted()
                        .collect(Collectors.joining(","));
                assertEquals(statuses, "NOT RUN,PASS");
            }
        } finally {
            Files.deleteIfExists(out);
        }
    }

    private static CoverageRow row(String module, String flow, String variant, String testCaseId, String group, boolean expanded) {
        return CoverageRow.builder().module(module).flow(flow).variantId(variant).testCaseId(testCaseId)
                .group(group).description("d").scenarioType(variant).dbExpanded(expanded).build();
    }

    private static CoverageResult result(String module, String flow, String testCaseId, String variant,
                                         String scenarioType, String status) {
        return CoverageResult.builder().module(module).flow(flow).testCaseId(testCaseId).variantId(variant)
                .scenarioType(scenarioType).description("").methodName("m").status(status).durationMs(1500).reason("").build();
    }
}
