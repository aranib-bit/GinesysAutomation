package com.gsl.utils.dataprovider.source.finance;

import com.gsl.api.client.ApVoucherApiClient;
import com.gsl.api.client.ExtinctPartyFilter;
import com.gsl.api.client.VendorApiClient;
import com.gsl.api.client.DocumentAdjustmentApiClient;
import com.gsl.api.core.ApiSessionBundle;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.api.model.apvoucher.TdsSectionLookupResult;
import com.gsl.api.model.common.CashBankLedgerLookupResult;
import com.gsl.api.model.common.ReferenceSiteLookupResult;
import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.api.model.documentadjustment.ReferenceSitePending;
import com.gsl.api.model.common.DocNumberSchemeEntry;
import com.gsl.database.BackfillStrategy;
import com.gsl.database.DataAvailabilityGuard;
import com.gsl.database.DocumentSelectionResult;
import com.gsl.database.ReferenceSiteAmountRegistry;
import com.gsl.database.finance.ArVoucherApBackfillStrategy;
import com.gsl.database.finance.TdsVendorBackfillStrategy;
import com.gsl.database.finance.PendingApDocumentBackfillStrategy;
import com.gsl.database.query.PendingAdjQuery;
import com.gsl.enums.CashBankType;
import com.gsl.model.finance.AdmCls;
import com.gsl.model.finance.PendingSlDocument;
import com.gsl.model.finance.SlNameWithId;
import com.gsl.model.finance.TdsResolution;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.fixture.ApVoucherLiveDbRefs;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherFixtureOptions;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherScenarioType;
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
 * {@code testdata.source=API} source for AP Voucher: the same placeholder matrix as {@link DbApVoucherDataSource} (inherited, since the
 * combination logic is pure in-memory), with every database read replaced by Finance API calls — owner site and Cash/Bank ledger,
 * sub-ledger/class, TDS applicability, doc scheme, reference site and, the big one, the pending documents
 * ({@code DocumentAdjustments/apvoucher}, selected and reserved by {@link ApiPendingDocumentSelector}).
 *
 * <p>Two things the API cannot tell us are approximated and logged rather than hidden: a classification's system/user flag
 * ({@code admcls.cmpcode}; the lowest class code per class type is treated as the system one) and per-vendor TDS-nature counts
 * (scenarios that need them skip).
 */
public class ApiApVoucherDataSource extends DbApVoucherDataSource {

    private static final String PREFERRED_DOC_SCHEME_NAME = "AR/AP Voucher";

    public ApiApVoucherDataSource(String xmlGroupParam, String xmlTagParam) {
        super(xmlGroupParam, xmlTagParam);
    }

    @Override
    protected String placeholderLoginName() {
        return "api-user";
    }

    /** Classifications from the API sub-ledger lookup (no pending probe here — the pending check happens per placeholder at test time). */
    @Override
    protected List<AdmCls> discoverClassificationTypes(String loginName) {
        return ApiSessionFactory.withWebApiSession(session -> {
            ApiVoucherSupport.Owner owner = ApiVoucherSupport.resolveOwner(session, CashBankType.BANK);
            List<SubLedgerLookupResult> sls = session.getApVoucherApiClient()
                    .fetchSubLedgers(owner.siteCode(), owner.ouCode(), (int) owner.empCode(), owner.workDate());
            return ApiVoucherSupport.discoverClasses(sls, "AP");
        });
    }

    /** Resolves one placeholder at test time through the API. */
    public static MergedFinanceApVoucherData loadFreshForScenario(MergedFinanceApVoucherData placeholder, String methodName) {
        return ApiSessionFactory.withWebApiSession(session -> resolve(session, placeholder, methodName));
    }

    // ─── resolution ──────────────────────────────────────────────────────────

    private static MergedFinanceApVoucherData resolve(ApiSessionBundle session, MergedFinanceApVoucherData placeholder, String methodName) {
        PendingDocumentRecheck.clearCurrentThread();
        ApVoucherScenarioType scenario = placeholder.getScenarioType();
        ApVoucherFixtureOptions opts = placeholder.getFixtureOptions() != null ? placeholder.getFixtureOptions() : ApVoucherFixtureOptions.defaults();
        ApiVoucherSupport.requireAutoDocScheme(opts.getDocSchemeMode());

        List<String> log = GatheringLog.start("API DATA GATHERING — RESOLVED REFERENCES (AP VOUCHER)");
        Log.info("[API] AP loadFreshForScenario: scenario={}, method={}, onAccount={}, glType={}, slClsCode={}",
                scenario, methodName, scenario.isOnAccount(), opts.getGlType(), opts.getSlClsCode());

        ApVoucherApiClient ap = session.getApVoucherApiClient();
        DocumentAdjustmentApiClient adj = session.getDocumentAdjustmentApiClient();

        // 1-4. Owner site + counter-leg Cash/Bank ledger
        CashBankType cashBankType = opts.getGlType() != null ? opts.getGlType() : CashBankType.BANK;
        ApiVoucherSupport.Owner owner = ApiVoucherSupport.resolveOwner(session, cashBankType);
        CashBankLedgerLookupResult cashBank = ApiVoucherSupport.pickCashBank(
                ap.fetchCashBankLedgers(owner.siteCode(), (int) owner.empCode(), owner.ouCode(), owner.workDate()), cashBankType, owner, "apvoucher", log);

        java.util.Set<Long> extinct = ExtinctPartyFilter.extinctSlCodes(session.getVendorApiClient(), owner.siteCode(), owner.ouCode(), owner.workDate());
        List<SubLedgerLookupResult> allSls = ExtinctPartyFilter.excluding(
                ap.fetchSubLedgers(owner.siteCode(), owner.ouCode(), (int) owner.empCode(), owner.workDate()), SubLedgerLookupResult::getSlCode, extinct);
        Map<Long, Integer> cmpByClass = ApiVoucherSupport.classCmpCodes(allSls);
        List<SubLedgerLookupResult> classScoped = opts.getSlClsCode() > 0
                ? allSls.stream().filter(s -> s.getClassCode() == opts.getSlClsCode()).collect(Collectors.toList())
                : allSls;

        // 5. Sub-ledger (+ documents) resolution
        List<SlNameWithId> pendingSlNames;
        List<SubLedgerLookupResult> extraSls = new ArrayList<>();
        Map<String, List<PendingSlDocument>> pendingDocsBySlName;
        if (scenario.isOnAccount()) {
            int need = Math.max(1, opts.getSubLedgerCount());
            boolean tdsNeeded = opts.isTdsApplicable() || opts.getTdsNatureMode() != null;
            List<SubLedgerLookupResult> vendors = scenario == ApVoucherScenarioType.NEG_EXTINCT_VENDOR_NOT_OFFERED
                    ? List.of(extinctVendorAsSubLedger(session, owner, log))
                    : tdsNeeded
                    ? DataAvailabilityGuard.selectOrBackfill(() -> {
                        // Re-read the sub-ledgers each attempt: a vendor created by the backfill is only visible in a fresh lookup.
                        List<SubLedgerLookupResult> fresh = ExtinctPartyFilter.excluding(
                                ap.fetchSubLedgers(owner.siteCode(), owner.ouCode(), (int) owner.empCode(), owner.workDate()), SubLedgerLookupResult::getSlCode, extinct);
                        List<SubLedgerLookupResult> scoped = opts.getSlClsCode() > 0
                                ? fresh.stream().filter(s -> s.getClassCode() == opts.getSlClsCode()).collect(Collectors.toList()) : fresh;
                        List<SubLedgerLookupResult> found = tdsVendors(session, owner, opts, scoped, need, log);
                        if (found.size() < need) {
                            throw new SkipException("Skipped due to lack of data: found " + found.size() + " vendor SL(s) for advance payment with TDS "
                                    + "applicable (VendorMaster tdsapplicable=Yes"
                                    + (opts.getTdsNatureMode() != null ? ", nature mode " + opts.getTdsNatureMode() : "") + "), need " + need + ".");
                        }
                        return found;
                    }, need, new TdsVendorBackfillStrategy(opts.getTdsNatureMode(), opts.getPreferredTdsType()))
                    : nonTdsAdvanceCandidates(classScoped, tdsApplicableSlCodes(session, owner), need);
            if (vendors.size() < need) {
                throw new SkipException("Skipped due to lack of data: found " + vendors.size() + " vendor SL(s) for advance payment"
                        + (tdsNeeded ? "" : " without TDS") + ", need " + need + ".");
            }
            extraSls.addAll(vendors);   // may include a vendor created just now, absent from allSls
            pendingSlNames = vendors.stream().map(s -> new SlNameWithId(s.getSlName(), s.getSlid())).collect(Collectors.toList());
            pendingDocsBySlName = Map.of();
            log.add("  Advance vendor SL(s): " + pendingSlNames);
        } else {
            int neededQty = Math.max(1, opts.getSubLedgerCount()) * Math.max(1, opts.getDocumentCount());
            BackfillStrategy backfill = backfillFor(opts, classScoped, allSls, owner, neededQty);
            boolean singleRef = scenario == ApVoucherScenarioType.DOC_FIFO;
            DocumentSelectionResult selection = DataAvailabilityGuard.selectOrBackfill(
                    () -> ApiPendingDocumentSelector.select(adj, ApiPendingDocumentSelector.Domain.AP, classScoped, owner.ouCode(), owner.workDate(),
                            opts.getSubLedgerCount(), opts.getDocumentCount(), singleRef, opts.getDocTypeFilter(),
                            scenario.usesUiFifoAllocation()),
                    neededQty, backfill);
            pendingSlNames = selection.getSelectedSls();
            pendingDocsBySlName = selection.getDocsBySlKey();
            log.add("  Pending SL(s): " + pendingSlNames + " (probed " + selection.getTotalOverfetched() + " sub-ledger(s))");
        }

        Map<String, SubLedgerLookupResult> slByKey = ApiVoucherSupport.indexSubLedgers(
                pendingSlNames, java.util.stream.Stream.concat(allSls.stream(), extraSls.stream()).collect(Collectors.toList()));

        Map<String, String> slToArApName = new LinkedHashMap<>();
        Map<String, String> slToClstype = new LinkedHashMap<>();
        Map<String, Integer> slToCmpCode = new LinkedHashMap<>();
        Map<String, String> slToRefSiteName = new LinkedHashMap<>();
        Map<String, TdsResolution> slToTds = new LinkedHashMap<>();
        for (Map.Entry<String, SubLedgerLookupResult> e : slByKey.entrySet()) {
            String key = e.getKey();
            SubLedgerLookupResult sl = e.getValue();
            slToArApName.put(key, sl.getGlName());
            slToClstype.put(key, sl.getClassType());
            slToCmpCode.put(key, cmpByClass.getOrDefault(sl.getClassCode(), 0));
            slToRefSiteName.put(key, ApiVoucherSupport.defaultRefSite(
                    ap.fetchReferenceSites(owner.ouCode(), sl.getSlCode(), sl.getGlCode(), owner.siteCode(), owner.workDate()), owner));
            if (scenario.isOnAccount() && opts.isTdsApplicable()) {
                slToTds.put(key, resolveTds(session, ap, owner, sl));
            }
        }
        if (scenario == ApVoucherScenarioType.DOC_REFERENCE_SITEWISE_FIFO) {
            ApiVoucherSupport.applyFifoRefSites(adj, DocumentAdjustmentApiClient.MODULE_AP, owner, pendingSlNames, slByKey, pendingDocsBySlName, slToRefSiteName);
        }

        // 6. Doc scheme: the auto-numbered AP Voucher scheme
        DocNumberSchemeEntry scheme = ApiVoucherSupport.pickDocScheme(ap.fetchDocNumberSchemes(owner.siteCode(), owner.workDate()), "AP Voucher", log,
                com.gsl.enums.VoucherDomain.AP.getMenuShortcode(), owner);

        ApVoucherLiveDbRefs refs = ApVoucherLiveDbRefs.builder()
                .cashBankName(cashBank.getGlName())
                .cashBankGlcode(cashBank.getGlCode())
                .selectDocKnownCheque(opts.isKnownCheque())
                .ownerSiteName(owner.siteName())
                .docSchemeName(scheme.getName())
                .pendingSlNames(pendingSlNames)
                .pendingDocsBySlName(pendingDocsBySlName)
                .slToArApName(slToArApName)
                .cashBankType(cashBankType)
                .tdsBehaviour(opts.getTdsBehaviour())
                .slToTdsResolution(slToTds)
                .slToRefSiteName(slToRefSiteName)
                .slToCmpCode(slToCmpCode)
                .slToClstype(slToClstype)
                .selectDocPaymentMode(opts.getSelectDocPaymentMode())
                .build();

        MergedFinanceApVoucherData data = scenario.loadData(refs);
        if (data.getSubLedgerInformationList() != null) {
            Map<String, String> classNameBySlid = allSls.stream()
                    .collect(Collectors.toMap(SubLedgerLookupResult::getSlid, SubLedgerLookupResult::getClassName, (a, b) -> a));
            data.getSubLedgerInformationList().forEach(sl -> sl.setSlType(classNameBySlid.get(sl.getId())));
        }
        pendingDocsBySlName.forEach((key, docs) -> {
            log.add("  [" + key + "]");
            docs.forEach(d -> log.add("    → DocNo=" + d.getDocumentNo() + "  Date=" + d.getDocumentDate() + "  Type=" + d.getEntype()
                    + "  Pending=" + d.getPending()));
        });
        GatheringLog.end(log);
        data.setDbGatheringLog(log);
        log.forEach(line -> Log.info("[API] {}", line));
        return data;
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    /** Backfill choice mirrors the DB path: Supplier-classified need → Service Invoice Adhoc; any other class → AR Voucher. */
    static BackfillStrategy backfillFor(ApVoucherFixtureOptions opts, List<SubLedgerLookupResult> classScoped,
                                                List<SubLedgerLookupResult> all, ApiVoucherSupport.Owner owner, int neededQty) {
        long clsCode = opts.getSlClsCode();
        String clstype = clsCode > 0 ? classScoped.stream().map(SubLedgerLookupResult::getClassType).findFirst().orElse(null) : null;
        boolean useArVoucherBackfill = clsCode > 0 && !"S".equalsIgnoreCase(clstype);
        ReportLogger.dataBackfill("[API] AP backfill combination if shortfall: neededQty={}, clsCode={} (clstype={}), ownerSite={} (code={})",
                neededQty, clsCode, clstype, owner.siteName(), owner.siteCode());
        if (useArVoucherBackfill) {
            return new ArVoucherApBackfillStrategy(clsCode, owner.siteCode(), owner.ouCode(), owner.empCode());
        }
        String vendorSlid = classScoped.stream().findFirst().map(SubLedgerLookupResult::getSlid)
                .orElseThrow(() -> new SkipException("Skipped due to lack of data: no vendor sub-ledger available to backfill an AP document against."));
        return new PendingApDocumentBackfillStrategy(vendorSlid, owner.siteCode());
    }

            /**
     * The extinct vendor the NEG_EXTINCT_VENDOR_NOT_OFFERED scenario checks: an existing one from the vendor master ({@code isextinct=Yes}),
     * or - when the environment has none - a throw-away vendor created now and marked extinct through {@code VendorMaster/Update}. Returned
     * as a lookup-shaped row so the shared template code can use it; it is NOT in the Finance sub-ledger lookup (that is the point).
     */
    private static SubLedgerLookupResult extinctVendorAsSubLedger(ApiSessionBundle session, ApiVoucherSupport.Owner owner, List<String> log) {
        VendorApiClient vc = session.getVendorApiClient();
        VendorApiClient.Vendor extinct = vc.fetchVendors(owner.siteCode(), owner.ouCode(), owner.workDate()).stream()
                .filter(VendorApiClient.Vendor::extinct).findFirst().orElse(null);
        if (extinct == null) {
            VendorApiClient.EnvCodes env = vc.fetchEnvCodesFromExistingVendor(owner.siteCode(), owner.ouCode(), owner.workDate());
            if (env == null) {
                throw new SkipException("Skipped due to lack of data: no extinct vendor exists and the vendor master has no supplier to copy the codes from.");
            }
            String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String slId = "ZE" + (System.currentTimeMillis() % 1_000_000L);
            VendorApiClient.VendorSpec spec = new VendorApiClient.VendorSpec("ZZ API EXTINCT " + stamp, slId, false, List.of());
            ReportLogger.dataBackfill("[API] No extinct vendor exists - creating throw-away vendor '{}' (slid={}) and marking it extinct.", spec.name(), slId);
            vc.createVendor(spec, env, 0, owner.siteCode(), owner.ouCode(), owner.workDate());
            VendorApiClient.Vendor created = waitForVendor(vc, owner, v -> slId.equals(v.slId()), "to appear in the vendor master");
            vc.setExtinct(created.code(), true);
            ExtinctPartyFilter.invalidate();
            extinct = waitForVendor(vc, owner, v -> v.code() == created.code() && v.extinct(), "to show isextinct=Yes");
        }
        java.util.Map<String, Object> record = vc.fetchVendorRecord(extinct.code());
        log.add("  Extinct vendor: '" + extinct.name() + "' (slid=" + extinct.slId() + ", slcode=" + extinct.code() + ")");
        return SubLedgerLookupResult.builder()
                .slCode(extinct.code())
                .slName(extinct.name())
                .slid(extinct.slId())
                .classType("S")
                .className("Supplier")
                .glCode(((Number) record.get("glcode")).longValue())
                .glName(String.valueOf(record.get("glName")))
                .build();
    }

    /** Polls the vendor master until a vendor matching {@code match} shows up (a master create/update is visible a moment later). */
    private static VendorApiClient.Vendor waitForVendor(VendorApiClient vc, ApiVoucherSupport.Owner owner,
                                                        java.util.function.Predicate<VendorApiClient.Vendor> match, String what) {
        long deadline = System.currentTimeMillis() + 30_000L;
        while (System.currentTimeMillis() < deadline) {
            for (VendorApiClient.Vendor v : vc.fetchVendors(owner.siteCode(), owner.ouCode(), owner.workDate())) {
                if (match.test(v)) return v;
            }
            try {
                Thread.sleep(1_500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new SkipException("Skipped - the throw-away vendor did not " + what + " within 30s.");
    }

    /**
     * The sub-ledgers an advance payment WITHOUT TDS may use: none that is TDS-applicable. A TDS-applicable party makes the voucher grid
     * auto-calculate the amount and demand a TDS nature, so the save is rejected ("Some problems encountered in the detail section") - the
     * failure of FIN-AP-ADD-ADV-01 when the first Supplier on the clone happened to be TDS-applicable. A party counts as TDS-applicable when
     * either the sub-ledger lookup ({@code tdsApp}) or the Vendor master ({@code tdsapplicable}) says so. Suppliers first, then the rest.
     */
    public static List<SubLedgerLookupResult> nonTdsAdvanceCandidates(List<SubLedgerLookupResult> classScoped, java.util.Set<Long> tdsApplicableSlCodes, int need) {
        return classScoped.stream()
                .filter(s -> !s.isTdsApplicable() && !tdsApplicableSlCodes.contains(s.getSlCode()))
                .sorted(Comparator.comparingInt((SubLedgerLookupResult s) -> "S".equalsIgnoreCase(s.getClassType()) ? 0 : 1))
                .limit(need)
                .collect(Collectors.toList());
    }

    /** The sub-ledger codes of the non-extinct vendors the Vendor master flags TDS-applicable. */
    private static java.util.Set<Long> tdsApplicableSlCodes(ApiSessionBundle session, ApiVoucherSupport.Owner owner) {
        java.util.Set<Long> codes = new java.util.HashSet<>();
        for (VendorApiClient.Vendor v : session.getVendorApiClient().fetchVendors(owner.siteCode(), owner.ouCode(), owner.workDate())) {
            if (v.tdsApplicable() && !v.extinct()) codes.add(v.code());
        }
        return codes;
    }

    /**
     * API twin of {@code findVendorSlsForAdvance}'s TDS filters ({@code finsl.tds_app='Y'} plus the {@code fintds_exception} nature-count
     * subqueries): the vendors the Vendor master flags TDS-applicable, narrowed by how many TDS natures are tagged to each
     * (ANY_NATURE = none, SINGLE = one, MULTI = several, MULTI_BY_TYPE = several incl. the preferred Services/Goods type).
     */
    private static List<SubLedgerLookupResult> tdsVendors(ApiSessionBundle session, ApiVoucherSupport.Owner owner, ApVoucherFixtureOptions opts,
                                                          List<SubLedgerLookupResult> classScoped, int need, List<String> log) {
        VendorApiClient vendorClient = session.getVendorApiClient();
        Map<Long, VendorApiClient.Vendor> tdsVendorBySlCode = new LinkedHashMap<>();
        for (VendorApiClient.Vendor v : vendorClient.fetchVendors(owner.siteCode(), owner.ouCode(), owner.workDate())) {
            if (v.tdsApplicable() && !v.extinct()) tdsVendorBySlCode.put(v.code(), v);
        }
        log.add("  Vendor master: " + tdsVendorBySlCode.size() + " TDS-applicable vendor(s)");
        List<SubLedgerLookupResult> out = new ArrayList<>();
        for (SubLedgerLookupResult sl : classScoped) {
            if (out.size() >= need) break;
            if (!tdsVendorBySlCode.containsKey(sl.getSlCode())) continue;
            if (opts.getTdsNatureMode() != null) {
                List<VendorApiClient.TdsNature> natures = vendorClient.fetchVendorTds(sl.getSlCode()).natures();
                if (!matchesNatureMode(opts, natures)) continue;
            }
            out.add(sl);
        }
        return out;
    }

    private static boolean matchesNatureMode(ApVoucherFixtureOptions opts, List<VendorApiClient.TdsNature> natures) {
        return switch (opts.getTdsNatureMode()) {
            case ANY_NATURE -> natures.isEmpty();
            case SINGLE_NATURE -> natures.size() == 1;
            case MULTI_NATURE_ANY -> natures.size() > 1;
            case MULTI_NATURE_BY_TYPE -> natures.size() > 1 && natures.stream().anyMatch(n -> n.selectionType() != null
                    && n.selectionType().toUpperCase().startsWith(String.valueOf(opts.getPreferredTdsType()).toUpperCase()));
        };
    }

    /** API twin of {@code TdsNameQuery.resolve}: a TDS nature the sub-ledger is offered, varied by sl code (same pick rule as the DB path). */
    /**
     * The TDS name the voucher uses for a TDS-applicable party. A party only offers the natures that have a rate for its own TDS source, so when none
     * is offered, a section with a rate for that source is created (once) and the lookup is read again; if still none, the scenario skips.
     */
    private static TdsResolution resolveTds(ApiSessionBundle session, ApVoucherApiClient ap, ApiVoucherSupport.Owner owner, SubLedgerLookupResult sl) {
        long srcCode = sl.getSourceCode() > 0 ? sl.getSourceCode() : 1;
        List<TdsSectionLookupResult> offered = ap.fetchTdsSection(owner.siteCode(), (int) owner.empCode(), owner.ouCode(), owner.workDate(), sl.getSlCode(), srcCode);
        if (offered.isEmpty() && sl.getSourceCode() > 0) {
            new com.gsl.service.TdsMasterService(session.getTdsMasterApiClient(),
                    new com.gsl.api.client.TdsMasterApiClient.Context(owner.siteCode(), owner.ouCode(), owner.empCode(), owner.workDate()))
                    .ensureSections(sl.getSourceCode(), 1, null);
            offered = com.gsl.api.client.MasterDataSupport.pollFor(30_000L, 1_500L, () -> {
                List<TdsSectionLookupResult> now = ap.fetchTdsSection(owner.siteCode(), (int) owner.empCode(), owner.ouCode(), owner.workDate(), sl.getSlCode(), srcCode);
                return now.isEmpty() ? null : now;
            });
            if (offered == null) offered = List.of();
        }
        if (offered.isEmpty()) {
            throw new SkipException("Skipped due to lack of data: no TDS name is offered for sub-ledger '" + sl.getSlName() + "'.");
        }
        TdsSectionLookupResult picked = offered.get((int) (sl.getSlCode() % offered.size()));
        String type = picked.getTdsSourceType() == null || picked.getTdsSourceType().isBlank() ? "S" : picked.getTdsSourceType().substring(0, 1);
        return new TdsResolution(picked.getTdsName(), type);
    }
}
