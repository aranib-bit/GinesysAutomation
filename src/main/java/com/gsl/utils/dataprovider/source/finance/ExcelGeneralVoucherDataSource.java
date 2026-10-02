package com.gsl.utils.dataprovider.source.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.HrEmpDao;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.driver.DriverManager;
import com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType;
import com.gsl.model.finance.FinGl;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.source.TestDataSource;
import com.gsl.utils.io.OptionalColumnMerger;
import io.github.sskorol.data.XlsxReader;
import one.util.streamex.IntStreamEx;
import org.jdbi.v3.core.Jdbi;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static io.github.sskorol.data.TestDataReader.use;

/**
 * Excel-driven test-data source for General Voucher — mirrors
 * {@link ExcelApVoucherDataSource}'s shape, simplified for General Voucher's
 * flatter document (no On Account / SelectDocument / Payment / TDS concepts).
 *
 * <p>GL-type source of truth is always the DB: every {@code ExpenseIncomeDetail}
 * row's {@code resolvedGlType} is set here via {@code FinGlDao.findByGlName}
 * even in Excel mode — the optional {@code Expected GL Type} column is only
 * a soft assertion against that live-resolved value.
 */
public class ExcelGeneralVoucherDataSource implements TestDataSource<MergedFinanceGeneralVoucherData> {

    /**
     * Reads the General Voucher Excel fixtures, resolves each line's GL type live from
     * {@code fingl} (see class Javadoc), resolves the owner site for the logged-in user, joins
     * everything by {@code TestCaseID}, and expands each surviving row into
     * {@code invocationcount} copies with globally unique {@code invocationIndex}es — mirrors
     * {@link ExcelApVoucherDataSource#load}'s shape, simplified for GV's flatter document.
     */
    @Override
    public List<MergedFinanceGeneralVoucherData> load(String methodName) {

        List<MasterTestData> masterData = use(XlsxReader.class)
                .withTarget(MasterTestData.class)
                .withSource(ConfigFactory.getExcelDataConfig().MasterTestData())
                .read().toList();

        List<DocumentInformation> docList = use(XlsxReader.class)
                .withTarget(DocumentInformation.class)
                .withSource(ConfigFactory.getExcelDataConfig().generalVoucherPath())
                .read().toList();

        docList.forEach(doc -> {
            if (doc.getDocumentdate() == null) {
                throw new IllegalStateException(
                        "[ExcelGeneralVoucherDataSource] documentdate is null for TestCaseID='"
                        + doc.getTestCaseId() + "'. Ensure the 'Date' column in the "
                        + "'DocumentInformation' sheet is non-empty and uses a supported format: "
                        + "dd/MM/yyyy, dd-MM-yyyy, or yyyy-MM-dd.");
            }
        });

        // ── Owner site: resolved live via the logged-in user's site, same as GV's DB mode
        // (DbGeneralVoucherDataSource.findFirstUsableOwnerSite) — deliberately never sourced
        // from Excel (see DocumentInformationBase's Javadoc). Previously this resolution only
        // ran in DB mode; Excel-mode tests left ownersite permanently null, which surfaced as a
        // false FAIL in ActualResultsLogger's finvchmain "Owner site" row (Expected "—" could
        // never match the real DB value). Resolve once per load() call and apply to every row.
        resolveOwnerSite(DatabaseManager.getJdbi()).ifPresent(ownerSiteName ->
                docList.forEach(doc -> doc.setOwnersite(ownerSiteName)));

        List<ExpenseIncomeDetail> lineList = use(XlsxReader.class)
                .withTarget(ExpenseIncomeDetail.class)
                .withSource(ConfigFactory.getExcelDataConfig().generalVoucherPath())
                .read().toList();

        Log.debug("[ExcelGeneralVoucherDataSource] ExpenseIncomeDetail rows read: {}", lineList.size());
        lineList.forEach(l -> Log.debug(
                "  [ExpenseIncomeDetail] TestCaseID='{}' | LineNo='{}' | GL='{}' | SubLedger='{}'",
                l.getTestCaseID(), l.getLineNo(), l.getGeneralLedger(), l.getSubLedger()));

        // ── Optional: "Expected GL Type" column (Apache POI direct read) ──────────
        // XlsxReader throws for any @Column field whose header is absent in the sheet.
        // expectedGlType is NOT annotated with @Column for this reason.
        readOptionalExpectedGlType(
                ConfigFactory.getExcelDataConfig().generalVoucherPath(), lineList);
        // ── End optional ───────────────────────────────────────────────────────────

        // ── GL-type resolution: DB is authoritative (see class Javadoc) ────────────
        Jdbi jdbi = DatabaseManager.getJdbi();
        FinGlDao glDao = jdbi.onDemand(FinGlDao.class);
        lineList.forEach(line -> {
            FinGl gl = glDao.findByGlName(line.getGeneralLedger());
            if (gl == null) {
                throw new IllegalStateException(
                        "[ExcelGeneralVoucherDataSource] General Ledger '" + line.getGeneralLedger()
                        + "' (TestCaseID='" + line.getTestCaseID() + "' LineNo='" + line.getLineNo()
                        + "') was not found in fingl — verify the 'General Ledger' column matches a real, "
                        + "active GL name.");
            }
            GlLineType resolved = GlLineType.fromFlags(gl.isSiteApplicable(), gl.isSubLedgerApplicable());
            line.setResolvedGlType(resolved);

            if (line.getExpectedGlType() != null
                    && !line.getExpectedGlType().trim().equalsIgnoreCase(resolved.name())
                    && !matchesShortLabel(line.getExpectedGlType(), resolved)) {
                Log.warn("[ExcelGeneralVoucherDataSource] Expected GL Type mismatch for TestCaseID='{}' "
                        + "LineNo='{}': Excel says '{}', DB (fingl.slapp/costapp) resolves to '{}'. "
                        + "DB value wins — Excel's 'Expected GL Type' column is a soft assertion only.",
                        line.getTestCaseID(), line.getLineNo(), line.getExpectedGlType(), resolved);
            }
        });
        // ── End GL-type resolution ─────────────────────────────────────────────────

        List<SiteAllocation> siteAllocationList = use(XlsxReader.class)
                .withTarget(SiteAllocation.class)
                .withSource(ConfigFactory.getExcelDataConfig().generalVoucherPath())
                .read().toList();

        // ── Filter 1: match test method name ────────────────────────────────────
        List<MasterTestData> nameMatched = masterData.stream()
                .filter(tc -> methodName.equalsIgnoreCase(tc.getTestcasename()))
                .collect(Collectors.toList());

        Log.info("[ExcelGeneralVoucherDataSource] MasterTestData total rows read : {}", masterData.size());
        Log.info("[ExcelGeneralVoucherDataSource] Rows matching method '{}' : {}", methodName, nameMatched.size());

        // ── Filter 2: TestCaseID must exist in DocumentInformation ───────────────
        List<MasterTestData> filteredCases = nameMatched.stream()
                .filter(tc -> {
                    boolean found = docList.stream()
                            .anyMatch(d -> d.getTestCaseId().equalsIgnoreCase(tc.getTestcaseid()));
                    if (!found) {
                        Log.warn("  [SKIPPED by filter-2] TestCaseID='{}' has no row in DocumentInformation sheet",
                                tc.getTestcaseid());
                    }
                    return found;
                })
                .collect(Collectors.toList());

        Log.info("[ExcelGeneralVoucherDataSource] Test cases after both filters : {}", filteredCases.size());

        int grandTotal = filteredCases.stream()
                .mapToInt(tc -> tc.getInvocationcount() != null ? tc.getInvocationcount() : 1)
                .sum();
        AtomicInteger globalCounter = new AtomicInteger(0);

        return filteredCases.stream()
                .flatMap(tc -> {
                    String tcId = tc.getTestcaseid();

                    DocumentInformation doc = docList.stream()
                            .filter(d -> d.getTestCaseId().equalsIgnoreCase(tcId))
                            .findFirst()
                            .orElseThrow(() ->
                                    new IllegalStateException("Missing DocumentInformation for: " + tcId));

                    List<ExpenseIncomeDetail> lines = lineList.stream()
                            .filter(l -> l.getTestCaseID().equalsIgnoreCase(tcId))
                            .collect(Collectors.toList());

                    if (lines.isEmpty()) {
                        throw new IllegalStateException(
                                "[ExcelGeneralVoucherDataSource] No ExpenseIncomeDetail rows found for TestCaseID='"
                                + tcId + "'.");
                    }

                    List<SiteAllocation> allocations = siteAllocationList.stream()
                            .filter(a -> a.getTestCaseID() != null && a.getTestCaseID().equalsIgnoreCase(tcId))
                            .collect(Collectors.toList());

                    int totalInvocations = tc.getInvocationcount() != null ? tc.getInvocationcount() : 1;

                    return IntStreamEx.range(1, totalInvocations + 1)
                            .mapToObj(invIdx ->
                                    MergedFinanceGeneralVoucherData.builder()
                                            .masterTestData(tc)
                                            .documentInformation(doc)
                                            .expenseIncomeDetailList(lines)
                                            .siteAllocationList(allocations)
                                            .needsPaymentInfoWarning(false)
                                            .invocationIndex(globalCounter.incrementAndGet())
                                            .totalInvocations(grandTotal)
                                            .build()
                            );
                })
                .collect(Collectors.toList());
    }

    /**
     * Resolves the current login user's first active OS-OO-CM owner site — same query
     * {@code DbGeneralVoucherDataSource.findFirstUsableOwnerSite} uses for DB mode. Returns
     * {@link Optional#empty()} (rather than throwing) on any resolution failure — Excel-mode
     * tests worked without this field before, so a lookup failure here should degrade to the
     * pre-fix behavior (ownersite left null) rather than newly failing every GV Excel test.
     */
    private static Optional<String> resolveOwnerSite(Jdbi jdbi) {
        try {
            UserCredential threadUser = DriverManager.getCurrentUser();
            String loginName = threadUser != null ? threadUser.username() : ConfigFactory.getConfig().username();
            Long ecode = jdbi.onDemand(HrEmpDao.class).findEcodeByAbbre(loginName).orElse(null);
            if (ecode == null) return Optional.empty();

            String sql =
                "SELECT a.name " +
                "FROM hrdemp_ou ho " +
                "JOIN admsite a ON a.code = ho.admsite_code " +
                "WHERE ho.hrdemp_code = :ecode " +
                "  AND a.sitetype = 'OS-OO-CM' " +
                "  AND a.ext = 'N' " +
                "ORDER BY a.code";

            return jdbi.withHandle(handle -> handle.createQuery(sql)
                    .bind("ecode", ecode)
                    .mapTo(String.class)
                    .findFirst());
        } catch (Exception e) {
            Log.warn("[ExcelGeneralVoucherDataSource] Could not resolve owner site for logged-in user "
                    + "— leaving ownersite unset ({})", e.getMessage());
            return Optional.empty();
        }
    }

    /** {@code true} when the Excel value is a recognizable short form ("TYPE1"/"1"/etc.) matching {@code resolved}. */
    private static boolean matchesShortLabel(String excelValue, GlLineType resolved) {
        String v = excelValue.trim().toUpperCase();
        return switch (resolved) {
            case TYPE1_NONE -> v.equals("TYPE1") || v.equals("1");
            case TYPE2_SITE_ONLY -> v.equals("TYPE2") || v.equals("2");
            case TYPE3_SITE_AND_SUBLEDGER -> v.equals("TYPE3") || v.equals("3");
        };
    }

    /**
     * Reads the optional "Expected GL Type" column from the ExpenseIncomeDetail sheet —
     * bypassing XlsxReader so that an absent column header never causes a hard failure — and
     * merges values into {@code lineList} via {@link ExpenseIncomeDetail#setExpectedGlType(String)},
     * keyed on TestCaseID (case-insensitive) + LineNo. Mechanics live in {@link OptionalColumnMerger}.
     */
    private static void readOptionalExpectedGlType(
            String excelPath, List<ExpenseIncomeDetail> lineList) {
        OptionalColumnMerger.mergeByTwoKeys(
                excelPath, "ExpenseIncomeDetail", "TestCaseID", "LineNo", "Expected GL Type",
                lineList, ExpenseIncomeDetail::getTestCaseID, ExpenseIncomeDetail::getLineNo,
                ExpenseIncomeDetail::setExpectedGlType, "[ExcelGeneralVoucherDataSource]");
    }
}
