package com.gsl.service;

import com.gsl.api.client.DocSchemeSelector;
import com.gsl.api.webapi.factory.ArVoucherRequestFactory;
import java.util.ArrayList;

import com.gsl.api.core.ApiException;
import com.gsl.api.webapi.WebApiEndpoints;
import com.gsl.api.model.arvoucher.ArVoucherCreateRequest;
import com.gsl.api.model.arvoucher.ArVoucherCreateResponse;
import com.gsl.api.model.arvoucher.ArVoucherDetailLine;
import com.gsl.api.model.common.CashBankLedgerLookupResult;
import com.gsl.api.model.common.DocNumberSchemeEntry;
import com.gsl.api.model.common.ReferenceSiteLookupResult;
import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.api.model.udf.UdfValueEntry;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.gsl.api.client.ArVoucherApiClient;

/**
 * AR Voucher creation workflow: threads each live lookup (doc scheme, Cash/Bank, sub-ledger, reference site, UDFs) into the create request. The client
 * ({@link ArVoucherApiClient}) only makes the HTTP calls.
 */
public final class ArVoucherService {

    /** Confirmed via the HARs as the scheme actually used to create a real AR/AP Voucher (code=138). */
    private static final String PREFERRED_DOC_SCHEME_NAME = "AR/AP Voucher";

    private final ArVoucherApiClient client;

    public ArVoucherService(ArVoucherApiClient client) {
        this.client = client;
    }

    /**
     * Orchestrates the full flow for a given required classification, threading each step's live
     * response into the next rather than any hardcoded business data — same principle as
     * {@code ServiceInvoiceAdhocApiClient.createServiceInvoiceAdhocWithDefaults}. This is the
     * method {@code ArVoucherApBackfillStrategy} calls to backfill AP Voucher's document-payment
     * shortfall for any non-Supplier clsCode.
     *
     * @param requiredClsCode the admcls.clscode the backfilled sub-ledger must belong to (0 = any)
     */
    public ArVoucherCreateResponse createArVoucherWithDefaults(
            long requiredClsCode, int ownerSiteCode, int ouCode, long empCode, int quantity, int rate) {
        return createArVoucherWithDefaults(requiredClsCode, ownerSiteCode, ouCode, empCode, quantity, rate, null);
    }

    /**
     * Same as above, but uses the doc scheme named {@code docSchemeName} (resolved by the caller from
     * the DB so an exhausted scheme is never picked). {@code null} keeps the legacy pick: the
     * preferred "AR/AP Voucher" scheme, else the first one the API offers.
     */
    public ArVoucherCreateResponse createArVoucherWithDefaults(
            long requiredClsCode, int ownerSiteCode, int ouCode, long empCode, int quantity, int rate,
            String docSchemeName) {
        return createArVoucherWithDefaults(requiredClsCode, ownerSiteCode, ouCode, empCode, quantity, rate, docSchemeName, sl -> true);
    }

    /** As above, raised against a sub-ledger {@code slFilter} accepts — lets a backfill spread its vouchers over several sub-ledgers. */
    public ArVoucherCreateResponse createArVoucherWithDefaults(
            long requiredClsCode, int ownerSiteCode, int ouCode, long empCode, int quantity, int rate,
            String docSchemeName, java.util.function.Predicate<SubLedgerLookupResult> slFilter) {
        ArVoucherRequestFactory.Inputs inputs = resolve(requiredClsCode, ownerSiteCode, ouCode, empCode, quantity, rate, docSchemeName, slFilter);
        ArVoucherCreateRequest request = ArVoucherRequestFactory.create(inputs, String.valueOf(System.nanoTime()));

        ReportLogger.dataBackfill(
                "[ArVoucherService] Creating AR Voucher — requiredClsCode={}, subLedger='{}' (classCode={}), ownerSiteCode={}, cashBankGLCode={}, amount={}",
                requiredClsCode, inputs.subLedger().getSlName(), inputs.subLedger().getClassCode(), ownerSiteCode, inputs.cashBankGlCode(), inputs.amount());

        return StepExecutor.call("Create AR Voucher via API", () -> client.createArVoucher(request));
    }

    /**
     * {@code count} create requests for the k6 bulk hand-over: every lookup runs once, each request gets its own request id, nothing is posted.
     * Same resolution and building as {@link #createArVoucherWithDefaults}.
     */
    public List<ArVoucherCreateRequest> buildBulkRequests(long requiredClsCode, int ownerSiteCode, int ouCode, long empCode, int quantity, int rate,
                                                          String docSchemeName, int count) {
        ArVoucherRequestFactory.Inputs inputs = resolve(requiredClsCode, ownerSiteCode, ouCode, empCode, quantity, rate, docSchemeName);
        long base = System.nanoTime();
        List<ArVoucherCreateRequest> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) out.add(ArVoucherRequestFactory.create(inputs, String.valueOf(base + i)));
        return out;
    }

    /** Runs every lookup an AR Voucher create needs (doc scheme, Cash/Bank, sub-ledger, reference site, date check, UDFs) and returns the resolved values. */
    private ArVoucherRequestFactory.Inputs resolve(long requiredClsCode, int ownerSiteCode, int ouCode, long empCode, int quantity, int rate,
                                                    String docSchemeName) {
        return resolve(requiredClsCode, ownerSiteCode, ouCode, empCode, quantity, rate, docSchemeName, sl -> true);
    }

    private ArVoucherRequestFactory.Inputs resolve(long requiredClsCode, int ownerSiteCode, int ouCode, long empCode, int quantity, int rate,
                                                    String docSchemeName, java.util.function.Predicate<SubLedgerLookupResult> slFilter) {

        String workDate = LocalDate.now() + "T00:00:00";

        List<DocNumberSchemeEntry> schemes = StepExecutor.call("Get Doc Scheme Nos for AR Voucher",
                () -> client.fetchDocNumberSchemes(ownerSiteCode, workDate));
        DocNumberSchemeEntry scheme = DocSchemeSelector.select(
                schemes, docSchemeName, PREFERRED_DOC_SCHEME_NAME, "AR Voucher");
        long docSchemeCode = scheme != null ? scheme.getCode() : 0;

        List<CashBankLedgerLookupResult> cashBankLedgers = StepExecutor.call("Get Cash/Bank Details for AR Voucher",
                () -> client.fetchCashBankLedgers(ownerSiteCode, (int) empCode, ouCode, workDate));
        if (cashBankLedgers.isEmpty()) {
            throw new ApiException(
                    "GetCashBankLedgers returned no ledgers for ownerSiteCode=" + ownerSiteCode + " — cannot raise an AR Voucher.", 0);
        }
        CashBankLedgerLookupResult cashBankLedger = cashBankLedgers.get(0);

        SubLedgerLookupResult subLedger = StepExecutor.call("Get Sub Ledger Details for AR Voucher",
                () -> client.resolveSubLedger(ownerSiteCode, ouCode, (int) empCode, workDate, requiredClsCode, slFilter));

        List<ReferenceSiteLookupResult> refSites = StepExecutor.call("Get Reference Sites for AR Voucher",
                () -> client.fetchReferenceSites(ouCode, subLedger.getSlCode(), ownerSiteCode, workDate));
        ReferenceSiteLookupResult refSite = refSites.stream()
                .filter(s -> s.getCode() == ownerSiteCode)
                .findFirst()
                .orElseGet(() -> refSites.isEmpty() ? null : refSites.get(0));
        long referenceSiteCode = refSite != null ? refSite.getCode() : ownerSiteCode;
        String referenceSiteName = refSite != null ? refSite.getName() : null;

        StepExecutor.call("Validate Voucher Date for AR Voucher", () -> {
            client.validateVoucherDate(empCode, workDate, ouCode);
            return null;
        });

        List<UdfValueEntry> udfValues = StepExecutor.call("Get UDF Metadata for AR Voucher",
                () -> client.fetchAndBuildUdfValues("FINVCHMAIN"));

        return new ArVoucherRequestFactory.Inputs(subLedger, referenceSiteCode, referenceSiteName, docSchemeCode, cashBankLedger.getGlCode(),
                ownerSiteCode, ouCode, empCode, workDate, quantity * rate, udfValues);
    }
}
