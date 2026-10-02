package com.gsl.utils.dataprovider.source.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.database.DataAvailabilityGuard;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.HrEmpDao;
import com.gsl.database.dao.HrEmpOuDao;
import com.gsl.database.dao.adm.AdmSiteDao;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.database.dao.finance.FinGlSiteDao;
import com.gsl.database.dao.finance.FinSlDao;
import com.gsl.database.dao.finance.FinSlGlDao;
import com.gsl.database.dao.finance.FinSlGlSiteDao;
import com.gsl.database.finance.LedgerMasterBackfillStrategy;
import com.gsl.database.finance.SubLedgerOthersBackfillStrategy;
import com.gsl.driver.DriverManager;
import com.gsl.enums.CashBankType;
import com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType;
import com.gsl.model.adm.AdmSite;
import com.gsl.model.finance.FinGl;
import com.gsl.model.finance.FinGlSite;
import com.gsl.model.finance.FinSl;
import com.gsl.model.finance.FinSlGl;
import com.gsl.model.finance.FinSlGlSite;
import com.gsl.reports.GeneralVoucherDescriptionBuilder;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.fixture.GeneralVoucherFixtureFactory;
import com.gsl.utils.dataprovider.fixture.LiveGvDbRefs;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherCombinationFilter;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherCombinations;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherExecutionGroup;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherFixtureOptions;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherPaymentVariant;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherScenarioType;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherTag;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherVariant;
import com.gsl.utils.dataprovider.source.TestDataSource;
import org.jdbi.v3.core.Jdbi;
import org.testng.SkipException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DB-driven test-data source for General Voucher's Expense/Income Detail combination
 * matrix — see {@link GeneralVoucherCombinations} for the full 128-combination
 * enumeration and its Mandatory/Regression-phase tier split.
 *
 * <p>Two-phase placeholder/{@link #loadFreshForScenario} split, mirroring
 * {@code DbApVoucherDataSource}/{@code DbArVoucherDataSource}: {@link #load} (at
 * {@code @DataSupplier} time) emits one thin placeholder per combination surviving the
 * active {@code gv.group}/{@code gv.tag} filter; {@link #loadFreshForScenario} (at
 * test-execution time) does the full live DB resolution for that one combination,
 * throwing {@link SkipException} when the exact (type, site, sub-ledger) combination has
 * no matching {@code fingl} row today, or when the combination's payment variant is
 * blocked pending the UI-discovery spike (see {@link GeneralVoucherPaymentVariant}).
 *
 * <p><b>Critical correctness point (carried over from the original single-phase version):
 * </b> valid Sub Ledgers for a Type 3 GL come from {@code finsl_gl} (not every active Sub
 * Ledger), and valid Sites for a Type 2/Type 3 GL come from {@code fingl_site}/
 * {@code finsl_gl_site} (not every active Site) — an arbitrary pick would produce test
 * data the Allocate Sites popup silently rejects.
 */
public class DbGeneralVoucherDataSource implements TestDataSource<MergedFinanceGeneralVoucherData> {

    private final String xmlGroupParam;
    private final String xmlTagParam;

    /** No execution-group/tag filter — every scenario/combination is emitted (full-suite run). */
    public DbGeneralVoucherDataSource() {
        this(null, null);
    }

    /** @param xmlGroupParam fallback {@code gv.group} value from TestNG {@code <parameter>}, used
     *                       when the {@code gv.group} system property is absent (IntelliJ direct-run path) */
    public DbGeneralVoucherDataSource(String xmlGroupParam) {
        this(xmlGroupParam, null);
    }

    /**
     * @param xmlGroupParam see {@link #DbGeneralVoucherDataSource(String)}
     * @param xmlTagParam   fallback {@code gv.tag} value from TestNG {@code <parameter>}, used
     *                      when the {@code gv.tag} system property is absent
     */
    public DbGeneralVoucherDataSource(String xmlGroupParam, String xmlTagParam) {
        this.xmlGroupParam = xmlGroupParam;
        this.xmlTagParam = xmlTagParam;
    }

    // =========================================================================
    // Phase 1 — placeholder emission
    // =========================================================================

    /**
     * Emits one placeholder per {@link GeneralVoucherScenarioType} surviving the active
     * {@code gv.group}/{@code gv.tag} filter — {@code MIXED_TYPES} gets its own single, hand-built
     * placeholder (see the inline comment below); every other scenario emits one placeholder per
     * surviving {@link GeneralVoucherVariant} from {@link GeneralVoucherCombinations#allCombinations()}.
     * No DB resolution happens here — that's deferred to {@link #loadFreshForScenario}/
     * {@link #loadFreshForMixedTypes}, called per placeholder at test-execution time.
     */
    @Override
    public List<MergedFinanceGeneralVoucherData> load(String methodName) {
        GeneralVoucherExecutionGroup activeGroup = GeneralVoucherCombinationFilter.resolveActiveGroup(xmlGroupParam);
        Set<GeneralVoucherTag> activeTags = GeneralVoucherCombinationFilter.resolveActiveTags(xmlTagParam);

        List<GeneralVoucherScenarioType> scenarios = GeneralVoucherFixtureFactory.scenariosFor(methodName);
        List<MergedFinanceGeneralVoucherData> placeholders = new ArrayList<>();

        for (GeneralVoucherScenarioType scenario : scenarios) {
            if (scenario == GeneralVoucherScenarioType.MIXED_TYPES) {
                // The one true "MIXED" scenario is a single, standalone voucher spanning multiple
                // ledger types — not a combination-matrix sweep. Its data is resolved entirely by
                // DbGeneralVoucherDataSource.loadFreshForMixedTypes, so this placeholder carries no
                // fixtureOptions/variant of its own (same idiom as GeneralVoucherPredicateFactory's
                // hand-built negative fixtures, which also legitimately hardcode a single row).
                // Bug fix (2026-09): the Round 5 fix here hand-typed "GV | Mixed ledger types
                // (Asset/Liability/Income/Expense, Type1/2/3) in one voucher" as testcasename,
                // *believing* it mirrored GeneralVoucherDescriptionBuilder.build()'s own sentence
                // for this scenario — it didn't (that method actually returns "Verify that a
                // General Voucher is created successfully with entries across multiple ledger
                // types (Asset/Liability/Income/Expense, Type1/2/3) in a single voucher."),
                // confirmed live (Test Data — Header's "TestCase Name" visibly differed from the
                // report's own title sentence). Fixed by actually CALLING that builder against
                // this placeholder (it only needs scenarioType + existingVoucherNo=null, both
                // already true at this point) instead of hand-duplicating its wording, so the two
                // can never drift apart again.
                MergedFinanceGeneralVoucherData mixedPlaceholder = MergedFinanceGeneralVoucherData.builder()
                        .scenarioType(scenario)
                        .expenseIncomeDetailList(List.of())   // empty = placeholder signal
                        .siteAllocationList(List.of())
                        .needsPaymentInfoWarning(false)
                        .scenarioTypeLabel("MIXED_TYPES")
                        .invocationIndex(1)
                        .totalInvocations(1)
                        .build();
                String mixedSentence = GeneralVoucherDescriptionBuilder.build(mixedPlaceholder);
                // Same reason buildPlaceholder() sets this for the combination-matrix rows below:
                // without it, VoucherExtentDescriptionLogger.logHeaderTable()'s "TestCase ID"/
                // "TestCase Name" cells render blank for the MIXED_TYPES report row. TestCase ID
                // stays the short "MIXED_TYPES" token (there's only ever one such row, so it's
                // already unique).
                mixedPlaceholder.setMasterTestData(new com.gsl.testdata.MasterTestData(
                        mixedSentence, null, null, null, "MIXED_TYPES", 1));
                placeholders.add(mixedPlaceholder);
                continue;
            }
            // Round 20: the Fixture Factory itself decides known-vs-unknown for every CHEQUE row
            // here, at an exact ratio (com.gsl.utils.WeightedSequenceBuilder), before this loop
            // ever filters/iterates the list — so the decision is made once per run, not per row.
            for (GeneralVoucherVariant variant
                    : GeneralVoucherFixtureFactory.assignKnownChequeFlags(GeneralVoucherCombinations.allCombinations())) {
                if (!GeneralVoucherCombinationFilter.survives(variant, activeGroup, activeTags)) continue;

                Set<GeneralVoucherTag> tags = GeneralVoucherCombinationFilter.tagsFor(variant);
                // Round 20: carry the variant's already-decided known/unknown-cheque flag across
                // into the placeholder's own opts — buildVariantFromOptions (Phase 2) rebuilds a
                // fresh GeneralVoucherVariant from opts alone, not this original variant object.
                GeneralVoucherFixtureOptions opts = GeneralVoucherFixtureOptions.of(
                        variant.getAccountType(), variant.getGlLineType(),
                        variant.isDebitSide(), variant.isReversal(), variant.getPaymentVariant(),
                        variant.getGlCount(), variant.getSiteCount())
                        .toBuilder().knownCheque(variant.isKnownCheque())
                        // Round 6: same hand-off for the per-row payment assignment.
                        .multiRowPayment(variant.isMultiRowPayment())
                        .rowPayments(variant.getRowPayments())
                        .rowKnownCheque(variant.getRowKnownCheque())
                        .build();

                placeholders.add(buildPlaceholder(scenario, opts, variant, tags));
            }
        }

        // Assign sequential invocation indices so TestListener formats FIN-GV-ADD-COMBO-01, -02, … —
        // mirrors DbArVoucherDataSource/DbApVoucherDataSource's own post-loop pass.
        int total = placeholders.size();
        for (int i = 0; i < total; i++) {
            placeholders.get(i).setInvocationIndex(i + 1);
            placeholders.get(i).setTotalInvocations(total);
        }

        Log.info("[DB] GeneralVoucher emitted {} placeholder(s) for '{}' — group filter={}, tag filter={}",
                placeholders.size(), methodName, activeGroup, activeTags.isEmpty() ? "ALL" : activeTags);
        return placeholders;
    }

    /** Builds one thin combination-matrix placeholder carrying only {@code scenarioType}/
     *  {@code fixtureOptions}/{@code tags} — real DB resolution happens later, in
     *  {@link #loadFreshForScenario}. */
    private static MergedFinanceGeneralVoucherData buildPlaceholder(GeneralVoucherScenarioType scenario,
            GeneralVoucherFixtureOptions opts, GeneralVoucherVariant variant, Set<GeneralVoucherTag> tags) {
        // MasterTestData carries the variant's ledger-type/count label into the Extent report's
        // Description/TestCase ID/TestCase Name fields — mirrors DbArVoucherDataSource/
        // DbApVoucherDataSource's own buildPlaceholder, which is what makes their rows uniquely
        // identifiable (this GV data source never did this before).
        com.gsl.testdata.MasterTestData master = new com.gsl.testdata.MasterTestData(
                variant.getTestCaseName(), null, null, null, variant.getVariantId(), 1);
        return MergedFinanceGeneralVoucherData.builder()
                .scenarioType(scenario)
                .masterTestData(master)
                .fixtureOptions(opts)
                .tags(tags)
                .expenseIncomeDetailList(List.of())   // empty = placeholder signal, same idiom as AP/AR
                .siteAllocationList(List.of())
                .needsPaymentInfoWarning(false)
                .scenarioTypeLabel(variant.getVariantId())
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    // =========================================================================
    // Phase 2 — live DB resolution for one combination
    // =========================================================================

    /**
     * Resolves live DB values for one placeholder's combination and builds the fully
     * populated {@link MergedFinanceGeneralVoucherData}. Called from the test method body
     * once it detects a placeholder (empty {@code expenseIncomeDetailList}).
     *
     * @throws SkipException when the exact combination has no matching {@code fingl} row
     *                        today, when required Sub-Ledger/Site master data is missing,
     *                        or when the combination's payment variant is blocked pending
     *                        the UI-discovery spike (Cheque/NEFT-RTGS — see
     *                        {@link GeneralVoucherPaymentVariant}).
     */
    public static MergedFinanceGeneralVoucherData loadFreshForScenario(
            MergedFinanceGeneralVoucherData placeholder, String methodName) {

        GeneralVoucherFixtureOptions opts = placeholder.getFixtureOptions();
        GeneralVoucherPaymentVariant paymentVariant = opts.getPaymentVariant();

        if (paymentVariant.isBlockedPendingUiDiscovery()) {
            throw new SkipException("Skipped — " + paymentVariant + " is blocked pending a manual UI-discovery "
                    + "spike (General Voucher has no known Payment entry control for its Cash/Bank counter-leg "
                    + "yet — see the plan's 'Blocking prerequisite' callout). Variant: "
                    + placeholder.getScenarioTypeLabel());
        }

        List<String> gatheringLog = new ArrayList<>();
        gatheringLog.add("═══════════════════════════════════════════════════════════");
        gatheringLog.add("  DB DATA GATHERING — RESOLVED REFERENCES (GENERAL VOUCHER)");
        gatheringLog.add("═══════════════════════════════════════════════════════════");

        // Buffer every actual SQL statement JDBI executes from here on — flushed and appended
        // as real "[DB Query ⏱ Xms]" lines below, mirroring DbArVoucherDataSource/
        // DbApVoucherDataSource. Narrative lines added directly to gatheringLog below are plain
        // business text (no "[DB Query" prefix) so DbGatheringExtentLogger.render() correctly
        // buckets them under "Test Data Resolution" instead of "SQL Queries Executed".
        com.gsl.database.DbQueryBuffer.start();
        Jdbi jdbi = DatabaseManager.getJdbi();

        // 1. Resolve login user → ecode, site codes
        UserCredential threadUser = DriverManager.getCurrentUser();
        String loginName = (threadUser != null) ? threadUser.username() : ConfigFactory.getConfig().username();
        HrEmpDao hrEmpDao = jdbi.onDemand(HrEmpDao.class);
        long ecode = hrEmpDao.findEcodeByAbbre(loginName)
                .orElseThrow(() -> new IllegalStateException(
                        "No hrdemp record found for login user: '" + loginName +
                        "'. Check appusername in config.properties."));
        gatheringLog.add("  Resolved login user '" + loginName + "' → ecode=" + ecode);

        // 2. Owner site + counter-leg Cash/Bank GL (mirrors DbApVoucherDataSource's own pair).
        // Round 15: ownerSite.admouCode is now threaded into resolveType2Sites/resolveType3Sites
        // below (step 4) — a Type2/Type3 candidate site belonging to a DIFFERENT Organizational
        // Unit than this Owner Site is invisible to the app's Allocate Sites picker regardless of
        // GL/user-assignment linkage (confirmed live: "S&CM" — admou_code=3 "Prawns Corner" — was
        // linked to the same GL and assigned to the same user as valid sites under the Owner
        // Site's own OU (admou_code=1), yet the picker returned "No data to display" for it).
        OwnerSiteRef ownerSite = findFirstUsableOwnerSite(jdbi, ecode);
        String cashBankName = pickGlByType(jdbi, List.of(ownerSite.code), opts.cashBankType());
        // Round 20: resolved once here (reusing the existing FinGlDao.findByGlName lookup this
        // codebase already uses elsewhere for the same "name -> glcode" purpose) so a "known"
        // cheque number can later be looked up against THIS voucher's own counter-leg bank.
        long cashBankGlcode = jdbi.onDemand(com.gsl.database.dao.finance.FinGlDao.class)
                .findByGlName(cashBankName).getGlcode();
        gatheringLog.add("  Owner site='" + ownerSite.name + "' (code=" + ownerSite.code
                + ", OU=" + ownerSite.admouCode + "), Cash/Bank GL='" + cashBankName + "' (glcode="
                + cashBankGlcode + ", " + opts.cashBankType() + ")");

        // 2b. Sites assigned to the logged-in user — same DAO/query the sibling
        // DbApVoucherDataSource/DbArVoucherDataSource already use for this exact purpose
        // (see HrEmpOuDao.findSiteCodesByEcode's Javadoc). Resolved once, here, via a plain DB
        // read — everything downstream that needs this list (steps 3-4, plus the backfill
        // strategies they may invoke) only ever receives it as an already-resolved value.
        List<Long> assignedSiteCodes = jdbi.onDemand(HrEmpOuDao.class).findSiteCodesByEcode(ecode);
        if (assignedSiteCodes.isEmpty()) {
            throw new IllegalStateException("User '" + loginName + "' (ecode=" + ecode
                    + ") has no site assignments in hrdemp_ou.");
        }
        Set<Long> siteCodeSet = new HashSet<>(assignedSiteCodes);
        gatheringLog.add("  Sites assigned to logged-in user (via hrdemp_ou): " + siteCodeSet);

        // 3. Exact (type, slapp, costapp) combination lookup — backfilled via LedgerApiClient
        // (see LedgerMasterBackfillStrategy) when the environment has no matching fingl row.
        // For Site-Applicable combinations (costapp='Y'), "available" additionally requires the
        // candidate to already carry a fingl_site row for a site assigned to THIS logged-in user —
        // a same-shape GL whose attached site isn't one of the user's assigned sites is not usable
        // by the app (see run 20260810_135033's "No site is assigned to this ledger" root-cause) and
        // must not be silently reused. Filtering it out here lets DataAvailabilityGuard's shortfall
        // path correctly trigger a fresh, correctly-sited backfill instead of reusing a poisoned row
        // forever (DataAvailabilityGuard only backfills on SkipException — it does not otherwise
        // vet what a non-empty query result actually contains).
        FinGlDao glDao = jdbi.onDemand(FinGlDao.class);
        FinGlSiteDao glSiteDao = jdbi.onDemand(FinGlSiteDao.class);
        String slapp = opts.getGlLineType().isSubLedgerApplicable() ? "Y" : "N";
        String costapp = opts.getGlLineType().isSiteApplicable() ? "Y" : "N";
        boolean siteApplicable = opts.getGlLineType().isSiteApplicable();
        // GL count — the "multi GL" entity axis (mirrors AR's unique-SL-count). 1 for the mandatory
        // baseline; >1 only for the MULTI_GL/MULTI_GL_N_SITES regression variants.
        int glCount = Math.max(1, opts.getGlCount());
        List<FinGl> candidates = DataAvailabilityGuard.selectOrBackfill(
                () -> {
                    List<FinGl> found = glDao.findByTypeAndApplicability(opts.getAccountType().code(), slapp, costapp);
                    if (siteApplicable) {
                        found = found.stream()
                                .filter(candidate -> glSiteDao.findByGlcode(candidate.getGlcode()).stream()
                                        .anyMatch(s -> siteCodeSet.contains(s.getAdmsiteCode())))
                                .collect(Collectors.toList());
                    }
                    // GeneralVoucherFixtureOptions#requireAmbiguousSubLedger — only set by the
                    // NEG_SUBLEDGER_REQUIRED negative scenario, which needs a Type3 GL with MORE
                    // THAN ONE eligible finsl_gl row (a GL with exactly one has it auto-filled by
                    // the app, with nothing left to block on). Every positive combination leaves
                    // this false and is unaffected.
                    if (opts.isRequireAmbiguousSubLedger()) {
                        FinSlGlDao ambiguityCheckDao = jdbi.onDemand(FinSlGlDao.class);
                        found = found.stream()
                                .filter(candidate -> ambiguityCheckDao.findByGlcode(candidate.getGlcode()).size() > 1)
                                .collect(Collectors.toList());
                    }
                    // Round 16 — GeneralVoucherFixtureOptions#requireCrossOuSite — only set by the
                    // NEG_SITE_ALLOCATION_CROSS_OU negative scenario, which needs a GL with at
                    // least one fingl_site-linked, user-assigned site belonging to a DIFFERENT
                    // Organizational Unit than the resolved Owner Site (see isCrossOu(...)) — the
                    // inverse of Round 15's own same-OU requirement for every positive scenario.
                    if (opts.isRequireCrossOuSite()) {
                        found = found.stream()
                                .filter(candidate -> glSiteDao.findByGlcode(candidate.getGlcode()).stream()
                                        .anyMatch(s -> siteCodeSet.contains(s.getAdmsiteCode())
                                                && isCrossOu(jdbi, s.getAdmsiteCode(), ownerSite.admouCode)))
                                .collect(Collectors.toList());
                    }
                    if (found.size() < glCount) {
                        throw new SkipException("Skipped due to lack of data: found " + found.size()
                                + " fingl row(s) for type=" + opts.getAccountType() + " (" + opts.getAccountType().code()
                                + "), site=" + costapp + ", subLedger=" + slapp
                                + (siteApplicable ? ", matching any of the logged-in user's assigned sites "
                                        + siteCodeSet : "")
                                + (opts.isRequireAmbiguousSubLedger() ? ", with more than one eligible finsl_gl row" : "")
                                + (opts.isRequireCrossOuSite() ? ", with at least one site belonging to a "
                                        + "DIFFERENT Organizational Unit than the Owner Site (admou_code="
                                        + ownerSite.admouCode + ")" : "")
                                + " — need " + glCount + ".");
                    }
                    return found;
                },
                glCount,
                new LedgerMasterBackfillStrategy(opts.getAccountType(),
                        opts.getGlLineType().isSubLedgerApplicable(), siteApplicable, assignedSiteCodes));
        List<FinGl> selectedGls = candidates.subList(0, glCount);
        gatheringLog.add("  Selected " + glCount + " GL(s) for type=" + opts.getAccountType()
                + " (" + opts.getGlLineType() + "): "
                + selectedGls.stream().map(FinGl::getGlname).collect(Collectors.toList()));

        // 4. Per-GL: Type3 resolves a real Sub Ledger via finsl_gl (NOT a bare FinSlDao lookup) plus
        // its Site(s); Type2 resolves Site(s) only. Site count — the "N sites" sub-axis (mirrors
        // AR's docs-per-SL) — is fixed at 1 unless this variant is a N_SITES/MULTI_GL_N_SITES
        // regression row. Sub Ledger selection itself always stays at exactly 1 per GL, unchanged.
        int siteCount = Math.max(1, opts.getSiteCount());
        List<LiveGvDbRefs.ResolvedGlRow> resolvedRows = new ArrayList<>();

        for (FinGl gl : selectedGls) {
            String subLedgerName = null;
            String subLedgerId = null;
            List<String> siteNames = List.of();

            if (opts.getGlLineType() == GlLineType.TYPE3_SITE_AND_SUBLEDGER) {
                // gl.isSiteApplicable() is always true for this GlLineType (costapp was forced "Y"
                // above), and step 3's candidate filter already guarantees gl carries a fingl_site row
                // matching one of the user's assigned sites — reuse that same site as targetSiteCode to
                // backfill a new finsl_gl row with, rather than re-deriving it ambiguously.
                Long targetSiteCode = gl.isSiteApplicable()
                        ? glSiteDao.findByGlcode(gl.getGlcode()).stream()
                                .map(FinGlSite::getAdmsiteCode)
                                .filter(siteCodeSet::contains)
                                .findFirst()
                                .map(Long::valueOf)
                                .orElseThrow(() -> new SkipException("Skipped due to lack of data: GL '" + gl.getGlname()
                                        + "' (glcode=" + gl.getGlcode() + ") has no fingl_site row matching any of the "
                                        + "logged-in user's assigned sites " + siteCodeSet + " — step 3's candidate "
                                        + "filter should have guaranteed this."))
                        : null;

                FinSlGlDao slGlDao = jdbi.onDemand(FinSlGlDao.class);
                FinSlGlSiteDao slGlSiteDao = jdbi.onDemand(FinSlGlSiteDao.class);
                List<FinSlGl> pairs = DataAvailabilityGuard.selectOrBackfill(
                        () -> {
                            List<FinSlGl> found = slGlDao.findByGlcode(gl.getGlcode());
                            // "Available" additionally requires the pair's own finsl_gl_site override
                            // (when present) to match one of the user's assigned sites — an empty
                            // override falls back to the GL's own fingl_site, which step 3 already
                            // guaranteed matches.
                            found = found.stream()
                                    .filter(pair -> {
                                        List<FinSlGlSite> narrowSites = slGlSiteDao.findByFinslGlCode(pair.getCode());
                                        return narrowSites.isEmpty()
                                                || narrowSites.stream().anyMatch(s -> siteCodeSet.contains(s.getAdmsiteCode()));
                                    })
                                    .collect(Collectors.toList());
                            if (found.isEmpty()) {
                                throw new SkipException("Skipped due to lack of data: no finsl_gl rows for GL '"
                                        + gl.getGlname() + "' (glcode=" + gl.getGlcode() + ") matching any of the "
                                        + "logged-in user's assigned sites " + siteCodeSet + " — no Sub Ledger is "
                                        + "permitted against this GL for the current user, so it cannot be used for "
                                        + "a Type3 row.");
                            }
                            return found;
                        },
                        1,
                        new SubLedgerOthersBackfillStrategy(gl.getGlcode(), gl.getGlname(), targetSiteCode));
                FinSlGl chosenPair = pairs.get(0);
                FinSl subLedger = jdbi.onDemand(FinSlDao.class).findBySlcode(chosenPair.getSlcode());
                subLedgerName = subLedger.getSlname();
                subLedgerId = subLedger.getSlid();
                gatheringLog.add("  Sub Ledger='" + subLedgerName + "' (slcode=" + subLedger.getSlcode()
                        + "), pair code=" + chosenPair.getCode() + ", GL='" + gl.getGlname() + "'");

                siteNames = resolveType3Sites(jdbi, gl, chosenPair, siteCodeSet, ownerSite.admouCode,
                        siteCount, gatheringLog);
            } else if (opts.getGlLineType() == GlLineType.TYPE2_SITE_ONLY) {
                // Round 16: NEG_SITE_ALLOCATION_CROSS_OU deliberately wants the ONE site step 3's
                // new filter above already guaranteed exists — resolved via the inverse method
                // instead of resolveType2Sites (which, per Round 15, now excludes exactly this
                // kind of candidate). Result still lands in the same siteNames/ResolvedGlRow slot
                // every other scenario already uses.
                siteNames = opts.isRequireCrossOuSite()
                        ? List.of(resolveCrossOuSiteForNegativeTest(jdbi, gl, siteCodeSet, ownerSite.admouCode, gatheringLog))
                        : resolveType2Sites(jdbi, gl, siteCodeSet, ownerSite.admouCode, siteCount, gatheringLog);
            }

            resolvedRows.add(LiveGvDbRefs.ResolvedGlRow.builder()
                    .gl(gl)
                    .siteNames(siteNames)
                    .subLedgerName(subLedgerName)
                    .subLedgerId(subLedgerId)
                    .build());
        }

        LiveGvDbRefs refs = LiveGvDbRefs.builder()
                .ownerSiteName(ownerSite.name)
                .cashBankName(cashBankName)
                .cashBankGlcode(cashBankGlcode)
                .cashBankType(opts.cashBankType())
                .resolvedRows(resolvedRows)
                .variant(buildVariantFromOptions(placeholder, opts))
                .build();

        MergedFinanceGeneralVoucherData data = placeholder.getScenarioType().loadData(refs);
        data.setFixtureOptions(opts);
        data.setTags(placeholder.getTags());
        // Carried from the placeholder for the same reason fixtureOptions/tags are above — without
        // this, the report's Header table renders a blank TestCase ID/Name for every GV-ADD-COMBO row.
        data.setMasterTestData(placeholder.getMasterTestData());

        // Flush the real SQL captured since DbQueryBuffer.start() above and append it under its
        // own section — these are actual executed statements (auto-prefixed "[DB Query ⏱ Xms]" by
        // JdbiSqlLogger), distinct from the plain narrative lines added throughout this method.
        List<String> executedQueries = com.gsl.database.DbQueryBuffer.flush();
        if (!executedQueries.isEmpty()) {
            gatheringLog.add("");
            gatheringLog.add("───────────────────────────────────────────────────────────");
            gatheringLog.add("  QUERIES EXECUTED DURING DATA GATHERING");
            gatheringLog.add("───────────────────────────────────────────────────────────");
            gatheringLog.addAll(executedQueries);
        }
        gatheringLog.add("═══════════════════════════════════════════════════════════");

        data.setDbGatheringLog(gatheringLog);
        return data;
    }

    // =========================================================================
    // Phase 2b — live DB resolution for the dedicated "MIXED types" scenario
    // =========================================================================

    /**
     * Resolves live DB values for the one true "MIXED" scenario — a single voucher whose
     * Expense/Income Detail rows span multiple different ledger types (Asset/Type1,
     * Liability/Type2, Income/Type3), fulfilling {@code CreateGeneralVoucher}'s original
     * class-Javadoc claim that the combinatorial sweep itself never actually implements (every one
     * of its rows is exactly one ledger type — see {@link GeneralVoucherCombinations}'s "GL count
     * x Site count" breakup). Reuses {@link #loadFreshForScenario} once per representative ledger
     * type (glCount=1/siteCount=1 each, {@code CASH} payment to avoid the CHEQUE/NEFT_RTGS
     * UI-discovery-spike block) and merges the results via
     * {@link GeneralVoucherTemplateLoader#mergeIntoMixedVoucher}.
     */
    public static MergedFinanceGeneralVoucherData loadFreshForMixedTypes(
            MergedFinanceGeneralVoucherData placeholder, String methodName) {

        List<MergedFinanceGeneralVoucherData> perType = new ArrayList<>();
        perType.add(resolveOneLedgerType(com.gsl.enums.GlAccountType.ASSET, GlLineType.TYPE1_NONE, true, methodName));
        perType.add(resolveOneLedgerType(com.gsl.enums.GlAccountType.LIABILITY, GlLineType.TYPE2_SITE_ONLY, true, methodName));
        perType.add(resolveOneLedgerType(com.gsl.enums.GlAccountType.INCOME, GlLineType.TYPE3_SITE_AND_SUBLEDGER, false, methodName));

        MergedFinanceGeneralVoucherData data =
                com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherTemplateLoader.mergeIntoMixedVoucher(perType);
        data.setScenarioType(placeholder.getScenarioType());
        data.setTags(placeholder.getTags());
        // Same TestCase ID/Name fix as loadFreshForScenario — copy from the outer MIXED_TYPES
        // placeholder (not from any of the per-type results in perType, which never set it).
        data.setMasterTestData(placeholder.getMasterTestData());
        return data;
    }

    /** Resolves one representative ledger type (1 GL, 1 Site) via {@link #loadFreshForScenario},
     *  reusing all its DB-discovery/backfill logic unchanged. */
    private static MergedFinanceGeneralVoucherData resolveOneLedgerType(com.gsl.enums.GlAccountType accountType,
            GlLineType glLineType, boolean debitSide, String methodName) {
        GeneralVoucherFixtureOptions opts = GeneralVoucherFixtureOptions.of(
                accountType, glLineType, debitSide, false, GeneralVoucherPaymentVariant.CASH, 1, 1);
        MergedFinanceGeneralVoucherData typePlaceholder = MergedFinanceGeneralVoucherData.builder()
                .scenarioType(GeneralVoucherScenarioType.POSITIVE_COMBINATION)
                .fixtureOptions(opts)
                .expenseIncomeDetailList(List.of())
                .siteAllocationList(List.of())
                .build();
        return loadFreshForScenario(typePlaceholder, methodName);
    }

    /** Reconstructs the originating {@link GeneralVoucherVariant} — carried on the placeholder
     *  only as scattered fixtureOptions/label fields, not as a single object; rebuilt here
     *  purely for {@link LiveGvDbRefs#getVariant()}'s naming/labeling use. */
    private static GeneralVoucherVariant buildVariantFromOptions(
            MergedFinanceGeneralVoucherData placeholder, GeneralVoucherFixtureOptions opts) {
        return GeneralVoucherVariant.builder()
                .variantId(placeholder.getScenarioTypeLabel())
                .testCaseName(placeholder.getScenarioTypeLabel())
                .accountType(opts.getAccountType())
                .glLineType(opts.getGlLineType())
                .debitSide(opts.isDebitSide())
                .reversal(opts.isReversal())
                .paymentVariant(opts.getPaymentVariant())
                // Round 20: carries the known/unknown-cheque decision through from opts — see
                // GeneralVoucherFixtureOptions.knownCheque's own Javadoc for why this rebuild step
                // is where that decision would otherwise silently get lost.
                .knownCheque(opts.isKnownCheque())
                // Round 6: per-row payment assignment, same hand-off.
                .multiRowPayment(opts.isMultiRowPayment())
                .rowPayments(opts.getRowPayments())
                .rowKnownCheque(opts.getRowKnownCheque())
                .build();
    }

    /**
     * Round 15/16 — {@code true} when {@code candidateSiteCode}'s own {@code admou_code} differs
     * from {@code ownerSiteAdmouCode}. Shared by Round 15's positive-scenario exclusion filter
     * (see {@link #resolveType2Sites}/{@link #resolveType3Sites}) and Round 16's negative-scenario
     * inverse filter (see {@link #resolveCrossOuSiteForNegativeTest}). {@code false} when
     * {@code ownerSiteAdmouCode} is {@code null} (can't determine "different" without a baseline)
     * or the candidate site can't be resolved at all.
     */
    private static boolean isCrossOu(Jdbi jdbi, long candidateSiteCode, Long ownerSiteAdmouCode) {
        if (ownerSiteAdmouCode == null) return false;
        AdmSite site = jdbi.onDemand(AdmSiteDao.class).findByCode(candidateSiteCode);
        return site != null && !Objects.equals(site.getAdmouCode(), ownerSiteAdmouCode);
    }

    /**
     * Round 16 — resolves ONE Site for the {@code NEG_SITE_ALLOCATION_CROSS_OU} negative
     * scenario: same GL-linkage ({@code fingl_site}) + user-assignment ({@code siteCodeSet})
     * filtering {@link #resolveType2Sites} already uses, but keeps the FIRST candidate whose own
     * OU DIFFERS from the Owner Site's — the exact inverse of Round 15's exclusion filter — since
     * this scenario deliberately needs a site the app's Allocate Sites picker cannot show.
     */
    private static String resolveCrossOuSiteForNegativeTest(Jdbi jdbi, FinGl gl, Set<Long> siteCodeSet,
            Long ownerSiteAdmouCode, List<String> gatheringLog) {
        FinGlSiteDao glSiteDao = jdbi.onDemand(FinGlSiteDao.class);
        AdmSiteDao admSiteDao = jdbi.onDemand(AdmSiteDao.class);
        AdmSite crossOuSite = glSiteDao.findByGlcode(gl.getGlcode()).stream()
                .map(FinGlSite::getAdmsiteCode)
                .filter(siteCodeSet::contains)
                .distinct()
                .map(admSiteDao::findByCode)
                .filter(Objects::nonNull)
                .filter(site -> isCrossOu(jdbi, site.getCode(), ownerSiteAdmouCode))
                .findFirst()
                .orElseThrow(() -> new SkipException("Skipped due to lack of data: found no fingl_site row "
                        + "matching any of the logged-in user's assigned sites " + siteCodeSet
                        + " AND belonging to a DIFFERENT Organizational Unit than the Owner Site (admou_code="
                        + ownerSiteAdmouCode + ") for GL '" + gl.getGlname() + "' (glcode=" + gl.getGlcode()
                        + ") — this negative scenario needs one."));
        gatheringLog.add("  Cross-OU site under test='" + crossOuSite.getName() + "' (admou_code="
                + crossOuSite.getAdmouCode() + ", differs from Owner Site's admou_code=" + ownerSiteAdmouCode
                + ") — the app's Allocate Sites picker is expected to show NO row for this site.");
        return crossOuSite.getName();
    }

    /**
     * Resolves up to {@code siteCount} distinct Sites for a Type2 row — always from the logged-in
     * user's assigned sites, never just "whichever attached site sorts first." {@code siteCount=1}
     * (the mandatory baseline) preserves the original single-site behavior exactly; {@code >1}
     * (the {@code N_SITES}/{@code MULTI_GL_N_SITES} regression variants) is new — see class Javadoc
     * "GL count × Site count" breakup. Throws {@link SkipException} rather than silently returning
     * fewer sites than requested if the DB doesn't have enough today.
     *
     * @param ownerSiteAdmouCode the resolved Owner Site's own {@code admou_code} (Round 15) — a
     *                           candidate site belonging to a DIFFERENT Organizational Unit is
     *                           excluded, since it's invisible to the app's Allocate Sites picker
     *                           regardless of GL/user-assignment linkage (confirmed live: "S&CM",
     *                           OU=3, was linked to the same GL and assigned to the same user as
     *                           valid OU=1 sites, yet the picker returned "No data to display" for
     *                           it). {@code null} (owner's own OU unresolved — not expected given
     *                           live evidence, but handled defensively) skips this filter entirely
     *                           rather than excluding every candidate.
     */
    private static List<String> resolveType2Sites(Jdbi jdbi, FinGl gl, Set<Long> siteCodeSet,
            Long ownerSiteAdmouCode, int siteCount, List<String> gatheringLog) {
        if (ownerSiteAdmouCode == null) {
            Log.warn("[DbGeneralVoucherDataSource] Owner Site's own admou_code is null — "
                    + "resolveType2Sites falls back to GL+assigned-site filtering only, without "
                    + "the Round 15 same-OU constraint.");
        }
        AdmSiteDao admSiteDao = jdbi.onDemand(AdmSiteDao.class);
        FinGlSiteDao glSiteDao = jdbi.onDemand(FinGlSiteDao.class);
        List<AdmSite> matchedSites = glSiteDao.findByGlcode(gl.getGlcode()).stream()
                .map(FinGlSite::getAdmsiteCode)
                .filter(siteCodeSet::contains)
                .distinct()
                .map(admSiteDao::findByCode)
                .filter(Objects::nonNull)
                .filter(site -> ownerSiteAdmouCode == null
                        || Objects.equals(site.getAdmouCode(), ownerSiteAdmouCode))
                .limit(siteCount)
                .collect(Collectors.toList());
        if (matchedSites.size() < siteCount) {
            throw new SkipException("Skipped due to lack of data: found " + matchedSites.size()
                    + " fingl_site row(s) matching any of the logged-in user's assigned sites " + siteCodeSet
                    + (ownerSiteAdmouCode != null
                            ? " AND belonging to the Owner Site's own Organizational Unit (admou_code="
                                    + ownerSiteAdmouCode + ")"
                            : "")
                    + " for GL '" + gl.getGlname() + "' (glcode=" + gl.getGlcode() + ") — need " + siteCount + ".");
        }
        List<String> names = matchedSites.stream().map(AdmSite::getName).collect(Collectors.toList());
        gatheringLog.add("  Type2 site(s)=" + names + " (matches assigned sites via fingl_site, same OU as "
                + "Owner Site, GL='" + gl.getGlname() + "')");
        return names;
    }

    /**
     * Resolves up to {@code siteCount} distinct Sites for a Type3 row — always from the logged-in
     * user's assigned sites, preferring the Sub Ledger pair's own {@code finsl_gl_site} override
     * when present and falling back to the GL's own {@code fingl_site} otherwise, exactly like the
     * original single-site version. {@code siteCount=1} preserves the original behavior; {@code >1}
     * is new — see {@link #resolveType2Sites}'s Javadoc for the same rationale.
     *
     * @param ownerSiteAdmouCode see {@link #resolveType2Sites}'s Javadoc — applied identically in
     *                           BOTH the {@code finsl_gl_site} pair-specific branch and the
     *                           {@code fingl_site} fallback branch below.
     */
    private static List<String> resolveType3Sites(Jdbi jdbi, FinGl gl, FinSlGl chosenPair, Set<Long> siteCodeSet,
            Long ownerSiteAdmouCode, int siteCount, List<String> gatheringLog) {
        if (ownerSiteAdmouCode == null) {
            Log.warn("[DbGeneralVoucherDataSource] Owner Site's own admou_code is null — "
                    + "resolveType3Sites falls back to GL+assigned-site filtering only, without "
                    + "the Round 15 same-OU constraint.");
        }
        AdmSiteDao admSiteDao = jdbi.onDemand(AdmSiteDao.class);
        FinSlGlSiteDao slGlSiteDao = jdbi.onDemand(FinSlGlSiteDao.class);
        List<FinSlGlSite> narrowSites = slGlSiteDao.findByFinslGlCode(chosenPair.getCode());
        List<AdmSite> matchedSites;
        String source;
        if (!narrowSites.isEmpty()) {
            matchedSites = narrowSites.stream()
                    .map(FinSlGlSite::getAdmsiteCode)
                    .filter(siteCodeSet::contains)
                    .distinct()
                    .map(admSiteDao::findByCode)
                    .filter(Objects::nonNull)
                    .filter(site -> ownerSiteAdmouCode == null
                            || Objects.equals(site.getAdmouCode(), ownerSiteAdmouCode))
                    .limit(siteCount)
                    .collect(Collectors.toList());
            source = "finsl_gl_site (pair-specific override)";
        } else {
            FinGlSiteDao glSiteDao = jdbi.onDemand(FinGlSiteDao.class);
            matchedSites = glSiteDao.findByGlcode(gl.getGlcode()).stream()
                    .map(FinGlSite::getAdmsiteCode)
                    .filter(siteCodeSet::contains)
                    .distinct()
                    .map(admSiteDao::findByCode)
                    .filter(Objects::nonNull)
                    .filter(site -> ownerSiteAdmouCode == null
                            || Objects.equals(site.getAdmouCode(), ownerSiteAdmouCode))
                    .limit(siteCount)
                    .collect(Collectors.toList());
            source = "fingl_site fallback (no finsl_gl_site override)";
        }
        if (matchedSites.size() < siteCount) {
            throw new SkipException("Skipped due to lack of data: found " + matchedSites.size() + " site(s) via "
                    + source + " matching any of the logged-in user's assigned sites " + siteCodeSet
                    + (ownerSiteAdmouCode != null
                            ? " AND belonging to the Owner Site's own Organizational Unit (admou_code="
                                    + ownerSiteAdmouCode + ")"
                            : "")
                    + " for GL '" + gl.getGlname() + "' (glcode=" + gl.getGlcode() + ") — need " + siteCount + ".");
        }
        List<String> names = matchedSites.stream().map(AdmSite::getName).collect(Collectors.toList());
        gatheringLog.add("  Type3 site(s) resolved via " + source + " (same OU as Owner Site): " + names);
        return names;
    }

    // =========================================================================
    // Shared helpers (unchanged from the original single-phase version)
    // =========================================================================

    /** Carries the numeric {@code admsite.code}, display name, and Organizational Unit
     *  ({@code admou_code}) of an owner site. {@code admouCode} added Round 15 — needed so
     *  {@link #resolveType2Sites}/{@link #resolveType3Sites} can reject a candidate Type2/Type3
     *  site belonging to a DIFFERENT OU than this one; nullable defensively (not expected to be
     *  null in practice, since every {@code admsite} row observed live carries one). */
    private static final class OwnerSiteRef {
        final long code;
        final String name;
        final Long admouCode;
        OwnerSiteRef(long code, String name, Long admouCode) {
            this.code = code;
            this.name = name;
            this.admouCode = admouCode;
        }
    }

    /**
     * Finds the first active OS-OO-CM site for the employee. Mirrors
     * {@code DbApVoucherDataSource.findFirstUsableOwnerSite}'s site-selection shape (no
     * Cash/Bank-GL-availability filter here, since that's checked separately by
     * {@link #pickGlByType}).
     */
    private static OwnerSiteRef findFirstUsableOwnerSite(Jdbi jdbi, long ecode) {
        String sql =
            "SELECT a.code, a.name, a.admou_code " +
            "FROM hrdemp_ou ho " +
            "JOIN admsite a ON a.code = ho.admsite_code " +
            "WHERE ho.hrdemp_code = :ecode " +
            "  AND a.sitetype = 'OS-OO-CM' " +
            "  AND a.ext = 'N' " +
            "ORDER BY a.code";

        List<OwnerSiteRef> candidates = jdbi.withHandle(handle ->
            handle.createQuery(sql)
                .bind("ecode", ecode)
                .map((rs, ctx) -> {
                    // getLong()+wasNull(), not getObject()+cast — avoids a ClassCastException risk
                    // if the driver maps admou_code to a non-Long numeric wrapper (Integer/BigDecimal).
                    long ouRaw = rs.getLong("admou_code");
                    Long ou = rs.wasNull() ? null : ouRaw;
                    return new OwnerSiteRef(rs.getLong("code"), rs.getString("name"), ou);
                })
                .list()
        );
        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                    "No active OS-OO-CM owner site found for ecode=" + ecode + ".");
        }
        return candidates.get(0);
    }

    /** Mirrors {@code DbApVoucherDataSource.pickGlByType}'s Cash/Bank GL selection. */
    private static String pickGlByType(Jdbi jdbi, List<Long> siteCodes, CashBankType type) {
        String sql =
            "SELECT glname FROM fingl " +
            "WHERE srctype = :srcType " +
            "  AND ext = 'N' " +
            "  AND admsite_code_owner IN (<siteCodes>) " +
            "ORDER BY glcode";

        List<String> results = jdbi.withHandle(handle ->
            handle.createQuery(sql)
                .bind("srcType", type.srcType())
                .bindList("siteCodes", siteCodes)
                .mapTo(String.class)
                .list()
        );
        if (results.isEmpty()) {
            throw new SkipException("Skipped due to lack of data: no active " + type.label()
                    + " GL found for owner site(s) " + siteCodes + ".");
        }
        return results.get(0);
    }
}
