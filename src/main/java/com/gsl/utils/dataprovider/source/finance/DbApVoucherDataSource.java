package com.gsl.utils.dataprovider.source.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.config.WaitConfig;
import com.gsl.database.DatabaseManager;
import com.gsl.database.DocumentReservationRegistry;
import com.gsl.database.ReferenceSiteAmountRegistry;
import com.gsl.database.DocumentSelectionResult;
import com.gsl.database.dao.HrEmpDao;
import com.gsl.database.dao.HrEmpOuDao;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.database.dao.finance.FinSlDao;
import com.gsl.database.dialect.DbDialect;
import com.gsl.database.dialect.DbDialectFactory;
import com.gsl.database.query.AdmClsQuery;
import com.gsl.database.query.PendingAdjQuery;
import com.gsl.database.dao.finance.FinTdsExceptionDao;
import com.gsl.database.dao.finance.FinTdsTypeDao;
import com.gsl.database.query.TdsNameQuery;
import com.gsl.enums.CashBankType;
import com.gsl.enums.DocumentSelectionMode;
import com.gsl.enums.Finance.Operations.APVoucher.TdsApplicableAmountBehaviour;
import com.gsl.model.finance.AdmCls;
import com.gsl.model.finance.FinGl;
import com.gsl.model.finance.FinSl;
import com.gsl.model.finance.PendingSlDocument;
import com.gsl.model.finance.SlNameWithId;
import com.gsl.model.finance.TdsResolution;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.Faker.FakerUtils;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.fixture.ApVoucherFixtureFactory;
import com.gsl.utils.dataprovider.fixture.LiveDbRefs;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherExecutionGroup;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherFixtureOptions;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherScenarioType;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherTag;
import com.gsl.utils.dataprovider.source.TestDataSource;
import org.jdbi.v3.core.Jdbi;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads AP Voucher test data by querying the live database.
 *
 * <p>Mirrors {@code DbArVoucherDataSource} with these AP-specific differences:
 * <ol>
 *   <li><b>Advance payment</b> (On Account = Yes): vendor SLs are fetched directly from
 *       {@code finsl} — no pending documents needed. {@code pendingDocsBySlName} is empty.</li>
 *   <li><b>Document payment</b> (On Account = No): pending AP docs use
 *       {@code debit_credit='C'} (AR uses {@code 'D'}).</li>
 *   <li>Doc scheme shortcode: {@code 'FinanceAPVoucher'} (AR uses {@code 'FinanceARVoucher'}).</li>
 *   <li>Group property: {@code apv.group} (AR uses {@code arv.group}).</li>
 *   <li>Tag property: {@code apv.tag} — orthogonal filter selecting ADVANCE/DOCUMENT paths
 *       and dimensional variants (GL type, TDS, etc.).</li>
 * </ol>
 *
 * <p><b>Lazy placeholder pattern:</b> {@link #load} emits lightweight placeholder objects
 * (one per applicable scenario type). Each placeholder carries only the {@code scenarioType}
 * and fixture options. At test-execution time the test method calls
 * {@link #loadFreshForScenario} to fetch real DB data for that specific scenario.
 */
public class DbApVoucherDataSource implements TestDataSource<MergedFinanceApVoucherData> {

    // Captured from TestNG <parameter name="apv.group"> at construction time.
    private final String xmlGroupParam;

    // Captured from TestNG <parameter name="apv.tag"> at construction time.
    private final String xmlTagParam;

    public DbApVoucherDataSource() {
        this.xmlGroupParam = null;
        this.xmlTagParam   = null;
    }

    public DbApVoucherDataSource(String xmlGroupParam) {
        this.xmlGroupParam = xmlGroupParam;
        this.xmlTagParam   = null;
    }

    public DbApVoucherDataSource(String xmlGroupParam, String xmlTagParam) {
        this.xmlGroupParam = xmlGroupParam;
        this.xmlTagParam   = xmlTagParam;
    }

    // ─── Point 1: Placeholder generation — called at DataSupplier time ────────

    @Override
    public List<MergedFinanceApVoucherData> load(String testMethodName) {
        String loginName = ConfigFactory.getConfig().username();

        ApVoucherExecutionGroup activeGroup = resolveActiveGroup();
        Set<ApVoucherTag>       activeTags  = resolveActiveTags();

        Log.info("[DB] APVoucher execution-group filter: {}",
                activeGroup != null ? activeGroup : "ALL (no filter)");
        Log.info("[DB] APVoucher tag filter: {}",
                activeTags.isEmpty() ? "ALL (no filter)" : activeTags);

        List<ApVoucherScenarioType> scenarios = ApVoucherFixtureFactory.scenariosFor(testMethodName);

        Jdbi jdbi        = DatabaseManager.getJdbi();
        List<Long> ouCodes = resolveOuCodesForUser(jdbi, loginName);

        // Only run the AP-pending discovery when document scenarios are present
        boolean hasDocScenarios = scenarios.stream().anyMatch(s -> !s.isOnAccount());
        List<AdmCls> classificationTypes = hasDocScenarios
                ? findDistinctClsCodesInPendingAP(jdbi, ouCodes)
                : List.of();

        long systemClsCode = classificationTypes.stream()
                .filter(c -> c.getCmpcode() == 1)
                .mapToLong(AdmCls::getClscode)
                .findFirst().orElse(0L);
        long userClsCode = classificationTypes.stream()
                .filter(c -> c.getCmpcode() == 0)
                .mapToLong(AdmCls::getClscode)
                .findFirst().orElse(0L);

        if (hasDocScenarios) {
            Log.info("[DB] AP Discovered {} SL classification type(s) with pending AP: {} | systemClsCode={}, userClsCode={}",
                    classificationTypes.size(),
                    classificationTypes.stream()
                            .map(c -> c.getClsname() + "(" + c.cmpLabel() + ")")
                            .collect(Collectors.joining(", ")),
                    systemClsCode, userClsCode);
        }

        List<MergedFinanceApVoucherData> placeholders = new ArrayList<>();

        for (ApVoucherScenarioType scenario : scenarios) {
            List<MergedFinanceApVoucherData> batch = new ArrayList<>();

            if (scenario.isOnAccount()) {
                // ── Advance Payment ────────────────────────────────────────────
                if (scenario == ApVoucherScenarioType.ADVANCE_NO_TDS) {
                    if (matchesGroup(activeGroup, ApVoucherExecutionGroup.MANDATORY)) {
                        ApVoucherFixtureOptions opts = ApVoucherFixtureOptions.forAdvance(CashBankType.BANK);
                        batch.add(buildPlaceholder(testMethodName, loginName, scenario, opts,
                                "DB_ADV_NO_TDS_MAND",
                                "ADVANCE | [Bank GL] | No TDS"));
                    }
                } else if (scenario == ApVoucherScenarioType.ADVANCE_MULTI_SL_WITH_PAYMENT) {
                    if (matchesGroup(activeGroup, ApVoucherExecutionGroup.REGRESSION_PHASE2)) {
                        batch.add(buildPlaceholder(testMethodName, loginName, scenario,
                                ApVoucherFixtureOptions.forAdvanceMultiSl(CashBankType.BANK),
                                "DB_ADV_MULTI_SL_PAY_REG2",
                                "ADVANCE | [Bank GL] | Multi-SL | Mixed Payment"));
                    }
                } else if (scenario == ApVoucherScenarioType.ADVANCE_WITH_TDS) {

                    // ── MANDATORY: GROSS_EQUALS_TDS (baseline smoke) ──────────────────────
                    if (matchesGroup(activeGroup, ApVoucherExecutionGroup.MANDATORY)) {
                        batch.add(buildPlaceholder(testMethodName, loginName, scenario,
                                ApVoucherFixtureOptions.forAdvanceWithTds(CashBankType.BANK,
                                        TdsApplicableAmountBehaviour.GROSS_EQUALS_TDS),
                                "DB_ADV_TDS_MAND",
                                "ADVANCE | [Bank GL] | TDS | Gross=TDS (Smoke)"));
                    }

                    // ── REGRESSION_PHASE1: PARTIAL_EDIT ──────────────────────────────────
                    if (matchesGroup(activeGroup, ApVoucherExecutionGroup.REGRESSION_PHASE1)) {
                        batch.add(buildPlaceholder(testMethodName, loginName, scenario,
                                ApVoucherFixtureOptions.forAdvanceWithTds(CashBankType.BANK,
                                        TdsApplicableAmountBehaviour.PARTIAL_EDIT),
                                "DB_ADV_TDS_PE_REG1",
                                "ADVANCE | [Bank GL] | TDS | Partial Edit"));
                    }

                    // ── REGRESSION_PHASE2: CLEAR_ERROR_THEN_PARTIAL_EDIT ─────────────────
                    if (matchesGroup(activeGroup, ApVoucherExecutionGroup.REGRESSION_PHASE2)) {
                        batch.add(buildPlaceholder(testMethodName, loginName, scenario,
                                ApVoucherFixtureOptions.forAdvanceWithTds(CashBankType.BANK,
                                        TdsApplicableAmountBehaviour.CLEAR_ERROR_THEN_PARTIAL_EDIT),
                                "DB_ADV_TDS_CEPT_REG2",
                                "ADVANCE | [Bank GL] | TDS | Clear Error Then Partial Edit"));
                    }

                // ── TDS Nature Regression: Scenario 1 — no natures tagged ──────────────
                } else if (scenario == ApVoucherScenarioType.ADVANCE_WITH_TDS_NO_NATURE) {
                    if (matchesGroup(activeGroup, ApVoucherExecutionGroup.REGRESSION_PHASE1)) {
                        batch.add(buildPlaceholder(testMethodName, loginName, scenario,
                                ApVoucherFixtureOptions.forAdvanceWithTdsNoNature(CashBankType.BANK),
                                "DB_ADV_TDS_NO_NATURE",
                                "ADVANCE | [Bank GL] | TDS | No Nature Tagged"));
                    }

                // ── TDS Nature Regression: Scenario 2 — exactly 1 nature tagged ─────────
                } else if (scenario == ApVoucherScenarioType.ADVANCE_WITH_TDS_SINGLE_NATURE) {
                    if (matchesGroup(activeGroup, ApVoucherExecutionGroup.REGRESSION_PHASE1)) {
                        batch.add(buildPlaceholder(testMethodName, loginName, scenario,
                                ApVoucherFixtureOptions.forAdvanceWithTdsSingleNature(CashBankType.BANK),
                                "DB_ADV_TDS_SINGLE_NATURE",
                                "ADVANCE | [Bank GL] | TDS | Single Nature Tagged"));
                    }

                // ── T-03 Negative & Boundary (advance path) ─────────────────────────────
                //    One placeholder each; emitted for every group (the negative test method's
                //    suite block sets no apv.group, so activeGroup is null → always matches).
                } else if (scenario == ApVoucherScenarioType.NEG_BLANK_CASHBANK
                        || scenario == ApVoucherScenarioType.NEG_BLANK_SUBLEDGER
                        || scenario == ApVoucherScenarioType.BLANK_REFERENCE_ALLOWED
                        || scenario == ApVoucherScenarioType.BOUND_AMOUNT_MIN_VALID
                        || scenario == ApVoucherScenarioType.BOUND_AMOUNT_MIN_INVALID) {
                    batch.add(buildPlaceholder(testMethodName, loginName, scenario,
                            ApVoucherFixtureOptions.forAdvance(CashBankType.BANK),
                            "DB_" + scenario.name(),
                            scenario.name()));
                }

            } else {
                // ── Document Payment — DOC_SELECT_DOCUMENT / DOC_FIFO / DOC_REFERENCE_SITEWISE_FIFO
                String baseId  = scenarioBaseId(scenario);
                String scenLbl = scenarioShortLabel(scenario);

                if (matchesGroup(activeGroup, ApVoucherExecutionGroup.REGRESSION_PHASE1)) {
                    batch.add(buildPlaceholder(testMethodName, loginName, scenario,
                            ApVoucherFixtureOptions.forCashBankType(CashBankType.CASH),
                            baseId + "_CASH",
                            scenLbl + " | [Cash GL]"));
                }
                if (systemClsCode > 0 && matchesGroup(activeGroup, ApVoucherExecutionGroup.MANDATORY)) {
                    batch.add(buildPlaceholder(testMethodName, loginName, scenario,
                            ApVoucherFixtureOptions.forCashBankTypeAndClsCode(CashBankType.BANK, systemClsCode),
                            baseId + "_BANK_SYS",
                            scenLbl + " | [Bank GL] | System SL"));
                }
                if (userClsCode > 0 && matchesGroup(activeGroup, ApVoucherExecutionGroup.REGRESSION_PHASE1)) {
                    batch.add(buildPlaceholder(testMethodName, loginName, scenario,
                            ApVoucherFixtureOptions.forCashBankTypeAndClsCode(CashBankType.BANK, userClsCode),
                            baseId + "_BANK_USER",
                            scenLbl + " | [Bank GL] | User SL"));
                }
            }

            // Apply tag gate — skip placeholder if it does not match the active tag filter
            for (MergedFinanceApVoucherData ph : batch) {
                if (!activeTags.isEmpty()
                        && Collections.disjoint(activeTags, ph.getTags())) {
                    Log.debug("[DB] Skipping placeholder '{}' — tags {} not in filter {}",
                            ph.getMasterTestData().getTestcaseid(), ph.getTags(), activeTags);
                    continue;
                }
                placeholders.add(ph);
            }
        }

        // Assign sequential invocation indices (FIN-AP-ADD-01, -02, …)
        int total = placeholders.size();
        for (int i = 0; i < total; i++) {
            placeholders.get(i).setInvocationIndex(i + 1);
            placeholders.get(i).setTotalInvocations(total);
        }

        Log.info("[DB] Emitting {} AP Voucher placeholder(s) for method='{}'. DB queries deferred to test-execution time.",
                placeholders.size(), testMethodName);
        return placeholders;
    }

    // ─── Point 2: Fresh data fetch per scenario, called at test-execution time ─

    /**
     * Runs the full DB resolution chain for ONE specific scenario type and returns
     * a fully populated {@link MergedFinanceApVoucherData}.
     *
     * <p>Called from the test method body when a placeholder object is detected.
     * Each scenario sees the freshest possible DB state — documents settled by a prior
     * scenario are not stale here.
     *
     * <p><b>Advance payment</b>: vendor SLs are fetched from {@code finsl} directly;
     * {@code pendingDocsBySlName} is empty.
     * <b>Document payment</b>: pending AP docs are fetched from
     * {@code v_pending_adj WHERE debit_credit='C'} and reserved atomically.
     *
     * @param placeholder the thin placeholder object emitted by {@link #load}
     * @param methodName  the test method name (for logging)
     * @return fully built test data with {@code scenarioType} stamped on it
     * @throws IllegalStateException if insufficient DB data is available for this scenario
     */
    public static MergedFinanceApVoucherData loadFreshForScenario(
            MergedFinanceApVoucherData placeholder, String methodName) {

        ApVoucherScenarioType scenarioType = placeholder.getScenarioType();
        ApVoucherFixtureOptions opts = placeholder.getFixtureOptions() != null
                ? placeholder.getFixtureOptions()
                : ApVoucherFixtureOptions.defaults();

        Log.info("[DB] loadFreshForScenario: scenario={}, method={}, onAccount={}, glType={}, slClsCode={}",
                scenarioType, methodName, scenarioType.isOnAccount(),
                opts.getGlType(), opts.getSlClsCode());

        Jdbi jdbi = DatabaseManager.getJdbi();
        DbDialect d = DbDialectFactory.current();

        // 1. Resolve login user → ecode
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
                    "User '" + loginName + "' (ecode=" + ecode +
                    ") has no site assignments in hrdemp_ou.");
        }

        // 3. First OS-OO-CM owner site that has at least one active Cash/Bank GL
        OwnerSiteRef ownerSite = findFirstUsableOwnerSite(jdbi, d, ecode);
        String ownerSiteName = ownerSite.name;
        List<Long> ownerSiteCodes = List.of(ownerSite.code);

        // 4. Pick a GL matching the target CashBankType for this variant
        CashBankType targetCashBankType = opts.getGlType() != null ? opts.getGlType() : CashBankType.BANK;
        String cashBankName = pickGlByType(jdbi, d, ownerSiteCodes, targetCashBankType);
        Log.info("[DB] AP Picked GL: {} ({})", cashBankName, targetCashBankType.label());

        // 5. SL + document resolution — branches on onAccount flag
        List<SlNameWithId> pendingSlNames;
        Map<String, List<PendingSlDocument>> pendingDocsBySlName;

        if (scenarioType.isOnAccount()) {
            // ── Advance Payment: fetch vendor SLs directly from finsl ─────────
            pendingSlNames = findVendorSlsForAdvance(jdbi, d, opts);
            if (pendingSlNames.isEmpty()) {
                // Data-availability shortfall → skip (amber), not fail (red).
                throw new org.testng.SkipException(
                        "[DbApVoucherDataSource] No usable vendor SLs found in finsl for advance payment." +
                        " ownerSiteCodes=" + ownerSiteCodes +
                        " tdsApplicable=" + opts.isTdsApplicable() +
                        ". Ensure active finsl rows exist for the owner site" +
                        (opts.isTdsApplicable() ? " with tds_app='Y'." : "."));
            }
            pendingDocsBySlName = Collections.emptyMap();

            Log.info("[DB] AP Advance — found {} vendor SL(s): {}",
                    pendingSlNames.size(), pendingSlNames);

        } else {
            // ── Document Payment: fetch pending AP docs (debit_credit='C') ────
            DocumentSelectionResult selection = selectApDocumentsAtomic(
                    jdbi, d, ouCodes, ownerSiteCodes, opts);
            pendingSlNames      = selection.getSelectedSls();
            pendingDocsBySlName = selection.getDocsBySlKey();

            Log.info("[DB] AP Document — selected {} SL(s), attemptsUsed={}, totalOverfetched={}",
                    pendingSlNames.size(), selection.getAttemptsUsed(), selection.getTotalOverfetched());
        }

        // 5b. Resolve GL (AP ledger) name for each selected SL via finsl → fingl
        FinSlDao finSlDao = jdbi.onDemand(FinSlDao.class);
        FinGlDao finGlDao = jdbi.onDemand(FinGlDao.class);
        Map<String, String> slToArApName = new LinkedHashMap<>();
        for (SlNameWithId sl : pendingSlNames) {
            String key = PendingAdjQuery.compositeKey(sl.getSlName(), sl.getSlId());
            FinSl finSl = finSlDao.findBySlnameAndSlid(sl.getSlName(), sl.getSlId());
            if (finSl != null) {
                FinGl finGl = finGlDao.findByGlCode(finSl.getGlcode());
                if (finGl != null) slToArApName.put(key, finGl.getGlname());
            }
        }

        // 5c. Resolve TDS name + tdstype per vendor SL for all ADVANCE_WITH_TDS variants.
        //     Implements the two-path logic: if fintds_exception has rows for this slcode,
        //     only those tdstypecodes are candidates; otherwise all active fintdstype rows.
        //     Pick index = slcode % candidates.size() for variety across vendors.
        Map<String, TdsResolution> slToTdsResolution = new LinkedHashMap<>();
        if (scenarioType.isOnAccount() && opts.isTdsApplicable()) {
            for (SlNameWithId sl : pendingSlNames) {
                String key = PendingAdjQuery.compositeKey(sl.getSlName(), sl.getSlId());
                FinSl finSl = finSlDao.findBySlnameAndSlid(sl.getSlName(), sl.getSlId());
                if (finSl != null) {
                    TdsResolution res = TdsNameQuery.resolve(
                            jdbi, finSl.getSlcode(), opts.getPreferredTdsType());
                    slToTdsResolution.put(key, res);
                    Log.info("[DB] TDS resolved for SL '{}': name='{}', tdstype='{}'",
                            sl.getSlName(), res.getTdsName(), res.getTdsType());
                }
            }
        }

        // 5d. Resolve best reference site per SL for DOC_REFERENCE_SITEWISE_FIFO.
        //     Queries v_pending_adj grouped by ref_admsite_name and reserves amount ceiling
        //     via ReferenceSiteAmountRegistry so parallel tests don't exceed pending.
        Map<String, String> slToRefSiteName = new LinkedHashMap<>();
        if (scenarioType == ApVoucherScenarioType.DOC_REFERENCE_SITEWISE_FIFO) {
            for (SlNameWithId sl : pendingSlNames) {
                String key = PendingAdjQuery.compositeKey(sl.getSlName(), sl.getSlId());
                FinSl finSl = finSlDao.findBySlnameAndSlid(sl.getSlName(), sl.getSlId());
                if (finSl == null) continue;
                BigDecimal slPending = pendingDocsBySlName.getOrDefault(key, List.of())
                        .stream()
                        .map(PendingSlDocument::getPending)
                        .filter(p -> p != null && p.compareTo(BigDecimal.ZERO) > 0)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                String refSiteName = findBestRefSiteForSl(jdbi, d, finSl.getSlcode(), ouCodes, slPending, sl.getSlName(), sl.getSlId());
                if (refSiteName != null) {
                    slToRefSiteName.put(key, refSiteName);
                    Log.info("[DB] RefSiteFIFO SL '{}': refSite='{}', slPending={}", sl.getSlName(), refSiteName, slPending);
                } else {
                    Log.warn("[DB] RefSiteFIFO SL '{}': no reservable reference site found.", sl.getSlName());
                }
            }
        }

        // 6. AP Voucher doc scheme verified for user's site
        String docSchemeName = findApVoucherDocSchemeName(jdbi, d, siteCodes);

        // 7. Package into LiveDbRefs — include tdsBehaviour from fixture options so that
        //    ApVoucherTemplateLoader.buildTdsRows() generates the correct behaviour name
        //    on the TdsAndNetPayable DTO for flow-layer dispatch at test execution time.
        //    slToTdsResolution is only set for TDS-applicable advance scenarios (step 5c above);
        //    slToRefSiteName is only set for DOC_REFERENCE_SITEWISE_FIFO (step 5d above);
        //    both default to empty map for all other scenarios.
        LiveDbRefs refs = LiveDbRefs.builder()
                .cashBankName(cashBankName)
                .ownerSiteName(ownerSiteName)
                .docSchemeName(docSchemeName)
                .pendingSlNames(pendingSlNames)
                .pendingDocsBySlName(pendingDocsBySlName)
                .slToArApName(slToArApName)
                .cashBankType(targetCashBankType)
                .tdsBehaviour(opts.getTdsBehaviour())
                .slToTdsResolution(slToTdsResolution)
                .slToRefSiteName(slToRefSiteName)
                .build();

        printRefs(loginName, ecode, refs, pendingDocsBySlName);

        // 8. Build fully populated test data for this specific scenario
        MergedFinanceApVoucherData data = scenarioType.loadData(refs);

        // 8b. Populate SL Type for each sub-ledger (admcls.clsname via finsl → admcls)
        //     Mirrors the pattern in ExcelApVoucherDataSource and ArVoucherTemplateLoader.
        if (data.getSubLedgerInformationList() != null) {
            data.getSubLedgerInformationList().forEach(sl ->
                    sl.setSlType(AdmClsQuery.findClsnameBySlId(jdbi, sl.getId())));
        }

        printGeneratedData(data);

        // P3: Store gathering log in the data object for the Extent Report
        buildAndSetGatheringLog(loginName, ecode, refs, pendingDocsBySlName, data);

        return data;
    }

    // ─── Group / Tag resolution ───────────────────────────────────────────────

    /**
     * Resolves the active execution group from (1) system property {@code apv.group}
     * (Maven/CI path) or (2) {@link #xmlGroupParam} (IntelliJ direct-run path).
     * Returns {@code null} when neither source provides a value (run all groups).
     */
    private ApVoucherExecutionGroup resolveActiveGroup() {
        String prop = System.getProperty("apv.group");
        if (prop == null || prop.isBlank()) prop = this.xmlGroupParam;
        if (prop == null || prop.isBlank()) return null;
        try {
            return ApVoucherExecutionGroup.valueOf(prop.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            Log.warn("[DB] Unknown apv.group value '{}' — running all groups.", prop);
            return null;
        }
    }

    /**
     * Resolves the active tag filter from (1) system property {@code apv.tag}
     * (Maven/CI path) or (2) {@link #xmlTagParam} (IntelliJ direct-run path).
     * Returns an empty set when neither source provides a value (run all variants).
     *
     * <p>Multiple tags may be comma-separated: {@code -Dapv.tag=ADVANCE,DOC_FIFO}.
     * Matching is OR — a placeholder is included if its tag set intersects the active set.
     * Unknown tag names are logged as warnings and skipped.
     */
    private Set<ApVoucherTag> resolveActiveTags() {
        String prop = System.getProperty("apv.tag");
        if (prop == null || prop.isBlank()) prop = this.xmlTagParam;
        if (prop == null || prop.isBlank()) return Set.of();

        final String finalProp = prop;
        return Arrays.stream(finalProp.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> {
                    try {
                        return ApVoucherTag.valueOf(s.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        Log.warn("[DB] Unknown apv.tag '{}' — ignoring.", s);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ApVoucherTag.class)));
    }

    /**
     * Returns {@code true} when the placeholder should be emitted for the given active group.
     * {@code null} activeGroup = no filter (always true).
     */
    private static boolean matchesGroup(ApVoucherExecutionGroup activeGroup,
                                        ApVoucherExecutionGroup targetGroup) {
        return activeGroup == null || activeGroup == targetGroup;
    }

    /**
     * Assigns the tag set for a given scenario + fixture options pair.
     * Called at placeholder-build time; the result is stored in
     * {@link MergedFinanceApVoucherData#getTags()} and used to gate the tag filter.
     */
    private static Set<ApVoucherTag> tagsFor(ApVoucherScenarioType scenario,
                                              ApVoucherFixtureOptions opts) {
        EnumSet<ApVoucherTag> tags = EnumSet.noneOf(ApVoucherTag.class);

        switch (scenario) {
            case ADVANCE_NO_TDS:
                tags.addAll(EnumSet.of(ApVoucherTag.ADVANCE, ApVoucherTag.ADVANCE_NO_TDS));
                break;
            case ADVANCE_WITH_TDS:
                tags.addAll(EnumSet.of(ApVoucherTag.ADVANCE, ApVoucherTag.ADVANCE_TDS));
                break;
            case ADVANCE_WITH_TDS_NO_NATURE:
            case ADVANCE_WITH_TDS_SINGLE_NATURE:
                tags.addAll(EnumSet.of(ApVoucherTag.ADVANCE, ApVoucherTag.ADVANCE_TDS));
                break;
            case DOC_SELECT_DOCUMENT:
                tags.addAll(EnumSet.of(ApVoucherTag.DOCUMENT, ApVoucherTag.DOC_SELECT_DOCUMENT));
                break;
            case DOC_FIFO:
                tags.addAll(EnumSet.of(ApVoucherTag.DOCUMENT, ApVoucherTag.DOC_FIFO));
                break;
            case DOC_REFERENCE_SITEWISE_FIFO:
                tags.addAll(EnumSet.of(ApVoucherTag.DOCUMENT, ApVoucherTag.DOC_REF_SITEWISE_FIFO));
                break;
        }

        // Dimensional tags from fixture options
        if (opts != null) {
            if (opts.getGlType() == CashBankType.CASH) tags.add(ApVoucherTag.CASH_GL);
            if (opts.getGlType() == CashBankType.BANK) tags.add(ApVoucherTag.BANK_GL);
            if (opts.getSubLedgerCount() > 1)          tags.add(ApVoucherTag.MULTI_SL);
            if (opts.isTdsApplicable())                 tags.add(ApVoucherTag.ADVANCE_TDS);
        }

        return Collections.unmodifiableSet(tags);
    }

    // ─── Placeholder builder ──────────────────────────────────────────────────

    /** Constructs a minimal placeholder carrying only scenarioType + fixture options. */
    private static MergedFinanceApVoucherData buildPlaceholder(
            String methodName, String loginName,
            ApVoucherScenarioType scenario, ApVoucherFixtureOptions opts,
            String testCaseId, String testCaseName) {

        MasterTestData master = new MasterTestData(
                testCaseName, null, null, null, testCaseId, 1);

        Set<ApVoucherTag> tags = tagsFor(scenario, opts);

        return MergedFinanceApVoucherData.builder()
                .scenarioType(scenario)
                .masterTestData(master)
                .subLedgerInformationList(List.of())   // empty list = placeholder signal
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(List.of())
                .tdsAndNetPayableList(List.of())
                .onAccount(scenario.isOnAccount())
                .tdsProvided(false)
                .paymentProvided(false)
                .needsPaymentInfoWarning(false)
                .invocationIndex(1)
                .totalInvocations(1)
                .fixtureOptions(opts)
                .tags(tags)
                .build();
    }

    /** Resolves the list of OU codes for the given login user at DataSupplier time. */
    private static List<Long> resolveOuCodesForUser(Jdbi jdbi, String loginName) {
        HrEmpDao hrEmpDao = jdbi.onDemand(HrEmpDao.class);
        long ecode = hrEmpDao.findEcodeByAbbre(loginName)
                .orElseThrow(() -> new IllegalStateException(
                        "No hrdemp record found for login user: '" + loginName +
                        "'. Check appusername in config.properties."));
        HrEmpOuDao hrOuDao = jdbi.onDemand(HrEmpOuDao.class);
        return hrOuDao.findOuCodesByEcode(ecode);
    }

    // ─── Reference Site Wise FIFO — ref site resolution ──────────────────────

    /**
     * Queries {@code v_pending_adj} for the given SL, grouped by {@code ref_admsite_name},
     * and returns the name of the first reference site for which
     * {@link ReferenceSiteAmountRegistry#tryReserve} succeeds.
     *
     * <p>Sites are ordered by descending pending amount so the largest pool is tried first.
     * Returns {@code null} if all candidates are already fully reserved by parallel threads.
     *
     * @param slCode     numeric finsl.slcode for the sub-ledger
     * @param ouCodes    user's OU codes — scopes the query to the user's organisation
     * @param slPending  total pending AP amount for this SL (from v_pending_adj documents)
     * @param slName     sub-ledger name (used as registry key component)
     * @param slId       sub-ledger ID (used as registry key component)
     */
    private static String findBestRefSiteForSl(Jdbi jdbi, DbDialect d,
                                               long slCode, List<Long> ouCodes,
                                               BigDecimal slPending,
                                               String slName, String slId) {
        if (ouCodes == null || ouCodes.isEmpty()) return null;

        String inList = ouCodes.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        String sql =
            "SELECT ref_admsite_name, SUM(pending) AS site_pending " +
            "FROM v_pending_adj " +
            "WHERE slcode = :slCode " +
            "  AND admou_code IN (" + inList + ") " +
            "  AND debit_credit = 'C' " +
            "  AND pending > 0 " +
            "  AND " + d.castToDate("document_date") + " <= " + d.currentDate() + " " +
            "  AND ref_admsite_name IS NOT NULL " +
            "GROUP BY ref_admsite_name " +
            "ORDER BY site_pending DESC " +
            d.limitBind("lim");

        List<String[]> candidates = jdbi.withHandle(handle ->
            handle.createQuery(sql)
                .bind("slCode", slCode)
                .bind("lim", 10)
                .map((rs, ctx) -> new String[]{
                        rs.getString("ref_admsite_name"),
                        rs.getBigDecimal("site_pending").toPlainString()})
                .list()
        );

        for (String[] row : candidates) {
            String refSiteName = row[0];
            BigDecimal sitePending = new BigDecimal(row[1]);
            // Use slPending as the reservation amount — voucher amount equals total SL pending.
            // Registry ceiling = sitePending (can't adjust more than ref site has pending).
            BigDecimal ceiling = sitePending.min(slPending.compareTo(BigDecimal.ZERO) > 0
                    ? slPending : sitePending);
            if (ReferenceSiteAmountRegistry.tryReserve(slName, slId, refSiteName, ceiling, sitePending)) {
                return refSiteName;
            }
        }
        return null;
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
     * active Cash or Bank GL. Sites with no allocated Cash/Bank GL are skipped.
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
            Log.warn("[DB] AP Site '{}' (code={}) has no Cash/Bank GL — skipping.", site.name, site.code);
        }

        throw new IllegalStateException(
            "No OS-OO-CM site with an active Cash/Bank GL found for ecode=" + ecode +
            ". Checked: " + candidates.stream()
                .map(s -> s.name + "(code=" + s.code + ")")
                .collect(Collectors.joining(", ")));
    }

    /**
     * Picks a random active GL of the given type scoped to the owner site.
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
                    + "', ext='N') found in fingl for sites: " + siteCodes);
        }
        return FakerUtils.randomElement(results);
    }

    /**
     * Finds the AP Voucher doc scheme name that is:
     * (1) Linked to {@code 'FinanceAPVoucher'} menu shortcode in admmenu_docscheme
     * (2) Assigned to at least one of the user's sites in admou_docscheme
     */
    private static String findApVoucherDocSchemeName(Jdbi jdbi, DbDialect d, List<Long> siteCodes) {
        String sql =
            "SELECT d.docname " +
            "FROM admdocscheme d " +
            "WHERE d.doccode IN (" +
            "    SELECT mds.doccode FROM admmenu_docscheme mds " +
            "    WHERE mds.mdmenushortcode = 'FinanceAPVoucher'" +
            ") " +
            "AND d.doccode IN (" +
            "    SELECT ods.doccode FROM admou_docscheme ods " +
            "    WHERE ods.admsite_code IN (<siteCodes>)" +
            ") " +
            d.limit1();

        Optional<String> result = jdbi.withHandle(handle ->
            handle.createQuery(sql)
                .bindList("siteCodes", siteCodes)
                .mapTo(String.class)
                .findFirst()
        );
        return result.orElseThrow(() -> new IllegalStateException(
                "No AP Voucher doc scheme found for sites: " + siteCodes +
                ". Check admmenu_docscheme WHERE mdmenushortcode='FinanceAPVoucher' " +
                "and admou_docscheme for the user's sites."));
    }

    /**
     * Returns all distinct {@code admcls} classification codes that have at least one
     * vendor SL with pending AP documents ({@code debit_credit='C'}) for the given OU codes.
     *
     * <p>AP equivalent of {@code AdmClsQuery.findDistinctClsCodesInPendingAR}.
     */
    private static List<AdmCls> findDistinctClsCodesInPendingAP(Jdbi jdbi, List<Long> ouCodes) {
        String sql =
            "SELECT DISTINCT ac.clscode, ac.cmpcode, ac.clsname, ac.clstype, ac.ext " +
            "FROM v_pending_adj pa " +
            "JOIN finsl fs ON fs.slname = pa.slname AND fs.slid = pa.slid " +
            "JOIN admcls ac ON ac.clscode = fs.clscode " +
            "WHERE pa.admou_code IN (<ouCodes>) " +
            "  AND pa.debit_credit = 'C' " +
            "  AND pa.pending > 0 " +
            "  AND ac.ext = 'N' " +
            "ORDER BY ac.clscode";

        return jdbi.withHandle(handle ->
            handle.createQuery(sql)
                .bindList("ouCodes", ouCodes)
                .map((rs, ctx) -> {
                    AdmCls ac = new AdmCls();
                    ac.setClscode(rs.getLong("clscode"));
                    ac.setCmpcode(rs.getInt("cmpcode"));
                    ac.setClsname(rs.getString("clsname"));
                    ac.setClstype(rs.getString("clstype"));
                    ac.setExt(rs.getString("ext"));
                    return ac;
                })
                .list()
        );
    }

    // ─── Advance Payment — vendor SL lookup ───────────────────────────────────

    /**
     * Fetches vendor sub-ledger rows directly from {@code finsl} for Advance Payment scenarios.
     *
     * <p>Selects active vendor SLs from {@code finsl}.
     * When {@code opts.isTdsApplicable()} is {@code true}, restricts to TDS-applicable
     * vendor SLs ({@code finsl.tds_app = 'Y'}).
     *
     * <p>Returns up to {@code opts.subLedgerCount} results ordered by {@code slname}.
     */
    private static List<SlNameWithId> findVendorSlsForAdvance(
            Jdbi jdbi, DbDialect d, ApVoucherFixtureOptions opts) {

        String tdsFilter = opts.isTdsApplicable() ? "  AND fs.tds_app = 'Y' " : "";

        // Nature-count subquery filter for TDS regression scenarios.
        // Narrows vendor selection to only those matching the target nature-tagging situation.
        // null tdsNatureMode = no filter (smoke baseline — any tds_app='Y' vendor).
        String natureFilter = "";
        if (opts.getTdsNatureMode() != null) {
            natureFilter = switch (opts.getTdsNatureMode()) {
                case ANY_NATURE ->
                    // Regression 1: no natures tagged (53 vendors in DB)
                    " AND NOT EXISTS (" +
                    "   SELECT 1 FROM fintds_exception fe WHERE fe.slcode = fs.slcode" +
                    ") ";
                case SINGLE_NATURE ->
                    // Regression 2: exactly 1 nature tagged (e.g. AARUCHI FASHIONS)
                    " AND (SELECT COUNT(*) FROM fintds_exception fe" +
                    "      WHERE fe.slcode = fs.slcode) = 1 ";
                case MULTI_NATURE_ANY ->
                    // Regression 3: multiple natures tagged (no vendors in current DB)
                    " AND (SELECT COUNT(*) FROM fintds_exception fe" +
                    "      WHERE fe.slcode = fs.slcode) > 1 ";
                case MULTI_NATURE_BY_TYPE ->
                    // Regression 4: multiple natures tagged AND at least one matches preferred tdstype
                    " AND (SELECT COUNT(*) FROM fintds_exception fe" +
                    "      WHERE fe.slcode = fs.slcode) > 1 " +
                    " AND EXISTS (" +
                    "   SELECT 1 FROM fintds_exception fe2" +
                    "   JOIN fintdstype ft ON ft.tdstypecode = fe2.tdstypecode" +
                    "   WHERE fe2.slcode = fs.slcode" +
                    "   AND ft.tdstype = '" + opts.getPreferredTdsType() + "'" +
                    "   AND ft.ext = 'N'" +
                    ") ";
            };
        }

        String sql =
            "SELECT DISTINCT fs.slname, fs.slid " +
            "FROM finsl fs " +
            "WHERE fs.ext = 'N' " +
            tdsFilter +
            natureFilter +
            "ORDER BY fs.slname " +
            d.limitBind("lim");

        return jdbi.withHandle(handle ->
            handle.createQuery(sql)
                .bind("lim", Math.max(1, opts.getSubLedgerCount()))
                .map((rs, ctx) -> new SlNameWithId(
                        rs.getString("slname"),
                        rs.getString("slid")))
                .list()
        );
    }

    // ─── Document Payment — AP-specific atomic document selection ─────────────

    /**
     * Atomic SL + AP document selection with over-fetch and retry.
     *
     * <p>AP equivalent of {@code DocumentSelector.selectAtomic} — uses
     * {@code debit_credit='C'} in all underlying queries (AP = Credit).
     * Still uses {@link DocumentReservationRegistry#filterAndReserve} for cross-thread
     * document uniqueness, ensuring AP tests don't conflict with each other.
     *
     * <p>AR (debit_credit='D') and AP (debit_credit='C') queries operate on different
     * rows in {@code v_pending_adj}, so AR and AP tests never compete for the same
     * document numbers.
     *
     * @throws IllegalStateException if insufficient AP documents are available after all retries
     */
    private static DocumentSelectionResult selectApDocumentsAtomic(
            Jdbi jdbi, DbDialect d,
            List<Long> ouCodes, List<Long> ownerSiteCodes,
            ApVoucherFixtureOptions opts) {

        int  subLedgerCount = Math.max(1, opts.getSubLedgerCount());
        int  documentCount  = Math.max(1, opts.getDocumentCount());
        long clsCode        = opts.getSlClsCode();

        WaitConfig fcfg = ConfigFactory.getWaitConfig();
        long retryBackoff = fcfg.docSelectorRetryBackoffMs();
        DocumentSelectionMode mode = DocumentSelectionMode.fromString(fcfg.docSelectorMode());

        long distinctCount = countDistinctApSlCodes(jdbi, d, ouCodes, ownerSiteCodes, clsCode);
        int  maxAttempts   = (int) Math.max(1L, distinctCount);
        // Ensure the candidate query retrieves ALL distinct SLs in one shot — if overfetch is
        // smaller than distinctCount, some SLs are cut off by LIMIT and never tried, which is
        // the root cause of "did not try all subledgers" EXHAUSTED failures.
        int  overfetch = Math.max(
                (int) Math.max(1L, distinctCount),
                subLedgerCount * Math.max(1, fcfg.docSelectorOverfetchMultiplier()));

        Log.info("[DocSelectorAP] distinctSlCount={} → maxAttempts={}", distinctCount, maxAttempts);

        int totalOverfetched   = 0;
        int lastCandidatesSize = 0;
        int lastReservedSize   = 0;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {

            List<SlNameWithId> candidates = findApSlCandidates(
                    jdbi, d, ouCodes, ownerSiteCodes, clsCode, overfetch);
            lastCandidatesSize = candidates.size();
            totalOverfetched  += candidates.size();

            if (candidates.isEmpty()) {
                Log.warn("[DocSelectorAP] attempt {}/{} — no AP candidate SLs in DB.",
                        attempt + 1, maxAttempts);
                if (attempt < maxAttempts - 1) sleepMs(retryBackoff);
                continue;
            }

            Map<String, List<PendingSlDocument>> rawDocs =
                    findApDocumentsForSlNames(jdbi, d, ouCodes, ownerSiteCodes, candidates, documentCount);

            Map<String, List<PendingSlDocument>> reserved =
                    DocumentReservationRegistry.filterAndReserve(rawDocs);
            lastReservedSize = reserved.size();

            Map<String, List<PendingSlDocument>> moded = applyMode(reserved, mode);

            // Trim to requested SL count
            List<SlNameWithId>                    kept     = new ArrayList<>(subLedgerCount);
            Map<String, List<PendingSlDocument>>  keptDocs = new LinkedHashMap<>();
            List<String>                          surplus  = new ArrayList<>();

            for (SlNameWithId sl : candidates) {
                String key  = PendingAdjQuery.compositeKey(sl.getSlName(), sl.getSlId());
                List<PendingSlDocument> docs = moded.get(key);
                if (docs == null || docs.isEmpty()) continue;

                if (kept.size() < subLedgerCount) {
                    kept.add(sl);
                    keptDocs.put(key, docs);
                } else {
                    docs.forEach(doc -> { if (doc.getDocumentNo() != null) surplus.add(doc.getDocumentNo()); });
                }
            }
            if (!surplus.isEmpty()) {
                DocumentReservationRegistry.release(surplus);
            }

            Log.info("[DocSelectorAP] attempt={}/{} candidates={} reservedSls={} kept={}",
                    attempt + 1, maxAttempts, candidates.size(), lastReservedSize, kept.size());

            if (kept.size() >= subLedgerCount) {
                return new DocumentSelectionResult(kept, keptDocs, attempt, totalOverfetched);
            }

            if (attempt < maxAttempts - 1) sleepMs(retryBackoff);
        }

        // Data-availability shortfall (no pending AP documents to adjust) → skip, not fail.
        throw new org.testng.SkipException(
            "[DbApVoucherDataSource] EXHAUSTED — could not select " + subLedgerCount +
            " AP SL(s) after " + maxAttempts + " attempt(s).\n" +
            "  Inputs: ouCodes=" + ouCodes + ", ownerSiteCodes=" + ownerSiteCodes +
            ", clsCode=" + clsCode + ", documentCount=" + documentCount +
            ", maxAttempts=" + maxAttempts +
            "\n  Last: candidates=" + lastCandidatesSize + ", reservedSls=" + lastReservedSize +
            "\n" + DocumentReservationRegistry.snapshotAsString());
    }

    /**
     * Fetches candidate AP SL names from {@code v_pending_adj WHERE debit_credit='C'}.
     * Routes through clsCode filter when {@code clsCode > 0}.
     */
    private static List<SlNameWithId> findApSlCandidates(
            Jdbi jdbi, DbDialect d,
            List<Long> ouCodes, List<Long> ownerSiteCodes,
            long clsCode, int limit) {

        if (clsCode > 0) {
            // clsCode-filtered path
            String sql =
                "SELECT DISTINCT pa.slname, pa.slid " +
                "FROM v_pending_adj pa " +
                "JOIN finsl fs ON fs.slname = pa.slname AND fs.slid = pa.slid " +
                "WHERE pa.admou_code IN (<ouCodes>) " +
                "  AND pa.ReferenceSiteCode IN (<ownerSiteCodes>) " +
                "  AND pa.debit_credit = 'C' " +
                "  AND pa.pending > 0 " +
                "  AND " + d.castToDate("pa.document_date") + " <= " + d.currentDate() + " " +
                "  AND fs.clscode = :clsCode " +
                "  AND fs.ext = 'N' " +
                "ORDER BY pa.slname " +
                d.limitBind("lim");

            return jdbi.withHandle(handle ->
                handle.createQuery(sql)
                    .bindList("ouCodes", ouCodes)
                    .bindList("ownerSiteCodes", ownerSiteCodes)
                    .bind("clsCode", clsCode)
                    .bind("lim", limit)
                    .map((rs, ctx) -> new SlNameWithId(
                            rs.getString("slname"), rs.getString("slid")))
                    .list()
            );
        }

        // No clsCode filter
        String sql =
            "SELECT DISTINCT pa.slname, pa.slid " +
            "FROM v_pending_adj pa " +
            "JOIN finsl fs ON fs.slname = pa.slname AND fs.slid = pa.slid AND fs.ext = 'N' " +
            "WHERE pa.admou_code IN (<ouCodes>) " +
            "  AND pa.ReferenceSiteCode IN (<ownerSiteCodes>) " +
            "  AND pa.debit_credit = 'C' " +
            "  AND pa.pending > 0 " +
            "  AND " + d.castToDate("pa.document_date") + " <= " + d.currentDate() + " " +
            "ORDER BY pa.slname " +
            d.limitBind("lim");

        return jdbi.withHandle(handle ->
            handle.createQuery(sql)
                .bindList("ouCodes", ouCodes)
                .bindList("ownerSiteCodes", ownerSiteCodes)
                .bind("lim", limit)
                .map((rs, ctx) -> new SlNameWithId(
                        rs.getString("slname"), rs.getString("slid")))
                .list()
        );
    }

    /**
     * Fetches open AP documents ({@code debit_credit='C'}) for the given SL name+ID pairs.
     * AP equivalent of {@code PendingAdjQuery.findDocumentsForSlNames}.
     */
    private static Map<String, List<PendingSlDocument>> findApDocumentsForSlNames(
            Jdbi jdbi, DbDialect d,
            List<Long> ouCodes, List<Long> ownerSiteCodes,
            List<SlNameWithId> slNames, int docsPerSl) {

        List<String> nameList = slNames.stream()
                .map(SlNameWithId::getSlName)
                .distinct()
                .collect(Collectors.toList());

        Set<String> validKeys = slNames.stream()
                .map(s -> PendingAdjQuery.compositeKey(s.getSlName(), s.getSlId()))
                .collect(Collectors.toSet());

        String sql =
            "SELECT pa.slname, pa.slid, pa.document_no, " +
            d.castToDate("pa.document_date") + " AS document_date, " +
            "pa.entype, pa.refno, pa.ref_admsite_name, pa.pending " +
            "FROM v_pending_adj pa " +
            "JOIN finsl fs ON fs.slname = pa.slname AND fs.slid = pa.slid AND fs.ext = 'N' " +
            "WHERE pa.admou_code IN (<ouCodes>) " +
            "  AND pa.ReferenceSiteCode IN (<ownerSiteCodes>) " +
            "  AND pa.slname IN (<nameList>) " +
            "  AND pa.debit_credit = 'C' " +
            "  AND pa.pending > 0 " +
            "  AND " + d.castToDate("pa.document_date") + " <= " + d.currentDate() + " " +
            "ORDER BY pa.slname, pa.document_date";

        List<PendingSlDocument> rows = jdbi.withHandle(handle ->
            handle.createQuery(sql)
                .bindList("ouCodes", ouCodes)
                .bindList("ownerSiteCodes", ownerSiteCodes)
                .bindList("nameList", nameList)
                .map((rs, ctx) -> {
                    PendingSlDocument doc = new PendingSlDocument();
                    doc.setSlName(rs.getString("slname"));
                    doc.setSlId(rs.getString("slid"));
                    doc.setDocumentNo(rs.getString("document_no"));
                    java.sql.Date sqlDate = rs.getDate("document_date");
                    doc.setDocumentDate(sqlDate != null ? sqlDate.toLocalDate() : null);
                    doc.setEntype(rs.getString("entype"));
                    doc.setRefNo(rs.getString("refno"));
                    doc.setReferenceSite(rs.getString("ref_admsite_name"));
                    doc.setPending(rs.getBigDecimal("pending"));
                    return doc;
                })
                .list()
        );

        return rows.stream()
                .filter(doc -> validKeys.contains(PendingAdjQuery.compositeKey(doc.getSlName(), doc.getSlId())))
                .filter(doc -> doc.getPending() != null && doc.getPending().compareTo(BigDecimal.ZERO) > 0)
                .collect(
                    Collectors.groupingBy(
                        doc -> PendingAdjQuery.compositeKey(doc.getSlName(), doc.getSlId()),
                        Collectors.collectingAndThen(
                            Collectors.toList(),
                            list -> list.stream().limit(docsPerSl).collect(Collectors.toList())
                        )
                    )
                );
    }

    /**
     * Counts distinct {@code slcode} values in pending AP ({@code debit_credit='C'}).
     * Used to derive the retry bound in {@link #selectApDocumentsAtomic}.
     * When {@code clsCode > 0}, restricts to SLs with that classification.
     */
    private static long countDistinctApSlCodes(
            Jdbi jdbi, DbDialect d,
            List<Long> ouCodes, List<Long> ownerSiteCodes, long clsCode) {

        if (clsCode > 0) {
            String sql =
                "SELECT COUNT(DISTINCT pa.slcode) " +
                "FROM v_pending_adj pa " +
                "JOIN finsl fs ON fs.slname = pa.slname AND fs.slid = pa.slid " +
                "WHERE pa.admou_code IN (<ouCodes>) " +
                "  AND pa.ReferenceSiteCode IN (<ownerSiteCodes>) " +
                "  AND pa.debit_credit = 'C' " +
                "  AND pa.pending > 0 " +
                "  AND " + d.castToDate("pa.document_date") + " <= " + d.currentDate() + " " +
                "  AND fs.clscode = :clsCode " +
                "  AND fs.ext = 'N'";
            return jdbi.withHandle(h ->
                h.createQuery(sql)
                    .bindList("ouCodes", ouCodes)
                    .bindList("ownerSiteCodes", ownerSiteCodes)
                    .bind("clsCode", clsCode)
                    .mapTo(Long.class).one());
        }

        String sql =
            "SELECT COUNT(DISTINCT pa.slcode) " +
            "FROM v_pending_adj pa " +
            "JOIN finsl fs ON fs.slname = pa.slname AND fs.slid = pa.slid AND fs.ext = 'N' " +
            "WHERE pa.admou_code IN (<ouCodes>) " +
            "  AND pa.ReferenceSiteCode IN (<ownerSiteCodes>) " +
            "  AND pa.debit_credit = 'C' " +
            "  AND pa.pending > 0 " +
            "  AND " + d.castToDate("pa.document_date") + " <= " + d.currentDate();
        return jdbi.withHandle(h ->
            h.createQuery(sql)
                .bindList("ouCodes", ouCodes)
                .bindList("ownerSiteCodes", ownerSiteCodes)
                .mapTo(Long.class).one());
    }

    /**
     * Applies the per-SL selection mode filter.
     * Mirrors the logic in {@code DocumentSelector.applyMode}.
     */
    private static Map<String, List<PendingSlDocument>> applyMode(
            Map<String, List<PendingSlDocument>> reserved, DocumentSelectionMode mode) {
        if (mode == null || mode == DocumentSelectionMode.MIXED) return reserved;

        Map<String, List<PendingSlDocument>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<PendingSlDocument>> entry : reserved.entrySet()) {
            List<PendingSlDocument> all = entry.getValue();
            List<PendingSlDocument> positive = all.stream()
                    .filter(doc -> doc.getPending() != null
                            && doc.getPending().compareTo(BigDecimal.ZERO) > 0)
                    .collect(Collectors.toList());

            if (mode == DocumentSelectionMode.POSITIVE) {
                if (!positive.isEmpty()) out.put(entry.getKey(), positive);
            } else {   // HYBRID
                if (!positive.isEmpty()) {
                    out.put(entry.getKey(), positive);
                } else {
                    Log.info("[DocSelectorAP] HYBRID fallback — SL '{}' has no positive docs, using mixed pool ({} doc(s)).",
                            entry.getKey(), all.size());
                    out.put(entry.getKey(), all);
                }
            }
        }
        return out;
    }

    private static void sleepMs(long ms) {
        if (ms <= 0L) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // ─── Scenario → base test-case ID / short label ───────────────────────────

    private static String scenarioBaseId(ApVoucherScenarioType scenario) {
        switch (scenario) {
            case DOC_SELECT_DOCUMENT:        return "DB_AP_SEL_DOC";
            case DOC_FIFO:                   return "DB_AP_FIFO";
            case DOC_REFERENCE_SITEWISE_FIFO: return "DB_AP_REF_FIFO";
            default:                         return "DB_AP_" + scenario.name();
        }
    }

    /**
     * Compact scenario label used as the first segment of placeholder test case names,
     * matching the style used in {@link ApVoucherTemplateLoader} (e.g. {@code "SEL_DOC | [Bank GL]"}).
     */
    private static String scenarioShortLabel(ApVoucherScenarioType scenario) {
        switch (scenario) {
            case DOC_SELECT_DOCUMENT:         return "SEL_DOC";
            case DOC_FIFO:                    return "FIFO";
            case DOC_REFERENCE_SITEWISE_FIFO: return "REF_FIFO";
            default:                          return scenario.name();
        }
    }

    // ─── Logging helpers ──────────────────────────────────────────────────────

    /**
     * Builds a structured log of the DB data-gathering phase and stores it in
     * {@link MergedFinanceApVoucherData#getDbGatheringLog()} for Extent Report rendering.
     */
    private static void buildAndSetGatheringLog(
            String loginName, long ecode,
            LiveDbRefs refs,
            Map<String, List<PendingSlDocument>> pendingDocsBySlName,
            MergedFinanceApVoucherData data) {

        List<String> log = new ArrayList<>();

        log.add("═══════════════════════════════════════════════════════════");
        log.add("  DB DATA GATHERING — RESOLVED REFERENCES (AP VOUCHER)");
        log.add("═══════════════════════════════════════════════════════════");
        log.add("  Login User   : " + loginName + "  (ecode=" + ecode + ")");
        log.add("  Owner Site   : " + refs.getOwnerSiteName());
        log.add("  Cash/Bank GL : " + refs.getCashBankName() + "  (" + refs.getCashBankType().label() + ")");
        log.add("  Doc Scheme   : " + refs.getDocSchemeName());
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

        log.add("");
        log.add("───────────────────────────────────────────────────────────");
        log.add("  KEY QUERY — Pending AP SL / Open Document lookup");
        log.add("───────────────────────────────────────────────────────────");
        log.add("  SELECT DISTINCT pa.slname, pa.slid");
        log.add("  FROM   v_pending_adj pa");
        log.add("  JOIN   finsl fs  ON fs.slname = pa.slname AND fs.slid = pa.slid");
        log.add("  JOIN   admcls ac ON ac.clscode = fs.clscode");
        log.add("  WHERE  pa.admou_code IN (ouCodes)");
        log.add("    AND  pa.ReferenceSiteCode IN (ownerSiteCodes)");
        log.add("    AND  pa.debit_credit = 'C'    -- AP (Credit side)");
        log.add("    AND  pa.pending > 0");
        log.add("    AND  fs.ext = 'N'");
        log.add("  ORDER BY pa.slname;");

        log.add("");
        log.add("───────────────────────────────────────────────────────────");
        log.add("  GENERATED TEST DATA SUMMARY");
        log.add("───────────────────────────────────────────────────────────");
        log.add("  Scenario   : " + data.getScenarioType());
        log.add("  On Account : " + data.isOnAccount());
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
                    + "  amountDr=" + sl.getAmountDr()));
        }
        if (data.getSelectDocumentList() != null && !data.getSelectDocumentList().isEmpty()) {
            log.add("  Select Documents:");
            data.getSelectDocumentList().forEach(sd ->
                log.add("    lineNo=" + sd.getLineNo()
                    + "  docNo=" + sd.getDocumentNumber()
                    + "  date=" + sd.getDocumentDate()
                    + "  adjAmt=" + sd.getAdjustmentAmount()));
        }
        if (data.getTdsAndNetPayableList() != null && !data.getTdsAndNetPayableList().isEmpty()) {
            log.add("  TDS Rows:");
            data.getTdsAndNetPayableList().forEach(tds ->
                log.add("    lineNo=" + tds.getLineNo()
                    + "  grossPayable=" + tds.getGrossPayable()
                    + "  tdsApplicable=" + tds.getTdsApplicableAmount()));
        }
        log.add("═══════════════════════════════════════════════════════════");

        data.setDbGatheringLog(log);
    }

    private static void printRefs(String loginName, long ecode,
                                   LiveDbRefs refs,
                                   Map<String, List<PendingSlDocument>> pendingDocsBySlName) {
        Log.info("[DB] ============================================================");
        Log.info("[DB] AP Voucher — Live DB reference values resolved:");
        Log.info("[DB]   Login user   = {} (ecode={})", loginName, ecode);
        Log.info("[DB]   Owner Site   = {}", refs.getOwnerSiteName());
        Log.info("[DB]   Cash/Bank GL = {} ({})", refs.getCashBankName(), refs.getCashBankType().label());
        Log.info("[DB]   Doc Scheme   = {}", refs.getDocSchemeName());
        Log.info("[DB]   Pending SLs  = {}", refs.getPendingSlNames());
        Log.info("[DB]   SL→ARAP map  = {}", refs.getSlToArApName());
        if (!pendingDocsBySlName.isEmpty()) {
            Log.info("[DB]   Open docs/SL :");
            pendingDocsBySlName.forEach((key, docs) -> {
                Log.info("[DB]     [{}]", key);
                docs.forEach(doc -> Log.info("[DB]       -> {} | date={} | type={} | pending={}",
                        doc.getDocumentNo(), doc.getDocumentDate(), doc.getEntype(), doc.getPending()));
            });
        }
        Log.info("[DB] ============================================================");
    }

    private static void printGeneratedData(MergedFinanceApVoucherData row) {
        Log.info("[DB] ------------------------------------------------------------");
        Log.info("[DB] AP  Scenario   = {}", row.getScenarioType());
        Log.info("[DB]     TestCaseId = {}", row.getMasterTestData().getTestcaseid());
        Log.info("[DB]     TestCase   = {}", row.getMasterTestData().getTestcasename());
        Log.info("[DB]     OnAccount  = {}", row.isOnAccount());
        Log.info("[DB]     Owner Site = {}", row.getDocumentInformation().getOwnersite());
        Log.info("[DB]     Cash/Bank  = {}", row.getDocumentInformation().getCashbank());
        Log.info("[DB]     Doc Scheme = {}", row.getDocumentInformation().getVoucherno());
        Log.info("[DB]   SubLedgers :");
        row.getSubLedgerInformationList().forEach(sl ->
                Log.info("[DB]     [{}] {} (id={}) | adjType={} | amountDr={}",
                        sl.getLineNo(), sl.getSubLedger(), sl.getId(),
                        sl.getAdjustmentType(), sl.getAmountDr()));
        if (row.getSelectDocumentList() != null && !row.getSelectDocumentList().isEmpty()) {
            Log.info("[DB]   SelectDocs :");
            row.getSelectDocumentList().forEach(sd ->
                    Log.info("[DB]     lineNo={} | docNo={} | date={} | amount={}",
                            sd.getLineNo(), sd.getDocumentNumber(), sd.getDocumentDate(),
                            sd.getAdjustmentAmount()));
        }
        if (row.getTdsAndNetPayableList() != null && !row.getTdsAndNetPayableList().isEmpty()) {
            Log.info("[DB]   TDS Rows :");
            row.getTdsAndNetPayableList().forEach(tds ->
                    Log.info("[DB]     lineNo={} | grossPayable={} | tdsAmt={}",
                            tds.getLineNo(), tds.getGrossPayable(), tds.getTdsApplicableAmount()));
        }
        Log.info("[DB] ============================================================");
    }
}
