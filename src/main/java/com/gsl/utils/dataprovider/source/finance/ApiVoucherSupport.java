package com.gsl.utils.dataprovider.source.finance;

import com.gsl.api.client.DocumentAdjustmentApiClient;
import com.gsl.api.client.GeneralVoucherApiClient;
import com.gsl.api.core.ApiSessionBundle;
import com.gsl.api.model.common.CashBankLedgerLookupResult;
import com.gsl.api.model.common.DocNumberSchemeEntry;
import com.gsl.api.model.common.ReferenceSiteLookupResult;
import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.api.model.common.UserSiteContext;
import com.gsl.api.model.documentadjustment.ReferenceSitePending;
import com.gsl.database.ReferenceSiteAmountRegistry;
import com.gsl.enums.CashBankType;
import com.gsl.database.query.PendingAdjQuery;
import com.gsl.model.finance.AdmCls;
import com.gsl.model.finance.PendingSlDocument;
import com.gsl.model.finance.SlNameWithId;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.source.finance.common.DocSchemeMode;
import org.testng.SkipException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Resolution steps AP and AR share in API mode (the voucher-agnostic part of what {@code ApiGvRefsResolver} does for GV): the owner
 * site/OU the vouchers are created under, the Cash/Bank and doc-scheme pick, the reference-site pick and the classification
 * system/user flag. AP, AR (and, over time, GV) call these instead of each carrying a copy - see docs/conventions/no-duplication.md.
 */
public final class ApiVoucherSupport {

    private ApiVoucherSupport() {}

    /** Owner site / OU / user the vouchers are created under. */
    public record Owner(int siteCode, String siteName, int ouCode, long empCode, String workDate) {}

    /** The resolved owner plus how it was found (the Cash/Bank ledgers offered there, the sites probed) - GV logs the latter. */
    public record OwnerResolution(Owner owner, com.gsl.service.GeneralVoucherService.OwnerSiteChoice choice) {}

    /** Owner site = the user's first OS-OO-CM site that offers a Cash/Bank ledger of the wanted kind (the one rule AP, AR and GV share). */
    public static OwnerResolution resolveOwner(GeneralVoucherApiClient gv, long empCode, CashBankType type) {
        String workDate = GeneralVoucherApiClient.workDate(LocalDate.now());
        UserSiteContext ctx = gv.fetchUserContext();
        com.gsl.service.GeneralVoucherService.OwnerSiteChoice choice =
                new com.gsl.service.GeneralVoucherService(gv).findOwnerSiteWithCashBank(ctx, empCode, type == CashBankType.BANK ? "B" : "C", workDate);
        UserSiteContext.Site site = choice.site();
        return new OwnerResolution(
                new Owner((int) site.getSiteCode(), site.getSiteName(), (int) site.getOuCode(), empCode, workDate), choice);
    }

    /** {@link #resolveOwner(GeneralVoucherApiClient, long, CashBankType)} for the API user of {@code session}. */
    public static Owner resolveOwner(ApiSessionBundle session, CashBankType type) {
        return resolveOwner(session.getGeneralVoucherApiClient(), Long.parseLong(session.getUserId()), type).owner();
    }

    /**
     * {@code admcls.cmpcode} is not exposed by any lookup, so the lowest class code per class type is taken as the system
     * classification (cmpcode 1) and the rest as user-defined (0). An approximation, used only to pick the DOC_* variant matrix.
     */
    static Map<Long, Integer> classCmpCodes(List<SubLedgerLookupResult> sls) {
        Map<String, Long> lowestByType = new LinkedHashMap<>();
        for (SubLedgerLookupResult s : sls) {
            lowestByType.merge(s.getClassType() == null ? "" : s.getClassType(), s.getClassCode(), Math::min);
        }
        Map<Long, Integer> out = new LinkedHashMap<>();
        for (SubLedgerLookupResult s : sls) {
            String type = s.getClassType() == null ? "" : s.getClassType();
            out.putIfAbsent(s.getClassCode(), lowestByType.get(type) == s.getClassCode() ? 1 : 0);
        }
        return out;
    }

    /** Quantity and rate of the throw-away vouchers the API sources create (one line: qty x rate). */
    public static final int DEFAULT_QTY = 1;
    public static final int DEFAULT_RATE = 500;

    /** The release status of a just-created voucher as read back from rootview; "P" when the read returned nothing. */
    public static String releaseStatusOrPosted(String readBack) {
        return readBack == null ? "P" : readBack;
    }

    /** The AP and AR create flows' preferred auto-numbered scheme (falls back to the first auto-numbered one offered). */
    public static final String PREFERRED_DOC_SCHEME_NAME = "AR/AP Voucher";

    /** Owner for flows that only need a usable Cash/Bank site: Bank first, Cash when no Bank ledger is offered anywhere. */
    public static Owner resolveOwnerAnyCashBank(ApiSessionBundle session) {
        try {
            return resolveOwner(session, CashBankType.BANK);
        } catch (SkipException noBank) {
            return resolveOwner(session, CashBankType.CASH);
        }
    }

    /** The one-line description of the owner used in every API data-gathering log. */
    public static String ownerLine(Owner owner) {
        return "  API user code=" + owner.empCode() + "  Owner site='" + owner.siteName() + "' (code=" + owner.siteCode() + ", OU=" + owner.ouCode() + ")";
    }

    /** API mode cannot read doc-scheme capacity, so only the AUTO mode can be resolved; every other mode skips with the same reason. */
    public static void requireAutoDocScheme(DocSchemeMode mode) {
        if (mode != DocSchemeMode.AUTO) {
            throw new SkipException("Skipped — doc-scheme mode " + mode + " needs scheme capacity, which the API mode does not read yet.");
        }
    }

    /**
     * The Cash/Bank ledger of kind {@code type} among {@code ledgers} (the module's own lookup result); skips when none is offered.
     * Writes the owner line and the chosen GL to {@code log}.
     */
    public static CashBankLedgerLookupResult pickCashBank(List<CashBankLedgerLookupResult> ledgers, CashBankType type, Owner owner,
                                                          String module, List<String> log) {
        String wanted = type == CashBankType.BANK ? "B" : "C";
        CashBankLedgerLookupResult cashBank = ledgers.stream()
                .filter(l -> wanted.equalsIgnoreCase(l.getSourceType()))
                .findFirst()
                .orElseThrow(() -> new SkipException("Skipped due to lack of data: no " + type + " ledger is offered to the API user at site '"
                        + owner.siteName() + "' under module " + module + "."));
        log.add(ownerLine(owner));
        log.add("  Cash/Bank GL='" + cashBank.getGlName() + "' (glcode=" + cashBank.getGlCode() + ", " + type.label() + ")");
        return cashBank;
    }

    /**
     * The doc scheme for a UI voucher: when the DB is available the scheme is chosen from its counters ({@code DocSchemeResolver}, AUTO mode, never an
     * exhausted, manual or test scheme) and matched by name against the API's list, because the API list carries no counter and a full scheme makes the
     * app's Save fail with {@code 2F005: control reached end of function without RETURN}. Without a DB the legacy pick of
     * {@link #pickDocScheme(List, String, List)} applies.
     */
    public static DocNumberSchemeEntry pickDocScheme(List<DocNumberSchemeEntry> schemes, String voucherLabel, List<String> log, String menuShortcode, Owner owner) {
        String name;
        try {
            name = com.gsl.api.integration.VoucherDocSchemeSupport.resolveSchemeName(menuShortcode, owner.siteCode(), owner.empCode());
        } catch (IllegalStateException e) {
            throw new SkipException("Skipped due to lack of data: no " + voucherLabel + " doc scheme can still generate a number (" + e.getMessage() + ").");
        }
        if (name == null) return pickDocScheme(schemes, voucherLabel, log);
        DocNumberSchemeEntry scheme = com.gsl.api.client.DocSchemeSelector.select(schemes, name, PREFERRED_DOC_SCHEME_NAME, voucherLabel);
        log.add("  Doc scheme='" + scheme.getName() + "' (code=" + scheme.getCode() + ", chosen from the DB counters)");
        return scheme;
    }

    /** The auto-numbered doc scheme: {@link #PREFERRED_DOC_SCHEME_NAME} when offered, else the first auto-numbered one; skips when none. */
    public static DocNumberSchemeEntry pickDocScheme(List<DocNumberSchemeEntry> schemes, String voucherLabel, List<String> log) {
        DocNumberSchemeEntry scheme = schemes.stream().filter(s -> !"Y".equalsIgnoreCase(s.getManual()))
                .filter(s -> PREFERRED_DOC_SCHEME_NAME.equalsIgnoreCase(s.getName())).findFirst()
                .or(() -> schemes.stream().filter(s -> !"Y".equalsIgnoreCase(s.getManual())).findFirst())
                .orElseThrow(() -> new SkipException("Skipped due to lack of data: no auto-numbered " + voucherLabel
                        + " doc scheme is offered to the API user."));
        log.add("  Doc scheme='" + scheme.getName() + "' (code=" + scheme.getCode() + ")");
        return scheme;
    }

    /** A sub-ledger's default reference site: the owner site when the module's lookup offers it, else the first offered, else the owner site. */
    public static String defaultRefSite(List<ReferenceSiteLookupResult> sites, Owner owner) {
        Optional<ReferenceSiteLookupResult> own = sites.stream().filter(s -> s.getName() != null && s.getName().equalsIgnoreCase(owner.siteName())).findFirst();
        return own.map(ReferenceSiteLookupResult::getName).orElseGet(() -> sites.isEmpty() ? owner.siteName() : sites.get(0).getName());
    }

    /**
     * API twin of {@code ReferenceSiteQuery.findBestRefSiteForSl}: the reference site with the biggest pending (from
     * {@code DocumentAdjustments/{ap|ar}/reference}) whose amount ceiling this thread can reserve, or null.
     *
     * @param module {@link DocumentAdjustmentApiClient#MODULE_AP} or {@link DocumentAdjustmentApiClient#MODULE_AR}
     */
    public static String bestRefSite(DocumentAdjustmentApiClient adj, String module, Owner owner, SubLedgerLookupResult lookup,
                                     SlNameWithId sl, BigDecimal slPending) {
        List<ReferenceSitePending> sites = adj.fetchReferenceSitePending(module, lookup.getGlCode(), lookup.getSlCode(), owner.ouCode(), owner.workDate()).stream()
                .filter(r -> r.getPendingAmount() != null && r.getPendingAmount().signum() > 0 && r.getSiteName() != null)
                .sorted(Comparator.comparing(ReferenceSitePending::getPendingAmount).reversed())
                .collect(Collectors.toList());
        for (ReferenceSitePending r : sites) {
            BigDecimal ceiling = r.getPendingAmount().min(slPending.signum() > 0 ? slPending : r.getPendingAmount());
            if (ReferenceSiteAmountRegistry.tryReserve(sl.getSlName(), sl.getSlId(), r.getSiteName(), ceiling, r.getPendingAmount())) {
                return r.getSiteName();
            }
        }
        return null;
    }

    /** {@code AdmCls}-shaped view of a sub-ledger lookup row's class (cmpcode defaults to 0 = user-defined when unknown). */
    public static AdmCls toAdmCls(SubLedgerLookupResult sl, Integer cmpcode) {
        AdmCls c = new AdmCls();
        c.setClscode(sl.getClassCode());
        c.setClsname(sl.getClassName());
        c.setClstype(sl.getClassType());
        c.setCmpcode(cmpcode == null ? 0 : cmpcode);
        return c;
    }

    /** The distinct classifications present among {@code sls}, with the approximated system/user flag; logged under {@code label}. */
    public static List<AdmCls> discoverClasses(List<SubLedgerLookupResult> sls, String label) {
        Map<Long, Integer> cmp = classCmpCodes(sls);
        Map<Long, AdmCls> byClass = new LinkedHashMap<>();
        for (SubLedgerLookupResult sl : sls) {
            byClass.computeIfAbsent(sl.getClassCode(), code -> toAdmCls(sl, cmp.get(code)));
        }
        Log.info("[API] {} discovered {} sub-ledger classification(s): {}", label, byClass.size(),
                byClass.values().stream().map(c -> c.getClsname() + "(" + c.cmpLabel() + ")").collect(Collectors.joining(", ")));
        return new ArrayList<>(byClass.values());
    }

    /** The lookup row of each selected sub-ledger, keyed by {@code PendingAdjQuery.compositeKey(name, id)}; rows come from {@code candidates}. */
    public static Map<String, SubLedgerLookupResult> indexSubLedgers(List<SlNameWithId> selected, List<SubLedgerLookupResult> candidates) {
        Map<String, SubLedgerLookupResult> byKey = new LinkedHashMap<>();
        for (SlNameWithId sl : selected) {
            candidates.stream()
                    .filter(x -> sl.getSlId().equals(x.getSlid()) && sl.getSlName().equals(x.getSlName())).findFirst()
                    .ifPresent(x -> byKey.put(PendingAdjQuery.compositeKey(sl.getSlName(), sl.getSlId()), x));
        }
        return byKey;
    }

    /**
     * The REFERENCE_SITEWISE_FIFO scenarios: for every selected sub-ledger, overwrite its reference site in {@code slToRefSiteName} with the
     * pending-balance-aware pick ({@link #bestRefSite}); a sub-ledger with no reservable site keeps its default and a warning is logged.
     */
    public static void applyFifoRefSites(DocumentAdjustmentApiClient adj, String module, Owner owner, List<SlNameWithId> selected,
                                         Map<String, SubLedgerLookupResult> slByKey, Map<String, List<PendingSlDocument>> docsBySlName,
                                         Map<String, String> slToRefSiteName) {
        for (SlNameWithId sl : selected) {
            String key = PendingAdjQuery.compositeKey(sl.getSlName(), sl.getSlId());
            SubLedgerLookupResult lookup = slByKey.get(key);
            if (lookup == null) continue;
            BigDecimal slPending = docsBySlName.getOrDefault(key, List.of()).stream()
                    .map(PendingSlDocument::getPending).filter(p -> p != null && p.signum() > 0)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String refSite = bestRefSite(adj, module, owner, lookup, sl, slPending);
            if (refSite != null) slToRefSiteName.put(key, refSite);
            else Log.warn("[API] RefSiteFIFO SL '{}': no reservable reference site found.", sl.getSlName());
        }
    }
}
