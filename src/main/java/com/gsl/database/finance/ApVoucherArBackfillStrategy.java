package com.gsl.database.finance;

import com.aventstack.extentreports.ExtentTest;
import com.gsl.api.core.ApiSessionBundle;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.api.integration.ApVoucherDataSetup;
import com.gsl.api.model.apvoucher.ApVoucherCreateResponse;
import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.database.BackfillStrategy;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinPostDao;
import com.gsl.database.dao.finance.FinSlDao;
import com.gsl.enums.ApiPlatform;
import com.gsl.model.finance.FinPost;
import com.gsl.model.finance.FinSl;
import com.gsl.reports.BackfillStepLabel;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import org.jdbi.v3.core.Jdbi;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AR Voucher's document-payment shortfall backfill: an AP Voucher (Customer Advance), posted
 * {@code onAccount} against the required sub-ledger classification — the exact mirror of
 * {@link ArVoucherApBackfillStrategy}, which does the same thing in the opposite direction for AP
 * Voucher. It lands in {@code v_pending_adj} with {@code debit_credit='D'} once posted — exactly
 * what {@code DbArVoucherDataSource.loadFreshForScenarioInternal()} is searching for. Replaces the
 * previous {@code PendingArDocumentBackfillStrategy} (a fragile, conceptually-unrelated
 * Item -&gt; GRC -&gt; DC chain — AR Voucher test data has no item concept at all). See
 * {@link com.gsl.api.client.ApVoucherApiClient} for the full HAR-derived flow.
 *
 * <p>TDS applicability is checked from the DB ({@code finsl.tds_app}) for the resolved sub-ledger
 * — not from any API response field, since none of the WebAPI lookups exercised in either HAR
 * capture (TDS / No-TDS) surface a party-level "TDS applicable" flag.
 *
 * <p>v_pending_adj filters on {@code finpost.release_status = 'P'} (same lesson learned building
 * {@link PendingApDocumentBackfillStrategy} and {@link ArVoucherApBackfillStrategy}) — the
 * read-back below confirms posting actually took effect rather than trusting the Save response
 * alone.
 */
public final class ApVoucherArBackfillStrategy implements BackfillStrategy {

    private static final int DEFAULT_QTY = 1;
    private static final int DEFAULT_RATE = 500;

    private final long requiredClsCode;
    private final int ownerSiteCode;
    private final int ouCode;
    private final long empCode;
    private final boolean freshSubLedger;

    /**
     * @param requiredClsCode the {@code admcls.clscode} the AP Voucher's sub-ledger must belong to
     * @param ownerSiteCode   AR Voucher's owner site (drives which site's user/session creates the AP Voucher)
     * @param ouCode          organizational unit code for the created AP Voucher
     * @param empCode         logged-in employee code used to create the AP Voucher
     */
    public ApVoucherArBackfillStrategy(long requiredClsCode, int ownerSiteCode, int ouCode, long empCode) {
        this(requiredClsCode, ownerSiteCode, ouCode, empCode, false);
    }

    /**
     * @param freshSubLedger true when the scenario will claim a whole sub-ledger (FIFO set-off): every record is then raised against a NEW party of the
     *                       class, because documents added to an existing sub-ledger can never satisfy a claim while another scenario holds one of its documents
     */
    public ApVoucherArBackfillStrategy(long requiredClsCode, int ownerSiteCode, int ouCode, long empCode, boolean freshSubLedger) {
        this.requiredClsCode = requiredClsCode;
        this.ownerSiteCode = ownerSiteCode;
        this.ouCode = ouCode;
        this.empCode = empCode;
        this.freshSubLedger = freshSubLedger && requiredClsCode > 0;   // a fresh party needs a class to be created in
    }

    /** Creates {@code neededQuantity} AP Vouchers (Customer Advance, {@code onAccount}) against {@link #requiredClsCode} via {@code platform}; see the class Javadoc for the data shape this produces. */
    @Override
    public void backfill(ApiPlatform platform, int neededQuantity) {
        ApiSessionBundle session = ApiSessionFactory.create(platform);
        boolean dbAvailable = com.gsl.database.DbAvailability.isAvailable();
        Jdbi jdbi = dbAvailable ? DatabaseManager.getJdbi() : null;
        String workDate = LocalDate.now() + "T00:00:00";

        if (requiredClsCode > 0 && !freshSubLedger) {
            StepExecutor.call("Ensure a party of class " + requiredClsCode + " exists", () -> {
                PartyBackfill.ensurePartyOfClass(session, requiredClsCode, ownerSiteCode, ouCode, empCode, workDate);
                return null;
            });
        }

        for (int i = 0; i < neededQuantity; i++) {
            final int idx = i;
            ExtentTest recordNode = StepExecutor.group("Record " + (idx + 1) + "/" + neededQuantity);

            SubLedgerLookupResult[] slHolder = new SubLedgerLookupResult[1];
            if (freshSubLedger) {
                StepExecutor.stepUnder(recordNode, "Create a new party of class " + requiredClsCode + " for this scenario", () ->
                        slHolder[0] = PartyBackfill.createFreshPartyOfClass(session, requiredClsCode, ownerSiteCode, ouCode, empCode, workDate));
            } else {
                StepExecutor.stepUnder(recordNode, "Get Sub Ledger Details for AP Voucher", () ->
                        slHolder[0] = session.getApVoucherApiClient().resolveSubLedger(
                                ownerSiteCode, ouCode, (int) empCode, workDate, requiredClsCode));
            }
            SubLedgerLookupResult subLedger = slHolder[0];

            // TDS applicability check — DB-only, no API response carries this flag (confirmed via
            // both the TDS and No-TDS HAR captures).
            // With db.available=no the sub-ledger lookup's own tdsApp field stands in for finsl.tds_app.
            boolean tdsApplicable;
            if (dbAvailable) {
                FinSl finSl = jdbi.onDemand(FinSlDao.class).findBySlnameAndSlid(subLedger.getSlName(), subLedger.getSlid());
                tdsApplicable = finSl != null && "Y".equalsIgnoreCase(finSl.getTdsApplicable());
            } else {
                tdsApplicable = subLedger.isTdsApplicable();
            }

            ReportLogger.dataBackfill(
                    "[ApVoucherArBackfillStrategy] Creating record {}/{} — requiredClsCode={}, subLedger='{}', " +
                            "ownerSiteCode={}, ouCode={}, empCode={}, tdsApplicable={}, quantity={}, rate={}, documentDate={}",
                    idx + 1, neededQuantity, requiredClsCode, subLedger.getSlName(), ownerSiteCode, ouCode, empCode,
                    tdsApplicable, DEFAULT_QTY, DEFAULT_RATE, workDate);

            ApVoucherCreateResponse[] voucherHolder = new ApVoucherCreateResponse[1];
            StepExecutor.stepUnder(recordNode, BackfillStepLabel.CREATE_AP_VOUCHER.label(), () ->
                    voucherHolder[0] = ApVoucherDataSetup.setupApVoucherForTest(
                            session.getApVoucherApiClient(), subLedger, ownerSiteCode, ouCode, empCode,
                            DEFAULT_QTY, DEFAULT_RATE, tdsApplicable));
            ApVoucherCreateResponse voucher = voucherHolder[0];

            ReportLogger.dataBackfill("[ApVoucherArBackfillStrategy] Backfilled record {}/{} — voucherCode={}, docScheme={}",
                    idx + 1, neededQuantity, voucher.getVoucherCode(), voucher.getDocScheme());

            StepExecutor.stepUnder(recordNode, BackfillStepLabel.VERIFY_POSTED.label(), () ->
                    logReleaseStatus(voucher.getVoucherCode()));
        }
    }

    /**
     * Reads back finpost.release_status for the just-created voucher — v_pending_adj only
     * surfaces release_status='P' rows, so this confirms whether posting actually took effect
     * instead of trusting the Save response's own claim (same pattern as
     * {@link ArVoucherApBackfillStrategy} and {@link PendingApDocumentBackfillStrategy}).
     */
    private static void logReleaseStatus(String voucherCode) {
        if (voucherCode == null) return;
        if (!com.gsl.database.DbAvailability.isAvailable()) {
            ReportLogger.dataBackfill("[ApVoucherArBackfillStrategy] db.available=no - posting read-back skipped (the next API read of the pending documents confirms it).");
            return;
        }
        Jdbi jdbi = DatabaseManager.getJdbi();
        FinPostDao finPostDao = jdbi.onDemand(FinPostDao.class);
        List<FinPost> posts = finPostDao.findByEntCode(voucherCode);
        if (posts.isEmpty()) {
            ReportLogger.dataBackfillWarn(
                    "[ApVoucherArBackfillStrategy] No finpost row found yet for voucherCode={} — release status unknown.",
                    voucherCode);
            return;
        }
        String statuses = posts.stream().map(FinPost::getReleaseStatus).distinct().collect(Collectors.joining(","));
        if (posts.stream().anyMatch(p -> !"P".equals(p.getReleaseStatus()))) {
            ReportLogger.dataBackfillWarn(
                    "[ApVoucherArBackfillStrategy] voucherCode={} still not fully posted — finpost.release_status=[{}] " +
                            "(v_pending_adj only shows 'P'). Document will not be found on retry.",
                    voucherCode, statuses);
        } else {
            ReportLogger.dataBackfill(
                    "[ApVoucherArBackfillStrategy] voucherCode={} confirmed posted — finpost.release_status=[{}]",
                    voucherCode, statuses);
        }
    }

    /** {@inheritDoc} Always {@code "apVoucherBackfill"} — see the inline comment below for why {@code K6BULK} never actually reaches this path. */
    @Override
    public String k6ScriptKey() {
        // No k6 script exists for this path yet — quantities here are always small (well under
        // smallQuantityThreshold), so DataSourceRouter never actually routes this to K6BULK.
        return "apVoucherBackfill";
    }
}
