package com.gsl.api.client;

import com.gsl.api.client.udf.UdfValueBuilder;
import com.gsl.api.core.ApiClient;
import com.gsl.api.webapi.client.ChequeBookApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.core.QueryParams;
import com.gsl.api.webapi.WebApiEndpoints;
import com.gsl.api.webapi.model.request.voucher.CashBankLedgerQuery;
import com.gsl.api.webapi.model.request.voucher.ChequeQuery;
import com.gsl.api.webapi.model.request.voucher.CostCenterSiteQuery;
import com.gsl.api.webapi.model.request.voucher.UserSiteQuery;
import com.gsl.api.webapi.model.request.voucher.DocNumberSchemeQuery;
import com.gsl.api.webapi.model.request.voucher.GlLedgerQuery;
import com.gsl.api.webapi.model.request.voucher.ParentGlSubLedgerQuery;
import com.gsl.api.webapi.model.request.voucher.RootviewQuery;
import com.gsl.api.webapi.model.response.voucher.GeneralVoucherListResponse;
import com.gsl.api.webapi.model.response.voucher.GeneralVoucherRootViewResponse;
import com.gsl.api.webapi.model.response.voucher.UserSiteContextResponse;
import com.gsl.api.model.common.CashBankLedgerLookupResult;
import com.gsl.api.model.common.DocNumberSchemeEntry;
import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.api.model.common.VoucherDateValidationRequest;
import com.gsl.api.model.common.UserSiteContext;
import com.gsl.api.model.generalvoucher.ChequeLookupResult;
import com.gsl.api.model.generalvoucher.CostCenterAllocation;
import com.gsl.api.model.generalvoucher.CostCenterSiteLookupResult;
import com.gsl.api.model.common.VoucherActionRequest;
import com.gsl.api.model.common.VoucherActionResponse;
import com.gsl.api.model.generalvoucher.GeneralVoucherCreateRequest;
import com.gsl.api.model.generalvoucher.GeneralVoucherCreateResponse;
import com.gsl.api.model.common.VoucherDeleteRequest;
import com.gsl.api.model.generalvoucher.GeneralVoucherDetailLine;
import com.gsl.api.model.generalvoucher.GeneralVoucherDocSchemeRef;
import com.gsl.api.model.generalvoucher.GeneralVoucherListItem;
import com.gsl.api.model.generalvoucher.GeneralVoucherListRequest;
import com.gsl.api.model.generalvoucher.GeneralVoucherOwnerSite;
import com.gsl.api.model.generalvoucher.GeneralVoucherRootView;
import com.gsl.api.model.generalvoucher.GlLedgerLookupResult;
import com.gsl.api.model.udf.UdfValueEntry;
import com.gsl.config.ConfigFactory;
import com.gsl.enums.GlAccountType;
import com.gsl.reports.ReportLogger;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finance module API client for General Voucher (GV). Every endpoint and body here is confirmed from captured HARs
 * ({@code GVVoucherSave…}, {@code GVVoucherRevertDocPage…}, {@code GVVoucherRevertListPage…}, {@code GVVoucherDelete…},
 * {@code GVVoucherbydocno…}, {@code GeneralVoucherAudit…}) — see the plan's Part F catalogue:
 * <ul>
 *   <li>create {@code POST /vouchers/general}, edit {@code PUT /vouchers/general} (voucher must be Unposted),
 *       revert/release {@code PUT /vouchers/general/revert|release}, delete {@code DELETE /vouchers/general} (body {@code voucherCodes});</li>
 *   <li>read {@code POST /vouchers/generalvouchers} (list, JSON-string {@code filter}) and
 *       {@code GET /vouchers/generalvouchers/rootview?code=};</li>
 *   <li>Add-form lookups with {@code module=generalvoucher}: {@code /docnumberschemes}, {@code /ledgers/cashbankledgers},
 *       {@code /ledgers}, {@code /subledgers?parentGLCode=}, {@code /sites?target=costcentertagging}, {@code /cheques};</li>
 *   <li>user context {@code POST /WebAPI/api/UserRolePolicy/GetAllSiteForAUser} (sites, OU, profile flags).</li>
 * </ul>
 * Revert/release/delete report business failures inside {@code result[].item3} with HTTP 200 — see
 * {@link VoucherActionResponse}; the {@code *OrThrow} variants turn those into {@link ApiException}.
 */
public class GeneralVoucherApiClient extends ApiClient {

    private static final String MODULE = "generalvoucher";
    /** Path segment of the action endpoints ({@code /vouchers/general/revert}). */
    private static final String SEGMENT = "general";
    /** Path segment of the list/rootview endpoints ({@code /vouchers/generalvouchers}). */
    private static final String LIST_SEGMENT = "generalvouchers";
    private static final String SITE_TYPE = "OS-OO-CM";
    private static final String MENU_SHORT_CODE = "FinanceGenVoucher";
    private static final String DOC_SCHEME_NAME = "General Voucher";

    /** Wraps an already-authenticated session {@code spec} — see {@link ApiClient}'s class Javadoc. */
    public GeneralVoucherApiClient(RequestSpecification spec) {
        super(spec);
    }

    /** Cheque Book master client sharing this session and Finance base (used by the API-mode cheque shortfall backfill). */
    public ChequeBookApiClient chequeBooks() {
        final GeneralVoucherApiClient outer = this;
        return new ChequeBookApiClient(spec) {
            @Override
            protected String financeBase() {
                return outer.financeBase();
            }
        };
    }

    /** {@code yyyy-MM-ddT00:00:00} for {@code date}, the work-date format every Finance call expects. */
    public static String workDate(LocalDate date) {
        return date + "T00:00:00";
    }

    // ───────────────────────────── user context ─────────────────────────────

    /** {@code POST /WebAPI/api/UserRolePolicy/GetAllSiteForAUser?userId=0} (body {@code {}}) — assigned sites, OU, profile flags. */
    public UserSiteContext fetchUserContext() {
        return postRaw(WebApiEndpoints.User.ALL_SITES, Map.of(), QueryParams.toMap(UserSiteQuery.currentUser())).as(UserSiteContextResponse.class).getResult();
    }

    // ───────────────────────────── create / edit / actions ─────────────────────────────

    /** {@code POST /vouchers/general} — create. */
    public GeneralVoucherCreateResponse createGeneralVoucher(GeneralVoucherCreateRequest request) {
        return post(WebApiEndpoints.Voucher.root(SEGMENT), request, GeneralVoucherCreateResponse.class);
    }

    /** Negative-scenario counterpart to {@link #createGeneralVoucher} — returns the raw response without throwing on failure. */
    public Response createGeneralVoucherExpectFailure(GeneralVoucherCreateRequest request) {
        return postRawExpectFailure(endpointUrl(WebApiEndpoints.Voucher.root(SEGMENT)), request);
    }

    /** {@code PUT /vouchers/general} — edit an <b>Unposted</b> voucher; {@code request} needs {@code code}, {@code docNumber}, {@code dataVersion} and line codes/versions. */
    public GeneralVoucherCreateResponse updateGeneralVoucher(GeneralVoucherCreateRequest request) {
        return put(WebApiEndpoints.Voucher.root(SEGMENT), request, GeneralVoucherCreateResponse.class);
    }

    /** {@code PUT /vouchers/general/revert} — Unpost. Inspect {@link VoucherActionResponse#allSucceeded()}. */
    public VoucherActionResponse revertGeneralVoucher(List<Long> vchcodes, long empCode, long ouCode) {
        return put(WebApiEndpoints.Voucher.action(SEGMENT, "revert"), action(vchcodes, empCode, ouCode), VoucherActionResponse.class);
    }

    /** {@code PUT /vouchers/general/release} — Post. Inspect {@link VoucherActionResponse#allSucceeded()}. */
    public VoucherActionResponse releaseGeneralVoucher(List<Long> vchcodes, long empCode, long ouCode) {
        return put(WebApiEndpoints.Voucher.action(SEGMENT, "release"), action(vchcodes, empCode, ouCode), VoucherActionResponse.class);
    }

    /** {@code DELETE /vouchers/general} (body {@code voucherCodes}) — the vouchers must be Unposted. */
    public VoucherActionResponse deleteGeneralVouchers(List<Long> vchcodes, long empCode, long ouCode) {
        return delete(WebApiEndpoints.Voucher.root(SEGMENT),
                VoucherDeleteRequest.builder().empCode(empCode).connectedOUCode(ouCode).voucherCodes(vchcodes).build(),
                VoucherActionResponse.class);
    }

    /** {@link #revertGeneralVoucher} for one voucher, throwing {@link ApiException} when the app reports a failure in {@code item3}. */
    public void revertGeneralVoucherOrThrow(long vchcode, long empCode, long ouCode) {
        requireAllSucceeded("revert", vchcode, revertGeneralVoucher(List.of(vchcode), empCode, ouCode));
    }

    /** {@link #releaseGeneralVoucher} for one voucher, throwing {@link ApiException} on a reported failure. */
    public void releaseGeneralVoucherOrThrow(long vchcode, long empCode, long ouCode) {
        requireAllSucceeded("release", vchcode, releaseGeneralVoucher(List.of(vchcode), empCode, ouCode));
    }

    /** {@link #deleteGeneralVouchers} for one voucher, throwing {@link ApiException} on a reported failure. */
    public void deleteGeneralVoucherOrThrow(long vchcode, long empCode, long ouCode) {
        requireAllSucceeded("delete", vchcode, deleteGeneralVouchers(List.of(vchcode), empCode, ouCode));
    }

    private static VoucherActionRequest action(List<Long> vchcodes, long empCode, long ouCode) {
        return VoucherActionRequest.builder().code(vchcodes).empCode(empCode).connectedOUCode(ouCode).build();
    }

    private static void requireAllSucceeded(String what, long vchcode, VoucherActionResponse response) {
        if (response == null || !response.allSucceeded()) {
            throw new ApiException("General Voucher " + what + " of vchcode=" + vchcode + " was not successful: "
                    + (response == null ? "no response" : response.failureMessages()
                    + (response.getError() != null ? " error=" + response.getError() : "")), 200);
        }
    }

    // ───────────────────────────── read ─────────────────────────────

    /** {@code POST /vouchers/generalvouchers} — list/search; {@code filter} is the JSON-string filter (may be null). */
    public List<GeneralVoucherListItem> listGeneralVouchers(GeneralVoucherListRequest request) {
        return postRaw(WebApiEndpoints.Voucher.root(LIST_SEGMENT), request).as(GeneralVoucherListResponse.class).items();
    }

    /** The list row whose {@code voucherNumber} equals {@code voucherNumber}, or null when none (e.g. after a delete). */
    public GeneralVoucherListItem findByVoucherNumber(String voucherNumber, long connectedSite, long connectedUser,
                                                      long ouCode, String workDate) {
        GeneralVoucherListRequest request = GeneralVoucherListRequest.builder()
                .connectedSite(connectedSite).connectedUser(connectedUser).connectedSiteOuCode(ouCode)
                .workDate(workDate).filter(GeneralVoucherListRequest.voucherNumberEquals(voucherNumber)).build();
        List<GeneralVoucherListItem> rows = listGeneralVouchers(request);
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }

    /** {@code GET /vouchers/generalvouchers/rootview?code=} — the full voucher incl. {@code dataVersion}s needed to edit it. */
    public GeneralVoucherRootView fetchRootView(long vchcode, long ouCode, long userCode) {
        return getRawQuery(WebApiEndpoints.Voucher.rootview(LIST_SEGMENT), new RootviewQuery(vchcode, ouCode, userCode))
                .as(GeneralVoucherRootViewResponse.class).getResult();
    }

    // ───────────────────────────── Add-form lookups ─────────────────────────────

    /** {@code GET /docnumberschemes?menusysshortcode=FinanceGenVoucher} (all pages). */
    public List<DocNumberSchemeEntry> fetchDocNumberSchemes(int connectedSite, String workDate) {
        return getAllPages(WebApiEndpoints.Voucher.DOC_NUMBER_SCHEMES,
                DocNumberSchemeQuery.builder().connectedSite(connectedSite).menuShortCode(MENU_SHORT_CODE).workDate(workDate).build(),
                100, DocNumberSchemeEntry.class);
    }

    /** {@code GET /ledgers/cashbankledgers?module=generalvoucher} — header Cash/Bank ledgers ({@code sourceType} B/C). */
    public List<CashBankLedgerLookupResult> fetchCashBankLedgers(int connectedSite, long userCode, int ouCode, String workDate) {
        return getAllPages(WebApiEndpoints.Voucher.CASH_BANK_LEDGERS,
                CashBankLedgerQuery.forModule(MODULE, connectedSite, userCode, ouCode, workDate, SITE_TYPE).build(), 50, CashBankLedgerLookupResult.class);
    }

    /** {@code GET /ledgers?module=generalvoucher} — the GL lookup of the Expense/Income grid (all pages, up to 1000 rows). */
    public List<GlLedgerLookupResult> fetchLedgers(int connectedSite, long userCode, int ouCode, String workDate) {
        List<GlLedgerLookupResult> items = getAllPages(WebApiEndpoints.Voucher.LEDGERS,
                GlLedgerQuery.forModule(MODULE, connectedSite, userCode, ouCode, workDate, SITE_TYPE).build(), 1000, GlLedgerLookupResult.class);
        logLedgerShape(items, connectedSite, userCode, ouCode);
        return items;
    }

    /**
     * Diagnostic for the GL lookup: HTTP status, item count and a histogram of the attributes the fixtures filter on, so a
     * "0 usable GL(s)" skip shows whether the list was empty, had null attributes, or was simply filtered out. When the list
     * is empty the start of the raw body is logged.
     */
    private static void logLedgerShape(List<GlLedgerLookupResult> items, int site, long user, int ou) {
        int n = items == null ? 0 : items.size();
        Map<String, Long> hist = new java.util.TreeMap<>();
        if (items != null) {
            for (GlLedgerLookupResult g : items) {
                hist.merge("type=" + g.getType() + "/src=" + g.getSourceType() + "/site=" + g.getSiteApplicable()
                        + "/sl=" + g.getSubLedgerApplicable(), 1L, Long::sum);
            }
        }
        ReportLogger.warn("[GeneralVoucherApiClient] /ledgers site=" + site + " ou=" + ou + " user=" + user + " → "
                + n + " item(s); shapes=" + hist);
        if (n == 0) {
            ReportLogger.warn("[GeneralVoucherApiClient] /ledgers returned no items for this user/site/OU.");
        }
    }

    /** {@code GET /subledgers?module=generalvoucher&parentGLCode=} — the sub-ledgers permitted for a sub-ledger-applicable GL. */
    public List<SubLedgerLookupResult> fetchSubLedgers(long parentGlCode, int connectedSite, long userCode, int ouCode, String workDate) {
        return getAllPages(WebApiEndpoints.Voucher.SUB_LEDGERS,
                ParentGlSubLedgerQuery.forModule(MODULE, connectedSite, userCode, ouCode, workDate, SITE_TYPE, parentGlCode).build(),
                100, SubLedgerLookupResult.class);
    }

    /**
     * {@code GET /sites?target=costcentertagging&glCode=[&slCode=]} — sites selectable for a line's allocation. Pages through the
     * result (50 per page, as the UI does) until {@code wanted} sites of OU {@code ouCode} are found or the list is exhausted.
     */
    public List<CostCenterSiteLookupResult> fetchCostCenterSites(long glCode, Long slCode, int ouCode, int connectedSite,
                                                                 long userCode, String workDate, int wanted) {
        List<CostCenterSiteLookupResult> found = new java.util.ArrayList<>();
        CostCenterSiteQuery query = CostCenterSiteQuery.forModule(MODULE, connectedSite, userCode, ouCode, workDate, SITE_TYPE, glCode, slCode).build();
        int page = 1;
        int total = Integer.MAX_VALUE;
        while (found.size() < wanted && (page - 1) * 50 < total && page <= 40) {
            Map<String, Object> params = QueryParams.toMap(query);
            params.put("page", page);
            params.put("start", (page - 1) * 50);
            params.put("limit", 50);
            Response response = getRaw(WebApiEndpoints.Voucher.SITES, params);
            List<CostCenterSiteLookupResult> items = response.jsonPath().getList("result.items", CostCenterSiteLookupResult.class);
            total = response.jsonPath().getInt("result.totalCount");
            if (items == null || items.isEmpty()) break;
            for (CostCenterSiteLookupResult s : items) {
                boolean sameOu = s.getOuCode() == null || s.getOuCode() == ouCode;
                boolean blocked = "Y".equalsIgnoreCase(s.getBlockFin());
                if (sameOu && !blocked) found.add(s);
            }
            page++;
        }
        return found;
    }

    /** {@code GET /cheques?module=generalvoucher&glCode=<bankGl>} — the usable cheque leaves of a bank GL. */
    public List<ChequeLookupResult> fetchCheques(long bankGlCode, String workDate) {
        Map<String, Object> params = QueryParams.toMap(new ChequeQuery(MODULE, bankGlCode, workDate));
        params.put("page", 1);
        params.put("start", 0);
        params.put("limit", 100);
        return getRaw(WebApiEndpoints.Voucher.CHEQUES, params).jsonPath().getList("result.items", ChequeLookupResult.class);
    }

    /** {@code POST /vouchers/general/validations/voucherdate} — throws {@link ApiException} when the date is rejected. */
    public void validateVoucherDate(long empCode, String voucherDate, long ouCode, long voucherCode) {
        validateVoucherDate(empCode, voucherDate, ouCode, voucherCode, 0);
    }

    /**
     * As above for an EXISTING voucher: {@code oldYcode} is the voucher's current accounting year ({@code GeneralVoucherRootView#yCode}) —
     * without it the application compares the new date against year 0 and rejects every date with "cannot be changed to a new accounting
     * year" (confirmed live), so an edit-date check must pass it.
     */
    public void validateVoucherDate(long empCode, String voucherDate, long ouCode, long voucherCode, int oldYcode) {
        VoucherDateValidationRequest request = VoucherDateValidationRequest.builder()
                .oldYcode(oldYcode)
                .voucherDate(voucherDate).empCode(empCode).connectedOUCode(ouCode)
                .voucherCode((int) voucherCode).isNewEntity(voucherCode == 0).build();
        postRaw(WebApiEndpoints.Voucher.validateVoucherDate(SEGMENT), request);
    }

    /** True when the application's own voucher-date validation accepts {@code date} for {@code voucherCode} ({@code 0} = new voucher). */
    public boolean isVoucherDateAccepted(long empCode, LocalDate date, long ouCode, long voucherCode, int oldYcode) {
        try {
            validateVoucherDate(empCode, workDate(date), ouCode, voucherCode, oldYcode);
            return true;
        } catch (ApiException rejected) {
            return false;
        }
    }

    /**
     * The contiguous run of dates the application accepts around {@code anchor} when EDITING {@code voucherCode} (whose current accounting
     * year is {@code oldYcode}), found purely by probing {@link #validateVoucherDate} — there is no accounting-year API. Under
     * {@code db.available=no} this replaces the {@code admyear}/{@code admmonth} reads the DB data sources use to pick first/last/
     * cross-year/future edit dates. A NEW voucher (code 0) is not year-bounded by the validator, so only existing vouchers are meaningful.
     */
    public com.gsl.utils.ValidDateRangeFinder.Range findValidVoucherDateRange(long empCode, LocalDate anchor, long ouCode, long voucherCode,
                                                                              int oldYcode) {
        return com.gsl.utils.ValidDateRangeFinder.find(anchor, d -> isVoucherDateAccepted(empCode, d, ouCode, voucherCode, oldYcode), 400);
    }

    /** Fetches the UDF metadata of {@code FINVCHMAIN} and builds the {@code udfValues}/{@code udfDetails} list. */
    public List<UdfValueEntry> fetchAndBuildUdfValues() {
        return UdfValueBuilder.fetchAndBuild(this, "FINVCHMAIN");
    }
}
