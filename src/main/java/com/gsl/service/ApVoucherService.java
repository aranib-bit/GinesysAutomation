package com.gsl.service;

import com.gsl.api.client.DocSchemeSelector;
import com.gsl.api.webapi.factory.ApVoucherRequestFactory;
import java.util.ArrayList;

import com.gsl.api.core.ApiException;
import com.gsl.api.webapi.WebApiEndpoints;
import com.gsl.api.model.apvoucher.ApVoucherAdjustmentDetail;
import com.gsl.api.model.apvoucher.ApVoucherAttachment;
import com.gsl.api.model.apvoucher.ApVoucherCreateRequest;
import com.gsl.api.model.apvoucher.ApVoucherCreateResponse;
import com.gsl.api.model.apvoucher.ApVoucherDetailLine;
import com.gsl.api.model.apvoucher.TdsSectionLookupResult;
import com.gsl.api.model.common.CashBankLedgerLookupResult;
import com.gsl.api.model.common.DocNumberSchemeEntry;
import com.gsl.api.model.common.ReferenceSiteLookupResult;
import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.api.model.common.VoucherActionResponse;
import com.gsl.api.model.udf.UdfValueEntry;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.gsl.api.client.ApVoucherApiClient;

/**
 * AP Voucher creation workflow: threads each live lookup (doc scheme, Cash/Bank, reference site, UDFs, TDS section) into the create request. The client
 * ({@link ApVoucherApiClient}) only makes the HTTP calls.
 */
public final class ApVoucherService {

    /** Confirmed via the HARs as the scheme actually used to create a real AR/AP Voucher (code=138). */
    private static final String PREFERRED_DOC_SCHEME_NAME = "AR/AP Voucher";

    private final ApVoucherApiClient client;

    public ApVoucherService(ApVoucherApiClient client) {
        this.client = client;
    }

    /**
     * Orchestrates the full flow against an already-resolved {@code subLedger} and a
     * caller-supplied {@code tdsApplicable} decision — see {@link #resolveSubLedger} javadoc for
     * why resolution and the TDS decision happen outside this method rather than inside it (as
     * {@link ArVoucherApiClient#createArVoucherWithDefaults} does for its own, DB-free case).
     * This is the method {@code ApVoucherArBackfillStrategy} calls to backfill AR Voucher's
     * document-payment shortfall.
     */
    public ApVoucherCreateResponse createApVoucherWithDefaults(
            SubLedgerLookupResult subLedger, int ownerSiteCode, int ouCode, long empCode, int quantity, int rate,
            boolean tdsApplicable) {
        return createApVoucherWithDefaults(subLedger, ownerSiteCode, ouCode, empCode, quantity, rate,
                tdsApplicable, null);
    }

    /**
     * Same as above, but uses the doc scheme named {@code docSchemeName} (resolved by the caller from
     * the DB so an exhausted scheme is never picked). {@code null} keeps the legacy pick: the
     * preferred "AR/AP Voucher" scheme, else the first one the API offers.
     */
    public ApVoucherCreateResponse createApVoucherWithDefaults(
            SubLedgerLookupResult subLedger, int ownerSiteCode, int ouCode, long empCode, int quantity, int rate,
            boolean tdsApplicable, String docSchemeName) {
        return createApVoucherWithDefaults(subLedger, ownerSiteCode, ouCode, empCode, quantity, rate, tdsApplicable, docSchemeName, null);
    }

    /**
     * As above, but when {@code adjustments} is non-empty the line adjusts those pending documents (a "document payment" voucher, which
     * tags it against them — HAR: APVoucherwithalllookupsDocAdjustment): {@code onAccount=false}, no TDS, and the line amount is the
     * sum of the adjusted amounts.
     */
    public ApVoucherCreateResponse createApVoucherWithDefaults(
            SubLedgerLookupResult subLedger, int ownerSiteCode, int ouCode, long empCode, int quantity, int rate,
            boolean tdsApplicable, String docSchemeName, List<ApVoucherAdjustmentDetail> adjustments) {
        ApVoucherRequestFactory.Inputs inputs = resolve(subLedger, ownerSiteCode, ouCode, empCode, quantity, rate, tdsApplicable, docSchemeName, adjustments);
        ApVoucherCreateRequest request = ApVoucherRequestFactory.create(inputs, String.valueOf(System.nanoTime()));

        ReportLogger.dataBackfill(
                "[ApVoucherService] Creating AP Voucher — subLedger='{}' (classCode={}), ownerSiteCode={}, cashBankGLCode={}, tdsApplicable={}, amount={}",
                subLedger.getSlName(), subLedger.getClassCode(), ownerSiteCode, inputs.cashBankGlCode(), inputs.tds() != null,
                request.getApVoucherDetail().get(0).getAmount());

        return StepExecutor.call("Create AP Voucher via API", () -> client.createApVoucher(request));
    }

    /**
     * {@code count} advance (on-account) create requests for the k6 bulk hand-over: every lookup runs once, each request gets its own request id,
     * nothing is posted. Same resolution and building as {@link #createApVoucherWithDefaults}.
     */
    public List<ApVoucherCreateRequest> buildBulkRequests(SubLedgerLookupResult subLedger, int ownerSiteCode, int ouCode, long empCode, int quantity, int rate,
                                                          boolean tdsApplicable, String docSchemeName, int count) {
        ApVoucherRequestFactory.Inputs inputs = resolve(subLedger, ownerSiteCode, ouCode, empCode, quantity, rate, tdsApplicable, docSchemeName, null);
        long base = System.nanoTime();
        List<ApVoucherCreateRequest> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) out.add(ApVoucherRequestFactory.create(inputs, String.valueOf(base + i)));
        return out;
    }

    /** Runs every lookup an AP Voucher create needs (doc scheme, Cash/Bank, reference site, date check, UDFs, TDS) and returns the resolved values. */
    private ApVoucherRequestFactory.Inputs resolve(SubLedgerLookupResult subLedger, int ownerSiteCode, int ouCode, long empCode, int quantity, int rate,
                                                   boolean tdsApplicable, String docSchemeName, List<ApVoucherAdjustmentDetail> adjustments) {
        boolean adjusting = adjustments != null && !adjustments.isEmpty();
        if (adjusting) tdsApplicable = false;

        String workDate = LocalDate.now() + "T00:00:00";

        List<DocNumberSchemeEntry> schemes = StepExecutor.call("Get Doc Scheme Nos for AP Voucher",
                () -> client.fetchDocNumberSchemes(ownerSiteCode, workDate));
        DocNumberSchemeEntry scheme = DocSchemeSelector.select(
                schemes, docSchemeName, PREFERRED_DOC_SCHEME_NAME, "AP Voucher");
        long docSchemeCode = scheme != null ? scheme.getCode() : 0;

        List<CashBankLedgerLookupResult> cashBankLedgers = StepExecutor.call("Get Cash/Bank Details for AP Voucher",
                () -> client.fetchCashBankLedgers(ownerSiteCode, (int) empCode, ouCode, workDate));
        if (cashBankLedgers.isEmpty()) {
            throw new ApiException(
                    "GetCashBankLedgers returned no ledgers for ownerSiteCode=" + ownerSiteCode + " — cannot raise an AP Voucher.", 0);
        }
        CashBankLedgerLookupResult cashBankLedger = cashBankLedgers.get(0);

        List<ReferenceSiteLookupResult> refSites = StepExecutor.call("Get Reference Sites for AP Voucher",
                () -> client.fetchReferenceSites(ouCode, subLedger.getSlCode(), subLedger.getGlCode(), ownerSiteCode, workDate));
        ReferenceSiteLookupResult refSite = refSites.stream()
                .filter(s -> s.getCode() == ownerSiteCode)
                .findFirst()
                .orElseGet(() -> refSites.isEmpty() ? null : refSites.get(0));
        long referenceSiteCode = refSite != null ? refSite.getCode() : ownerSiteCode;

        StepExecutor.call("Validate Voucher Date for AP Voucher", () -> {
            client.validateVoucherDate(empCode, workDate, ouCode);
            return null;
        });

        List<UdfValueEntry> udfValues = StepExecutor.call("Get UDF Metadata for AP Voucher",
                () -> client.fetchAndBuildUdfValues("FINVCHMAIN"));

        double grossAmount = adjusting
                ? adjustments.stream().mapToDouble(ApVoucherAdjustmentDetail::getAdjustedAmount).sum()
                : quantity * rate;
        TdsSectionLookupResult tds = null;
        Long journalDocSchemeCode = null;
        if (tdsApplicable) {
            List<TdsSectionLookupResult> tdsSections = StepExecutor.call("Get TDS Section for AP Voucher",
                    () -> client.fetchTdsSection(ownerSiteCode, (int) empCode, ouCode, workDate, subLedger.getSlCode()));
            if (tdsSections.isEmpty()) {
                throw new ApiException(
                        "GetTdsSection returned no TDS types for slCode=" + subLedger.getSlCode()
                                + " — tds_app='Y' in DB but no TDS section configured to deduct against.", 0);
            }
            // Deterministic pick, same "vary by slcode" convention as TdsNameQuery.resolve — any
            // one candidate is a valid choice for backfill purposes.
            tds = tdsSections.get((int) (subLedger.getSlCode() % tdsSections.size()));
            journalDocSchemeCode = StepExecutor.call("Get TDS Journal Doc Scheme for AP Voucher",
                    () -> client.fetchTdsJournalDocSchemeCode(ownerSiteCode));
            ReportLogger.dataBackfill(
                    "[ApVoucherService] TDS applicable — subLedger='{}', tdsName='{}', tdsTypeCode={}, tdsRate={}, gross={}",
                    subLedger.getSlName(), tds.getTdsName(), tds.getCode(), tds.getTdsRate(), grossAmount);
        }
        return new ApVoucherRequestFactory.Inputs(subLedger, docSchemeCode, cashBankLedger.getGlCode(), referenceSiteCode, ownerSiteCode, ouCode, empCode,
                workDate, grossAmount, tds, journalDocSchemeCode, adjusting ? adjustments : null, udfValues);
    }
}
