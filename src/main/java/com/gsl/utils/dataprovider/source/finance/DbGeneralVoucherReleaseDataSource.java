package com.gsl.utils.dataprovider.source.finance;

import com.gsl.database.DatabaseManager;
import com.gsl.database.DocumentReservationRegistry;
import com.gsl.enums.PeriodDomain;
import com.gsl.model.finance.FinVchMain;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.source.finance.common.AccountingPeriodCache;
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
 *
 * <p><b>DB-validation design, ready once the UI flow above is confirmed and unblocked:</b> mirror
 * {@code RevertGeneralVoucher}'s DB-validation shape exactly, inverted — a
 * {@code GeneralVoucherReleaseChangeSets.release(context, beforeSnapshot, expectedNewStatus)}
 * builder with the same shape as {@code com.gsl.validation.snapshot.finance
 * .GeneralVoucherRevertChangeSets#revert} (transitioning {@code release_status} {@code 'U'} →
 * {@code 'P'} instead of {@code 'P'} → {@code 'U'}; {@code finvchdet}/{@code fincosttag} need no
 * explicit overrides either, since the persistence map's own table-wide {@code MUST_NOT_CHANGE}
 * default already asserts they stay untouched), run through the identical
 * {@code VoucherDocumentContextResolver}/{@code SnapshotService}/{@code VoucherSnapshotValidator}
 * pipeline. A future {@code ReleaseGeneralVoucher} test class would follow
 * {@code RevertGeneralVoucher}'s exact {@code StepExecutor.step(...)} skeleton, swapping only the
 * click/confirm calls and the expected status transition — near-zero design lift once
 * {@code VoucherMainGridBase#clickRelease()}/{@code #confirmRelease()} are confirmed live.
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
        // Finding A (Round 12): SELECT * (not just vchcode) + .mapToBean — removes the scan loop's
        // own per-candidate SELECT * FROM finvchmain WHERE vchcode = ? round trip.
        String sql =
            "SELECT * FROM finvchmain " +
            "WHERE vchtype = 'G' AND release_status = 'U' " +
            "ORDER BY vchcode";

        List<FinVchMain> candidates = jdbi.withHandle(handle -> handle.createQuery(sql).mapToBean(FinVchMain.class).list());
        // Memoizes the OU/year/month lookups isEditable below would otherwise re-query per
        // rejected candidate — see AccountingPeriodCache's own Javadoc.
        AccountingPeriodCache periodCache = new AccountingPeriodCache();

        for (FinVchMain header : candidates) {
            long vchcode = header.getVchcode();
            if (claimedThisRun.contains(vchcode)) continue;
            if (!periodCache.isEditable(jdbi, header, PeriodDomain.FINANCE)) continue;
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
