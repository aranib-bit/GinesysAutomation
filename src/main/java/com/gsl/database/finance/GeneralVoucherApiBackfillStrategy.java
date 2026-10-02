package com.gsl.database.finance;

import com.gsl.api.client.GeneralVoucherApiClient;
import com.gsl.api.core.ApiSessionBundle;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.api.model.generalvoucher.GeneralVoucherCreateResponse;
import com.gsl.database.BackfillStrategy;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.enums.ApiPlatform;
import com.gsl.enums.GlAccountType;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import org.jdbi.v3.core.Jdbi;

/**
 * {@code DbGeneralVoucherRevertDataSource}'s shortfall backfill: creates one Posted General
 * Voucher of a required {@link GlAccountType} via {@link GeneralVoucherApiClient} when the DB has
 * no unclaimed {@code release_status='P'} row of that type left to revert. Mirrors
 * {@link LedgerMasterBackfillStrategy}'s shape exactly (session creation → loop → verify via
 * re-read; {@link #backfill} stays {@code void} — {@link com.gsl.database.DataAvailabilityGuard}'s
 * retry picks the freshly-created row up through the caller's own claim query, not through this
 * method's return value).
 *
 * <p><b>Currently non-functional</b> — {@link GeneralVoucherApiClient#createGeneralVoucherWithDefaults}
 * is a structural stub pending a real HAR capture (see that class's Javadoc), so calling this
 * strategy today surfaces a clearly-labeled {@link UnsupportedOperationException} instead of
 * silently fabricating incorrect data.
 */
public final class GeneralVoucherApiBackfillStrategy implements BackfillStrategy {

    private final GlAccountType accountType;

    /** @param accountType the required {@link GlAccountType} for the Posted General Voucher to create */
    public GeneralVoucherApiBackfillStrategy(GlAccountType accountType) {
        this.accountType = accountType;
    }

    /**
     * Creates {@code neededQuantity} Posted General Vouchers of {@link #accountType} via {@code platform}.
     *
     * @throws UnsupportedOperationException always — see the class Javadoc; {@link GeneralVoucherApiClient
     *         #createGeneralVoucherWithDefaults} is a structural stub pending a real HAR capture.
     */
    @Override
    public void backfill(ApiPlatform platform, int neededQuantity) {
        ApiSessionBundle session = ApiSessionFactory.create(platform);
        try {
            for (int i = 0; i < neededQuantity; i++) {
                ReportLogger.dataBackfill(
                        "[GeneralVoucherApiBackfillStrategy] Creating record {}/{} — accountType={}",
                        i + 1, neededQuantity, accountType);

                // ownerSiteCode/ouCode/empCode are left as placeholders (0) — createGeneralVoucherWithDefaults
                // throws UnsupportedOperationException before using them; see its Javadoc.
                GeneralVoucherCreateResponse response = StepExecutor.call("Create General Voucher via API",
                        () -> session.getGeneralVoucherApiClient().createGeneralVoucherWithDefaults(accountType, 0, 0, 0));

                StepExecutor.call("Verify General Voucher Landed Posted in DB", () -> {
                    verifyLandedPosted(response);
                    return null;
                });
            }
        } finally {
            session.logout();
        }
    }

    /**
     * Reads back the just-created row via {@link FinVchMainDao#findByVoucherCode} and confirms it
     * actually landed {@code release_status='P'} — mirrors
     * {@code ArVoucherApBackfillStrategy}'s own post-create {@code release_status} re-read.
     */
    private void verifyLandedPosted(GeneralVoucherCreateResponse response) {
        if (response == null || response.getVoucherCode() == null) return;
        long vchcode = Long.parseLong(response.getVoucherCode());
        Jdbi jdbi = DatabaseManager.getJdbi();
        FinVchMain created = jdbi.onDemand(FinVchMainDao.class).findByVoucherCode(vchcode).orElse(null);
        if (created == null) {
            ReportLogger.dataBackfillWarn(
                    "[GeneralVoucherApiBackfillStrategy] No finvchmain row found yet for vchcode={} — the create "
                            + "call may not have actually persisted.", vchcode);
            return;
        }
        if (!"P".equals(created.getReleaseStatus())) {
            ReportLogger.dataBackfillWarn(
                    "[GeneralVoucherApiBackfillStrategy] vchcode={} landed with release_status='{}', not 'P' — "
                            + "the retrying claim query (release_status='P') will not find it.",
                    vchcode, created.getReleaseStatus());
            return;
        }
        ReportLogger.dataBackfill(
                "[GeneralVoucherApiBackfillStrategy] Confirmed Posted finvchmain row — vchcode={}, scheme_docno='{}'",
                vchcode, created.getSchemeDocno());
    }

    /** {@inheritDoc} Always {@code "generalVoucherRevertBackfill"} — see the inline comment below for why {@code K6BULK} never actually reaches this path. */
    @Override
    public String k6ScriptKey() {
        // No k6 script exists for this path — quantities here are always 1 (one voucher per missing
        // account type), so DataSourceRouter never actually routes this to K6BULK.
        return "generalVoucherRevertBackfill";
    }
}
