package com.gsl.api.client;

import com.gsl.api.client.udf.UdfValueBuilder;
import com.gsl.api.core.ApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.model.arvoucher.CashBankLedgerLookupResult;
import com.gsl.api.model.arvoucher.DocNumberSchemeEntry;
import com.gsl.api.model.arvoucher.SubLedgerLookupResult;
import com.gsl.api.model.arvoucher.VoucherDateValidationRequest;
import com.gsl.api.model.common.UserSiteContext;
import com.gsl.api.model.generalvoucher.ChequeLookupResult;
import com.gsl.api.model.generalvoucher.CostCenterSiteLookupResult;
import com.gsl.api.model.generalvoucher.GeneralVoucherActionRequest;
import com.gsl.api.model.generalvoucher.GeneralVoucherActionResponse;
import com.gsl.api.model.generalvoucher.GeneralVoucherCreateRequest;
import com.gsl.api.model.generalvoucher.GeneralVoucherCreateResponse;
import com.gsl.api.model.generalvoucher.GeneralVoucherDeleteRequest;
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
 * {@link GeneralVoucherActionResponse}; the {@code *OrThrow} variants turn those into {@link ApiException}.
 */
public class GeneralVoucherApiClient extends ApiClient {

    private static final String MODULE = "generalvoucher";
    private static final String SITE_TYPE = "OS-OO-CM";
    private static final String MENU_SHORT_CODE = "FinanceGenVoucher";
    private static final String DOC_SCHEME_NAME = "General Voucher";

    /** Wraps an already-authenticated session {@code spec} — see {@link ApiClient}'s class Javadoc. */
    public GeneralVoucherApiClient(RequestSpecification spec) {
        super(spec);
    }

    /** The Finance API base ({@code financeApiBaseUri}, i.e. {@code …/Finance/api}); overridable so unit tests can point at a local server. */
    protected String financeBase() {
        return ConfigFactory.getApiConfig().financeApiBaseUri();
    }

    /** Prefixes {@code path} with {@link #financeBase()}. */
    private String url(String path) {
        return financeBase() + path;
    }

    /** {@code yyyy-MM-ddT00:00:00} for {@code date}, the work-date format every Finance call expects. */
    public static String workDate(LocalDate date) {
        return date + "T00:00:00";
    }

    // ───────────────────────────── user context ─────────────────────────────

    /** {@code POST /WebAPI/api/UserRolePolicy/GetAllSiteForAUser?userId=0} (body {@code {}}) — assigned sites, OU, profile flags. */
    public UserSiteContext fetchUserContext() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("userId", 0);
        Response response = postRaw("/UserRolePolicy/GetAllSiteForAUser", Map.of(), params);
        return response.jsonPath().getObject("result", UserSiteContext.class);
    }

    // ───────────────────────────── create / edit / actions ─────────────────────────────

    /** {@code POST /vouchers/general} — create. */
    public GeneralVoucherCreateResponse createGeneralVoucher(GeneralVoucherCreateRequest request) {
        return post(url("/vouchers/general"), request, GeneralVoucherCreateResponse.class);
    }

    /** Negative-scenario counterpart to {@link #createGeneralVoucher} — returns the raw response without throwing on failure. */
    public Response createGeneralVoucherExpectFailure(GeneralVoucherCreateRequest request) {
        return postRawExpectFailure(url("/vouchers/general"), request);
    }

    /** {@code PUT /vouchers/general} — edit an <b>Unposted</b> voucher; {@code request} needs {@code code}, {@code docNumber}, {@code dataVersion} and line codes/versions. */
    public GeneralVoucherCreateResponse updateGeneralVoucher(GeneralVoucherCreateRequest request) {
        return put(url("/vouchers/general"), request, GeneralVoucherCreateResponse.class);
    }

    /** {@code PUT /vouchers/general/revert} — Unpost. Inspect {@link GeneralVoucherActionResponse#allSucceeded()}. */
    public GeneralVoucherActionResponse revertGeneralVoucher(List<Long> vchcodes, long empCode, long ouCode) {
        return put(url("/vouchers/general/revert"), action(vchcodes, empCode, ouCode), GeneralVoucherActionResponse.class);
    }

    /** {@code PUT /vouchers/general/release} — Post. Inspect {@link GeneralVoucherActionResponse#allSucceeded()}. */
    public GeneralVoucherActionResponse releaseGeneralVoucher(List<Long> vchcodes, long empCode, long ouCode) {
        return put(url("/vouchers/general/release"), action(vchcodes, empCode, ouCode), GeneralVoucherActionResponse.class);
    }

    /** {@code DELETE /vouchers/general} (body {@code voucherCodes}) — the vouchers must be Unposted. */
    public GeneralVoucherActionResponse deleteGeneralVouchers(List<Long> vchcodes, long empCode, long ouCode) {
        return delete(url("/vouchers/general"),
                GeneralVoucherDeleteRequest.builder().empCode(empCode).connectedOUCode(ouCode).voucherCodes(vchcodes).build(),
                GeneralVoucherActionResponse.class);
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

    private static GeneralVoucherActionRequest action(List<Long> vchcodes, long empCode, long ouCode) {
        return GeneralVoucherActionRequest.builder().code(vchcodes).empCode(empCode).connectedOUCode(ouCode).build();
    }

    private static void requireAllSucceeded(String what, long vchcode, GeneralVoucherActionResponse response) {
        if (response == null || !response.allSucceeded()) {
            throw new ApiException("General Voucher " + what + " of vchcode=" + vchcode + " was not successful: "
                    + (response == null ? "no response" : response.failureMessages()
                    + (response.getError() != null ? " error=" + response.getError() : "")), 200);
        }
    }

    // ───────────────────────────── read ─────────────────────────────

    /** {@code POST /vouchers/generalvouchers} — list/search; {@code filter} is the JSON-string filter (may be null). */
    public List<GeneralVoucherListItem> listGeneralVouchers(GeneralVoucherListRequest request) {
        Response response = postRaw(url("/vouchers/generalvouchers"), request);
        return response.jsonPath().getList("result.items", GeneralVoucherListItem.class);
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
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("code", vchcode);
        params.put("connectedOUCode", ouCode);
        params.put("connectedUserCode", userCode);
        Response response = getRaw(url("/vouchers/generalvouchers/rootview"), params);
        return response.jsonPath().getObject("result", GeneralVoucherRootView.class);
    }

    // ───────────────────────────── Add-form lookups ─────────────────────────────

    /** {@code GET /docnumberschemes?menusysshortcode=FinanceGenVoucher}. */
    public List<DocNumberSchemeEntry> fetchDocNumberSchemes(int connectedSite, String workDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("connectedsite", connectedSite);
        params.put("menusysshortcode", MENU_SHORT_CODE);
        params.put("page", 1);
        params.put("limit", 25);
        params.put("workDate", workDate);
        params.put("start", 0);
        Response response = getRaw(url("/docnumberschemes"), params);
        return response.jsonPath().getList("result.items", DocNumberSchemeEntry.class);
    }

    /** {@code GET /ledgers/cashbankledgers?module=generalvoucher} — header Cash/Bank ledgers ({@code sourceType} B/C). */
    public List<CashBankLedgerLookupResult> fetchCashBankLedgers(int connectedSite, long userCode, int ouCode, String workDate) {
        Map<String, Object> params = lookupParams(connectedSite, userCode, ouCode, workDate);
        params.put("query", "");
        params.put("queryFieldName", List.of("glName", "abbreviation"));
        params.put("page", 1);
        params.put("start", 0);
        params.put("limit", 50);
        params.put("sort", "glName ASC");
        Response response = getRaw(url("/ledgers/cashbankledgers"), params);
        return response.jsonPath().getList("result.items", CashBankLedgerLookupResult.class);
    }

    /** {@code GET /ledgers?module=generalvoucher} — the GL lookup of the Expense/Income grid (all pages, up to 1000 rows). */
    public List<GlLedgerLookupResult> fetchLedgers(int connectedSite, long userCode, int ouCode, String workDate) {
        Map<String, Object> params = lookupParams(connectedSite, userCode, ouCode, workDate);
        params.put("query", "");
        params.put("queryFieldName", List.of("glName", "abbreviation", "groupName", "siteApplicable", "subLedgerApplicable"));
        params.put("pagingNotRequired", true);
        params.put("page", 1);
        params.put("start", 0);
        params.put("limit", 1000);
        Response response = getRaw(url("/ledgers"), params);
        List<GlLedgerLookupResult> items = response.jsonPath().getList("result.items", GlLedgerLookupResult.class);
        logLedgerShape(response, items, connectedSite, userCode, ouCode);
        return items;
    }

    /**
     * Diagnostic for the GL lookup: HTTP status, item count and a histogram of the attributes the fixtures filter on, so a
     * "0 usable GL(s)" skip shows whether the list was empty, had null attributes, or was simply filtered out. When the list
     * is empty the start of the raw body is logged.
     */
    private static void logLedgerShape(Response response, List<GlLedgerLookupResult> items, int site, long user, int ou) {
        int n = items == null ? 0 : items.size();
        Map<String, Long> hist = new java.util.TreeMap<>();
        if (items != null) {
            for (GlLedgerLookupResult g : items) {
                hist.merge("type=" + g.getType() + "/src=" + g.getSourceType() + "/site=" + g.getSiteApplicable()
                        + "/sl=" + g.getSubLedgerApplicable(), 1L, Long::sum);
            }
        }
        ReportLogger.warn("[GeneralVoucherApiClient] /ledgers site=" + site + " ou=" + ou + " user=" + user + " → HTTP "
                + response.statusCode() + ", " + n + " item(s); shapes=" + hist);
        if (n == 0) {
            String body = response.asString();
            ReportLogger.warn("[GeneralVoucherApiClient] /ledgers empty — raw body: "
                    + (body == null ? "null" : body.substring(0, Math.min(body.length(), 600))));
        }
    }

    /** {@code GET /subledgers?module=generalvoucher&parentGLCode=} — the sub-ledgers permitted for a sub-ledger-applicable GL. */
    public List<SubLedgerLookupResult> fetchSubLedgers(long parentGlCode, int connectedSite, long userCode, int ouCode, String workDate) {
        Map<String, Object> params = lookupParams(connectedSite, userCode, ouCode, workDate);
        params.put("query", "");
        params.put("parentGLCode", parentGlCode);
        params.put("queryFieldName", List.of("slName", "className"));
        params.put("page", 1);
        params.put("start", 0);
        params.put("limit", 100);
        Response response = getRaw(url("/subledgers"), params);
        return response.jsonPath().getList("result.items", SubLedgerLookupResult.class);
    }

    /**
     * {@code GET /sites?target=costcentertagging&glCode=[&slCode=]} — sites selectable for a line's allocation. Pages through the
     * result (50 per page, as the UI does) until {@code wanted} sites of OU {@code ouCode} are found or the list is exhausted.
     */
    public List<CostCenterSiteLookupResult> fetchCostCenterSites(long glCode, Long slCode, int ouCode, int connectedSite,
                                                                 long userCode, String workDate, int wanted) {
        List<CostCenterSiteLookupResult> found = new java.util.ArrayList<>();
        int page = 1;
        int total = Integer.MAX_VALUE;
        while (found.size() < wanted && (page - 1) * 50 < total && page <= 40) {
            Map<String, Object> params = lookupParams(connectedSite, userCode, ouCode, workDate);
            params.put("query", "");
            params.put("queryFieldName", List.of("name", "shortName", "siteType", "city"));
            params.put("glCode", glCode);
            if (slCode != null) params.put("slCode", slCode);
            params.put("connectedoucode", ouCode);
            params.put("target", "costcentertagging");
            params.put("page", page);
            params.put("start", (page - 1) * 50);
            params.put("limit", 50);
            params.put("sort", "name ASC");
            Response response = getRaw(url("/sites"), params);
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
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("module", MODULE);
        params.put("glCode", bankGlCode);
        params.put("workDate", workDate);
        params.put("page", 1);
        params.put("start", 0);
        params.put("limit", 100);
        Response response = getRaw(url("/cheques"), params);
        return response.jsonPath().getList("result.items", ChequeLookupResult.class);
    }

    /** {@code POST /vouchers/general/validations/voucherdate} — throws {@link ApiException} when the date is rejected. */
    public void validateVoucherDate(long empCode, String voucherDate, long ouCode, long voucherCode) {
        VoucherDateValidationRequest request = VoucherDateValidationRequest.builder()
                .voucherDate(voucherDate).empCode(empCode).connectedOUCode(ouCode)
                .voucherCode((int) voucherCode).isNewEntity(voucherCode == 0).build();
        postRaw(url("/vouchers/general/validations/voucherdate"), request);
    }

    /** Fetches the UDF metadata of {@code FINVCHMAIN} and builds the {@code udfValues}/{@code udfDetails} list. */
    public List<UdfValueEntry> fetchAndBuildUdfValues() {
        return UdfValueBuilder.fetchAndBuild(this, "FINVCHMAIN");
    }

    private static Map<String, Object> lookupParams(int connectedSite, long userCode, int ouCode, String workDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("searchText", "");
        params.put("module", MODULE);
        params.put("connectedSite", connectedSite);
        params.put("connectedUserCode", userCode);
        params.put("connectedOUCode", ouCode);
        params.put("connectedSiteOUCode", ouCode);
        params.put("connectedSiteType", SITE_TYPE);
        params.put("workDate", workDate);
        params.put("pagingNotRequired", false);
        return params;
    }


    // ───────────────────────────── owner-site selection ─────────────────────────────

    /** Upper bound on sites probed while looking for an owner site that has a Cash/Bank ledger. */
    private static final int MAX_OWNER_SITE_PROBES = 60;

    private static final java.util.concurrent.ConcurrentHashMap<String, Long> OWNER_SITE_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** The owner site chosen for a Cash/Bank type, the ledgers it offers and the sites probed on the way (for the report). */
    public record OwnerSiteChoice(UserSiteContext.Site site, List<CashBankLedgerLookupResult> ledgers, List<String> probed) {}

    /**
     * Finds the user's first {@code OS-OO-CM} site (ascending code) whose Cash/Bank lookup offers a ledger of
     * {@code wantedSourceType} ({@code "B"} or {@code "C"}). Most sites own no Cash/Bank ledger (the lookup then returns an empty
     * list), so the first assigned site is not usable in general. The chosen site code is cached per environment/user/type.
     *
     * @return the choice, or {@code null} when none of the probed sites qualifies ({@code probed} is then not available; use
     *         {@link #describeProbes} via the exception thrown by callers)
     */
    public OwnerSiteChoice findOwnerSiteWithCashBank(UserSiteContext ctx, long userCode, String wantedSourceType, String workDate) {
        List<UserSiteContext.Site> candidates = ctx.getSites() == null ? List.of() : ctx.getSites().stream()
                .filter(x -> "OS-OO-CM".equalsIgnoreCase(x.getSiteType()))
                .sorted(java.util.Comparator.comparingLong(UserSiteContext.Site::getSiteCode))
                .toList();
        String key = financeBase() + "|" + userCode + "|" + wantedSourceType;
        Long cached = OWNER_SITE_CACHE.get(key);
        List<String> probed = new java.util.ArrayList<>();
        if (cached != null) {
            for (UserSiteContext.Site site : candidates) {
                if (site.getSiteCode() == cached) {
                    List<CashBankLedgerLookupResult> ledgers = ledgersOfType(site, userCode, wantedSourceType, workDate);
                    if (!ledgers.isEmpty()) return new OwnerSiteChoice(site, ledgers, probed);
                }
            }
            OWNER_SITE_CACHE.remove(key);
        }
        int probes = 0;
        for (UserSiteContext.Site site : candidates) {
            if (probes++ >= MAX_OWNER_SITE_PROBES) break;
            List<CashBankLedgerLookupResult> ledgers = ledgersOfType(site, userCode, wantedSourceType, workDate);
            probed.add(site.getSiteName() + "(" + site.getSiteCode() + ")=" + ledgers.size());
            if (!ledgers.isEmpty()) {
                OWNER_SITE_CACHE.put(key, site.getSiteCode());
                return new OwnerSiteChoice(site, ledgers, probed);
            }
        }
        throw new org.testng.SkipException("Skipped due to lack of data: none of the " + probes + " probed OS-OO-CM site(s) of user "
                + userCode + " (of " + candidates.size() + " assigned) offers a " + ("B".equals(wantedSourceType) ? "BANK" : "CASH")
                + " ledger. Probed (site=ledgers): " + probed);
    }

    private List<CashBankLedgerLookupResult> ledgersOfType(UserSiteContext.Site site, long userCode, String sourceType, String workDate) {
        List<CashBankLedgerLookupResult> all = fetchCashBankLedgers((int) site.getSiteCode(), userCode, (int) site.getOuCode(), workDate);
        if (all == null) return List.of();
        return all.stream().filter(l -> sourceType.equalsIgnoreCase(l.getSourceType())).toList();
    }

    // ───────────────────────────── defaults-based backfill ─────────────────────────────

    /**
     * Creates and releases one simple Posted General Voucher whose single line is a Type1 (no site, no sub-ledger) GL of
     * {@code accountType}, paid against a Cash ledger — the data-preparation call {@code GeneralVoucherApiBackfillStrategy}
     * makes when no Posted voucher of that account type exists. Asset/Expense lines are debits ({@code expense}), Liability/Income
     * lines credits ({@code income}).
     *
     * @param ownerSiteCode owner site code, or {@code <= 0} to use the user's first {@code OS-OO-CM} site
     * @param ouCode        OU code, or {@code <= 0} to use that site's OU
     * @param empCode       the logged-in user's code ({@code ApiSessionBundle#getUserId()})
     */
    public GeneralVoucherCreateResponse createGeneralVoucherWithDefaults(GlAccountType accountType, int ownerSiteCode, int ouCode, long empCode) {
        return createGeneralVoucherWithDefaults(accountType, ownerSiteCode, ouCode, empCode, true);
    }

    /** As {@link #createGeneralVoucherWithDefaults(GlAccountType, int, int, long)}; {@code release=false} leaves the voucher Unposted. */
    public GeneralVoucherCreateResponse createGeneralVoucherWithDefaults(GlAccountType accountType, int ownerSiteCode, int ouCode,
                                                                          long empCode, boolean release) {
        UserSiteContext ctx = fetchUserContext();
        String workDate = workDate(LocalDate.now());
        UserSiteContext.Site site;
        CashBankLedgerLookupResult header;
        if (ownerSiteCode > 0) {
            site = ctx.getSites().stream().filter(s -> s.getSiteCode() == ownerSiteCode).findFirst().orElse(ctx.firstOwnerSite());
            if (site == null) {
                throw new ApiException("User has no OS-OO-CM site assigned — cannot raise a General Voucher.", 0);
            }
            List<CashBankLedgerLookupResult> cashBank = fetchCashBankLedgers((int) site.getSiteCode(), empCode, (int) site.getOuCode(), workDate);
            header = cashBank.stream().filter(l -> "C".equalsIgnoreCase(l.getSourceType())).findFirst()
                    .orElseGet(() -> cashBank.isEmpty() ? null : cashBank.get(0));
            if (header == null) {
                throw new ApiException("No Cash/Bank ledger available for site " + site.getSiteCode() + " — cannot raise a General Voucher.", 0);
            }
        } else {
            OwnerSiteChoice choice;
            try {
                choice = findOwnerSiteWithCashBank(ctx, empCode, "C", workDate);
            } catch (org.testng.SkipException noCash) {
                choice = findOwnerSiteWithCashBank(ctx, empCode, "B", workDate);
            }
            site = choice.site();
            header = choice.ledgers().get(0);
        }
        int ou = ouCode > 0 ? ouCode : (int) site.getOuCode();
        int siteCode = (int) site.getSiteCode();

        boolean cash = "C".equalsIgnoreCase(header.getSourceType());

        String typeCode = accountType.code();
        GlLedgerLookupResult gl = fetchLedgers(siteCode, empCode, ou, workDate).stream()
                .filter(l -> typeCode.equalsIgnoreCase(l.getType()) && "G".equalsIgnoreCase(l.getSourceType())
                        && !l.isSiteApplicable() && !l.isSubLedgerApplicable())
                .findFirst()
                .orElseThrow(() -> new ApiException("No Type1 (no site, no sub-ledger) General ledger of type " + typeCode
                        + " found — cannot raise a General Voucher of account type " + accountType + ".", 0));

        List<DocNumberSchemeEntry> schemes = fetchDocNumberSchemes(siteCode, workDate);
        DocNumberSchemeEntry scheme = schemes.stream().filter(s -> DOC_SCHEME_NAME.equalsIgnoreCase(s.getName())).findFirst()
                .orElseGet(() -> schemes.isEmpty() ? null : schemes.get(0));
        if (scheme == null) {
            throw new ApiException("No document scheme returned for " + MENU_SHORT_CODE, 0);
        }

        boolean debit = accountType == GlAccountType.ASSET || accountType == GlAccountType.EXPENSE;
        GeneralVoucherDetailLine line = GeneralVoucherDetailLine.builder()
                .glCode(gl.getGlCode()).glName(gl.getGlName()).siteApplicable("N").groupName(gl.getGroupName())
                .expense(debit ? 1 : 0).income(debit ? 0 : 1)
                .paymentMode(cash ? "H" : "C")
                .build();

        List<UdfValueEntry> udf = fetchAndBuildUdfValues();
        validateVoucherDate(empCode, workDate, ou, 0);
        GeneralVoucherCreateRequest request = GeneralVoucherCreateRequest.builder()
                .empCode(empCode).connectedOUCode(ou)
                .ownerSite(GeneralVoucherOwnerSite.builder().code(siteCode).name(site.getSiteName()).siteType(site.getSiteType())
                        .shortName(site.getShortName()).ouName(site.getOuName()).ouCode(site.getOuCode()).build())
                .docScheme(GeneralVoucherDocSchemeRef.builder().code(scheme.getCode()).build())
                .cashBankLedger(header)
                .voucherDate(workDate)
                .expenseIncomeDetail(List.of(line))
                .markedForSaveAndRelease(release)
                .requestId(String.valueOf(System.nanoTime()))
                .udfDetails(udf).udfValues(udf)
                .build();
        GeneralVoucherCreateResponse response = createGeneralVoucher(request);
        ReportLogger.dataBackfill("[GeneralVoucherApiClient] Created GV {} (vchcode={}) type={} gl='{}'",
                response.getDocScheme(), response.getVoucherCode(), accountType, gl.getGlName());
        return response;
    }
}
