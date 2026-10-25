package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.api.client.GeneralVoucherApiClient;
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
    public record Caller(GeneralVoucherApiClient client, long empCode) {}

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
        List<GlLedgerLookupResult> candidates = api
                .fetchLedgers(owner.siteCode(), caller.empCode(), owner.ouCode(), owner.workDate()).stream()
                .filter(g -> typeCode.equalsIgnoreCase(g.getType()))
                .filter(g -> g.getSourceType() == null || g.getSourceType().isBlank() || "G".equalsIgnoreCase(g.getSourceType()))
                .filter(g -> siteApp.equalsIgnoreCase(g.getSiteApplicable()) && slApp.equalsIgnoreCase(g.getSubLedgerApplicable()))
                .filter(g -> !lineType.isSiteApplicable() || g.getGlSiteCount() > 0)
                .filter(g -> !lineType.isSubLedgerApplicable() || g.getSlCount() > 0)
                .filter(g -> !opts.isRequireAmbiguousSubLedger() || g.getSlCount() > 1)
                .collect(Collectors.toList());
        log.add("  GL candidates for type=" + opts.getAccountType() + " (" + lineType + "): " + candidates.size());

        int glCount = Math.max(1, opts.getGlCount());
        int siteCount = Math.max(1, opts.getSiteCount());
        List<LiveGvDbRefs.ResolvedGlRow> rows = new ArrayList<>();
        for (GlLedgerLookupResult candidate : candidates) {
            if (rows.size() >= glCount) break;
            LiveGvDbRefs.ResolvedGlRow row = tryResolveRow(api, caller, owner, candidate, lineType, siteCount, log);
            if (row != null) rows.add(row);
        }
        if (rows.size() < glCount) {
            throw new SkipException("Skipped due to lack of data: found " + rows.size() + " usable GL(s) for type="
                    + opts.getAccountType() + " (" + lineType + ", " + siteCount + " site(s) each) — need " + glCount + ".");
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
