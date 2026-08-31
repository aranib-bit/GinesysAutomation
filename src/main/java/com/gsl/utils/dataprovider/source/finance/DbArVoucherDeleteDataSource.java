package com.gsl.utils.dataprovider.source.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.database.DocumentReservationRegistry;
import com.gsl.database.MenuOperationKey;
import com.gsl.database.DbIdQueue;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.enums.PeriodDomain;
import com.gsl.model.finance.FinVchMain;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.source.TestDataSource;
import com.gsl.utils.dataprovider.source.finance.common.AccountingPeriodResolver;
import org.jdbi.v3.core.Jdbi;
import org.testng.SkipException;

import java.util.List;
import java.util.Optional;

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

    /**
     * Claims one AR Voucher whose own accounting year OR financial month is already CLOSED —
     * ready-to-use infrastructure for a "cannot Unpost/Delete a closed-period document" negative
     * scenario (see the "GV Revert picks documents in a now-closed accounting period,
     * + closed-period negative-test coverage" plan, Part B.3), mirroring
     * {@code DbGeneralVoucherRevertDataSource#claimOneClosedPeriodVoucher}'s exact shape:
     * live ad-hoc query (bypassing {@link DbIdQueue} deliberately — a second consumer draining
     * the SAME "FinanceARVoucher.Delete" queue while searching for a closed-period id would
     * silently starve {@link #load}'s own positive claim of eligible candidates; this queries
     * live instead, same as {@code DbGeneralVoucherRevertDataSource}'s own negative claim) plus
     * {@link DocumentReservationRegistry} reservation.
     *
     * <p><b>Deliberately NOT wired into {@link #load}</b> — unlike GV Revert's own closed-period
     * negative row, there is no way to responsibly assert the outcome yet: {@code DeleteArVoucher}
     * drives {@code VoucherMainGridBase.clickUnpost()}/{@code clickDeleteForVoucher()}/
     * {@code confirmDelete()}, all still {@code //TODO-} placeholder locators unconfirmed against
     * the live DOM (this class's own Javadoc already flags the positive path as not running
     * end-to-end today), AND none of those methods read/return a dialog outcome the way
     * {@code GeneralVoucherMainGrid#confirmRevert()} does — there is no
     * {@code GeneralVoucherRevertOutcome} equivalent for AR to assert against. Wiring this in
     * requires both gaps closed first (confirm the real locators, then add an outcome-reading
     * {@code confirmUnpost()}-style method) — out of scope for a data-source-level fix; kept here,
     * documented and ready, rather than either skipped silently or fabricated as a false-passing
     * assertion against page-object behavior that doesn't exist yet.
     */
    public static Optional<MergedFinanceArVoucherData> claimOneClosedPeriodCandidate(String testMethodName) {
        Jdbi jdbi = DatabaseManager.getJdbi();
        String sql =
            "SELECT vchcode FROM finvchmain " +
            "WHERE vchtype = 'R' AND release_status IN ('N', 'U', 'P') " +
            "ORDER BY vchcode";

        List<Long> candidates = jdbi.withHandle(handle -> handle.createQuery(sql).mapTo(Long.class).list());
        FinVchMainDao headerDao = jdbi.onDemand(FinVchMainDao.class);

        for (Long vchcode : candidates) {
            FinVchMain header = headerDao.findByVoucherCode(vchcode).orElse(null);
            if (header == null) continue;
            // Inverse of the usual guard: we WANT a closed year/month here.
            if (AccountingPeriodResolver.isEditable(jdbi, header, PeriodDomain.FINANCE)) continue;
            if (!DocumentReservationRegistry.tryReserve(String.valueOf(vchcode))) continue;

            Log.info("[DB-Delete] Claimed closed-period AR Voucher for negative scenario: vchcode={}, "
                    + "scheme_docno='{}', release_status='{}'",
                    vchcode, header.getSchemeDocno(), header.getReleaseStatus());

            MasterTestData master = new MasterTestData(
                    testMethodName, null, null, null,
                    "FIN-AR-DELETE-MONTH-CLOSED-" + vchcode, 1);

            return Optional.of(MergedFinanceArVoucherData.builder()
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
                    .build());
        }

        Log.warn("[DB-Delete] No closed-period AR Voucher available for a negative scenario claim.");
        return Optional.empty();
    }
}
