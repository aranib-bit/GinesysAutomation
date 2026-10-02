package com.gsl.utils.dataprovider.source.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.ApRevertVoucherInfo;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.source.TestDataSource;
import io.github.sskorol.data.XlsxReader;
import one.util.streamex.IntStreamEx;
import org.testng.SkipException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static io.github.sskorol.data.TestDataReader.use;

/**
 * Excel-driven test-data source for AP Voucher's <b>Revert (from List Page)</b> flow — mirrors
 * {@code ExcelGeneralVoucherRevertDataSource}'s exact shape, adapted for AP's own
 * {@link MergedFinanceApVoucherData} DTO.
 *
 * <p>Deliberately separate from {@link ExcelApVoucherDataSource} — that class is shaped entirely
 * around Create's {@code DocumentInformation}/{@code SubLedgerInformation}/etc. sheets. Revert
 * only needs one thing: an existing, already-posted voucher number to locate in the list page — so
 * it reads its own small {@link ApRevertVoucherInfo} sheet instead. Untag detection is a live DB
 * read regardless of source (Excel vs DB — see {@code DocumentAdjustmentFlow}), so this sheet
 * needs no separate "is it tagged" column; a row's {@code Existing Voucher No.} may or may not
 * point at a currently-tagged document, and {@code RevertApVoucher}'s own untag step handles
 * either case transparently.
 */
public class ExcelApVoucherRevertDataSource implements TestDataSource<MergedFinanceApVoucherData> {

    /**
     * Reads {@code MasterTestData} + {@code ApRevertVoucherInfo}, joined by {@code TestCaseID} and
     * filtered to {@code methodName}, and builds one {@link MergedFinanceApVoucherData} per row
     * with {@code existingVoucherNo} set. Throws {@link SkipException} (rather than silently
     * returning an empty list) when either sheet has no matching row — an empty Revert data
     * provider is always a fixture-authoring mistake, never a legitimate "nothing to do" state.
     */
    @Override
    public List<MergedFinanceApVoucherData> load(String methodName) {

        List<MasterTestData> masterData = use(XlsxReader.class)
                .withTarget(MasterTestData.class)
                .withSource(ConfigFactory.getExcelDataConfig().MasterTestData())
                .read().toList();

        List<ApRevertVoucherInfo> revertInfoList = use(XlsxReader.class)
                .withTarget(ApRevertVoucherInfo.class)
                .withSource(ConfigFactory.getExcelDataConfig().apVoucherPath())
                .read().toList();

        List<MasterTestData> nameMatched = masterData.stream()
                .filter(tc -> methodName.equalsIgnoreCase(tc.getTestcasename()))
                .collect(Collectors.toList());

        Log.info("[ExcelApVoucherRevertDataSource] MasterTestData total rows read : {}", masterData.size());
        Log.info("[ExcelApVoucherRevertDataSource] Rows matching method '{}' : {}", methodName, nameMatched.size());

        if (nameMatched.isEmpty()) {
            throw new SkipException(
                    "[ExcelApVoucherRevertDataSource] No MasterTestData row found for method='"
                    + methodName + "'. Add a row with testcasename='" + methodName + "' to the "
                    + "MasterTestData sheet.");
        }

        List<MasterTestData> filteredCases = nameMatched.stream()
                .filter(tc -> {
                    boolean found = revertInfoList.stream()
                            .anyMatch(r -> r.getTestCaseId().equalsIgnoreCase(tc.getTestcaseid()));
                    if (!found) {
                        Log.warn("  [SKIPPED] TestCaseID='{}' has no row in ApRevertVoucherInfo sheet",
                                tc.getTestcaseid());
                    }
                    return found;
                })
                .collect(Collectors.toList());

        if (filteredCases.isEmpty()) {
            throw new SkipException(
                    "[ExcelApVoucherRevertDataSource] No ApRevertVoucherInfo row matches any "
                    + "MasterTestData TestCaseID for method='" + methodName + "'. Ensure the "
                    + "'ApRevertVoucherInfo' sheet has a row whose TestCaseID matches.");
        }

        int grandTotal = filteredCases.stream()
                .mapToInt(tc -> tc.getInvocationcount() != null ? tc.getInvocationcount() : 1)
                .sum();
        AtomicInteger globalCounter = new AtomicInteger(0);

        return filteredCases.stream()
                .flatMap(tc -> {
                    String tcId = tc.getTestcaseid();

                    ApRevertVoucherInfo revertInfo = revertInfoList.stream()
                            .filter(r -> r.getTestCaseId().equalsIgnoreCase(tcId))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    "[ExcelApVoucherRevertDataSource] Missing ApRevertVoucherInfo for: " + tcId));

                    int totalInvocations = tc.getInvocationcount() != null ? tc.getInvocationcount() : 1;

                    return IntStreamEx.range(1, totalInvocations + 1)
                            .mapToObj(invIdx ->
                                    MergedFinanceApVoucherData.builder()
                                            .masterTestData(tc)
                                            .existingVoucherNo(revertInfo.getExistingVoucherNo())
                                            .invocationIndex(globalCounter.incrementAndGet())
                                            .totalInvocations(grandTotal)
                                            .build()
                            );
                })
                .collect(Collectors.toList());
    }
}
