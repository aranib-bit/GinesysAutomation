package com.gsl.utils.dataprovider.source.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.RevertVoucherInfo;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.dto.GeneralVoucherOperationMode;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
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
 * Excel-driven test-data source for General Voucher's <b>Revert (from List Page)</b> flow.
 *
 * <p>Deliberately separate from {@link ExcelGeneralVoucherDataSource} — that class is shaped
 * entirely around Create's {@code DocumentInformation}/{@code ExpenseIncomeDetail}/
 * {@code SiteAllocation} sheets. Revert only needs one thing: an existing, already-posted
 * voucher number to locate in the list page — so it reads its own small
 * {@link RevertVoucherInfo} sheet instead.
 *
 * <h3>Data flow</h3>
 * <ol>
 *   <li>Read {@code MasterTestData} (shared sheet, same as every other source), filtered to
 *       {@code testcasename == "revertGeneralVoucher"}.</li>
 *   <li>Read the {@code RevertVoucherInfo} sheet, joined to the filtered {@code MasterTestData}
 *       rows by {@code TestCaseID}.</li>
 *   <li>Build one {@link MergedFinanceGeneralVoucherData} per row with {@code existingVoucherNo}
 *       set. No "Release Status" column (plan §16 — removed entirely, not merely downgraded to
 *       a cross-check): {@code RevertGeneralVoucher}'s own Step 1 "DB Pre-Check" reads
 *       {@code finvchmain.release_status} live and is the sole driver of every downstream
 *       decision/assertion.</li>
 * </ol>
 *
 * <p>This is the DB-mode {@code DbGeneralVoucherRevertDataSource}'s Excel-mode sibling (that
 * class is deferred to a later pass) — both populate the same two DTO fields so
 * {@code RevertGeneralVoucher} reads identically regardless of {@code testdata.source}.
 */
public class ExcelGeneralVoucherRevertDataSource implements TestDataSource<MergedFinanceGeneralVoucherData> {

    @Override
    public List<MergedFinanceGeneralVoucherData> load(String methodName) {

        List<MasterTestData> masterData = use(XlsxReader.class)
                .withTarget(MasterTestData.class)
                .withSource(ConfigFactory.getExcelDataConfig().MasterTestData())
                .read().toList();

        List<RevertVoucherInfo> revertInfoList = use(XlsxReader.class)
                .withTarget(RevertVoucherInfo.class)
                .withSource(ConfigFactory.getExcelDataConfig().generalVoucherPath())
                .read().toList();

        List<MasterTestData> nameMatched = masterData.stream()
                .filter(tc -> methodName.equalsIgnoreCase(tc.getTestcasename()))
                .collect(Collectors.toList());

        Log.info("[ExcelGeneralVoucherRevertDataSource] MasterTestData total rows read : {}", masterData.size());
        Log.info("[ExcelGeneralVoucherRevertDataSource] Rows matching method '{}' : {}", methodName, nameMatched.size());

        if (nameMatched.isEmpty()) {
            throw new SkipException(
                    "[ExcelGeneralVoucherRevertDataSource] No MasterTestData row found for method='"
                    + methodName + "'. Add a row with testcasename='" + methodName + "' to the "
                    + "MasterTestData sheet.");
        }

        List<MasterTestData> filteredCases = nameMatched.stream()
                .filter(tc -> {
                    boolean found = revertInfoList.stream()
                            .anyMatch(r -> r.getTestCaseId().equalsIgnoreCase(tc.getTestcaseid()));
                    if (!found) {
                        Log.warn("  [SKIPPED] TestCaseID='{}' has no row in RevertVoucherInfo sheet",
                                tc.getTestcaseid());
                    }
                    return found;
                })
                .collect(Collectors.toList());

        if (filteredCases.isEmpty()) {
            throw new SkipException(
                    "[ExcelGeneralVoucherRevertDataSource] No RevertVoucherInfo row matches any "
                    + "MasterTestData TestCaseID for method='" + methodName + "'. Ensure the "
                    + "'RevertVoucherInfo' sheet has a row whose TestCaseID matches.");
        }

        int grandTotal = filteredCases.stream()
                .mapToInt(tc -> tc.getInvocationcount() != null ? tc.getInvocationcount() : 1)
                .sum();
        AtomicInteger globalCounter = new AtomicInteger(0);

        return filteredCases.stream()
                .flatMap(tc -> {
                    String tcId = tc.getTestcaseid();

                    RevertVoucherInfo revertInfo = revertInfoList.stream()
                            .filter(r -> r.getTestCaseId().equalsIgnoreCase(tcId))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    "[ExcelGeneralVoucherRevertDataSource] Missing RevertVoucherInfo for: " + tcId));

                    // No "Release Status" column (plan §16 — removed entirely): RevertGeneralVoucher's
                    // own Step 1 "DB Pre-Check" already reads finvchmain.release_status live and is
                    // the sole driver of every downstream decision/assertion — a declarative sheet
                    // value was never anything more than a soft cross-check, and nothing is left to
                    // cross-check against once it's gone.
                    int totalInvocations = tc.getInvocationcount() != null ? tc.getInvocationcount() : 1;

                    return IntStreamEx.range(1, totalInvocations + 1)
                            .mapToObj(invIdx ->
                                    MergedFinanceGeneralVoucherData.builder()
                                            .masterTestData(tc)
                                            .existingVoucherNo(revertInfo.getExistingVoucherNo())
                                            .operationMode(GeneralVoucherOperationMode.REVERT)
                                            .invocationIndex(globalCounter.incrementAndGet())
                                            .totalInvocations(grandTotal)
                                            .build()
                            );
                })
                .collect(Collectors.toList());
    }
}
