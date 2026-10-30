package com.gsl.utils.dataprovider.source.finance;

import com.gsl.api.client.ArVoucherApiClient;
import com.gsl.api.client.DocumentAdjustmentApiClient;
import com.gsl.api.client.GeneralVoucherApiClient;
import com.gsl.api.core.ApiSessionBundle;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.api.model.common.CashBankLedgerLookupResult;
import com.gsl.api.model.common.DocNumberSchemeEntry;
import com.gsl.api.model.common.ReferenceSiteLookupResult;
import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.api.model.common.UserSiteContext;
import com.gsl.api.model.documentadjustment.ReferenceSitePending;
import com.gsl.api.model.generalvoucher.ChequeLookupResult;
import com.gsl.database.DataAvailabilityGuard;
import com.gsl.database.DocumentSelectionResult;
import com.gsl.database.ReferenceSiteAmountRegistry;
import com.gsl.database.finance.ApVoucherArBackfillStrategy;
import com.gsl.database.query.PendingAdjQuery;
import com.gsl.enums.CashBankType;
import com.gsl.model.finance.AdmCls;
import com.gsl.model.finance.PendingSlDocument;
import com.gsl.model.finance.SlNameWithId;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformation;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.cheque.ApiChequeResolver;
import com.gsl.utils.cheque.ChequeInstrumentResolver;
import com.gsl.utils.dataprovider.fixture.ArVoucherLiveDbRefs;
import com.gsl.utils.dataprovider.fixture.finance.ArTemplateLookups;
import com.gsl.utils.dataprovider.fixture.finance.ArVoucherFixtureOptions;
import com.gsl.utils.dataprovider.fixture.finance.ArVoucherScenarioType;
import com.gsl.utils.dataprovider.fixture.finance.ArVoucherTemplateLoader;
import com.gsl.utils.dataprovider.fixture.finance.SelectDocumentVariant;
import com.gsl.utils.dataprovider.source.finance.common.DocSchemeMode;
import org.testng.SkipException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code testdata.source=API} source for AR Voucher: the placeholder matrix is inherited from {@link DbArVoucherDataSource}, and every
 * database read of the test-time resolution is replaced by Finance API calls — owner site and Cash/Bank ledger, sub-ledger/class,
 * reference site, doc scheme and the pending documents ({@code DocumentAdjustments/arvoucher}, selected and reserved by
 * {@link ApiPendingDocumentSelector}). The SELECT_DOCUMENT template's own few lookups (reference uniqueness, alternative owner site,
 * class name, GL code, known cheque) come from {@link ApiLookups}.
 *
 * <p>Approximations, logged not hidden: a classification's system/user flag ({@code cmpcode}, see {@link ApiVoucherSupport#classCmpCodes})
 * and reference uniqueness (a fresh random reference is used without a duplicate check, since no API searches by reference).
 */
public class ApiArVoucherDataSource extends DbArVoucherDataSource {

    private static final String PREFERRED_DOC_SCHEME_NAME = "AR/AP Voucher";

    public ApiArVoucherDataSource(String xmlGroupParam) {
        super(xmlGroupParam);
    }

    @Override
    protected String placeholderLoginName() {
        return "api-user";
    }

    @Override
    protected List<AdmCls> discoverClassificationTypes(String loginName) {
        return ApiSessionFactory.withWebApiSession(session -> {
            ApiVoucherSupport.Owner owner = ApiVoucherSupport.resolveOwner(session, CashBankType.BANK);
            List<SubLedgerLookupResult> sls = session.getArVoucherApiClient()
                    .fetchSubLedgers(owner.siteCode(), owner.ouCode(), (int) owner.empCode(), owner.workDate());
            return ApiVoucherSupport.discoverClasses(sls, "AR");
        });
    }

    /** Resolves one placeholder at test time through the API. */
    public static MergedFinanceArVoucherData loadFreshForScenario(MergedFinanceArVoucherData placeholder, String methodName) {
        return ApiSessionFactory.withWebApiSession(session -> resolve(session, placeholder, methodName));
    }

    // ─── resolution ──────────────────────────────────────────────────────────

    private static MergedFinanceArVoucherData resolve(ApiSessionBundle session, MergedFinanceArVoucherData placeholder, String methodName) {
        PendingDocumentRecheck.clearCurrentThread();
        ArVoucherScenarioType scenario = placeholder.getScenarioType();
        ArVoucherFixtureOptions opts = placeholder.getFixtureOptions() != null ? placeholder.getFixtureOptions() : ArVoucherFixtureOptions.defaults();
        ApiVoucherSupport.requireAutoDocScheme(opts.getDocSchemeMode());
        SelectDocumentVariant variant = opts.getSelectDocVariant();
        Log.info("[API] AR loadFreshForScenario: scenario={}, method={}, subLedgerCount={}, documentCount={}",
                scenario, methodName, opts.getSubLedgerCount(), opts.getDocumentCount());

        List<String> log = GatheringLog.start("API DATA GATHERING — RESOLVED REFERENCES (AR VOUCHER)");

        ArVoucherApiClient ar = session.getArVoucherApiClient();
        DocumentAdjustmentApiClient adj = session.getDocumentAdjustmentApiClient();
        GeneralVoucherApiClient gv = session.getGeneralVoucherApiClient();

        CashBankType cashBankType = resolveTargetCashBankType(opts, variant);
        ApiVoucherSupport.Owner owner = ApiVoucherSupport.resolveOwner(session, cashBankType);
        List<CashBankLedgerLookupResult> cashBankLedgers =
                ar.fetchCashBankLedgers(owner.siteCode(), (int) owner.empCode(), owner.ouCode(), owner.workDate());
        CashBankLedgerLookupResult cashBank = ApiVoucherSupport.pickCashBank(cashBankLedgers, cashBankType, owner, "arvoucher", log);

        List<SubLedgerLookupResult> allSls = ar.fetchSubLedgers(owner.siteCode(), owner.ouCode(), (int) owner.empCode(), owner.workDate());
        Map<Long, Integer> cmpByClass = ApiVoucherSupport.classCmpCodes(allSls);

        long effectiveClsCode = (variant != null && variant.getSlClsCode() > 0) ? variant.getSlClsCode() : opts.getSlClsCode();
        List<SubLedgerLookupResult> candidates = effectiveClsCode > 0
                ? allSls.stream().filter(s -> s.getClassCode() == effectiveClsCode).collect(Collectors.toList())
                : allSls;

        int neededQty = Math.max(1, opts.getSubLedgerCount()) * Math.max(1, opts.getDocumentCount());
        // After a backfill the sub-ledger lookup may offer a party that did not exist when the candidates were read, so the retry re-reads them.
        java.util.concurrent.atomic.AtomicBoolean retry = new java.util.concurrent.atomic.AtomicBoolean();
        DocumentSelectionResult selection = DataAvailabilityGuard.selectOrBackfill(
                () -> ApiPendingDocumentSelector.select(adj, ApiPendingDocumentSelector.Domain.AR,
                        retry.getAndSet(true) ? candidatesNow(ar, owner, effectiveClsCode) : candidates, owner.ouCode(), owner.workDate(),
                        opts.getSubLedgerCount(), opts.getDocumentCount(), false, null,
                        scenario.usesUiFifoAllocation()),
                neededQty,
                new ApVoucherArBackfillStrategy(effectiveClsCode, owner.siteCode(), owner.ouCode(), owner.empCode(), scenario.usesUiFifoAllocation()));
        List<SlNameWithId> pendingSlNames = selection.getSelectedSls();
        Map<String, List<PendingSlDocument>> pendingDocsBySlName = selection.getDocsBySlKey();
        log.add("  Pending SL(s): " + pendingSlNames + " (probed " + selection.getTotalOverfetched() + " sub-ledger(s))");

        List<SubLedgerLookupResult> slsNow = retry.get() ? ar.fetchSubLedgers(owner.siteCode(), owner.ouCode(), (int) owner.empCode(), owner.workDate()) : allSls;
        Map<String, SubLedgerLookupResult> slByKey = ApiVoucherSupport.indexSubLedgers(pendingSlNames, slsNow);
        Map<String, String> slToArApName = new LinkedHashMap<>();
        Map<String, String> slToRefSiteName = new LinkedHashMap<>();
        for (Map.Entry<String, SubLedgerLookupResult> e : slByKey.entrySet()) {
            slToArApName.put(e.getKey(), e.getValue().getGlName());
            slToRefSiteName.put(e.getKey(), ApiVoucherSupport.defaultRefSite(
                    ar.fetchReferenceSites(owner.ouCode(), e.getValue().getSlCode(), owner.siteCode(), owner.workDate()), owner));
        }
        if (scenario == ArVoucherScenarioType.REFERENCE_SITEWISE_FIFO) {
            ApiVoucherSupport.applyFifoRefSites(adj, DocumentAdjustmentApiClient.MODULE_AR, owner, pendingSlNames, slByKey, pendingDocsBySlName, slToRefSiteName);
        }

        DocNumberSchemeEntry scheme = ApiVoucherSupport.pickDocScheme(ar.fetchDocNumberSchemes(owner.siteCode(), owner.workDate()), "AR Voucher", log,
                com.gsl.enums.VoucherDomain.AR.getMenuShortcode(), owner);

        ArVoucherLiveDbRefs refs = ArVoucherLiveDbRefs.builder()
                .cashBankName(cashBank.getGlName())
                .ownerSiteName(owner.siteName())
                .docSchemeName(scheme.getName())
                .pendingSlNames(pendingSlNames)
                .pendingDocsBySlName(pendingDocsBySlName)
                .slToArApName(slToArApName)
                .slToRefSiteName(slToRefSiteName)
                .cashBankType(cashBankType)
                .build();

        ApiLookups lookups = new ApiLookups(gv, owner, slsNow, cashBankLedgers, cmpByClass);
        MergedFinanceArVoucherData data;
        if (scenario == ArVoucherScenarioType.SELECT_DOCUMENT && variant != null) {
            data = ArVoucherTemplateLoader.selectDocumentTemplate(refs, lookups, variant);
            data.setScenarioType(scenario);
        } else {
            data = scenario.loadData(refs);
            if (data.getSubLedgerInformationList() != null) {
                for (SubLedgerInformation sl : data.getSubLedgerInformationList()) {
                    sl.setSlType(lookups.slClassName(sl.getId()));
                }
            }
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

                /** The SELECT_DOCUMENT template's lookups, served by the API. */
    private static final class ApiLookups implements ArTemplateLookups {
        private final GeneralVoucherApiClient gv;
        private final ApiVoucherSupport.Owner owner;
        private final List<SubLedgerLookupResult> sls;
        private final List<CashBankLedgerLookupResult> cashBankLedgers;
        private final Map<Long, Integer> cmpByClass;

        ApiLookups(GeneralVoucherApiClient gv, ApiVoucherSupport.Owner owner, List<SubLedgerLookupResult> sls,
                   List<CashBankLedgerLookupResult> cashBankLedgers, Map<Long, Integer> cmpByClass) {
            this.gv = gv;
            this.owner = owner;
            this.sls = sls;
            this.cashBankLedgers = cashBankLedgers;
            this.cmpByClass = cmpByClass;
        }

        /** No API searches vouchers by reference; the template's random reference is used as is. */
        @Override
        public boolean referenceUsed(String reference) {
            return false;
        }

        @Override
        public Optional<String> alternativeOwnerSite(String ownerSiteName) {
            UserSiteContext ctx = gv.fetchUserContext();
            return (ctx.getSites() == null ? List.<UserSiteContext.Site>of() : ctx.getSites()).stream()
                    .filter(s -> "OS-OO-CM".equalsIgnoreCase(s.getSiteType()))
                    .filter(s -> s.getSiteName() != null && !s.getSiteName().equalsIgnoreCase(ownerSiteName))
                    .sorted(Comparator.comparingLong(UserSiteContext.Site::getSiteCode))
                    .map(UserSiteContext.Site::getSiteName)
                    .findFirst();
        }

        @Override
        public String slClassName(String slId) {
            return sls.stream().filter(s -> slId != null && slId.equals(s.getSlid())).map(SubLedgerLookupResult::getClassName).findFirst().orElse(null);
        }

        @Override
        public long cashBankGlcode(String cashBankName) {
            return cashBankLedgers.stream().filter(l -> cashBankName.equalsIgnoreCase(l.getGlName())).mapToLong(CashBankLedgerLookupResult::getGlCode)
                    .findFirst().orElseThrow(() -> new SkipException("Cash/Bank ledger '" + cashBankName + "' is not in the API lookup."));
        }

        @Override
        public ChequeInstrumentResolver.ChequeResolution knownCheque(long thisVoucherGlcode, boolean differentBank) {
            if (!differentBank) {
                return ApiChequeResolver.resolveKnownSameBank(gv, thisVoucherGlcode);
            }
            for (CashBankLedgerLookupResult other : cashBankLedgers) {
                if (!"B".equalsIgnoreCase(other.getSourceType()) || other.getGlCode() == thisVoucherGlcode) continue;
                Optional<ChequeLookupResult> leaf = ApiChequeResolver.claim(gv, other.getGlCode(), owner.workDate(), Set.of());
                if (leaf.isPresent()) {
                    return new ChequeInstrumentResolver.ChequeResolution(leaf.get().getChequeNumber(),
                            ChequeInstrumentResolver.ChequeBankMatch.DIFFERENT_BANK, other.getGlCode(), leaf.get().getBookCode());
                }
            }
            Log.warn("[API] Different-bank known cheque requested but no other bank offers an unclaimed leaf — falling back to unknown.");
            return ChequeInstrumentResolver.resolveUnknown();
        }

        @Override
        public Optional<AdmCls> classification(long clsCode) {
            return sls.stream().filter(s -> s.getClassCode() == clsCode).findFirst().map(s -> ApiVoucherSupport.toAdmCls(s, cmpByClass.get(clsCode)));
        }
    }

    /** The module's sub-ledgers read again, narrowed to the class when one is required. */
    private static List<SubLedgerLookupResult> candidatesNow(com.gsl.api.client.ArVoucherApiClient ar, ApiVoucherSupport.Owner owner, long clsCode) {
        List<SubLedgerLookupResult> all = ar.fetchSubLedgers(owner.siteCode(), owner.ouCode(), (int) owner.empCode(), owner.workDate());
        return clsCode > 0 ? all.stream().filter(s -> s.getClassCode() == clsCode).collect(Collectors.toList()) : all;
    }
}
