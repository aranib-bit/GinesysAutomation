package com.gsl.service;

import com.gsl.api.client.udf.UdfValueBuilder;
import com.gsl.api.webapi.factory.GeneralVoucherRequestFactory;
import com.gsl.api.webapi.client.ChequeBookApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.core.QueryParams;
import com.gsl.api.webapi.WebApiEndpoints;
import com.gsl.api.webapi.model.request.voucher.CashBankLedgerQuery;
import com.gsl.api.webapi.model.request.voucher.ChequeQuery;
import com.gsl.api.webapi.model.request.voucher.CostCenterSiteQuery;
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
import com.gsl.api.client.GeneralVoucherApiClient;

/**
 * General Voucher workflows: owner-site discovery (the first site whose Cash/Bank lookup offers a ledger of a type) and the defaults-based
 * voucher creation used by the backfills. The client ({@link GeneralVoucherApiClient}) only makes the HTTP calls.
 */
public final class GeneralVoucherService {

    private static final String MENU_SHORT_CODE = "FinanceGenVoucher";
    private static final String DOC_SCHEME_NAME = "General Voucher";

    private final GeneralVoucherApiClient client;

    public GeneralVoucherService(GeneralVoucherApiClient client) {
        this.client = client;
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
     *         the skip message via the exception thrown by callers)
     */
    public OwnerSiteChoice findOwnerSiteWithCashBank(UserSiteContext ctx, long userCode, String wantedSourceType, String workDate) {
        List<UserSiteContext.Site> candidates = ctx.getSites() == null ? List.of() : ctx.getSites().stream()
                .filter(x -> "OS-OO-CM".equalsIgnoreCase(x.getSiteType()))
                .sorted(java.util.Comparator.comparingLong(UserSiteContext.Site::getSiteCode))
                .toList();
        String key = ConfigFactory.getApiConfig().financeApiBaseUri() + "|" + userCode + "|" + wantedSourceType;
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
        List<CashBankLedgerLookupResult> all = client.fetchCashBankLedgers((int) site.getSiteCode(), userCode, (int) site.getOuCode(), workDate);
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
        GeneralVoucherRequestFactory.Inputs inputs = resolve(accountType, ownerSiteCode, ouCode, empCode, release);
        GeneralVoucherCreateResponse response = client.createGeneralVoucher(GeneralVoucherRequestFactory.simple(inputs, String.valueOf(System.nanoTime())));
        ReportLogger.dataBackfill("[GeneralVoucherService] Created GV {} (vchcode={}) type={} gl='{}'",
                response.getDocScheme(), response.getVoucherCode(), accountType, inputs.gl().getGlName());
        return response;
    }

    /**
     * {@code count} simple one-line create requests for the k6 bulk hand-over: every lookup runs once, each request gets its own request id,
     * nothing is posted. Same resolution and building as {@link #createGeneralVoucherWithDefaults}.
     */
    public List<GeneralVoucherCreateRequest> buildBulkRequests(GlAccountType accountType, int ownerSiteCode, int ouCode, long empCode, boolean release, int count) {
        GeneralVoucherRequestFactory.Inputs inputs = resolve(accountType, ownerSiteCode, ouCode, empCode, release);
        long base = System.nanoTime();
        List<GeneralVoucherCreateRequest> out = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) out.add(GeneralVoucherRequestFactory.simple(inputs, String.valueOf(base + i)));
        return out;
    }

    /** Runs every lookup a simple General Voucher create needs (owner site, Cash/Bank header, a Type1/Type2 ledger, doc scheme, UDFs, date check). */
    private GeneralVoucherRequestFactory.Inputs resolve(GlAccountType accountType, int ownerSiteCode, int ouCode, long empCode, boolean release) {
        UserSiteContext ctx = client.fetchUserContext();
        String workDate = GeneralVoucherApiClient.workDate(LocalDate.now());
        UserSiteContext.Site site;
        CashBankLedgerLookupResult header;
        if (ownerSiteCode > 0) {
            site = ctx.getSites().stream().filter(s -> s.getSiteCode() == ownerSiteCode).findFirst().orElse(ctx.firstOwnerSite());
            if (site == null) {
                throw new ApiException("User has no OS-OO-CM site assigned — cannot raise a General Voucher.", 0);
            }
            List<CashBankLedgerLookupResult> cashBank = client.fetchCashBankLedgers((int) site.getSiteCode(), empCode, (int) site.getOuCode(), workDate);
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

        String typeCode = accountType.code();
        List<GlLedgerLookupResult> generalOfType = client.fetchLedgers(siteCode, empCode, ou, workDate).stream()
                .filter(l -> typeCode.equalsIgnoreCase(l.getType()) && "G".equalsIgnoreCase(l.getSourceType())
                        && !l.isSubLedgerApplicable())
                .toList();
        // Prefer Type1 (no site, no sub-ledger); fall back to Type2 (site only) with a cost-centre allocation, since some
        // environments (e.g. clone-staging) have no Type1 Expense/Income GL at all.
        GlLedgerLookupResult gl = generalOfType.stream().filter(l -> !l.isSiteApplicable()).findFirst().orElse(null);
        List<CostCenterAllocation> allocations = null;
        if (gl == null) {
            for (GlLedgerLookupResult candidate : generalOfType) {
                List<CostCenterSiteLookupResult> sites = client.fetchCostCenterSites(candidate.getGlCode(), null, ou, siteCode, empCode, workDate, 1);
                if (sites != null && !sites.isEmpty()) {
                    CostCenterSiteLookupResult s = sites.get(0);
                    gl = candidate;
                    allocations = List.of(CostCenterAllocation.builder().code(s.getCode()).name(s.getName()).siteType(s.getSiteType())
                            .shortName(s.getShortName()).amount(1).ouCode(s.getOuCode() == null ? ou : s.getOuCode()).build());
                    break;
                }
            }
        }
        if (gl == null) {
            throw new ApiException("No Type1 (no site, no sub-ledger) nor Type2 (site-only, with an allocatable site) General ledger of type "
                    + typeCode + " found (" + generalOfType.size() + " non-sub-ledger GL(s) of that type) — cannot raise a General Voucher of account type "
                    + accountType + ".", 0);
        }

        List<DocNumberSchemeEntry> schemes = client.fetchDocNumberSchemes(siteCode, workDate);
        DocNumberSchemeEntry scheme = schemes.stream().filter(s -> DOC_SCHEME_NAME.equalsIgnoreCase(s.getName())).findFirst()
                .orElseGet(() -> schemes.isEmpty() ? null : schemes.get(0));
        if (scheme == null) {
            throw new ApiException("No document scheme returned for " + MENU_SHORT_CODE, 0);
        }

        boolean debit = accountType == GlAccountType.ASSET || accountType == GlAccountType.EXPENSE;
        List<UdfValueEntry> udf = client.fetchAndBuildUdfValues();
        client.validateVoucherDate(empCode, workDate, ou, 0);
        return new GeneralVoucherRequestFactory.Inputs(site, ou, empCode, header, scheme.getCode(), gl, allocations, debit, workDate, release, udf);
    }
}
