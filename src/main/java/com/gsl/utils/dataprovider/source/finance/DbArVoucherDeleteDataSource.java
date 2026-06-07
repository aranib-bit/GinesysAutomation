package com.gsl.utils.dataprovider.source.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.database.MenuOperationKey;
import com.gsl.database.DbIdQueue;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.model.finance.FinVchMain;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.source.TestDataSource;
import org.testng.SkipException;

import java.util.List;

/**
 * Loads AR Voucher test data for <b>Delete</b> operations by fetching one
 * existing voucher from {@code finvchmain}.
 *
 * <h3>Data flow</h3>
 * <ol>
 *   <li>Polls one {@code vchcode} from {@link DbIdQueue} key
 *       {@code "FinanceARVoucher.Delete"} — guaranteed unique per thread.</li>
 *   <li>Fetches the voucher header ({@code finvchmain}).</li>
 *   <li>Builds a minimal {@link MergedFinanceArVoucherData} with:
 *       <ul>
 *         <li>{@code existingVoucherNo} — {@code scheme_docno} to locate the
 *             voucher in the UI list page.</li>
 *         <li>{@code releaseStatus} — carried from {@code finvchmain.release_status}
 *             so the test class knows whether unpost + fintag-check steps are needed
 *             before deletion.</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h3>P-status handling</h3>
 * Posted vouchers ({@code release_status='P'}) are included in the eligible pool.
 * The test class is responsible for:
 * <ol>
 *   <li>Clicking Unpost on the voucher.</li>
 *   <li>Querying {@code fintag} — if tagged, navigating to Document Adjustment
 *       to remove the tag before proceeding with deletion.</li>
 * </ol>
 */
public class DbArVoucherDeleteDataSource implements TestDataSource<MergedFinanceArVoucherData> {

    @Override
    public List<MergedFinanceArVoucherData> load(String testMethodName) {
        Long vchcode = DbIdQueue.getNextId(MenuOperationKey.build("FinanceARVoucher", "Delete"));
        if (vchcode == null) {
            throw new SkipException(
                    "[DbArVoucherDeleteDataSource] No AR Voucher available for Delete. " +
                    "Ensure finvchmain has rows WHERE vchtype='R' AND release_status IN ('N','U','P').");
        }

        Log.info("[DB-Delete] Fetching AR Voucher for Delete: vchcode={}", vchcode);

        FinVchMain header = DatabaseManager.getJdbi().onDemand(FinVchMainDao.class)
                .findByVoucherCode(vchcode)
                .orElseThrow(() -> new IllegalStateException(
                        "[DbArVoucherDeleteDataSource] finvchmain row not found for vchcode=" + vchcode));

        Log.info("[DB-Delete] vchcode={}, scheme_docno='{}', release_status='{}'",
                vchcode, header.getSchemeDocno(), header.getReleaseStatus());

        MasterTestData master = new MasterTestData(
                testMethodName, null, null, null,
                "FIN-AR-DELETE-" + vchcode, 1);

        MergedFinanceArVoucherData deleteData = MergedFinanceArVoucherData.builder()
                .existingVoucherNo(header.getSchemeDocno())
                .releaseStatus(header.getReleaseStatus())
                .masterTestData(master)
                .documentInformation(null)
                .subLedgerInformationList(List.of())
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(List.of())
                .paymentProvided(false)
                .needsPaymentInfoWarning(false)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();

        return List.of(deleteData);
    }
}
