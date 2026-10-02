package com.gsl.api.client;

import com.gsl.api.client.udf.UdfValueBuilder;
import com.gsl.api.core.ApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.model.arvoucher.ArVoucherCreateRequest;
import com.gsl.api.model.arvoucher.ArVoucherCreateResponse;
import com.gsl.api.model.arvoucher.ArVoucherDetailLine;
import com.gsl.api.model.arvoucher.CashBankLedgerLookupResult;
import com.gsl.api.model.arvoucher.DocNumberSchemeEntry;
import com.gsl.api.model.arvoucher.ReferenceSiteLookupResult;
import com.gsl.api.model.arvoucher.SubLedgerLookupResult;
import com.gsl.api.model.arvoucher.VoucherDateValidationRequest;
import com.gsl.api.model.udf.UdfFieldMetadata;
import com.gsl.api.model.udf.UdfValueEntry;
import com.gsl.config.ConfigFactory;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finance module API client for AR Voucher creation. Mirrors the real flow captured in
 * {@code CreateARVoucherstage-ideal.ginesys.cloud4.har} — used both as a standalone document
 * type AND as the AP Voucher document-payment backfill mechanism for any {@code clsCode} whose
 * {@code clstype} isn't Supplier (Service Invoice Adhoc's GetVendors lookup is Supplier-only, so
 * it can never satisfy a Customer/Jobber/Others/etc. shortfall — see
 * {@code ArVoucherApBackfillStrategy}). An AR Voucher against any sub-ledger classification
 * lands in {@code v_pending_adj} with {@code debit_credit='C'} once posted — exactly what
 * {@code DbApVoucherDataSource.selectApDocumentsAtomic()} is searching for.
 *
 * <p>Endpoints live under the Finance module's own app path ({@code financeApiBaseUri}), not the
 * WebAPI path the other four flow clients use — confirmed via the HAR that the same authenticated
 * WEBAPI session's {@code ginesysauth} bearer header works unchanged against both, only the base
 * path differs, so every call here passes a full absolute URL to override the shared spec's own
 * baseURI rather than needing a separate login.
 */
public class ArVoucherApiClient extends ApiClient {

    private static final String MENU_SHORT_CODE = "FinanceARVoucher";
    private static final String MODULE = "arvoucher";
    private static final String SITE_TYPE = "OS-OO-CM";
    /** Confirmed via HAR as the scheme actually used to create a real AR Voucher (code=138). */
    private static final String PREFERRED_DOC_SCHEME_NAME = "AR/AP Voucher";

    /** Wraps an already-authenticated session {@code spec} — see {@link ApiClient}'s class Javadoc. */
    public ArVoucherApiClient(RequestSpecification spec) {
        super(spec);
    }

    /** Prefixes {@code path} with {@code financeApiBaseUri} — see class Javadoc for why this client's endpoints need that override. */
    private static String url(String path) {
        return ConfigFactory.getApiConfig().financeApiBaseUri() + path;
    }

    /** GET Finance/api/docnumberschemes */
    public List<DocNumberSchemeEntry> fetchDocNumberSchemes(int connectedSite, String workDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("connectedSite", connectedSite);
        params.put("menusysshortcode", MENU_SHORT_CODE);
        params.put("pagingNotRequired", true);
        params.put("workDate", workDate);
        params.put("page", 1);
        params.put("start", 0);
        params.put("limit", 25);
        Response response = getRaw(url("/docnumberschemes"), params);
        return response.jsonPath().getList("result.items", DocNumberSchemeEntry.class);
    }

    /** GET Finance/api/ledgers/cashbankledgers */
    public List<CashBankLedgerLookupResult> fetchCashBankLedgers(
            int connectedSite, int connectedUserCode, int connectedOUCode, String workDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "");
        params.put("searchText", "");
        params.put("queryFieldName", List.of("glName", "abbreviation"));
        params.put("connectedSite", connectedSite);
        params.put("connectedUserCode", connectedUserCode);
        params.put("module", MODULE);
        params.put("workDate", workDate);
        params.put("connectedOUCode", connectedOUCode);
        params.put("connectedSiteType", SITE_TYPE);
        params.put("pagingNotRequired", false);
        params.put("page", 1);
        params.put("start", 0);
        params.put("limit", 50);
        params.put("sort", "glName ASC");
        Response response = getRaw(url("/ledgers/cashbankledgers"), params);
        return response.jsonPath().getList("result.items", CashBankLedgerLookupResult.class);
    }

    /**
     * GET Finance/api/subledgers — unlike Service Invoice Adhoc's GetVendors, returns every
     * classification (Customer/Supplier/Jobber/Others/TDS/etc.), not just Supplier.
     * {@code pagingNotRequired=true} so a target of any classCode further down the (potentially
     * 200+ row) list is never missed to a page-1-only fetch.
     */
    public List<SubLedgerLookupResult> fetchSubLedgers(
            int connectedSite, int connectedOUCode, int connectedUserCode, String workDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("searchText", "");
        params.put("module", MODULE);
        params.put("connectedOUCode", connectedOUCode);
        params.put("connectedUserCode", connectedUserCode);
        params.put("queryFieldName", List.of("slName", "className"));
        params.put("connectedSite", connectedSite);
        params.put("workDate", workDate);
        params.put("connectedSiteOUCode", connectedOUCode);
        params.put("connectedSiteType", SITE_TYPE);
        params.put("pagingNotRequired", true);
        params.put("page", 1);
        params.put("start", 0);
        params.put("limit", 25);
        Response response = getRaw(url("/subledgers"), params);
        return response.jsonPath().getList("result.items", SubLedgerLookupResult.class);
    }

    /**
     * Resolves a live sub-ledger matching {@code requiredClsCode} (0 = no filter, first result
     * wins). Unlike {@code ServiceInvoiceAdhocApiClient.resolveVendor}, there is no silent
     * fallback to a differently-classified SL on miss — that exact shortcut was the root cause of
     * a prior AP Voucher backfill bug (a wrongly-classified document that never satisfied the
     * shortfall it was meant to fix). A genuine miss here means this environment has no active
     * sub-ledger of the required classification at all, which callers must see as a hard failure.
     */
    public SubLedgerLookupResult resolveSubLedger(
            int connectedSite, int connectedOUCode, int connectedUserCode, String workDate, long requiredClsCode) {
        List<SubLedgerLookupResult> subLedgers = fetchSubLedgers(connectedSite, connectedOUCode, connectedUserCode, workDate);
        if (subLedgers.isEmpty()) {
            throw new ApiException(
                    "GetSubLedgers returned no sub-ledgers at all for connectedSite=" + connectedSite
                            + ", connectedOUCode=" + connectedOUCode + " — nothing usable to raise an AR Voucher against.", 0);
        }
        if (requiredClsCode <= 0) {
            return subLedgers.get(0);
        }
        return subLedgers.stream()
                .filter(sl -> sl.getClassCode() == requiredClsCode)
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        "No sub-ledger with classCode=" + requiredClsCode + " found among " + subLedgers.size()
                                + " live sub-ledger(s) for connectedSite=" + connectedSite
                                + " — cannot backfill an AR Voucher for this classification.", 0));
    }

    /** GET Finance/api/sites?target=referencesite */
    public List<ReferenceSiteLookupResult> fetchReferenceSites(
            int connectedOUCode, long slCode, int connectedSite, String workDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("target", "referencesite");
        params.put("module", MODULE);
        params.put("connectedOUCode", connectedOUCode);
        params.put("slCode", slCode);
        params.put("queryFieldName", List.of("name", "siteType", "ouName"));
        params.put("connectedSite", connectedSite);
        params.put("workDate", workDate);
        params.put("connectedSiteOUCode", connectedOUCode);
        params.put("connectedSiteType", SITE_TYPE);
        params.put("page", 1);
        params.put("start", 0);
        params.put("limit", 500);
        Response response = getRaw(url("/sites"), params);
        return response.jsonPath().getList("result.items", ReferenceSiteLookupResult.class);
    }

    /** POST Finance/api/vouchers/ar/validations/voucherdate — throws (via base validateResponse) on a real validation failure. */
    public void validateVoucherDate(long empCode, String voucherDate, long connectedOUCode) {
        VoucherDateValidationRequest request = VoucherDateValidationRequest.builder()
                .voucherDate(voucherDate)
                .empCode(empCode)
                .connectedOUCode(connectedOUCode)
                .build();
        postRaw(url("/vouchers/ar/validations/voucherdate"), request);
    }

    /** POST Finance/api/vouchers/ar — the final save call. */
    public ArVoucherCreateResponse createArVoucher(ArVoucherCreateRequest request) {
        return post(url("/vouchers/ar"), request, ArVoucherCreateResponse.class);
    }

    /**
     * Negative-scenario counterpart to {@link #createArVoucher(ArVoucherCreateRequest)} — for a
     * deliberately incomplete/invalid {@code request}, returns the raw {@link Response} instead
     * of throwing.
     */
    public Response createArVoucherExpectFailure(ArVoucherCreateRequest request) {
        return postRawExpectFailure(url("/vouchers/ar"), request);
    }

    /**
     * GET /udf/GetUDFMetaData — lives under the original WebAPI base (same as
     * {@code ServiceInvoiceAdhocApiClient}'s own relative endpoints), NOT {@code financeApiBaseUri}
     * — confirmed via the HAR path {@code /WebAPI/api/udf/GetUDFMetaData}. Delegates to the shared
     * {@link UdfValueBuilder} (extracted so {@code SubLedgerOthersApiClient} can reuse the exact
     * same fetch/build/mandatory-value logic for {@code FINSL}). Kept public — used directly by
     * {@code ArVoucherApiScenarioType}'s own UDF metadata scenarios.
     */
    public List<UdfFieldMetadata> fetchUdfMetadata(String tableName) {
        return UdfValueBuilder.fetchUdfMetadata(this, tableName);
    }

    /** Fetches metadata then builds {@code udfValues} in one call — see {@link UdfValueBuilder#fetchAndBuild}. */
    public List<UdfValueEntry> fetchAndBuildUdfValues(String tableName) {
        return UdfValueBuilder.fetchAndBuild(this, tableName);
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

        String workDate = LocalDate.now() + "T00:00:00";

        List<DocNumberSchemeEntry> schemes = StepExecutor.call("Get Doc Scheme Nos for AR Voucher",
                () -> fetchDocNumberSchemes(ownerSiteCode, workDate));
        DocNumberSchemeEntry scheme = schemes.stream()
                .filter(s -> PREFERRED_DOC_SCHEME_NAME.equalsIgnoreCase(s.getName()))
                .findFirst()
                .orElseGet(() -> schemes.isEmpty() ? null : schemes.get(0));
        long docSchemeCode = scheme != null ? scheme.getCode() : 0;

        List<CashBankLedgerLookupResult> cashBankLedgers = StepExecutor.call("Get Cash/Bank Details for AR Voucher",
                () -> fetchCashBankLedgers(ownerSiteCode, (int) empCode, ouCode, workDate));
        if (cashBankLedgers.isEmpty()) {
            throw new ApiException(
                    "GetCashBankLedgers returned no ledgers for ownerSiteCode=" + ownerSiteCode + " — cannot raise an AR Voucher.", 0);
        }
        CashBankLedgerLookupResult cashBankLedger = cashBankLedgers.get(0);

        SubLedgerLookupResult subLedger = StepExecutor.call("Get Sub Ledger Details for AR Voucher",
                () -> resolveSubLedger(ownerSiteCode, ouCode, (int) empCode, workDate, requiredClsCode));

        List<ReferenceSiteLookupResult> refSites = StepExecutor.call("Get Reference Sites for AR Voucher",
                () -> fetchReferenceSites(ouCode, subLedger.getSlCode(), ownerSiteCode, workDate));
        ReferenceSiteLookupResult refSite = refSites.stream()
                .filter(s -> s.getCode() == ownerSiteCode)
                .findFirst()
                .orElseGet(() -> refSites.isEmpty() ? null : refSites.get(0));
        long referenceSiteCode = refSite != null ? refSite.getCode() : ownerSiteCode;
        String referenceSiteName = refSite != null ? refSite.getName() : null;

        StepExecutor.call("Validate Voucher Date for AR Voucher", () -> {
            validateVoucherDate(empCode, workDate, ouCode);
            return null;
        });

        List<UdfValueEntry> udfValues = StepExecutor.call("Get UDF Metadata for AR Voucher",
                () -> fetchAndBuildUdfValues("FINVCHMAIN"));

        double amount = quantity * rate;
        ArVoucherDetailLine line = ArVoucherDetailLine.builder()
                .subLedgerCode(subLedger.getSlCode())
                .subLedgerName(subLedger.getSlName())
                .subLedgerId(subLedger.getSlid())
                .subLedgerClassName(subLedger.getClassName())
                .ledgerCode(subLedger.getGlCode())
                .ledgerName(subLedger.getGlName())
                .ledgerGroupName(subLedger.getGeneralLedgerGroupName())
                .ledgerAbbreviation(subLedger.getGeneralLedgerAbbre())
                .referenceSiteName(referenceSiteName)
                .referenceSiteCode(referenceSiteCode)
                .paymentMode("C")
                .amount(amount)
                .build();

        String requestId = String.valueOf(System.nanoTime());

        ArVoucherCreateRequest request = ArVoucherCreateRequest.builder()
                .voucherDate(workDate)
                .docSchemeCode(docSchemeCode)
                .empCode(empCode)
                .connectedOUCode(ouCode)
                .connectedSiteCode(ownerSiteCode)
                .ownerSiteCode(ownerSiteCode)
                .cashBankGLCode(cashBankLedger.getGlCode())
                .arVoucherDetail(List.of(line))
                .requestId(requestId)
                .udfValues(udfValues)
                .build();

        ReportLogger.dataBackfill(
                "[ArVoucherApiClient] Creating AR Voucher — requiredClsCode={}, subLedger='{}' (classCode={}), ownerSiteCode={}, cashBankGLCode={}, amount={}",
                requiredClsCode, subLedger.getSlName(), subLedger.getClassCode(), ownerSiteCode, cashBankLedger.getGlCode(), amount);

        return StepExecutor.call("Create AR Voucher via API", () -> createArVoucher(request));
    }
}
