package com.gsl.database.finance;

import com.gsl.api.core.ApiSessionBundle;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.api.integration.ApVoucherDataSetup;
import com.gsl.api.model.apvoucher.ApVoucherCreateResponse;
import com.gsl.api.model.arvoucher.SubLedgerLookupResult;
import com.gsl.database.BackfillStrategy;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinPostDao;
import com.gsl.database.dao.finance.FinSlDao;
import com.gsl.database.dao.finance.FinTagDao;
import com.gsl.enums.ApiPlatform;
import com.gsl.model.finance.FinPost;
import com.gsl.model.finance.FinSl;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import org.jdbi.v3.core.Jdbi;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code DbApVoucherRevertDataSource}/{@code DbApVoucherDeleteDataSource}/
 * {@code DbApVoucherEditDataSource}'s {@code UNTAGGED}-cell shortfall backfill: creates one
 * Posted AP Voucher via {@link com.gsl.api.client.ApVoucherApiClient#createApVoucherWithDefaults}
 * — the exact same on-account Customer Advance flow {@link ApVoucherArBackfillStrategy} already
 * uses to backfill AR Voucher's own document-payment shortfall (see that class's Javadoc for the
 * full HAR-derived flow this reuses) — when none is free to claim.
 *
 * <p>A freshly-created AP Voucher is guaranteed untagged (nothing has ever adjusted a document
 * against it yet), so this strategy never needs to check {@code fintag} itself — the read-back
 * only confirms it actually landed Posted, mirroring every other backfill strategy's own
 * post-create verification convention.
 */
public final class ApVoucherUntaggedBackfillStrategy implements BackfillStrategy {

    private static final int DEFAULT_QTY = 1;
    private static final int DEFAULT_RATE = 500;

    private final int ownerSiteCode;
    private final int ouCode;
    private final long empCode;

    /**
     * @param ownerSiteCode the site whose user/session creates the backfilled AP Voucher
     * @param ouCode        organizational unit code for the created AP Voucher
     * @param empCode       logged-in employee code used to create the AP Voucher
     */
    public ApVoucherUntaggedBackfillStrategy(int ownerSiteCode, int ouCode, long empCode) {
        this.ownerSiteCode = ownerSiteCode;
        this.ouCode = ouCode;
        this.empCode = empCode;
    }

    @Override
    public void backfill(ApiPlatform platform, int neededQuantity) {
        ApiSessionBundle session = ApiSessionFactory.create(platform);
        Jdbi jdbi = DatabaseManager.getJdbi();
        String workDate = LocalDate.now() + "T00:00:00";

        try {
            for (int i = 0; i < neededQuantity; i++) {
                final int idx = i;
                SubLedgerLookupResult subLedger = StepExecutor.call("Get Sub Ledger Details for AP Voucher (Revert/Delete/Edit backfill)",
                        () -> session.getApVoucherApiClient().resolveSubLedger(ownerSiteCode, ouCode, (int) empCode, workDate, 0));

                FinSl finSl = jdbi.onDemand(FinSlDao.class).findBySlnameAndSlid(subLedger.getSlName(), subLedger.getSlid());
                boolean tdsApplicable = finSl != null && "Y".equalsIgnoreCase(finSl.getTdsApplicable());

                ReportLogger.dataBackfill(
                        "[ApVoucherUntaggedBackfillStrategy] Creating record {}/{} — subLedger='{}', ownerSiteCode={}, "
                                + "ouCode={}, empCode={}, tdsApplicable={}",
                        idx + 1, neededQuantity, subLedger.getSlName(), ownerSiteCode, ouCode, empCode, tdsApplicable);

                ApVoucherCreateResponse response = StepExecutor.call("Create AP Voucher via API (Revert/Delete/Edit backfill)",
                        () -> ApVoucherDataSetup.setupApVoucherForTest(session.getApVoucherApiClient(),
                                subLedger, ownerSiteCode, ouCode, empCode, DEFAULT_QTY, DEFAULT_RATE, tdsApplicable));

                ReportLogger.dataBackfill("[ApVoucherUntaggedBackfillStrategy] Backfilled record {}/{} — voucherCode={}, docScheme={}",
                        idx + 1, neededQuantity, response.getVoucherCode(), response.getDocScheme());

                StepExecutor.call("Verify AP Voucher Landed Posted and Untagged in DB", () -> {
                    verifyLandedPostedAndUntagged(jdbi, response);
                    return null;
                });
            }
        } finally {
            session.logout();
        }
    }

    /** Reads back {@code finpost.release_status} and {@code fintag} for the just-created voucher —
     *  confirms it actually posted (same read-back convention every other backfill strategy in this
     *  codebase uses) and, defensively, that it is genuinely untagged (a freshly-created document
     *  should never already carry a fintag row, but this is logged rather than assumed). */
    private static void verifyLandedPostedAndUntagged(Jdbi jdbi, ApVoucherCreateResponse response) {
        if (response == null || response.getVoucherCode() == null) return;
        long vchcode = Long.parseLong(response.getVoucherCode());

        List<FinPost> posts = jdbi.onDemand(FinPostDao.class).findByEntCode(response.getVoucherCode());
        if (posts.isEmpty()) {
            ReportLogger.dataBackfillWarn(
                    "[ApVoucherUntaggedBackfillStrategy] No finpost row found yet for voucherCode={} — release status unknown.",
                    response.getVoucherCode());
        } else {
            String statuses = posts.stream().map(FinPost::getReleaseStatus).distinct().collect(Collectors.joining(","));
            if (posts.stream().anyMatch(p -> !"P".equals(p.getReleaseStatus()))) {
                ReportLogger.dataBackfillWarn(
                        "[ApVoucherUntaggedBackfillStrategy] voucherCode={} still not fully posted — finpost.release_status=[{}]",
                        response.getVoucherCode(), statuses);
            } else {
                ReportLogger.dataBackfill(
                        "[ApVoucherUntaggedBackfillStrategy] voucherCode={} confirmed posted — finpost.release_status=[{}]",
                        response.getVoucherCode(), statuses);
            }
        }

        FinTagDao finTagDao = jdbi.onDemand(FinTagDao.class);
        boolean tagged = !finTagDao.findByEntCode1(vchcode).isEmpty() || !finTagDao.findByEntCode2(vchcode).isEmpty();
        if (tagged) {
            ReportLogger.dataBackfillWarn(
                    "[ApVoucherUntaggedBackfillStrategy] voucherCode={} unexpectedly has a live fintag row right "
                            + "after creation — this should never happen for a brand-new document.",
                    response.getVoucherCode());
        }
    }

    /** {@inheritDoc} Always {@code "apVoucherRevertEditBackfill"} — quantities here are always
     *  small (well under {@code smallQuantityThreshold}), so {@code DataSourceRouter} never
     *  actually routes this to K6BULK; no k6 script exists for this path. */
    @Override
    public String k6ScriptKey() {
        return "apVoucherRevertEditBackfill";
    }
}
