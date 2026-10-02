package com.gsl.utils.dataprovider.source.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.enums.PeriodDomain;
import com.gsl.model.finance.FinVchMain;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.ApEditVoucherInfo;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.DocumentInformation;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.fixture.finance.VoucherEditDateVariant;
import com.gsl.utils.dataprovider.source.TestDataSource;
import com.gsl.utils.dataprovider.source.finance.common.AccountingPeriodResolver;
import io.github.sskorol.data.XlsxReader;
import org.jdbi.v3.core.Jdbi;
import org.testng.SkipException;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static io.github.sskorol.data.TestDataReader.use;

/**
 * Excel-driven test-data source for {@code EditApVoucher}'s single header-only scenario — mirrors
 * {@code ExcelGeneralVoucherEditDataSource}'s "Data flow" shape (read {@code MasterTestData} +
 * the sheet, join by {@code TestCaseID}, fetch the claimed voucher's live {@code finvchmain}
 * header, build a new {@code DocumentInformation} carrying only the new date/reference/remarks),
 * trimmed to header-only since AP Edit this pass has no DETAIL_LINE/PAYMENT_INFO variant.
 *
 * <p>{@code voucherno}/{@code cashbank}/{@code ownersite}/{@code onAccount} are all left
 * {@code null} on the built {@link DocumentInformation} — {@code EditApVoucher} itself resolves
 * the live On Account state right before filling the form (see that class's own "Apply Changes to
 * Document Header" step Javadoc for why On Account must never be blindly re-selected), and
 * {@code voucherno}/{@code cashbank} are unchanged-on-Edit fields the same way
 * {@code ExcelGeneralVoucherEditDataSource} already leaves them null for GV.
 *
 * <p><b>IMPORTANT — manual step required before running:</b> the sample row shipped in
 * {@code ApVoucher.xlsx}'s {@code ApEditVoucherInfo} tab uses a PLACEHOLDER "Existing Voucher No."
 * value that will NOT exist in a real database — replace it with a real, currently-existing AP
 * Voucher number from your test environment before running {@code EditApVoucher} against
 * {@code testdata.source=EXCEL}.
 */
public class ExcelApVoucherEditDataSource implements TestDataSource<MergedFinanceApVoucherData> {

    /**
     * Reads {@code MasterTestData} + {@code ApEditVoucherInfo}, filters to rows matching
     * {@code methodName} with a corresponding {@code ApEditVoucherInfo} row, then delegates each
     * surviving row to {@link #buildOne}.
     */
    @Override
    public List<MergedFinanceApVoucherData> load(String methodName) {

        List<MasterTestData> masterData = use(XlsxReader.class)
                .withTarget(MasterTestData.class)
                .withSource(ConfigFactory.getExcelDataConfig().MasterTestData())
                .read().toList();

        List<ApEditVoucherInfo> editInfoList = use(XlsxReader.class)
                .withTarget(ApEditVoucherInfo.class)
                .withSource(ConfigFactory.getExcelDataConfig().apVoucherPath())
                .read().toList();

        List<MasterTestData> nameMatched = masterData.stream()
                .filter(tc -> methodName.equalsIgnoreCase(tc.getTestcasename()))
                .collect(Collectors.toList());

        Log.info("[ExcelApVoucherEditDataSource] MasterTestData total rows read : {}", masterData.size());
        Log.info("[ExcelApVoucherEditDataSource] Rows matching method '{}' : {}", methodName, nameMatched.size());

        if (nameMatched.isEmpty()) {
            throw new SkipException(
                    "[ExcelApVoucherEditDataSource] No MasterTestData row found for method='"
                    + methodName + "'. Add a row with testcasename='" + methodName + "' to the "
                    + "MasterTestData sheet.");
        }

        List<MasterTestData> filteredCases = nameMatched.stream()
                .filter(tc -> {
                    boolean found = editInfoList.stream()
                            .anyMatch(r -> r.getTestCaseId().equalsIgnoreCase(tc.getTestcaseid()));
                    if (!found) {
                        Log.warn("  [SKIPPED] TestCaseID='{}' has no row in ApEditVoucherInfo sheet",
                                tc.getTestcaseid());
                    }
                    return found;
                })
                .collect(Collectors.toList());

        if (filteredCases.isEmpty()) {
            throw new SkipException(
                    "[ExcelApVoucherEditDataSource] No ApEditVoucherInfo row matches any "
                    + "MasterTestData TestCaseID for method='" + methodName + "'. Ensure the "
                    + "'ApEditVoucherInfo' sheet has a row whose TestCaseID matches.");
        }

        int total = filteredCases.size();
        AtomicInteger counter = new AtomicInteger(0);

        return filteredCases.stream()
                .map(tc -> buildOne(tc, editInfoList, counter.incrementAndGet(), total))
                .collect(Collectors.toList());
    }

    /**
     * Builds one {@link MergedFinanceApVoucherData} for {@code tc}: fetches the claimed voucher's
     * live {@code finvchmain} header (for {@code release_status} and, when the sheet's
     * {@code New Date} cell is blank, an accounting-year-aware fallback date), and builds a new
     * {@link DocumentInformation} carrying only the new reference/remarks/date.
     *
     * @throws SkipException if {@code editInfo.getExistingVoucherNo()} has no {@code finvchmain} row
     */
    private MergedFinanceApVoucherData buildOne(MasterTestData tc, List<ApEditVoucherInfo> editInfoList,
            int invocationIndex, int totalInvocations) {

        String tcId = tc.getTestcaseid();
        ApEditVoucherInfo editInfo = editInfoList.stream()
                .filter(r -> r.getTestCaseId().equalsIgnoreCase(tcId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "[ExcelApVoucherEditDataSource] Missing ApEditVoucherInfo for: " + tcId));

        Jdbi jdbi = DatabaseManager.getJdbi();
        FinVchMain header = jdbi.onDemand(FinVchMainDao.class)
                .findBySchemeDocno(editInfo.getExistingVoucherNo())
                .orElseThrow(() -> new SkipException("[ExcelApVoucherEditDataSource] TestCaseID='" + tcId
                        + "' 'Existing Voucher No.'='" + editInfo.getExistingVoucherNo() + "' not found in finvchmain."));

        LocalDate newDate;
        if (editInfo.getNewDate() != null) {
            newDate = editInfo.getNewDate();
        } else {
            // Blank cell — resolve an accounting-year-aware date rather than blindly defaulting to
            // LocalDate.now() (see ExcelGeneralVoucherEditDataSource's own identical reasoning: an
            // unconditional "today" default can fall outside the existing voucher's own accounting
            // year and cause a live save failure).
            long ouCode = AccountingPeriodResolver.resolveOuCode(jdbi, header.getAdmsiteCodeOwner());
            newDate = AccountingPeriodResolver.resolveEditDate(
                    jdbi, header.getYcode(), ouCode, VoucherEditDateVariant.RANDOM_MID, PeriodDomain.FINANCE);
        }

        // voucherno/cashbank/ownersite/onAccount all left null — see class Javadoc for why each is
        // deliberately unset here rather than guessed at data-source-build time.
        DocumentInformation newDocInfo = new DocumentInformation(
                tcId, null, null, null, editInfo.getNewRemarks(), newDate, editInfo.getNewReference(), null);

        return MergedFinanceApVoucherData.builder()
                .masterTestData(tc)
                .existingVoucherNo(editInfo.getExistingVoucherNo())
                .releaseStatus(header.getReleaseStatus())
                .documentInformation(newDocInfo)
                .invocationIndex(invocationIndex)
                .totalInvocations(totalInvocations)
                .build();
    }
}
