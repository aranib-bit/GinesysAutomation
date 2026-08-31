package com.gsl.utils.dataprovider.source.finance;

import com.gsl.database.DatabaseManager;
import com.gsl.database.DocumentReservationRegistry;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.enums.Finance.Operations.Common.DocumentMessageBoxMessage;
import com.gsl.enums.PeriodDomain;
import com.gsl.model.finance.FinVchMain;
import com.gsl.pages.enums.Finance.Operations.GeneralVoucher.GeneralVoucherRevertOutcome;
import com.gsl.testdata.dto.GeneralVoucherOperationMode;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.source.TestDataSource;
import com.gsl.utils.dataprovider.source.finance.common.AccountingPeriodResolver;
import org.jdbi.v3.core.Jdbi;
import org.testng.SkipException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * DB-driven test-data source for {@code deleteGeneralVoucher} — mirrors
 * {@link DbArVoucherDeleteDataSource}'s shape (minimal row: {@code existingVoucherNo}/
 * {@code releaseStatus} only, no document body — {@code DeleteGeneralVoucher} does its own live
 * DB reads for whatever it needs at execution time), adapted to also emit the one closed-period
 * negative scenario {@link DbGeneralVoucherRevertDataSource}/{@link DbGeneralVoucherEditDataSource}
 * already establish the pattern for (see the "GV Revert picks documents in a now-closed
 * accounting period, + closed-period negative-test coverage" plan, Part C).
 *
 * <p>Unlike AR's {@link DbArVoucherDeleteDataSource} (which polls a pre-seeded
 * {@code DbIdQueue}), this queries live via ad-hoc JDBI — same reasoning as
 * {@code DbGeneralVoucherRevertDataSource}: a synthetic "DeleteMonthClosed" operation has no
 * real {@code mdmenu}/{@code mdappoperation} row for {@code SuiteDataLoader}'s DB-driven queue
 * registration to discover, and a second consumer draining the SAME queue while searching for a
 * closed-period id would silently starve the positive claim's own pool.
 */
public class DbGeneralVoucherDeleteDataSource implements TestDataSource<MergedFinanceGeneralVoucherData> {

    @Override
    public List<MergedFinanceGeneralVoucherData> load(String testMethodName) {
        Jdbi jdbi = DatabaseManager.getJdbi();
        Set<Long> claimedThisRun = new HashSet<>();

        List<MergedFinanceGeneralVoucherData> results = new ArrayList<>();

        results.add(resolvePositiveCandidate(jdbi, claimedThisRun));
        resolveClosedPeriodNegativeCandidate(jdbi, claimedThisRun).ifPresent(results::add);

        int total = results.size();
        for (int i = 0; i < total; i++) {
            results.get(i).setInvocationIndex(i + 1);
            results.get(i).setTotalInvocations(total);
        }

        Log.info("[DB] GeneralVoucherDelete emitted {} row(s) for '{}'", results.size(), testMethodName);
        return results;
    }

    /**
     * Claims one existing General Voucher — {@code release_status IN ('U','P')} (GV has no
     * {@code 'N'}/never-posted state, unlike AR — confirmed live: every GV row is either Unposted
     * or Posted) — whose own accounting year/financial month is still OPEN, mirroring
     * {@code DbGeneralVoucherRevertDataSource#claimOnePostedVoucher}'s guard exactly (a document
     * in a closed period can't even be Unposted, let alone deleted).
     */
    private static MergedFinanceGeneralVoucherData resolvePositiveCandidate(Jdbi jdbi, Set<Long> claimedThisRun) {
        Long vchcode = claimOneCandidate(jdbi, claimedThisRun, false)
                .orElseThrow(() -> new SkipException(
                        "Skipped due to lack of data: no unclaimed General Voucher "
                        + "(release_status IN ('U','P')) found in an open accounting period for Delete."));

        FinVchMain header = jdbi.onDemand(FinVchMainDao.class).findByVoucherCode(vchcode)
                .orElseThrow(() -> new IllegalStateException(
                        "Claimed vchcode=" + vchcode + " vanished before its header could be re-read."));

        Log.info("[DB-Delete] Claimed General Voucher for Delete: vchcode={}, scheme_docno='{}', "
                + "release_status='{}'", vchcode, header.getSchemeDocno(), header.getReleaseStatus());

        return MergedFinanceGeneralVoucherData.builder()
                .scenarioTypeLabel("GV_DELETE_POSITIVE")
                .existingVoucherNo(header.getSchemeDocno())
                .releaseStatus(header.getReleaseStatus())
                .reservedDocumentKey(String.valueOf(vchcode))
                .operationMode(GeneralVoucherOperationMode.DELETE)
                .expenseIncomeDetailList(List.of())
                .siteAllocationList(List.of())
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    /**
     * Builds the one Delete negative scenario: claims an existing General Voucher whose own
     * accounting year or financial month is already closed and tags the resulting row with
     * {@code expectedRevertOutcome=CANNOT_REVERT_MONTH_CLOSED} — exactly
     * {@link DbGeneralVoucherEditDataSource}'s own closed-period negative row (Part B.2), reused
     * here since {@code DeleteGeneralVoucher}'s own "Unpost if Posted" prep step drives the same
     * {@code revertSelectedDocument()}/{@code confirmRevert()} machinery GV Edit/Revert already
     * use — this scenario never reaches the actual Delete click. Returns empty (logged, not
     * thrown) if no closed-period candidate exists — no sensible backfill for "manufacture a
     * document in a closed accounting period".
     */
    private static Optional<MergedFinanceGeneralVoucherData> resolveClosedPeriodNegativeCandidate(
            Jdbi jdbi, Set<Long> claimedThisRun) {

        Optional<Long> claimed = claimOneCandidate(jdbi, claimedThisRun, true);
        if (claimed.isEmpty()) {
            Log.warn("[DB] GeneralVoucherDelete: no closed-period General Voucher available for the "
                    + "CANNOT_REVERT_MONTH_CLOSED negative scenario — skipping it (nothing to backfill against).");
            return Optional.empty();
        }

        long vchcode = claimed.get();
        FinVchMain header = jdbi.onDemand(FinVchMainDao.class).findByVoucherCode(vchcode)
                .orElseThrow(() -> new IllegalStateException(
                        "Claimed closed-period vchcode=" + vchcode + " vanished before its header could be re-read."));

        Log.info("[DB-Delete] Claimed closed-period General Voucher for negative scenario: vchcode={}, "
                + "scheme_docno='{}', release_status='{}' — expecting Delete's \"Unpost if Posted\" prep step "
                + "to be REJECTED with: \"{}\"",
                vchcode, header.getSchemeDocno(), header.getReleaseStatus(),
                DocumentMessageBoxMessage.REVERT_MONTH_ALREADY_CLOSED.getText());

        return Optional.of(MergedFinanceGeneralVoucherData.builder()
                .scenarioTypeLabel("GV_DELETE_NEGATIVE_MONTH_CLOSED")
                .existingVoucherNo(header.getSchemeDocno())
                .releaseStatus(header.getReleaseStatus())
                .reservedDocumentKey(String.valueOf(vchcode))
                .operationMode(GeneralVoucherOperationMode.DELETE)
                .expectedRevertOutcome(GeneralVoucherRevertOutcome.CANNOT_REVERT_MONTH_CLOSED)
                .expenseIncomeDetailList(List.of())
                .siteAllocationList(List.of())
                .invocationIndex(1)
                .totalInvocations(1)
                .build());
    }

    /**
     * Claims one {@code finvchmain} row (GV, any release_status) whose own accounting year/
     * financial month is OPEN ({@code requireClosedPeriod=false}) or CLOSED
     * ({@code requireClosedPeriod=true}) — same dual-purpose shape
     * {@code DbGeneralVoucherRevertDataSource}'s {@code claimOnePostedVoucher}/
     * {@code claimOneClosedPeriodVoucher} pair uses, collapsed into one method here since neither
     * claim needs a GL-account-type match (Delete doesn't care which ledger type the voucher
     * uses).
     */
    private static Optional<Long> claimOneCandidate(Jdbi jdbi, Set<Long> claimedThisRun, boolean requireClosedPeriod) {
        String sql =
            "SELECT vchcode FROM finvchmain " +
            "WHERE vchtype = 'G' AND release_status IN ('U', 'P') " +
            "ORDER BY vchcode";

        List<Long> candidates = jdbi.withHandle(handle -> handle.createQuery(sql).mapTo(Long.class).list());
        FinVchMainDao headerDao = jdbi.onDemand(FinVchMainDao.class);

        for (Long vchcode : candidates) {
            if (claimedThisRun.contains(vchcode)) continue;

            FinVchMain header = headerDao.findByVoucherCode(vchcode).orElse(null);
            if (header == null) continue;

            boolean periodOpen = AccountingPeriodResolver.isEditable(jdbi, header, PeriodDomain.FINANCE);
            if (requireClosedPeriod ? periodOpen : !periodOpen) continue;

            if (!DocumentReservationRegistry.tryReserve(String.valueOf(vchcode))) continue;

            claimedThisRun.add(vchcode);
            return Optional.of(vchcode);
        }
        return Optional.empty();
    }
}
