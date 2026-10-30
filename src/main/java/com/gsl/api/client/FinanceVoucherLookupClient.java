package com.gsl.api.client;

import com.gsl.api.client.udf.UdfValueBuilder;
import com.gsl.api.core.ApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.core.QueryParams;
import com.gsl.api.webapi.WebApiEndpoints;
import com.gsl.api.webapi.model.request.voucher.CashBankLedgerQuery;
import com.gsl.api.webapi.model.request.voucher.DocNumberSchemeQuery;
import com.gsl.api.webapi.model.request.voucher.ReferenceSiteQuery;
import com.gsl.api.webapi.model.request.voucher.RootviewQuery;
import com.gsl.api.webapi.model.request.voucher.SubLedgerQuery;
import com.gsl.api.webapi.model.response.voucher.VoucherRootviewResponse;
import com.gsl.api.model.common.CashBankLedgerLookupResult;
import com.gsl.api.model.common.DocNumberSchemeEntry;
import com.gsl.api.model.common.ReferenceSiteLookupResult;
import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.api.model.common.VoucherDateValidationRequest;
import com.gsl.api.model.common.VoucherActionRequest;
import com.gsl.api.model.common.VoucherActionResponse;
import com.gsl.api.model.common.VoucherDeleteRequest;
import com.gsl.api.model.udf.UdfFieldMetadata;
import com.gsl.api.model.udf.UdfValueEntry;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The Finance lookups and voucher actions AP, AR and GV share, written once: Cash/Bank ledgers, doc schemes, sub-ledgers (all pages),
 * reference sites, voucher-date validation, rootview status/header reads, revert/release/delete and the UDF wrappers. A subclass says
 * which module it is ({@link #module()}, {@link #menuShortCode()}, {@link #voucherSegment()}, {@link #rootviewSegment()}) and keeps only
 * its own endpoints (create, TDS, ...). See docs/conventions/no-duplication.md.
 */
public abstract class FinanceVoucherLookupClient extends ApiClient {

    protected FinanceVoucherLookupClient(RequestSpecification spec) {
        super(spec);
    }

    /** {@code module=} value of the lookups, e.g. {@code apvoucher}. */
    protected abstract String module();

    /** {@code menusysshortcode=} of {@code docnumberschemes}, e.g. {@code FinanceAPVoucher}. */
    protected abstract String menuShortCode();

    /** Path segment of the action endpoints {@code /vouchers/{segment}/revert}, e.g. {@code ap}. */
    protected abstract String voucherSegment();

    /** Path segment of the list/rootview endpoints {@code /vouchers/{segment}/rootview}, e.g. {@code apvouchers}. */
    protected abstract String rootviewSegment();

    /** The site type the lookups are scoped to. */
    protected String siteType() {
        return "OS-OO-CM";
    }

    /** {@code yyyy-MM-ddT00:00:00} for {@code date}, the work-date format every Finance call expects. */
    public static String workDate(LocalDate date) {
        return date + "T00:00:00";
    }

    /** {@code GET /docnumberschemes} for this module's menu (all pages). */
    public List<DocNumberSchemeEntry> fetchDocNumberSchemes(int connectedSite, String workDate) {
        return getAllPages(WebApiEndpoints.Voucher.DOC_NUMBER_SCHEMES,
                DocNumberSchemeQuery.builder().connectedSite(connectedSite).menuShortCode(menuShortCode()).workDate(workDate).build(),
                100, DocNumberSchemeEntry.class);
    }

    /** {@code GET /ledgers/cashbankledgers} - header Cash/Bank ledgers ({@code sourceType} B/C), all pages. */
    public List<CashBankLedgerLookupResult> fetchCashBankLedgers(int connectedSite, long userCode, int ouCode, String workDate) {
        return getAllPages(WebApiEndpoints.Voucher.CASH_BANK_LEDGERS,
                CashBankLedgerQuery.forModule(module(), connectedSite, userCode, ouCode, workDate, siteType()).build(),
                50, CashBankLedgerLookupResult.class);
    }

    /** {@code GET /subledgers} - every classification, all pages (qaprod ignores {@code pagingNotRequired}). */
    public List<SubLedgerLookupResult> fetchSubLedgers(int connectedSite, int ouCode, long userCode, String workDate) {
        return getAllPages(WebApiEndpoints.Voucher.SUB_LEDGERS,
                SubLedgerQuery.forModule(module(), connectedSite, userCode, ouCode, workDate, siteType()).pagingNotRequired(true).build(),
                100, SubLedgerLookupResult.class);
    }

    /** {@code GET /subledgers} with a {@code searchText} - what the Sub Ledger picker sends while typing (first page only). */
    public List<SubLedgerLookupResult> fetchSubLedgersMatching(int connectedSite, int ouCode, long userCode, String workDate, String searchText) {
        SubLedgerQuery query = SubLedgerQuery.forModule(module(), connectedSite, userCode, ouCode, workDate, siteType()).searchText(searchText).build();
        java.util.Map<String, Object> params = QueryParams.toMap(query);
        params.put("page", 1);
        params.put("start", 0);
        params.put("limit", 25);
        List<SubLedgerLookupResult> items = getRaw(WebApiEndpoints.Voucher.SUB_LEDGERS, params).jsonPath().getList("result.items", SubLedgerLookupResult.class);
        return items == null ? List.of() : items;
    }

    /**
     * Resolves a live sub-ledger that satisfies {@code eligible}, optionally narrowed to {@code requiredClsCode} (0 = first eligible). There
     * is no silent fallback to a differently-classified sub-ledger on a miss - that shortcut was the root cause of a prior AP backfill bug.
     *
     * @param description what {@code eligible} means, used in the failure message (e.g. "Customer-classified")
     */
    protected SubLedgerLookupResult resolveSubLedger(int connectedSite, int ouCode, long userCode, String workDate, long requiredClsCode,
                                                     Predicate<SubLedgerLookupResult> eligible, String description) {
        List<SubLedgerLookupResult> all = fetchSubLedgers(connectedSite, ouCode, userCode, workDate);
        if (all.isEmpty()) {
            throw new ApiException("GetSubLedgers returned no sub-ledgers at all for connectedSite=" + connectedSite + ", connectedOUCode=" + ouCode
                    + " - nothing usable to raise a " + module() + " against.", 0);
        }
        List<SubLedgerLookupResult> usable = all.stream().filter(eligible).collect(java.util.stream.Collectors.toList());
        if (usable.isEmpty()) {
            throw new ApiException("GetSubLedgers returned " + all.size() + " sub-ledger(s) for connectedSite=" + connectedSite + " but none "
                    + description + " - cannot raise a " + module() + ".", 0);
        }
        if (requiredClsCode <= 0) return usable.get(0);
        return usable.stream().filter(sl -> sl.getClassCode() == requiredClsCode).findFirst()
                .orElseThrow(() -> new ApiException("No " + description + " sub-ledger with classCode=" + requiredClsCode + " found among "
                        + usable.size() + " sub-ledger(s) for connectedSite=" + connectedSite + " - cannot backfill an " + module() + " for this classification.", 0));
    }

    /** {@code GET /sites?target=referencesite} for a sub-ledger; {@code glCode <= 0} omits the GL (AR's lookup does not send it). */
    public List<ReferenceSiteLookupResult> fetchReferenceSites(int connectedOUCode, long slCode, long glCode, int connectedSite, String workDate) {
        List<ReferenceSiteLookupResult> items = getRawQuery(WebApiEndpoints.Voucher.SITES,
                ReferenceSiteQuery.forModule(module(), connectedSite, connectedOUCode, workDate, siteType(), slCode, glCode).build())
                .jsonPath().getList("result.items", ReferenceSiteLookupResult.class);
        return items == null ? List.of() : items;
    }

    /** {@link #fetchReferenceSites(int, long, long, int, String)} without a GL. */
    public List<ReferenceSiteLookupResult> fetchReferenceSites(int connectedOUCode, long slCode, int connectedSite, String workDate) {
        return fetchReferenceSites(connectedOUCode, slCode, 0, connectedSite, workDate);
    }

    /** {@code POST /vouchers/{segment}/validations/voucherdate} for a NEW voucher - throws on a real validation failure. */
    public void validateVoucherDate(long empCode, String voucherDate, long connectedOUCode) {
        postRaw(WebApiEndpoints.Voucher.validateVoucherDate(voucherSegment()), VoucherDateValidationRequest.builder()
                .voucherDate(voucherDate).empCode(empCode).connectedOUCode(connectedOUCode).build());
    }

    // ─── rootview ────────────────────────────────────────────────────────────

    private RootviewQuery rootviewQuery(long vchcode, long connectedOUCode, long connectedUserCode) {
        return new RootviewQuery(vchcode, connectedOUCode, connectedUserCode);
    }

    /** {@code GET /vouchers/{rootviewSegment}/rootview} - throws when the voucher cannot be read. */
    protected VoucherRootviewResponse getRootview(long vchcode, long connectedOUCode, long connectedUserCode) {
        return getRawQuery(WebApiEndpoints.Voucher.rootview(rootviewSegment()), rootviewQuery(vchcode, connectedOUCode, connectedUserCode))
                .as(VoucherRootviewResponse.class);
    }

    private VoucherRootviewResponse getRootviewOrNull(long vchcode, long connectedOUCode, long connectedUserCode) {
        Response response = getRawQueryExpectFailure(WebApiEndpoints.Voucher.rootview(rootviewSegment()),
                rootviewQuery(vchcode, connectedOUCode, connectedUserCode));
        return response.statusCode() >= 400 ? null : response.as(VoucherRootviewResponse.class);
    }

    /** The voucher's {@code documentStatus} ("P" posted, "U" unposted), or null when it no longer exists / cannot be read. */
    public String fetchDocumentStatus(long vchcode, long connectedOUCode, long connectedUserCode) {
        VoucherRootviewResponse response = getRootviewOrNull(vchcode, connectedOUCode, connectedUserCode);
        return response == null ? null : response.documentStatus();
    }

    /** The voucher's header fields as the rootview reports them ({@code reference}, {@code remarks}, {@code voucherDate}, ...); empty when unreadable. */
    public Map<String, Object> fetchVoucherHeader(long vchcode, long connectedOUCode, long connectedUserCode) {
        VoucherRootviewResponse response = getRootviewOrNull(vchcode, connectedOUCode, connectedUserCode);
        return response == null ? Map.of() : response.header();
    }

    // ─── actions ─────────────────────────────────────────────────────────────

    private VoucherActionRequest action(List<Long> vchcodes, long empCode, long ouCode) {
        return VoucherActionRequest.builder().code(vchcodes).empCode(empCode).connectedOUCode(ouCode).build();
    }

    /** {@code PUT /vouchers/{segment}/revert} - Unpost. Inspect {@link VoucherActionResponse#allSucceeded()}: a refusal is still HTTP 200. */
    public VoucherActionResponse revert(List<Long> vchcodes, long empCode, long ouCode) {
        return put(WebApiEndpoints.Voucher.action(voucherSegment(), "revert"), action(vchcodes, empCode, ouCode), VoucherActionResponse.class);
    }

    /** {@code PUT /vouchers/{segment}/release} - Post. */
    public VoucherActionResponse release(List<Long> vchcodes, long empCode, long ouCode) {
        return put(WebApiEndpoints.Voucher.action(voucherSegment(), "release"), action(vchcodes, empCode, ouCode), VoucherActionResponse.class);
    }

    /** {@code DELETE /vouchers/{segment}} - the vouchers must be Unposted. */
    public VoucherActionResponse delete(List<Long> vchcodes, long empCode, long ouCode) {
        return delete(WebApiEndpoints.Voucher.root(voucherSegment()),
                VoucherDeleteRequest.builder().empCode(empCode).connectedOUCode(ouCode).voucherCodes(vchcodes).build(), VoucherActionResponse.class);
    }

    // ─── UDF ─────────────────────────────────────────────────────────────────

    /** {@code GET /udf/GetUDFMetaData} for {@code tableName} (lives under the WebAPI base). */
    public List<UdfFieldMetadata> fetchUdfMetadata(String tableName) {
        return UdfValueBuilder.fetchUdfMetadata(this, tableName);
    }

    /** UDF values for {@code tableName} built from its metadata (mandatory ones get a value). */
    public List<UdfValueEntry> fetchAndBuildUdfValues(String tableName) {
        return UdfValueBuilder.fetchAndBuild(this, tableName);
    }
}
