package com.gsl.database.finance;

import com.gsl.api.core.ApiSessionBundle;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.api.integration.ServiceInvoiceAdhocDataSetup;
import com.gsl.api.model.serviceinvoiceadhoc.ServiceInvoiceAdhocCreateResponse;
import com.gsl.database.BackfillStrategy;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinPostDao;
import com.gsl.enums.ApiPlatform;
import com.gsl.model.finance.FinPost;
import com.gsl.reports.ApiCallReportLogger;
import com.gsl.reports.ReportLogger;
import org.jdbi.v3.core.Jdbi;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AP Voucher's document-payment shortfall backfill: a Service Invoice Adhoc against the vendor
 * SL, matching the existing manual practice — when AP Voucher creation has no document to
 * adjust against, create a Service Invoice Adhoc first, then process the AP Voucher. It lands
 * in v_pending_adj with debit_credit='C' — exactly what
 * DbApVoucherDataSource.selectApDocumentsAtomic() is searching for.
 *
 * <p>v_pending_adj filters on {@code finpost.release_status = 'P'} — a Service Invoice Adhoc
 * saved without releasing it stays invisible to that view even though the finpost rows exist.
 * {@link com.gsl.api.client.ServiceInvoiceAdhocApiClient} sets {@code saveAndRelease=true} for
 * this reason; the read-back below confirms it actually took effect rather than trusting the
 * Save response alone.
 */
public final class PendingApDocumentBackfillStrategy implements BackfillStrategy {

    private static final int DEFAULT_QTY = 1;
    private static final int DEFAULT_RATE = 500;

    private final String vendorSlCode;
    private final int ownerSiteCode;

    public PendingApDocumentBackfillStrategy(String vendorSlCode, int ownerSiteCode) {
        this.vendorSlCode = vendorSlCode;
        this.ownerSiteCode = ownerSiteCode;
    }

    @Override
    public void backfill(ApiPlatform platform, int neededQuantity) {
        ApiSessionBundle session = ApiSessionFactory.create(platform);
        ApiCallReportLogger.setSection("PreRequisites — Data Creation");
        try {
            for (int i = 0; i < neededQuantity; i++) {
                String documentDate = LocalDate.now().toString();
                ReportLogger.dataBackfill(
                        "[PendingApDocumentBackfillStrategy] Creating record {}/{} — vendorSlCode={}, ownerSiteCode={}, " +
                                "quantity={}, rate={}, documentDate={}",
                        i + 1, neededQuantity, vendorSlCode, ownerSiteCode, DEFAULT_QTY, DEFAULT_RATE, documentDate);

                ServiceInvoiceAdhocCreateResponse invoice = ServiceInvoiceAdhocDataSetup.setupServiceInvoiceAdhocForTest(
                        session.getServiceInvoiceAdhocApiClient(), vendorSlCode, ownerSiteCode, DEFAULT_QTY, DEFAULT_RATE);

                ReportLogger.dataBackfill("[PendingApDocumentBackfillStrategy] Backfilled record {}/{} — invoiceCode={}, schemeDocno={}",
                        i + 1, neededQuantity, invoice.getInvoiceCode(), invoice.getSchemeDocno());

                logReleaseStatus(invoice.getInvoiceCode());
            }
        } finally {
            ApiCallReportLogger.clearSection();
        }
    }

    /**
     * Reads back finpost.release_status for the just-created invoice — v_pending_adj only
     * surfaces release_status='P' rows, so this confirms whether saveAndRelease actually posted
     * the document instead of trusting the Save response's own claim.
     */
    private static void logReleaseStatus(String invoiceCode) {
        if (invoiceCode == null) return;
        Jdbi jdbi = DatabaseManager.getJdbi();
        FinPostDao finPostDao = jdbi.onDemand(FinPostDao.class);
        List<FinPost> posts = finPostDao.findByEntCode(invoiceCode);
        if (posts.isEmpty()) {
            ReportLogger.dataBackfillWarn(
                    "[PendingApDocumentBackfillStrategy] No finpost row found yet for invoiceCode={} — release status unknown.",
                    invoiceCode);
            return;
        }
        String statuses = posts.stream().map(FinPost::getReleaseStatus).distinct().collect(Collectors.joining(","));
        if (posts.stream().anyMatch(p -> !"P".equals(p.getReleaseStatus()))) {
            ReportLogger.dataBackfillWarn(
                    "[PendingApDocumentBackfillStrategy] invoiceCode={} still not fully posted — finpost.release_status=[{}] " +
                            "(v_pending_adj only shows 'P'). Document will not be found on retry.",
                    invoiceCode, statuses);
        } else {
            ReportLogger.dataBackfill(
                    "[PendingApDocumentBackfillStrategy] invoiceCode={} confirmed posted — finpost.release_status=[{}]",
                    invoiceCode, statuses);
        }
    }

    @Override
    public String k6ScriptKey() {
        return "serviceInvoiceAdhoc";
    }
}
