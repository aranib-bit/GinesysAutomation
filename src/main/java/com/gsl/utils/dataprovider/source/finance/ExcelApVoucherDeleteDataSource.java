package com.gsl.utils.dataprovider.source.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.model.finance.FinVchMain;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.ApDeleteVoucherInfo;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.source.TestDataSource;
import io.github.sskorol.data.XlsxReader;
import one.util.streamex.IntStreamEx;
import org.jdbi.v3.core.Jdbi;
import org.testng.SkipException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static io.github.sskorol.data.TestDataReader.use;

/**
 * Excel-driven test-data source for AP Voucher's <b>Delete</b> flow — mirrors
 * {@link ExcelApVoucherRevertDataSource}'s exact shape, reading {@link ApDeleteVoucherInfo}
 * instead. Unlike {@code ExcelGeneralVoucherRevertDataSource} (which leaves {@code releaseStatus}
 * unset, relying entirely on {@code RevertGeneralVoucher}'s own live DB pre-check step), this
 * class DOES resolve {@code release_status} live here — {@code DeleteApVoucher}'s "Unpost and
 * Untag if Posted" step (mirroring {@code DeleteArVoucher}'s own template) branches directly on
 * {@code testData.getReleaseStatus()} with no live re-read of its own, so this field must be
 * genuinely populated for Excel-mode Delete to unpost a Posted voucher correctly — the same live
 * DB read {@code ExcelGeneralVoucherEditDataSource} already performs for its own header fields,
 * just applied to one more column here.
 */
public class ExcelApVoucherDeleteDataSource implements TestDataSource<MergedFinanceApVoucherData> {

    /**
     * Reads {@code MasterTestData} + {@code ApDeleteVoucherInfo}, joined by {@code TestCaseID} and
     * filtered to {@code methodName}, and builds one {@link MergedFinanceApVoucherData} per row
     * with {@code existingVoucherNo} set. Throws {@link SkipException} when either sheet has no
     * matching row.
     */
    @Override
    public List<MergedFinanceApVoucherData> load(String methodName) {

        List<MasterTestData> masterData = use(XlsxReader.class)
                .withTarget(MasterTestData.class)
                .withSource(ConfigFactory.getExcelDataConfig().MasterTestData())
                .read().toList();

        List<ApDeleteVoucherInfo> deleteInfoList = use(XlsxReader.class)
                .withTarget(ApDeleteVoucherInfo.class)
                .withSource(ConfigFactory.getExcelDataConfig().apVoucherPath())
                .read().toList();

        List<MasterTestData> nameMatched = masterData.stream()
                .filter(tc -> methodName.equalsIgnoreCase(tc.getTestcasename()))
                .collect(Collectors.toList());

        Log.info("[ExcelApVoucherDeleteDataSource] MasterTestData total rows read : {}", masterData.size());
        Log.info("[ExcelApVoucherDeleteDataSource] Rows matching method '{}' : {}", methodName, nameMatched.size());

        if (nameMatched.isEmpty()) {
            throw new SkipException(
                    "[ExcelApVoucherDeleteDataSource] No MasterTestData row found for method='"
                    + methodName + "'. Add a row with testcasename='" + methodName + "' to the "
                    + "MasterTestData sheet.");
        }

        List<MasterTestData> filteredCases = nameMatched.stream()
                .filter(tc -> {
                    boolean found = deleteInfoList.stream()
                            .anyMatch(r -> r.getTestCaseId().equalsIgnoreCase(tc.getTestcaseid()));
                    if (!found) {
                        Log.warn("  [SKIPPED] TestCaseID='{}' has no row in ApDeleteVoucherInfo sheet",
                                tc.getTestcaseid());
                    }
                    return found;
                })
                .collect(Collectors.toList());

        if (filteredCases.isEmpty()) {
            throw new SkipException(
                    "[ExcelApVoucherDeleteDataSource] No ApDeleteVoucherInfo row matches any "
                    + "MasterTestData TestCaseID for method='" + methodName + "'. Ensure the "
                    + "'ApDeleteVoucherInfo' sheet has a row whose TestCaseID matches.");
        }

        int grandTotal = filteredCases.stream()
                .mapToInt(tc -> tc.getInvocationcount() != null ? tc.getInvocationcount() : 1)
                .sum();
        AtomicInteger globalCounter = new AtomicInteger(0);
        Jdbi jdbi = DatabaseManager.getJdbi();

        return filteredCases.stream()
                .flatMap(tc -> {
                    String tcId = tc.getTestcaseid();

                    ApDeleteVoucherInfo deleteInfo = deleteInfoList.stream()
                            .filter(r -> r.getTestCaseId().equalsIgnoreCase(tcId))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    "[ExcelApVoucherDeleteDataSource] Missing ApDeleteVoucherInfo for: " + tcId));

                    FinVchMain header = jdbi.onDemand(FinVchMainDao.class)
                            .findBySchemeDocno(deleteInfo.getExistingVoucherNo())
                            .orElseThrow(() -> new SkipException("[ExcelApVoucherDeleteDataSource] TestCaseID='" + tcId
                                    + "' 'Existing Voucher No.'='" + deleteInfo.getExistingVoucherNo()
                                    + "' not found in finvchmain."));

                    int totalInvocations = tc.getInvocationcount() != null ? tc.getInvocationcount() : 1;

                    return IntStreamEx.range(1, totalInvocations + 1)
                            .mapToObj(invIdx ->
                                    MergedFinanceApVoucherData.builder()
                                            .masterTestData(tc)
                                            .existingVoucherNo(deleteInfo.getExistingVoucherNo())
                                            .releaseStatus(header.getReleaseStatus())
                                            .invocationIndex(globalCounter.incrementAndGet())
                                            .totalInvocations(grandTotal)
                                            .build()
                            );
                })
                .collect(Collectors.toList());
    }
}
