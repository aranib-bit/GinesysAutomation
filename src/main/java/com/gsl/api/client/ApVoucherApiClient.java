package com.gsl.api.client;

import com.gsl.api.core.ApiException;
import com.gsl.api.webapi.WebApiEndpoints;
import com.gsl.api.core.QueryParams;
import com.gsl.api.webapi.model.request.voucher.DocNumberSchemeQuery;
import com.gsl.api.webapi.model.request.voucher.TdsSectionQuery;
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

/**
 * Finance module API client for AP Voucher creation. Mirrors the real flow captured in
 * {@code CreateAPVoucherCustomerAdvanceTDS_stage-ideal.ginesys.cloud4.har} and
 * {@code CreateAPVoucherCustomerAdvanceNoTDS_stage-ideal.ginesys.cloud4.har} — used as the AR
 * Voucher document-payment backfill mechanism (see {@code ApVoucherArBackfillStrategy}): an AP
 * Voucher posted {@code onAccount} against a Customer-classified sub-ledger (confirmed via HAR:
 * {@code generalLedgerGroupName="Sundry Debtors"}) lands in {@code v_pending_adj} with
 * {@code debit_credit='D'} once posted — exactly what
 * {@code DbArVoucherDataSource.loadFreshForScenarioInternal()} is searching for. The exact mirror
 * of {@link ArVoucherApiClient}, which does the same thing in the opposite direction for AP
 * Voucher's own shortfall.
 *
 * <p>Reuses {@code com.gsl.api.model.arvoucher}'s {@code SubLedgerLookupResult},
 * {@code DocNumberSchemeEntry}, {@code CashBankLedgerLookupResult}, {@code ReferenceSiteLookupResult}
 * and {@code VoucherDateValidationRequest} — these are generic Finance-module lookup/request shapes
 * shared by both voucher types (same underlying endpoints, package name is just historical from
 * AR being built first), not AR-specific. Only the shapes that genuinely differ for AP Voucher
 * (the create request/response, its detail line, the TDS section lookup, and the attachment shape)
 * get their own classes under {@code com.gsl.api.model.apvoucher}.
 */
public class ApVoucherApiClient extends FinanceVoucherLookupClient {

    /** Doc scheme menu for the auto-created TDS journal — confirmed via HAR (code=171, "TDS Journal"). */
    private static final String TDS_JOURNAL_MENU_SHORT_CODE = "FinanceTDSJournal";
    /** {@code srcCode} query param on the TDS section lookup — confirmed via HAR as 1 ("COMPANY") for every
     *  sub-ledger seen in both captures; the shared {@link SubLedgerLookupResult} model doesn't carry the
     *  underlying {@code sourceCode} field, so this mirrors the literal observed value rather than
     *  threading a new field through just for this one call. */
    private static final int DEFAULT_SRC_CODE = 1;

    /** Wraps an already-authenticated session {@code spec} — see {@link ApiClient}'s class Javadoc. */
    public ApVoucherApiClient(RequestSpecification spec) {
        super(spec);
    }

    @Override protected String module() { return "apvoucher"; }
    @Override protected String menuShortCode() { return "FinanceAPVoucher"; }
    @Override protected String voucherSegment() { return "ap"; }
    @Override protected String rootviewSegment() { return "apvouchers"; }

    /** GET Finance/api/docnumberschemes for the auto-created TDS journal scheme — TDS-only, confirmed via HAR. */
    public long fetchTdsJournalDocSchemeCode(int connectedSite) {
        DocNumberSchemeQuery query = DocNumberSchemeQuery.builder().connectedSite(connectedSite).menuShortCode(TDS_JOURNAL_MENU_SHORT_CODE)
                .pagingNotRequired(null).page(1).limit(10).build();
        Response response = getRaw(WebApiEndpoints.Voucher.DOC_NUMBER_SCHEMES, QueryParams.toMap(query));
        List<DocNumberSchemeEntry> schemes = response.jsonPath().getList("result.items", DocNumberSchemeEntry.class);
        if (schemes.isEmpty()) {
            throw new ApiException(
                    "GetDocNumberSchemes(menusysshortcode=" + TDS_JOURNAL_MENU_SHORT_CODE
                            + ") returned no scheme — cannot deduct TDS without an auto-created journal scheme.", 0);
        }
        return schemes.get(0).getCode();
    }

    /**
     * Resolves a live, Customer-classified ({@code classType='C'}) sub-ledger, optionally further
     * narrowed to {@code requiredClsCode} (0 = any Customer classification, first result wins).
     *
     * <p>Unlike {@link ArVoucherApiClient#resolveSubLedger} — which may legitimately resolve
     * against any classification, since AR Voucher itself has no classification restriction —
     * {@code /Finance/api/subledgers} under {@code module=apvoucher} returns sub-ledgers of
     * <b>every</b> classification (Customer/Supplier/Jobber/Agent/TDS-agency/Others/etc.), but
     * this client's on-account "Customer Advance" flow (confirmed via both the TDS and No-TDS
     * HAR captures — {@code generalLedgerGroupName="Sundry Debtors"} on the created line) only
     * produces the {@code debit_credit='D'} shape AR Voucher needs against a Customer-classified
     * SL. The Customer filter is therefore applied unconditionally, before any
     * {@code requiredClsCode} narrowing — never falls back to a differently-classified SL on miss,
     * same hard-fail principle as {@code ArVoucherApiClient.resolveSubLedger}.
     *
     * <p>Deliberately public and standalone (not folded into {@link #createApVoucherWithDefaults})
     * so {@code ApVoucherArBackfillStrategy} can resolve the sub-ledger first, run its own DB-only
     * TDS-applicability check ({@code finsl.tds_app}) against the resolved SL, then pass both the
     * SL and that decision into {@link #createApVoucherWithDefaults} — keeping DB access out of
     * this class entirely (no other {@code api.client.*} class touches JDBI).
     */
    public SubLedgerLookupResult resolveSubLedger(
            int connectedSite, int connectedOUCode, int connectedUserCode, String workDate, long requiredClsCode) {
        // A required class decides by itself (a Supplier, Jobber, ... class is fine); only the "any class" request is limited to customers.
        return requiredClsCode > 0
                ? resolveSubLedger(connectedSite, connectedOUCode, connectedUserCode, workDate, requiredClsCode, sl -> true, "usable")
                : resolveSubLedger(connectedSite, connectedOUCode, connectedUserCode, workDate, requiredClsCode,
                        sl -> "C".equalsIgnoreCase(sl.getClassType()), "Customer-classified (classType='C')");
    }

    /**
     * GET Finance/api/tdssection/tdsname — TDS-only. Lists the configured TDS deduction types
     * (name/section/rate/agency) available for the given sub-ledger; does NOT itself say whether
     * TDS applies (that's {@code finsl.tds_app} in the DB) — only call this once applicability is
     * already known to be true.
     */
    public List<TdsSectionLookupResult> fetchTdsSection(
            int connectedSite, int connectedUserCode, int connectedOUCode, String workDate, long slCode) {
        return fetchTdsSection(connectedSite, connectedUserCode, connectedOUCode, workDate, slCode, DEFAULT_SRC_CODE);
    }

    /** {@link #fetchTdsSection(int, int, int, String, long)} for the TDS source the party is tagged with (the natures offered are those with a rate for it). */
    public List<TdsSectionLookupResult> fetchTdsSection(
            int connectedSite, int connectedUserCode, int connectedOUCode, String workDate, long slCode, long srcCode) {
        TdsSectionQuery query = TdsSectionQuery.builder().searchText("").queryFieldName(TdsSectionQuery.QUERY_FIELDS)
                .connectedSite(connectedSite).connectedUserCode((long) connectedUserCode).module(module()).workDate(workDate)
                .connectedOuCode(connectedOUCode).connectedSiteType(siteType()).slCode(slCode).srcCode((int) srcCode).voucherDate(workDate).build();
        Map<String, Object> params = QueryParams.toMap(query);
        params.put("page", 1);
        params.put("start", 0);
        params.put("limit", 50);
        Response response = getRaw(WebApiEndpoints.Voucher.TDS_SECTION_NAME, params);
        return response.jsonPath().getList("result.items", TdsSectionLookupResult.class);
    }

    /** POST Finance/api/vouchers/ap — the final save call. */
    public ApVoucherCreateResponse createApVoucher(ApVoucherCreateRequest request) {
        return post(WebApiEndpoints.Voucher.root("ap"), request, ApVoucherCreateResponse.class);
    }

    /**
     * Negative-scenario counterpart to {@link #createApVoucher(ApVoucherCreateRequest)} — for a
     * deliberately incomplete/invalid {@code request}, returns the raw {@link Response} instead
     * of throwing.
     */
    public Response createApVoucherExpectFailure(ApVoucherCreateRequest request) {
        return postRawExpectFailure(endpointUrl(WebApiEndpoints.Voucher.root("ap")), request);
    }

    /** {@code PUT /vouchers/ap} - edit an Unposted AP Voucher ({@code request} carries {@code code}, {@code docNumber}, {@code dataVersion} and the line codes/versions). */
    public ApVoucherCreateResponse updateApVoucher(ApVoucherCreateRequest request) {
        return put(WebApiEndpoints.Voucher.root("ap"), request, ApVoucherCreateResponse.class);
    }

    /** Revert (Unpost) one Posted AP Voucher - a business refusal is still HTTP 200, so check {@link VoucherActionResponse#allSucceeded()}. */
    public VoucherActionResponse revertApVoucher(long vchcode, long empCode, long connectedOUCode) {
        return revert(List.of(vchcode), empCode, connectedOUCode);
    }

    /** Delete one Unposted AP Voucher (revert first). */
    public VoucherActionResponse deleteApVoucher(long vchcode, long empCode, long connectedOUCode) {
        return delete(List.of(vchcode), empCode, connectedOUCode);
    }
}
