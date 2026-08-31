package com.gsl.utils.dataprovider.source.finance;

import com.gsl.database.DatabaseManager;
import com.gsl.database.DocumentReservationRegistry;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.enums.PeriodDomain;
import com.gsl.model.finance.FinVchMain;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.source.finance.common.AccountingPeriodResolver;
import org.jdbi.v3.core.Jdbi;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Candidate-claim infrastructure for a future {@code releaseGeneralVoucher} test — see the "GV
 * Revert picks documents in a now-closed accounting period, + closed-period negative-test
 * coverage" plan, Part D. <b>Deliberately scaffold-only</b>, per explicit user decision: no
 * {@code TestDataSource} implementation, no wiring into {@code MergedFinGeneralVoucherDataSupplier},
 * and no test class consumes this yet — building those would require asserting what "Release"
 * actually does to {@code release_status} and what dialog(s)/text follow the click, none of
 * which is confirmed anywhere in this codebase (see
 * {@code VoucherMainGridBase#clickRelease()}/{@code #confirmRelease()}'s own Javadoc for the
 * same caveat on the UI side).
 *
 * <p>What IS safe to build now: claiming an existing Unposted ({@code release_status='U'})
 * General Voucher — that much doesn't depend on Release's own unconfirmed behavior, only on
 * {@code finvchmain}'s already-well-understood shape. A live count check
 * (via the {@code postgres} MCP connector) found 452 Unposted GVs in the current open year
 * ({@code ycode=76}, {@code admou_code=1}) alone — plenty of positive candidates once a real test
 * class is built. A closed-period negative claim would follow the exact same
 * {@code requireClosedPeriod}-inverted-guard pattern {@link DbGeneralVoucherDeleteDataSource}/
 * {@link DbGeneralVoucherRevertDataSource} already establish — not built here since there is no
 * consumer yet and no confirmed rejection outcome to assert against.
 */
public final class DbGeneralVoucherReleaseDataSource {

    private DbGeneralVoucherReleaseDataSource() {}

    /**
     * Claims one Unposted ({@code release_status='U'}) General Voucher whose own accounting
     * year/financial month is still open, mirroring {@link DbGeneralVoucherDeleteDataSource}'s
     * candidate-claim shape. Returns a minimal {@link MergedFinanceGeneralVoucherData} row
     * ({@code existingVoucherNo}/{@code releaseStatus} only) — same shape every other minimal
     * Revert/Delete row here uses, ready for a future test class to consume once the UI side is
     * confirmed.
     */
    public static Optional<MergedFinanceGeneralVoucherData> claimOneUnpostedCandidate(Set<Long> claimedThisRun) {
        Jdbi jdbi = DatabaseManager.getJdbi();
        String sql =
            "SELECT vchcode FROM finvchmain " +
            "WHERE vchtype = 'G' AND release_status = 'U' " +
            "ORDER BY vchcode";

        List<Long> candidates = jdbi.withHandle(handle -> handle.createQuery(sql).mapTo(Long.class).list());
        FinVchMainDao headerDao = jdbi.onDemand(FinVchMainDao.class);

        for (Long vchcode : candidates) {
            if (claimedThisRun.contains(vchcode)) continue;

            FinVchMain header = headerDao.findByVoucherCode(vchcode).orElse(null);
            if (header == null) continue;
            if (!AccountingPeriodResolver.isEditable(jdbi, header, PeriodDomain.FINANCE)) continue;
            if (!DocumentReservationRegistry.tryReserve(String.valueOf(vchcode))) continue;

            claimedThisRun.add(vchcode);
            Log.info("[DB-Release] Claimed Unposted General Voucher for a future Release test: vchcode={}, "
                    + "scheme_docno='{}'", vchcode, header.getSchemeDocno());

            return Optional.of(MergedFinanceGeneralVoucherData.builder()
                    .scenarioTypeLabel("GV_RELEASE_POSITIVE_SCAFFOLD")
                    .existingVoucherNo(header.getSchemeDocno())
                    .releaseStatus(header.getReleaseStatus())
                    .reservedDocumentKey(String.valueOf(vchcode))
                    .expenseIncomeDetailList(List.of())
                    .siteAllocationList(List.of())
                    .invocationIndex(1)
                    .totalInvocations(1)
                    .build());
        }
        return Optional.empty();
    }
}
