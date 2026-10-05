package com.gsl.utils.dataprovider.source.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.HrEmpDao;
import com.gsl.database.dao.HrEmpOuDao;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.database.dao.finance.FinSlDao;
import com.gsl.database.dialect.DbDialect;
import com.gsl.database.dialect.DbDialectFactory;
import com.gsl.enums.CashBankType;
import com.gsl.database.query.AdmClsQuery;
import com.gsl.database.query.PendingAdjQuery;
import com.gsl.database.query.ReferenceSiteQuery;
import com.gsl.model.finance.AdmCls;
import com.gsl.model.finance.FinGl;
import com.gsl.model.finance.FinSl;
import com.gsl.model.finance.PendingSlDocument;
import com.gsl.model.finance.SlNameWithId;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformation;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.Faker.FakerUtils;
import com.gsl.enums.VoucherDomain;
import com.gsl.utils.Log;
import com.gsl.database.finance.ExhaustedDocSchemeBackfill;
import com.gsl.utils.dataprovider.source.finance.common.DocSchemeMode;
import com.gsl.utils.dataprovider.source.finance.common.DocSchemeSelection;
import com.gsl.utils.dataprovider.fixture.ArVoucherFixtureFactory;
import com.gsl.utils.dataprovider.fixture.ArVoucherLiveDbRefs;
import com.gsl.utils.dataprovider.fixture.finance.ArVoucherExecutionGroup;
import com.gsl.utils.dataprovider.fixture.finance.ArVoucherFixtureOptions;
import com.gsl.utils.dataprovider.fixture.finance.ArVoucherScenarioType;
import com.gsl.utils.dataprovider.fixture.finance.ArVoucherTemplateLoader;
import com.gsl.utils.dataprovider.fixture.finance.SelectDocumentCombinations;
import com.gsl.utils.dataprovider.fixture.finance.SelectDocumentVariant;
import com.gsl.database.DataAvailabilityGuard;
import com.gsl.database.DocumentSelectionResult;
import com.gsl.database.DocumentSelector;
import com.gsl.database.SelectorRequest;
import com.gsl.database.finance.ApVoucherArBackfillStrategy;
import com.gsl.enums.DocumentSelectionMode;
import com.gsl.config.WaitConfig;
import com.gsl.utils.dataprovider.source.TestDataSource;
import org.jdbi.v3.core.Jdbi;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads AR Voucher test data by querying the live database.
 *
 * <p>Resolution chain:
 * <ol>
 *   <li>Login username → {@code hrdemp.ecode}</li>
 *   <li>ecode → site codes + OU codes (hrdemp_ou)</li>
 *   <li>Site codes → first owner site name (admsite WHERE sitetype='OS-OO-CM')</li>
 *   <li>Site codes → ALL active Bank GLs (fingl WHERE srctype='B'); one is picked randomly</li>
 *   <li>OU codes → (slName, slId) pairs with pending AR (v_pending_adj)</li>
 *   <li>(slName, slId) pairs → open documents per SL (v_pending_adj, SELECT_DOCUMENT scenario)</li>
 *   <li>Site codes → AR Voucher doc scheme (admdocscheme + admmenu_docscheme + admou_docscheme)</li>
 * </ol>
 *
 * <p><b>Point 1 — Lazy per-test data generation:</b> {@link #load} now emits lightweight
 * <em>placeholder</em> objects (one per applicable scenario type). Each placeholder carries only
 * the {@code scenarioType} and minimal login credentials. At test-execution time the test method
 * detects the placeholder and calls {@link #loadFreshForScenario} to fetch real DB data for
 * that specific scenario, guaranteeing freshness even when documents are settled between scenarios.
 */
public class DbArVoucherDataSource implements TestDataSource<MergedFinanceArVoucherData> {

    // Captured from TestNG <parameter name="arv.group"> at construction time.
    // Used as fallback when the system property is absent (IntelliJ direct run).
    private final String xmlGroupParam;

    /** No execution-group filter — every scenario/variant is emitted (full-suite run). */
    public DbArVoucherDataSource() { this.xmlGroupParam = null; }

    /** @param xmlGroupParam fallback {@code arv.group} value from TestNG {@code <parameter>}, used
     *                       when the {@code arv.group} system property is absent (IntelliJ direct-run path) */
    public DbArVoucherDataSource(String xmlGroupParam) { this.xmlGroupParam = xmlGroupParam; }

    /**
     * Emits one lightweight placeholder per applicable {@link ArVoucherScenarioType}/variant
     * surviving the active {@code arv.group} filter — see the class Javadoc's "Point 1" note. No
     * DB documents are selected/reserved here; that happens later, per placeholder, in
     * {@link #loadFreshForScenario}.
     */
    @Override
    public List<MergedFinanceArVoucherData> load(String testMethodName) {
        String loginName = ConfigFactory.getConfig().username();
        String loginPwd  = ConfigFactory.getConfig().password();

        // Read execution group filter from system property (set by Maven Surefire via -Darv.group=...)
        // null = no filter, run all groups (default testng.xml full-suite run)
        ArVoucherExecutionGroup activeGroup = resolveActiveGroup();

        Log.info("[DB] Execution group filter: {}", activeGroup != null ? activeGroup : "ALL (no filter)");

        // Discover SL classification types once — reused across all scenario types
        Jdbi jdbi        = DatabaseManager.getJdbi();
        List<Long> ouCodes = resolveOuCodesForUser(jdbi, loginName);
        List<AdmCls> classificationTypes =
                AdmClsQuery.findDistinctClsCodesInPendingAR(jdbi, ouCodes);

        // First system (cmpcode=1) and user-defined (cmpcode=0) clscode with pending AR
        long systemClsCode = classificationTypes.stream()
                .filter(c -> c.getCmpcode() == 1)
                .mapToLong(AdmCls::getClscode)
                .findFirst().orElse(0L);
        long userClsCode = classificationTypes.stream()
                .filter(c -> c.getCmpcode() == 0)
                .mapToLong(AdmCls::getClscode)
                .findFirst().orElse(0L);

        Log.info("[DB] Discovered {} SL classification type(s) with pending AR: {} | systemClsCode={}, userClsCode={}",
                classificationTypes.size(),
                classificationTypes.stream()
                        .map(c -> c.getClsname() + "(" + c.cmpLabel() + ")")
                        .collect(java.util.stream.Collectors.joining(", ")),
                systemClsCode, userClsCode);

        List<MergedFinanceArVoucherData> placeholders = new java.util.ArrayList<>();

        for (ArVoucherScenarioType scenario : ArVoucherFixtureFactory.scenariosFor(testMethodName)) {

            if (scenario == ArVoucherScenarioType.SELECT_DOCUMENT) {
                // ── One placeholder per variant, filtered by active execution group
                List<SelectDocumentVariant> variants = buildVariantList(classificationTypes, activeGroup);
                // Round 20: the Fixture Factory itself decides known-vs-unknown (AR: rare, 1% by
                // default) and, within the known subset, same-bank vs. different-bank match, for
                // every CHEQUE row here, at an exact ratio — mirrors GV's/AP's own
                // assignKnownChequeFlags call, plus AR's own second-pass bank-match split.
                variants = ArVoucherFixtureFactory.assignKnownChequeFlags(variants);
                variants = ArVoucherFixtureFactory.assignChequeBankMatchFlags(variants);
                for (SelectDocumentVariant variant : variants) {
                    ArVoucherFixtureOptions opts = ArVoucherFixtureOptions.builder()
                            .subLedgerCount(variant.getSlCount())
                            .documentCount(variant.getDocsPerSl())
                            .selectDocVariant(variant)
                            .build();
                    placeholders.add(buildPlaceholder(testMethodName, loginName, loginPwd, scenario, opts,
                            variant.getVariantId(), variant.getTestCaseName()));
                }

            } else if (scenario == ArVoucherScenarioType.DOC_NO_ADJUSTMENT) {
                // DOC_NO_ADJ_SYSTEM → MANDATORY
                // DOC_NO_ADJ_USER   → REGRESSION_PHASE1
                if (systemClsCode > 0 && matchesGroup(activeGroup, ArVoucherExecutionGroup.MANDATORY)) {
                    placeholders.add(buildPlaceholder(testMethodName, loginName, loginPwd, scenario,
                            ArVoucherFixtureOptions.forCashBankTypeAndClsCode(CashBankType.BANK, systemClsCode),
                            "DOC_NO_ADJ_SYSTEM", "DOC_NO_ADJUSTMENT | [Bank GL] | System SL"));
                }
                if (userClsCode > 0 && matchesGroup(activeGroup, ArVoucherExecutionGroup.REGRESSION_PHASE1)) {
                    placeholders.add(buildPlaceholder(testMethodName, loginName, loginPwd, scenario,
                            ArVoucherFixtureOptions.forCashBankTypeAndClsCode(CashBankType.BANK, userClsCode),
                            "DOC_NO_ADJ_USER", "DOC_NO_ADJUSTMENT | [Bank GL] | User SL"));
                }

            } else if (scenario == ArVoucherScenarioType.NEG_DOC_SCHEME_EXHAUSTED) {
                // Cash counter-leg (payment handled inline) + EXHAUSTED doc-scheme mode; REGRESSION_PHASE1 like the other negatives.
                if (matchesGroup(activeGroup, ArVoucherExecutionGroup.REGRESSION_PHASE1)) {
                    placeholders.add(buildPlaceholder(testMethodName, loginName, loginPwd, scenario,
                            ArVoucherFixtureOptions.forExhaustedScheme(CashBankType.CASH),
                            "DB_" + scenario.name(), scenario.name()));
                }

            } else if (scenario == ArVoucherScenarioType.NEG_BLANK_CASHBANK
                    || scenario == ArVoucherScenarioType.NEG_BLANK_SUBLEDGER
                    || scenario == ArVoucherScenarioType.NEG_BLANK_MANDATORY_REFERENCE
                    || scenario == ArVoucherScenarioType.BOUND_AMOUNT_MIN_VALID
                    || scenario == ArVoucherScenarioType.BOUND_AMOUNT_MIN_INVALID) {
                // T-03 Negative & Boundary — gated to REGRESSION_PHASE1 so mandatory suite stays lean.
                if (matchesGroup(activeGroup, ArVoucherExecutionGroup.REGRESSION_PHASE1)) {
                    placeholders.add(buildPlaceholder(testMethodName, loginName, loginPwd, scenario,
                            ArVoucherFixtureOptions.forCashBankType(CashBankType.BANK),
                            "DB_" + scenario.name(), scenario.name()));
                }

            } else {
                // FIFO_SETOFF / REFERENCE_SITEWISE_FIFO:
                //   FIFO Cash GL     → REGRESSION_PHASE1 (REG-11A)
                //   FIFO Bank System → MANDATORY         (MAND-03)
                //   FIFO Bank User   → REGRESSION_PHASE1 (REG-11B)
                String baseId  = scenario == ArVoucherScenarioType.FIFO_SETOFF ? "DB_FIFO" : "DB_REF_FIFO";
                String scenLbl = scenario.name();
                if (matchesGroup(activeGroup, ArVoucherExecutionGroup.REGRESSION_PHASE1)) {
                    placeholders.add(buildPlaceholder(testMethodName, loginName, loginPwd, scenario,
                            ArVoucherFixtureOptions.forCashBankType(CashBankType.CASH),
                            baseId + "_CASH", scenLbl + " | [Cash GL]"));
                }
                if (systemClsCode > 0 && matchesGroup(activeGroup, ArVoucherExecutionGroup.MANDATORY)) {
                    placeholders.add(buildPlaceholder(testMethodName, loginName, loginPwd, scenario,
                            ArVoucherFixtureOptions.forCashBankTypeAndClsCode(CashBankType.BANK, systemClsCode),
                            baseId + "_BANK_SYSTEM", scenLbl + " | [Bank GL] | System SL"));
                }
                if (userClsCode > 0 && matchesGroup(activeGroup, ArVoucherExecutionGroup.REGRESSION_PHASE1)) {
                    placeholders.add(buildPlaceholder(testMethodName, loginName, loginPwd, scenario,
                            ArVoucherFixtureOptions.forCashBankTypeAndClsCode(CashBankType.BANK, userClsCode),
                            baseId + "_BANK_USER", scenLbl + " | [Bank GL] | User SL"));
                }
            }
        }

        // Assign sequential invocation indices so TestListener formats FIN-AR-ADD-01, -02, -03 …
        int total = placeholders.size();
        for (int i = 0; i < total; i++) {
            placeholders.get(i).setInvocationIndex(i + 1);
            placeholders.get(i).setTotalInvocations(total);
        }

        Log.info("[DB] Emitting {} placeholder(s) for method='{}'. DB queries deferred to test-execution time.",
                placeholders.size(), testMethodName);
        return placeholders;
    }

    /**
     * Resolves the active execution group using a two-source priority:
     * <ol>
     *   <li>System property {@code arv.group} — set by Maven Surefire via profile (CI/Maven path)</li>
     *   <li>{@link #xmlGroupParam} — captured from TestNG {@code <parameter name="arv.group">}
     *       at construction time (IntelliJ direct-run path)</li>
     * </ol>
     * Returns {@code null} when neither source provides a value (full-suite run with no filter).
     */
    private ArVoucherExecutionGroup resolveActiveGroup() {
        String prop = System.getProperty("arv.group");
        if (prop == null || prop.isBlank()) prop = this.xmlGroupParam;
        if (prop == null || prop.isBlank()) return null;
        try {
            return ArVoucherExecutionGroup.valueOf(prop.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            Log.warn("[DB] Unknown arv.group value '{}' — running all groups.", prop);
            return null;
        }
    }

    /**
     * Returns true when the placeholder should be emitted for the given active group.
     *
     * <ul>
     *   <li>{@code activeGroup == null} → always true (no filter, run all)</li>
     *   <li>{@code activeGroup == targetGroup} → true</li>
     *   <li>otherwise → false</li>
     * </ul>
     */
    private static boolean matchesGroup(ArVoucherExecutionGroup activeGroup,
                                        ArVoucherExecutionGroup targetGroup) {
        return activeGroup == null || activeGroup == targetGroup;
    }

    /**
     * Builds the full ordered SELECT_DOCUMENT variant list from pre-discovered classification types,
     * then filters to only those matching {@code activeGroup} (null = no filter).
     *
     * <ol>
     *   <li>Group 0 — mandatory baseline per SL category (1 system + 1 user, if present)</li>
     *   <li>Group 1 — static reference variants (SD_REF_FIN, SD_REF_UNIQUE)</li>
     *   <li>Group 2 — per classification type:
     *       system ({@code cmpcode=1}) → 10 variants;
     *       user-defined ({@code cmpcode=0}) → 3 variants</li>
     *   <li>Groups 3–8 — static customer/SL-count/amount/grid/payment/date variants</li>
     *   <li>Cash GL — explicit Cash GL variants</li>
     *   <li>Phase-3 — specific document type filter variant (REG-09)</li>
     * </ol>
     *
     * @param classificationTypes active classification types discovered from pending AR
     * @param activeGroup         execution group filter; null = include all variants
     */
    private static List<SelectDocumentVariant> buildVariantList(
            List<AdmCls> classificationTypes, ArVoucherExecutionGroup activeGroup) {
        List<SelectDocumentVariant> variants = new java.util.ArrayList<>();

        // Group 0 — mandatory baseline: one per SL cmpcode category
        classificationTypes.stream().filter(c -> c.getCmpcode() == 1).findFirst()
                .ifPresent(cls -> variants.add(SelectDocumentCombinations.mandatoryBaselineVariant(cls)));
        // Group 0b — MAND-06: system SL, Bank GL, UniqueRandom(CHEQUE/NEFT_RTGS)
        classificationTypes.stream().filter(c -> c.getCmpcode() == 1).findFirst()
                .ifPresent(cls -> variants.add(SelectDocumentCombinations.mandatoryBankRandomVariant(cls)));
        classificationTypes.stream().filter(c -> c.getCmpcode() == 0).findFirst()
                .ifPresent(cls -> variants.add(SelectDocumentCombinations.mandatoryBaselineVariant(cls)));

        // Group 1 — reference uniqueness (static)
        variants.addAll(SelectDocumentCombinations.group1Variants());

        // Group 2 — per classification type (bifurcated by cmpcode)
        for (AdmCls cls : classificationTypes) {
            if (cls.getCmpcode() == 1) {
                variants.addAll(SelectDocumentCombinations.forSystemClassificationType(cls));   // 10 variants
            } else {
                variants.addAll(SelectDocumentCombinations.forUserDefinedClassificationType(cls)); // 3 variants
            }
        }

        // Groups 3–8 — static dimensional variants (Bank GL)
        variants.addAll(SelectDocumentCombinations.groups3to8Variants());

        // Cash GL — explicit variants
        variants.addAll(SelectDocumentCombinations.cashGlVariants());

        // Phase-3 — INV-only specific document type variant (REG-09)
        variants.add(SelectDocumentCombinations.specificDocTypeVariant());

        // Apply execution group filter — null activeGroup means no filtering
        if (activeGroup != null) {
            variants.removeIf(v -> v.getExecutionGroup() != activeGroup);
        }

        Log.info("[DB] SELECT_DOCUMENT variant list: {} variant(s) after group filter={}",
                variants.size(), activeGroup != null ? activeGroup : "ALL");
        return variants;
    }

    /**
     * Resolves the list of OU codes for the given login user.
     * Used at DataSupplier time (not test-execution time) to scope DB discovery queries.
     */
    private static List<Long> resolveOuCodesForUser(Jdbi jdbi, String loginName) {
        HrEmpDao hrEmpDao = jdbi.onDemand(HrEmpDao.class);
        long ecode = hrEmpDao.findEcodeByAbbre(loginName)
                .orElseThrow(() -> new IllegalStateException(
                        "No hrdemp record found for login user: '" + loginName +
                        "'. Check appusername in config.properties."));
        HrEmpOuDao hrOuDao = jdbi.onDemand(HrEmpOuDao.class);
        return hrOuDao.findOuCodesByEcode(ecode);
    }

    /** Constructs a minimal placeholder object that carries enough state to resolve at test time. */
    private static MergedFinanceArVoucherData buildPlaceholder(
            String methodName, String loginName, String loginPwd,
            ArVoucherScenarioType scenario, ArVoucherFixtureOptions opts,
            String testCaseId, String testCaseName) {

        com.gsl.testdata.MasterTestData master = new com.gsl.testdata.MasterTestData(
                testCaseName, null, null, null, testCaseId, 1);

        return MergedFinanceArVoucherData.builder()
                .scenarioType(scenario)
                .masterTestData(master)
                .subLedgerInformationList(List.of())   // empty = signal: placeholder
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(List.of())
                .paymentProvided(false)
                .needsPaymentInfoWarning(false)
                .invocationIndex(1)
                .totalInvocations(1)
                .fixtureOptions(opts)
                .build();
    }

    // ─── Point 1: Fresh data fetch per scenario, called at test-execution time ───

    /**
     * Runs the full DB resolution chain for ONE specific scenario type and returns
     * a fully populated {@link MergedFinanceArVoucherData}.
     *
     * <p>Called from the test method body when a placeholder object is detected.
     * Because this runs during test execution (not at DataSupplier time), each
     * scenario sees the freshest possible DB state — documents settled by a prior
     * scenario are not stale here.
     *
     * @param scenarioType the scenario to build data for
     * @param methodName   the test method name (passed through for logging)
     * @return fully built test data object with {@code scenarioType} stamped on it
     */
    public static MergedFinanceArVoucherData loadFreshForScenario(
            MergedFinanceArVoucherData placeholder, String methodName) {

        // Run the DB resolution chain inside a DataLoadScope so the LoggingAspect suppresses
        // its per-frame red FAIL logging while data is loading (see DataLoadScope). Any
        // IllegalStateException raised by an inner helper (no usable site / GL / pending docs)
        // is a data-availability shortfall → convert to a SkipException at this boundary so the
        // test renders as an amber Skip, not a misleading red Fail. Mirrors DbApVoucherDataSource,
        // which already throws SkipException at its availability checks.
        com.gsl.aspects.DataLoadScope.enter();
        try {
            return loadFreshForScenarioInternal(placeholder, methodName);
        } catch (IllegalStateException e) {
            throw new org.testng.SkipException(
                    "[DbArVoucherDataSource] Skipping — insufficient test data for scenario '"
                    + placeholder.getScenarioType() + "' / variant '"
                    + (placeholder.getMasterTestData() != null
                            ? placeholder.getMasterTestData().getTestcaseid() : "?")
                    + "': " + e.getMessage());
        } finally {
            com.gsl.aspects.DataLoadScope.exit();
        }
    }

    /** Does the actual DB resolution work for {@link #loadFreshForScenario} — see that method's
     *  Javadoc for the {@link org.testng.SkipException}-conversion wrapper around this. */
    private static MergedFinanceArVoucherData loadFreshForScenarioInternal(
            MergedFinanceArVoucherData placeholder, String methodName) {

        ArVoucherScenarioType scenarioType = placeholder.getScenarioType();
        ArVoucherFixtureOptions opts = placeholder.getFixtureOptions() != null
                ? placeholder.getFixtureOptions()
                : ArVoucherFixtureOptions.defaults();

        Log.info("[DB] loadFreshForScenario: scenario={}, method={}, subLedgerCount={}, documentCount={}",
                scenarioType, methodName, opts.getSubLedgerCount(), opts.getDocumentCount());

        com.gsl.database.DbQueryBuffer.start();
        Jdbi jdbi = DatabaseManager.getJdbi();
        DbDialect d = DbDialectFactory.current();

        // 1. Resolve login user → ecode
        //    Use the per-thread user when running in parallel; fall back to config for single-user mode.
        com.gsl.testdata.UserCredential threadUser = com.gsl.driver.DriverManager.getCurrentUser();
        String loginName = (threadUser != null)
                ? threadUser.username()
                : ConfigFactory.getConfig().username();
        HrEmpDao hrEmpDao = jdbi.onDemand(HrEmpDao.class);
        long ecode = hrEmpDao.findEcodeByAbbre(loginName)
                .orElseThrow(() -> new IllegalStateException(
                        "No hrdemp record found for login user: '" + loginName +
                        "'. Check appusername in config.properties."));

        // 2. Site codes and OU codes for the user
        HrEmpOuDao hrOuDao = jdbi.onDemand(HrEmpOuDao.class);
        List<Long> siteCodes = hrOuDao.findSiteCodesByEcode(ecode);
        List<Long> ouCodes   = hrOuDao.findOuCodesByEcode(ecode);

        if (siteCodes.isEmpty()) {
            throw new IllegalStateException(
                    "User '" + loginName + "' (ecode=" + ecode + ") has no site assignments in hrdemp_ou.");
        }

        // 3. First OS-OO-CM owner site that has at least one active Cash/Bank GL
        OwnerSiteRef ownerSite = findFirstUsableOwnerSite(jdbi, d, ecode);
        String ownerSiteName = ownerSite.name;
        List<Long> ownerSiteCodes = List.of(ownerSite.code);

        // 4. Pick a GL matching the target CashBankType for this variant/option
        SelectDocumentVariant variant = opts.getSelectDocVariant();
        CashBankType targetCashBankType = resolveTargetCashBankType(opts, variant);
        String cashBankName = pickGlByType(jdbi, d, ownerSiteCodes, targetCashBankType);
        Log.info("[DB] Picked GL: {} ({})", cashBankName, targetCashBankType.label());

        // 5 + 6. Atomic SL + document selection — over-fetch, reserve, retry, trim to size.
        //   Replaces the previous two-step fetch (findSlNamesWithPendingAR → filterAndReserve)
        //   which had a read-modify-write race: SLs returned by the first query could be fully
        //   reserved by other threads before the second query, leaving the fixture builder with
        //   an SL list that did not match the reserved-document map. The DocumentSelector
        //   returns a self-consistent (pendingSlNames, pendingDocsBySlName) pair.
        //   Priority for clsCode filter: variant.slClsCode > opts.slClsCode > no filter.
        long effectiveClsCode = (variant != null && variant.getSlClsCode() > 0)
                ? variant.getSlClsCode()
                : opts.getSlClsCode();

        WaitConfig fcfg = ConfigFactory.getWaitConfig();
        SelectorRequest selReq = SelectorRequest.builder()
                .jdbi(jdbi)
                .ouCodes(ouCodes)
                .ownerSiteCodes(ownerSiteCodes)
                .clsCode(effectiveClsCode > 0 ? effectiveClsCode : null)
                .cmpCode(null)
                .subLedgerCount(opts.getSubLedgerCount())
                .documentCount(opts.getDocumentCount())
                .overfetchMultiplier(fcfg.docSelectorOverfetchMultiplier())
                .retryBackoffMs(fcfg.docSelectorRetryBackoffMs())
                .mode(DocumentSelectionMode.fromString(fcfg.docSelectorMode()))
                .build();

        // OU code: no distinct "OU code" source exists in this framework — assumed 1:1 with
        // ownerSiteCode, same convention already established in DbApVoucherDataSource. An
        // earlier version of this line indexed into ouCodes (hrdemp_ou, no ORDER BY) instead,
        // which could produce an incoherent OU/site pairing and, when a row's admou_code was
        // NULL, a NullPointerException on ouCodes.get(0).intValue().
        DocumentSelectionResult selection = DataAvailabilityGuard.selectOrBackfill(
                () -> DocumentSelector.selectAtomic(selReq),
                selReq.getSubLedgerCount() * selReq.getDocumentCount(),
                new ApVoucherArBackfillStrategy(effectiveClsCode, (int) ownerSite.code, (int) ownerSite.code, ecode));
        List<SlNameWithId> pendingSlNames = selection.getSelectedSls();
        Map<String, List<PendingSlDocument>> pendingDocsBySlName = selection.getDocsBySlKey();

        Log.info("[DB] Selection ok | clsCode={} ownerSite='{}' (code={}) sls={} attemptsUsed={} overfetched={} mode={}",
                effectiveClsCode, ownerSite.name, ownerSite.code, pendingSlNames.size(),
                selection.getAttemptsUsed(), selection.getTotalOverfetched(), selReq.getMode());

        // 5b. Resolve GL (AR/AP ledger) name for each selected SL via finsl → fingl
        FinSlDao finSlDao = jdbi.onDemand(FinSlDao.class);
        FinGlDao finGlDao = jdbi.onDemand(FinGlDao.class);
        Map<String, String> slToArApName = new java.util.LinkedHashMap<>();
        for (SlNameWithId sl : pendingSlNames) {
            String key = PendingAdjQuery.compositeKey(sl.getSlName(), sl.getSlId());
            FinSl finSl = finSlDao.findBySlnameAndSlid(sl.getSlName(), sl.getSlId());
            if (finSl != null) {
                FinGl finGl = finGlDao.findByGlCode(finSl.getGlcode());
                if (finGl != null) slToArApName.put(key, finGl.getGlname());
            }
        }

        // 5c. Resolve Reference Site name per SL — mirrors AP step 5d.
        //     Required by Reference Sitewise FIFO Set-Off: the FIFO popup needs one row per
        //     SL keyed to a ref site that actually has open AR documents.
        //     For non-RefSiteFIFO scenarios the default-site resolution is harmless (kept
        //     symmetric with AP so future templates can read the map unconditionally).
        Map<String, String> slToRefSiteName = new LinkedHashMap<>();
        String defaultSiteName = ReferenceSiteQuery.resolveDefaultSiteName(jdbi);
        for (SlNameWithId sl : pendingSlNames) {
            String key  = PendingAdjQuery.compositeKey(sl.getSlName(), sl.getSlId());
            FinSl finSl = finSlDao.findBySlnameAndSlid(sl.getSlName(), sl.getSlId());
            if (finSl == null) continue;
            AdmCls admCls  = AdmClsQuery.findByClsCode(jdbi, finSl.getClscode()).orElse(null);
            String clstype = admCls != null ? admCls.getClstype() : null;
            String refSite = ReferenceSiteQuery.resolveForSl(jdbi, finSl.getSlcode(), clstype, defaultSiteName);
            slToRefSiteName.put(key, refSite);
            Log.info("[DB] SL '{}': clstype='{}', refSite='{}'", sl.getSlName(), clstype, refSite);
        }
        // For REFERENCE_SITEWISE_FIFO: overwrite with the pending-balance-aware resolution
        // (v_pending_adj grouped by ref_admsite_name, amount ceiling reserved). AR uses
        // debit_credit = 'D' — opposite of AP's 'C'.
        if (scenarioType == ArVoucherScenarioType.REFERENCE_SITEWISE_FIFO) {
            for (SlNameWithId sl : pendingSlNames) {
                String key  = PendingAdjQuery.compositeKey(sl.getSlName(), sl.getSlId());
                FinSl finSl = finSlDao.findBySlnameAndSlid(sl.getSlName(), sl.getSlId());
                if (finSl == null) continue;
                BigDecimal slPending = pendingDocsBySlName.getOrDefault(key, List.of())
                        .stream()
                        .map(PendingSlDocument::getPending)
                        .filter(p -> p != null && p.compareTo(BigDecimal.ZERO) > 0)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                String refSiteName = ReferenceSiteQuery.findBestRefSiteForSl(
                        jdbi, d, finSl.getSlcode(), ouCodes, "D", slPending, sl.getSlName(), sl.getSlId());
                if (refSiteName != null) {
                    slToRefSiteName.put(key, refSiteName);
                    Log.info("[DB] RefSiteFIFO SL '{}': refSite='{}', slPending={}",
                            sl.getSlName(), refSiteName, slPending);
                } else {
                    Log.warn("[DB] RefSiteFIFO SL '{}': no reservable reference site found.", sl.getSlName());
                }
            }
        }

        // 7. AR Voucher doc scheme verified for user's site
        // Mode comes from the scenario's fixture options: AUTO normally, EXHAUSTED for the full-scheme negative
        // scenario (which backfills a full test scheme when none exists).
        DocSchemeSelection docScheme = ExhaustedDocSchemeBackfill.resolve(jdbi,
                VoucherDomain.AR, siteCodes, ownerSite.code, opts.getDocSchemeMode());
        String docSchemeName = docScheme.docName();

        // 8. Package into ArVoucherLiveDbRefs — AR Voucher has no TDS behaviour or TDS name
        //    resolution (those fields don't exist on this AR-specific type at all; see its
        //    Javadoc for the full field-usage verification).
        ArVoucherLiveDbRefs refs = ArVoucherLiveDbRefs.builder()
                .cashBankName(cashBankName)
                .ownerSiteName(ownerSiteName)
                .docSchemeName(docSchemeName)
                .docSchemeNarrative(docScheme.getNarrative())
                .pendingSlNames(pendingSlNames)
                .pendingDocsBySlName(pendingDocsBySlName)
                .slToArApName(slToArApName)
                .slToRefSiteName(slToRefSiteName)
                .cashBankType(targetCashBankType)
                .build();

        printRefs(loginName, ecode, refs, pendingDocsBySlName);

        // 9. Build test data for this specific scenario
        // For SELECT_DOCUMENT with a variant, use the parameterised overload in ArVoucherTemplateLoader
        MergedFinanceArVoucherData data;
        if (scenarioType == ArVoucherScenarioType.SELECT_DOCUMENT && variant != null) {
            data = ArVoucherTemplateLoader.selectDocumentTemplate(refs, jdbi, siteCodes, variant);
            data.setScenarioType(scenarioType);
        } else {
            data = scenarioType.loadData(refs);
            if (data.getSubLedgerInformationList() != null) {
                for (SubLedgerInformation sl : data.getSubLedgerInformationList()) {
                    sl.setSlType(AdmClsQuery.findClsnameBySlId(jdbi, sl.getId()));
                }
            }
        }

        printGeneratedData(data);

        // P3: Flush buffered queries and store gathering log in the data object for report rendering
        List<String> executedQueries = com.gsl.database.DbQueryBuffer.flush();
        buildAndSetGatheringLog(loginName, ecode, refs, pendingDocsBySlName, data, executedQueries);

        return data;
    }

    // ─── Dialect-sensitive inline queries ─────────────────────────────────────

    /** Carries the numeric {@code admsite.code} and display name of an owner site. */
    private static final class OwnerSiteRef {
        final long   code;
        final String name;
        OwnerSiteRef(long code, String name) { this.code = code; this.name = name; }
    }

    /**
     * Finds the first active OS-OO-CM site for the employee that also has at least one
     * active Cash or Bank GL ({@code fingl.admsite_code_owner = site.code}).
     * Sites with no allocated Cash/Bank GL are skipped — they do not appear in the UI.
     */
    private static OwnerSiteRef findFirstUsableOwnerSite(Jdbi jdbi, DbDialect d, long ecode) {
        String siteSql =
            "SELECT a.code, a.name " +
            "FROM hrdemp_ou ho " +
            "JOIN admsite a ON a.code = ho.admsite_code " +
            "WHERE ho.hrdemp_code = :ecode " +
            "  AND a.sitetype = 'OS-OO-CM' " +
            "  AND a.ext = 'N' " +
            "ORDER BY a.code";

        List<OwnerSiteRef> candidates = jdbi.withHandle(handle ->
            handle.createQuery(siteSql)
                .bind("ecode", ecode)
                .map((rs, ctx) -> new OwnerSiteRef(rs.getLong("code"), rs.getString("name")))
                .list()
        );

        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                "No active OS-OO-CM site found for ecode=" + ecode +
                ". Check admsite (sitetype='OS-OO-CM', ext='N') + hrdemp_ou.");
        }

        String glCheckSql =
            "SELECT COUNT(*) FROM fingl " +
            "WHERE srctype IN ('B', 'C') " +
            "  AND ext = 'N' " +
            "  AND admsite_code_owner = :siteCode";

        for (OwnerSiteRef site : candidates) {
            long count = jdbi.withHandle(handle ->
                handle.createQuery(glCheckSql)
                    .bind("siteCode", site.code)
                    .mapTo(Long.class)
                    .one()
            );
            if (count > 0) return site;
            Log.warn("[DB] Site '{}' (code={}) has no Cash/Bank GL — skipping.", site.name, site.code);
        }

        throw new IllegalStateException(
            "No OS-OO-CM site with an active Cash/Bank GL found for ecode=" + ecode +
            ". Checked: " + candidates.stream()
                .map(s -> s.name + "(code=" + s.code + ")")
                .collect(java.util.stream.Collectors.joining(", ")));
    }

    /**
     * Resolves the target {@link CashBankType} for GL selection.
     * Priority: variant.glType → opts.glType → random fallback (picks CASH or BANK randomly).
     */
    private static CashBankType resolveTargetCashBankType(
            ArVoucherFixtureOptions opts, SelectDocumentVariant variant) {
        if (variant != null && variant.getGlType() != null) {
            return variant.getGlType();
        }
        if (opts != null && opts.getGlType() != null) {
            return opts.getGlType();
        }
        // Legacy fallback — should not be reached with properly tagged variants
        return FakerUtils.randomElement(List.of(CashBankType.CASH, CashBankType.BANK));
    }

    /**
     * Picks a random active GL of the given type scoped to the owner site.
     * {@code srctype='C'} = Cash, {@code srctype='B'} = Bank, {@code ext='N'} = active.
     */
    private static String pickGlByType(Jdbi jdbi, DbDialect d, List<Long> siteCodes, CashBankType type) {
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
            throw new IllegalStateException(
                    "No active " + type.label() + " GL (srctype='" + type.srcType()
                    + "', ext='N') found in fingl for sites: " + siteCodes
                    + ". Ensure at least one " + type.label() + " GL exists for the owner site.");
        }
        return FakerUtils.randomElement(results);
    }

    // ─── Logging helpers ──────────────────────────────────────────────────────

    /**
     * P3: Builds a structured log of the DB data-gathering phase and stores it
     * in {@link MergedFinanceArVoucherData#getDbGatheringLog()} for report rendering.
     */
    private static void buildAndSetGatheringLog(
            String loginName, long ecode,
            ArVoucherLiveDbRefs refs,
            java.util.Map<String, List<com.gsl.model.finance.PendingSlDocument>> pendingDocsBySlName,
            MergedFinanceArVoucherData data,
            List<String> executedQueries) {

        List<String> log = new java.util.ArrayList<>();

        // ── Resolved References ───────────────────────────────────────────
        log.add("═══════════════════════════════════════════════════════════");
        log.add("  DB DATA GATHERING — RESOLVED REFERENCES");
        log.add("═══════════════════════════════════════════════════════════");
        log.add("  Login User   : " + loginName + "  (ecode=" + ecode + ")");
        log.add("  Owner Site   : " + refs.getOwnerSiteName());
        log.add("  Cash/Bank GL : " + refs.getCashBankName() + "  (" + refs.getCashBankType().label() + ")");
        log.add("  Doc Scheme   : " + refs.getDocSchemeName());
        if (refs.getDocSchemeNarrative() != null) {
            log.addAll(refs.getDocSchemeNarrative());
        }
        log.add("  Pending SLs  : " + refs.getPendingSlNames());

        if (!pendingDocsBySlName.isEmpty()) {
            log.add("  Open Docs / SL:");
            pendingDocsBySlName.forEach((key, docs) -> {
                log.add("    [" + key + "]");
                docs.forEach(doc -> log.add(
                    "      → DocNo=" + doc.getDocumentNo()
                    + "  Date=" + doc.getDocumentDate()
                    + "  Type=" + doc.getEntype()
                    + "  Pending=" + doc.getPending()
                ));
            });
        }

        // ── Diagnostic SQL (pending SL lookup) ────────────────────────────
        log.add("");
        log.add("───────────────────────────────────────────────────────────");
        log.add("  KEY QUERY — Pending SL / Open Document lookup");
        log.add("───────────────────────────────────────────────────────────");
        log.add("  SELECT DISTINCT pa.slname, pa.slid, ac.clsname, ac.cmpcode");
        log.add("  FROM   v_pending_adj pa");
        log.add("  JOIN   finsl fs  ON fs.slname = pa.slname AND fs.slid = pa.slid");
        log.add("  JOIN   admcls ac ON ac.clscode = fs.clscode");
        log.add("  WHERE  pa.admou_code IN (ouCodes)");
        log.add("    AND  pa.ReferenceSiteCode IN (ownerSiteCodes)");
        log.add("    AND  pa.debit_credit = 'D'");
        log.add("    AND  pa.pending > 0");
        log.add("    AND  fs.ext = 'N'");
        log.add("  ORDER BY pa.slname;");

        // ── Generated Data Summary ────────────────────────────────────────
        log.add("");
        log.add("───────────────────────────────────────────────────────────");
        log.add("  GENERATED TEST DATA SUMMARY");
        log.add("───────────────────────────────────────────────────────────");
        log.add("  Scenario   : " + data.getScenarioType());
        if (data.getMasterTestData() != null) {
            log.add("  TestCaseId : " + data.getMasterTestData().getTestcaseid());
            log.add("  TestCase   : " + data.getMasterTestData().getTestcasename());
        }
        if (data.getDocumentInformation() != null) {
            log.add("  Owner Site : " + data.getDocumentInformation().getOwnersite());
            log.add("  Cash/Bank  : " + data.getDocumentInformation().getCashbank());
            log.add("  Doc Scheme : " + data.getDocumentInformation().getVoucherno());
            log.add("  Doc Date   : " + data.getDocumentInformation().getDocumentdate());
            log.add("  Reference  : " + data.getDocumentInformation().getReference());
            log.add("  Remarks    : " + data.getDocumentInformation().getRemarks());
        }
        if (data.getSubLedgerInformationList() != null) {
            log.add("  Sub Ledgers:");
            data.getSubLedgerInformationList().forEach(sl ->
                log.add("    [" + sl.getLineNo() + "] " + sl.getSubLedger()
                    + "  (id=" + sl.getId() + ")"
                    + "  adjType=" + sl.getAdjustmentType()
                    + "  amount=" + sl.getAmountCr()
                    + (sl.getSlType() != null ? "  slType=" + sl.getSlType() : "")));
        }
        if (data.getSelectDocumentList() != null && !data.getSelectDocumentList().isEmpty()) {
            log.add("  Select Documents:");
            data.getSelectDocumentList().forEach(sd ->
                log.add("    lineNo=" + sd.getLineNo()
                    + "  docNo=" + sd.getDocumentNumber()
                    + "  date=" + sd.getDocumentDate()
                    + "  adjAmt=" + sd.getAdjustmentAmount()));
        }
        if (!executedQueries.isEmpty()) {
            log.add("");
            log.add("───────────────────────────────────────────────────────────");
            log.add("  QUERIES EXECUTED DURING DATA GATHERING");
            log.add("───────────────────────────────────────────────────────────");
            log.addAll(executedQueries);
        }

        log.add("═══════════════════════════════════════════════════════════");

        data.setDbGatheringLog(log);
        log.forEach(line -> Log.info("[DB] {}", line));
    }

    /** Logs the resolved live-DB reference values ({@code refs}) and each SL's open documents. */
    private static void printRefs(String loginName, long ecode,
                                  ArVoucherLiveDbRefs refs,
                                  Map<String, List<PendingSlDocument>> pendingDocsBySlName) {
        Log.info("[DB] ============================================================");
        Log.info("[DB] Live DB reference values resolved:");
        Log.info("[DB]   Login user   = {} (ecode={})", loginName, ecode);
        Log.info("[DB]   Owner Site   = {}", refs.getOwnerSiteName());
        Log.info("[DB]   Cash/Bank GL = {} ({})", refs.getCashBankName(), refs.getCashBankType().label());
        Log.info("[DB]   Doc Scheme   = {}", refs.getDocSchemeName());
        Log.info("[DB]   Pending SLs  = {}", refs.getPendingSlNames());
        Log.info("[DB]   SL→ARAP map  = {}", refs.getSlToArApName());
        Log.info("[DB]   Open docs/SL :");
        pendingDocsBySlName.forEach((key, docs) -> {
            Log.info("[DB]     [{}]", key);
            docs.forEach(doc -> Log.info("[DB]       -> {} | date={} | type={} | refNo={} | site={} | pending={}",
                    doc.getDocumentNo(), doc.getDocumentDate(), doc.getEntype(),
                    doc.getRefNo(), doc.getReferenceSite(), doc.getPending()));
        });
        Log.info("[DB] ============================================================");
    }

    /** Logs the fully-built {@code row}'s header/sub-ledger/select-document fields. */
    private static void printGeneratedData(MergedFinanceArVoucherData row) {
        Log.info("[DB] ------------------------------------------------------------");
        Log.info("[DB]   Scenario   = {}", row.getScenarioType());
        Log.info("[DB]   TestCaseId = {}", row.getMasterTestData().getTestcaseid());
        Log.info("[DB]   TestCase   = {}", row.getMasterTestData().getTestcasename());
        Log.info("[DB]   Owner Site = {}", row.getDocumentInformation().getOwnersite());
        Log.info("[DB]   Cash/Bank  = {}", row.getDocumentInformation().getCashbank());
        Log.info("[DB]   Doc Scheme = {}", row.getDocumentInformation().getVoucherno());
        Log.info("[DB]   Doc Date   = {}", row.getDocumentInformation().getDocumentdate());
        Log.info("[DB]   Reference  = {}", row.getDocumentInformation().getReference());
        Log.info("[DB]   Remarks    = {}", row.getDocumentInformation().getRemarks());
        Log.info("[DB]   SubLedgers :");
        row.getSubLedgerInformationList().forEach(sl ->
                Log.info("[DB]     [{}] {} (id={}) | adjType={} | amount={}",
                        sl.getLineNo(), sl.getSubLedger(), sl.getId(),
                        sl.getAdjustmentType(), sl.getAmountCr()));
        if (row.getSelectDocumentList() != null && !row.getSelectDocumentList().isEmpty()) {
            Log.info("[DB]   SelectDocs :");
            row.getSelectDocumentList().forEach(sd ->
                    Log.info("[DB]     lineNo={} | docNo={} | date={} | type={} | amount={}",
                            sd.getLineNo(), sd.getDocumentNumber(), sd.getDocumentDate(),
                            sd.getDocumentType(), sd.getAdjustmentAmount()));
        }
        Log.info("[DB] ============================================================");
    }
}
