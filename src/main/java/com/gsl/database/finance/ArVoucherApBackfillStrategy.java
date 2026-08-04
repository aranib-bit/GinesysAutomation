package com.gsl.database.finance;

import com.aventstack.extentreports.ExtentTest;
import com.gsl.api.core.ApiSessionBundle;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.api.integration.ArVoucherDataSetup;
import com.gsl.api.model.arvoucher.ArVoucherCreateResponse;
import com.gsl.database.BackfillStrategy;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinPostDao;
import com.gsl.enums.ApiPlatform;
import com.gsl.model.finance.FinPost;
import com.gsl.reports.ApiCallReportLogger;
import com.gsl.reports.BackfillStepLabel;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import org.jdbi.v3.core.Jdbi;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AP Voucher's document-payment shortfall backfill for any {@code clsCode} whose {@code clstype}
 * isn't Supplier: an AR Voucher against the required classification, matching the fact that AR
 * Voucher (unlike Service Invoice Adhoc, which is Purchase/Supplier-only) can be raised against a
 * sub-ledger of any classification. It lands in {@code v_pending_adj} with {@code debit_credit='C'}
 * once posted — exactly what {@code DbApVoucherDataSource.selectApDocumentsAtomic()} is searching
 * for. See {@link com.gsl.api.client.ArVoucherApiClient} for the full HAR-derived flow.
 *
 * <p>v_pending_adj filters on {@code finpost.release_status = 'P'} (same lesson learned building
 * {@link PendingApDocumentBackfillStrategy}) — the read-back below confirms posting actually took
 * effect rather than trusting the Save response alone.
 */
public final class ArVoucherApBackfillStrategy implements BackfillStrategy {

    private static final int DEFAULT_QTY = 1;
    private static final int DEFAULT_RATE = 500;

    private final long requiredClsCode;
    private final int ownerSiteCode;
    private final int ouCode;
    private final long empCode;

    public ArVoucherApBackfillStrategy(long requiredClsCode, int ownerSiteCode, int ouCode, long empCode) {
        this.requiredClsCode = requiredClsCode;
        this.ownerSiteCode = ownerSiteCode;
        this.ouCode = ouCode;
        this.empCode = empCode;
    }

    @Override
    public void backfill(ApiPlatform platform, int neededQuantity) {
        ApiSessionBundle session = ApiSessionFactory.create(platform);
        ApiCallReportLogger.setSection("PreRequisites — Data Creation");
        try {
            for (int i = 0; i < neededQuantity; i++) {
                final int idx = i;
                ExtentTest recordNode = StepExecutor.group("Record " + (idx + 1) + "/" + neededQuantity);
                String documentDate = LocalDate.now().toString();
                ReportLogger.dataBackfill(
                        "[ArVoucherApBackfillStrategy] Creating record {}/{} — requiredClsCode={}, ownerSiteCode={}, " +
                                "ouCode={}, empCode={}, quantity={}, rate={}, documentDate={}",
                        idx + 1, neededQuantity, requiredClsCode, ownerSiteCode, ouCode, empCode,
                        DEFAULT_QTY, DEFAULT_RATE, documentDate);

                ArVoucherCreateResponse[] voucherHolder = new ArVoucherCreateResponse[1];
                StepExecutor.stepUnder(recordNode, BackfillStepLabel.CREATE_AR_VOUCHER.label(), () ->
                        voucherHolder[0] = ArVoucherDataSetup.setupArVoucherForTest(
                                session.getArVoucherApiClient(), requiredClsCode, ownerSiteCode, ouCode, empCode,
                                DEFAULT_QTY, DEFAULT_RATE));
                ArVoucherCreateResponse voucher = voucherHolder[0];

                ReportLogger.dataBackfill("[ArVoucherApBackfillStrategy] Backfilled record {}/{} — voucherCode={}, docScheme={}",
                        idx + 1, neededQuantity, voucher.getVoucherCode(), voucher.getDocScheme());

                StepExecutor.stepUnder(recordNode, BackfillStepLabel.VERIFY_POSTED.label(), () ->
                        logReleaseStatus(voucher.getVoucherCode()));
            }
        } finally {
            ApiCallReportLogger.clearSection();
        }
    }

    /**
     * Reads back finpost.release_status for the just-created voucher — v_pending_adj only
     * surfaces release_status='P' rows, so this confirms whether posting actually happened
     * instead of trusting the Save response's own claim (same pattern as
     * {@link PendingApDocumentBackfillStrategy}).
     */
    private static void logReleaseStatus(String voucherCode) {
        if (voucherCode == null) return;
        Jdbi jdbi = DatabaseManager.getJdbi();
        FinPostDao finPostDao = jdbi.onDemand(FinPostDao.class);
        List<FinPost> posts = finPostDao.findByEntCode(voucherCode);
        if (posts.isEmpty()) {
            ReportLogger.dataBackfillWarn(
                    "[ArVoucherApBackfillStrategy] No finpost row found yet for voucherCode={} — release status unknown.",
                    voucherCode);
            return;
        }
        String statuses = posts.stream().map(FinPost::getReleaseStatus).distinct().collect(Collectors.joining(","));
        if (posts.stream().anyMatch(p -> !"P".equals(p.getReleaseStatus()))) {
            ReportLogger.dataBackfillWarn(
                    "[ArVoucherApBackfillStrategy] voucherCode={} still not fully posted — finpost.release_status=[{}] " +
                            "(v_pending_adj only shows 'P'). Document will not be found on retry.",
                    voucherCode, statuses);
        } else {
            ReportLogger.dataBackfill(
                    "[ArVoucherApBackfillStrategy] voucherCode={} confirmed posted — finpost.release_status=[{}]",
                    voucherCode, statuses);
        }
    }

    @Override
    public String k6ScriptKey() {
        // No k6 script exists for this path yet — quantities here are always small (well under
        // smallQuantityThreshold), so DataSourceRouter never actually routes this to K6BULK.
        return "arVoucherBackfill";
    }
}
