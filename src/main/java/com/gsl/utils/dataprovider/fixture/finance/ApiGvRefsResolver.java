package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.api.client.GeneralVoucherApiClient;
import com.gsl.api.client.LedgerApiClient;
import com.gsl.api.client.SubLedgerOthersApiClient;
import com.gsl.api.integration.LedgerDataSetup;
import com.gsl.api.integration.SubLedgerOthersDataSetup;
import com.gsl.reports.ReportLogger;
import com.gsl.api.model.arvoucher.CashBankLedgerLookupResult;
import com.gsl.api.model.arvoucher.DocNumberSchemeEntry;
import com.gsl.api.model.arvoucher.SubLedgerLookupResult;
import com.gsl.api.model.common.UserSiteContext;
import com.gsl.api.model.generalvoucher.CostCenterSiteLookupResult;
import com.gsl.api.model.generalvoucher.GlLedgerLookupResult;
import com.gsl.enums.CashBankType;
import com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType;
import com.gsl.model.finance.FinGl;
import com.gsl.utils.dataprovider.fixture.LiveGvDbRefs;
import com.gsl.utils.dataprovider.source.finance.common.DocSchemeMode;
import org.testng.SkipException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * API counterpart of {@code DbGeneralVoucherDataSource.loadFreshForScenario}'s reference resolution: produces the same
 * {@link LiveGvDbRefs} seam from Finance-API lookups instead of {@code fingl}/{@code finsl_gl}/{@code fingl_site} reads, so
 * {@code GeneralVoucherTemplateLoader.combinationTemplate} and everything downstream is reused unchanged.
 *
 * <p>The lookups are the ones the General Voucher Add form itself makes (Part F.12): header Cash/Bank ledgers, the
 * {@code module=generalvoucher} GL lookup (type, source type, site/sub-ledger applicability), GL-scoped sub-ledgers and the
 * cost-centre site picker. Every shortfall is a {@link SkipException} with the exact reason, never a silent default.
 */
public final class ApiGvRefsResolver {

    private static final String DOC_SCHEME_NAME = "General Voucher";

    private ApiGvRefsResolver() {}

    /** The calling API user's identity for the lookups (token user = {@code empCode}). */
    public record Caller(GeneralVoucherApiClient client, long empCode,
                         LedgerApiClient ledgerClient, SubLedgerOthersApiClient subLedgerOthersClient) {
        /** Lookup-only caller: no master-data backfill, a shortfall is a data-shortage skip. */
        public Caller(GeneralVoucherApiClient client, long empCode) {
            this(client, empCode, null, null);
        }
    }



    /** Owner site / OU the vouchers are created under. */
    public record OwnerContext(int siteCode, String siteName, int ouCode, String workDate) {}

    /**
     * Resolves the owner site: the user's first {@code OS-OO-CM} site (ascending code) whose Cash/Bank lookup offers a ledger of
     * {@code cashBankType} - most sites own none, so the first assigned site is not usable in general. Every probed site is logged.
     */
    public static OwnerContext resolveOwner(Caller caller, CashBankType cashBankType, List<String> log) {
        UserSiteContext ctx = caller.client().fetchUserContext();
        String workDate = GeneralVoucherApiClient.workDate(LocalDate.now());
        String wanted = cashBankType == CashBankType.BANK ? "B" : "C";
        GeneralVoucherApiClient.OwnerSiteChoice choice =
                caller.client().findOwnerSiteWithCashBank(ctx, caller.empCode(), wanted, workDate);
        UserSiteContext.Site site = choice.site();
        OwnerContext owner = new OwnerContext((int) site.getSiteCode(), site.getSiteName(), (int) site.getOuCode(), workDate);
        log.add("  Resolved API user code=" + caller.empCode() + " → owner site='" + owner.siteName() + "' (code="
                + owner.siteCode() + ", OU=" + owner.ouCode() + ") offering " + choice.ledgers().size() + " " + cashBankType
                + " ledger(s)" + (choice.probed().isEmpty() ? " [cached]" : "; probed " + choice.probed()));
        return owner;
    }

    /**
     * Resolves every reference one combination needs.
     *
     * @param variant the originating combination, carried into {@link LiveGvDbRefs#getVariant()} for naming/labelling
     */
    public static LiveGvDbRefs resolve(Caller caller, OwnerContext owner, GeneralVoucherFixtureOptions opts,
                                       GeneralVoucherVariant variant, List<String> log) {
        if (opts.isRequireCrossOuSite()) {
            throw new SkipException("Skipped — NEG_SITE_ALLOCATION_CROSS_OU needs per-site OU data that the API mode does not "
                    + "resolve yet (the cost-centre picker already hides cross-OU sites).");
        }
        if (opts.getDocSchemeMode() != DocSchemeMode.AUTO) {
            throw new SkipException("Skipped — doc-scheme mode " + opts.getDocSchemeMode() + " needs scheme capacity, which the "
                    + "API mode does not read yet.");
        }
        GeneralVoucherApiClient api = caller.client();

        // 1. Counter-leg Cash/Bank ledger
        CashBankType cashBankType = opts.cashBankType();
        String wantedSource = cashBankType == CashBankType.BANK ? "B" : "C";
        CashBankLedgerLookupResult cashBank = api
                .fetchCashBankLedgers(owner.siteCode(), caller.empCode(), owner.ouCode(), owner.workDate()).stream()
                .filter(l -> wantedSource.equalsIgnoreCase(l.getSourceType()))
                .findFirst()
                .orElseThrow(() -> new SkipException("Skipped due to lack of data: no " + cashBankType
                        + " ledger (sourceType=" + wantedSource + ") is offered to the API user at site '" + owner.siteName() + "'."));
        log.add("  Cash/Bank GL='" + cashBank.getGlName() + "' (glcode=" + cashBank.getGlCode() + ", " + cashBankType + ")");

        // 2. GL candidates for the exact (type, site-applicable, sub-ledger-applicable) shape
        GlLineType lineType = opts.getGlLineType();
        String siteApp = lineType.isSiteApplicable() ? "Y" : "N";
        String slApp = lineType.isSubLedgerApplicable() ? "Y" : "N";
        String typeCode = opts.getAccountType().code();
        int glCount = Math.max(1, opts.getGlCount());
        int siteCount = Math.max(1, opts.getSiteCount());

        List<GlLedgerLookupResult> fetchedGls = api.fetchLedgers(owner.siteCode(), caller.empCode(), owner.ouCode(), owner.workDate());
        List<LiveGvDbRefs.ResolvedGlRow> rows = new ArrayList<>();
        List<GlLedgerLookupResult> candidates = collectRows(api, caller, owner, fetchedGls, opts, typeCode, siteApp, slApp, lineType,
                glCount, siteCount, rows, log);

        // Shortfall -> create the missing ledger(s) through the API (same backfill the DB path has), re-read the lookup, retry.
        if (rows.size() < glCount && caller.ledgerClient() != null) {
            int missing = glCount - rows.size();
            log.add("  Shortfall of " + missing + " GL(s) for type=" + opts.getAccountType() + " (" + lineType + ", " + siteCount
                    + " site(s)) - creating via Ledger API");
            List<Long> assignedSites = assignedSiteCodes(api, owner);
            for (int i = 0; i < missing; i++) {
                try {
                    LedgerDataSetup.createLedgerForTest(caller.ledgerClient(), opts.getAccountType(), lineType.isSubLedgerApplicable(),
                            lineType.isSiteApplicable(), assignedSites, siteCount);
                } catch (RuntimeException e) {
                    ReportLogger.dataBackfillWarn("[ApiGvRefsResolver] Ledger backfill failed: {}", e.getMessage());
                    log.add("  Ledger backfill failed: " + e.getMessage());
                    break;
                }
            }
            fetchedGls = api.fetchLedgers(owner.siteCode(), caller.empCode(), owner.ouCode(), owner.workDate());
            rows.clear();
            candidates = collectRows(api, caller, owner, fetchedGls, opts, typeCode, siteApp, slApp, lineType,
                    glCount, siteCount, rows, log);
        }
        if (rows.size() < glCount) {
            List<GlLedgerLookupResult> allGls = fetchedGls == null ? List.of() : fetchedGls;
            long ofType = allGls.stream().filter(g -> typeCode.equalsIgnoreCase(g.getType())).count();
            long ofTypeGeneral = allGls.stream().filter(g -> typeCode.equalsIgnoreCase(g.getType()))
                    .filter(g -> g.getSourceType() == null || g.getSourceType().isBlank() || "G".equalsIgnoreCase(g.getSourceType())).count();
            throw new SkipException("Skipped due to lack of data: found " + rows.size() + " usable GL(s) for type="
                    + opts.getAccountType() + " (" + lineType + ", " + siteCount + " site(s) each) — need " + glCount + ". "
                    + "Ledger funnel at site '" + owner.siteName() + "': total=" + allGls.size() + ", type=" + typeCode + ":" + ofType
                    + ", +source G:" + ofTypeGeneral + ", +site/sub-ledger shape:" + candidates.size() + "."
                    + (caller.ledgerClient() == null ? "" : " Ledger backfill was attempted."));
        }

        // 3. Doc scheme — the General Voucher auto-numbered scheme (capacity is not readable by API; see Part F.7)
        List<DocNumberSchemeEntry> schemes = api.fetchDocNumberSchemes(owner.siteCode(), owner.workDate());
        DocNumberSchemeEntry scheme = schemes.stream()
                .filter(s -> !"Y".equalsIgnoreCase(s.getManual()))
                .filter(s -> DOC_SCHEME_NAME.equalsIgnoreCase(s.getName()))
                .findFirst()
                .or(() -> schemes.stream().filter(s -> !"Y".equalsIgnoreCase(s.getManual())).findFirst())
                .orElseThrow(() -> new SkipException("Skipped due to lack of data: no auto-numbered General Voucher doc scheme "
                        + "is offered to the API user."));
        log.add("  Doc scheme='" + scheme.getName() + "' (code=" + scheme.getCode() + ")");

        return LiveGvDbRefs.builder()
                .ownerSiteName(owner.siteName())
                .docSchemeName(scheme.getName())
                .cashBankName(cashBank.getGlName())
                .cashBankGlcode(cashBank.getGlCode())
                .cashBankType(cashBankType)
                .resolvedRows(rows)
                .variant(variant)
                .build();
    }

    /** Filters the lookup to the shape and resolves up to {@code glCount} rows into {@code rows}; returns the shape candidates. */
    private static List<GlLedgerLookupResult> collectRows(GeneralVoucherApiClient api, Caller caller, OwnerContext owner,
                                                          List<GlLedgerLookupResult> fetchedGls, GeneralVoucherFixtureOptions opts,
                                                          String typeCode, String siteApp, String slApp, GlLineType lineType,
                                                          int glCount, int siteCount,
                                                          List<LiveGvDbRefs.ResolvedGlRow> rows, List<String> log) {
        List<GlLedgerLookupResult> allGls = fetchedGls == null ? List.of() : fetchedGls;
        List<GlLedgerLookupResult> candidates = allGls.stream()
                .filter(g -> typeCode.equalsIgnoreCase(g.getType()))
                .filter(g -> g.getSourceType() == null || g.getSourceType().isBlank() || "G".equalsIgnoreCase(g.getSourceType()))
                .filter(g -> siteApp.equalsIgnoreCase(g.getSiteApplicable()) && slApp.equalsIgnoreCase(g.getSubLedgerApplicable()))
                // glSiteCount is always 0 in the /ledgers payload (HAR-confirmed), so it cannot gate site availability;
                // tryResolveRow proves it per GL through the cost-centre site lookup instead.
                // slCount is not a gate for Type3 either: a GL without a sub-ledger gets a Sub Ledger Others created by
                // tryResolveRow (API backfill); only the explicit "ambiguous sub-ledger" option still needs slCount > 1.
                .filter(g -> !opts.isRequireAmbiguousSubLedger() || g.getSlCount() > 1)
                // GLs that already have a sub-ledger first, so backfill only happens when nothing existing can serve.
                .sorted(java.util.Comparator.comparingInt(g -> g.getSlCount() > 0 ? 0 : 1))
                .collect(Collectors.toList());
        log.add("  GL candidates for type=" + opts.getAccountType() + " (" + lineType + "): " + candidates.size());
        for (GlLedgerLookupResult candidate : candidates) {
            if (rows.size() >= glCount) break;
            LiveGvDbRefs.ResolvedGlRow row = tryResolveRow(api, caller, owner, candidate, lineType, siteCount, log);
            if (row != null) rows.add(row);
        }
        return candidates;
    }

    /** The user's assigned sites in the owner OU ({@code admsite.code}), from the API user context. */
    private static List<Long> assignedSiteCodes(GeneralVoucherApiClient api, OwnerContext owner) {
        UserSiteContext ctx = api.fetchUserContext();
        List<Long> codes = new ArrayList<>();
        if (ctx != null && ctx.getSites() != null) {
            for (UserSiteContext.Site s : ctx.getSites()) {
                if (s.getOuCode() == owner.ouCode()) codes.add(s.getSiteCode());
            }
        }
        return codes;
    }

    /**
     * Creates a Sub Ledger Others for {@code gl} and waits for it to appear in the {@code /subledgers} lookup (no DB read, so it
     * works with db.available=no). Returns the refreshed lookup, empty when nothing could be created/seen.
     */
    private static List<SubLedgerLookupResult> backfillSubLedger(GeneralVoucherApiClient api, Caller caller, OwnerContext owner,
                                                                 GlLedgerLookupResult gl, List<String> log) {
        if (caller.subLedgerOthersClient() == null) return List.of();
        Long siteCode = null;
        if ("Y".equalsIgnoreCase(gl.getSiteApplicable())) {
            List<CostCenterSiteLookupResult> sites = api.fetchCostCenterSites(gl.getGlCode(), null, owner.ouCode(), owner.siteCode(),
                    caller.empCode(), owner.workDate(), 1);
            if (sites.isEmpty()) {
                log.add("  GL '" + gl.getGlName() + "' offers no site - cannot create a Sub Ledger for it");
                return List.of();
            }
            siteCode = sites.get(0).getCode();
        }
        try {
            SubLedgerOthersDataSetup.createSubLedgerOthersForTest(caller.subLedgerOthersClient(), gl.getGlCode(), gl.getGlName(), siteCode);
        } catch (RuntimeException e) {
            ReportLogger.dataBackfillWarn("[ApiGvRefsResolver] Sub Ledger Others backfill failed for GL '{}': {}", gl.getGlName(), e.getMessage());
            log.add("  Sub Ledger backfill failed for GL '" + gl.getGlName() + "': " + e.getMessage());
            return List.of();
        }
        List<SubLedgerLookupResult> subLedgers = ApiSubLedgerWait.awaitAtLeast(api, gl.getGlCode(), owner.siteCode(), caller.empCode(),
                owner.ouCode(), owner.workDate(), 1);
        if (!subLedgers.isEmpty()) return subLedgers;
        log.add("  Sub Ledger created for GL '" + gl.getGlName() + "' but not visible in /subledgers within "
                + ApiSubLedgerWait.VISIBLE_TIMEOUT_MS / 1000 + "s");
        return List.of();
    }

    /** One GL plus its sub-ledger (Type3) and allocation sites; {@code null} when this GL cannot supply what the shape needs. */
    private static LiveGvDbRefs.ResolvedGlRow tryResolveRow(GeneralVoucherApiClient api, Caller caller, OwnerContext owner,
                                                            GlLedgerLookupResult gl, GlLineType lineType, int siteCount,
                                                            List<String> log) {
        String subLedgerName = null;
        String subLedgerId = null;
        Long slCode = null;
        if (lineType == GlLineType.TYPE3_SITE_AND_SUBLEDGER) {
            List<SubLedgerLookupResult> subLedgers = api.fetchSubLedgers(gl.getGlCode(), owner.siteCode(), caller.empCode(),
                    owner.ouCode(), owner.workDate());
            if (subLedgers == null || subLedgers.isEmpty()) {
                subLedgers = backfillSubLedger(api, caller, owner, gl, log);
            }
            if (subLedgers == null || subLedgers.isEmpty()) {
                log.add("  GL '" + gl.getGlName() + "' has no permitted sub-ledger — skipped");
                return null;
            }
            SubLedgerLookupResult sl = subLedgers.get(0);
            subLedgerName = sl.getSlName();
            subLedgerId = sl.getSlid();
            slCode = sl.getSlCode();
        }
        List<String> siteNames = List.of();
        if (lineType.isSiteApplicable()) {
            List<CostCenterSiteLookupResult> sites = api.fetchCostCenterSites(gl.getGlCode(), slCode, owner.ouCode(),
                    owner.siteCode(), caller.empCode(), owner.workDate(), siteCount);
            if (sites.size() < siteCount) {
                log.add("  GL '" + gl.getGlName() + "' offers " + sites.size() + " same-OU site(s), need " + siteCount + " — skipped");
                return null;
            }
            siteNames = sites.stream().limit(siteCount).map(CostCenterSiteLookupResult::getName).collect(Collectors.toList());
        }
        FinGl finGl = new FinGl();
        finGl.setGlcode(gl.getGlCode());
        finGl.setGlname(gl.getGlName());
        finGl.setSlapp(gl.getSubLedgerApplicable());
        finGl.setCostapp(gl.getSiteApplicable());
        finGl.setType(gl.getType());
        log.add("  Selected GL='" + gl.getGlName() + "' (glcode=" + gl.getGlCode() + ")"
                + (subLedgerName != null ? ", Sub Ledger='" + subLedgerName + "'" : "")
                + (siteNames.isEmpty() ? "" : ", Sites=" + siteNames));
        return LiveGvDbRefs.ResolvedGlRow.builder()
                .gl(finGl).siteNames(siteNames).subLedgerName(subLedgerName).subLedgerId(subLedgerId).build();
    }
}
