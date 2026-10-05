package com.gsl.utils.dataprovider.source.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.config.WaitConfig;
import com.gsl.database.BackfillStrategy;
import com.gsl.database.DataAvailabilityGuard;
import com.gsl.database.DatabaseManager;
import com.gsl.database.DocumentReservationRegistry;
import com.gsl.database.DocumentSelectionResult;
import com.gsl.database.finance.ArVoucherApBackfillStrategy;
import com.gsl.database.finance.PendingApDocumentBackfillStrategy;
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
import com.gsl.database.query.ReferenceSiteQuery;
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
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.Faker.FakerUtils;
import com.gsl.enums.VoucherDomain;
import com.gsl.utils.Log;
import com.gsl.database.finance.ExhaustedDocSchemeBackfill;
import com.gsl.utils.dataprovider.source.finance.common.DocSchemeMode;
import com.gsl.utils.dataprovider.source.finance.common.DocSchemeSelection;
import com.gsl.utils.dataprovider.fixture.ApVoucherFixtureFactory;
import com.gsl.utils.dataprovider.fixture.ApVoucherLiveDbRefs;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherExecutionGroup;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherFixtureOptions;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherScenarioType;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherTag;
import com.gsl.utils.dataprovider.fixture.finance.SelectDocumentCombinations;
import com.gsl.utils.dataprovider.fixture.finance.SelectDocumentVariant;
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

    /** No execution-group/tag filter — every scenario/variant is emitted (full-suite run). */
    public DbApVoucherDataSource() {
        this.xmlGroupParam = null;
        this.xmlTagParam   = null;
    }

    /** @param xmlGroupParam fallback {@code apv.group} value from TestNG {@code <parameter>}, used
     *                       when the {@code apv.group} system property is absent (IntelliJ direct-run path) */
    public DbApVoucherDataSource(String xmlGroupParam) {
        this.xmlGroupParam = xmlGroupParam;
        this.xmlTagParam   = null;
    }

    /**
     * @param xmlGroupParam see {@link #DbApVoucherDataSource(String)}
     * @param xmlTagParam   fallback {@code apv.tag} value from TestNG {@code <parameter>}, used
     *                      when the {@code apv.tag} system property is absent
     */
    public DbApVoucherDataSource(String xmlGroupParam, String xmlTagParam) {
        this.xmlGroupParam = xmlGroupParam;
        this.xmlTagParam   = xmlTagParam;
    }

    // ─── Point 1: Placeholder generation — called at DataSupplier time ────────

    /**
     * Emits one lightweight placeholder per applicable {@link ApVoucherScenarioType}/variant
     * surviving the active {@code apv.group}/{@code apv.tag} filter — see the class Javadoc's
     * "Lazy placeholder pattern". No DB documents are actually selected/reserved here; that only
     * happens later, per placeholder, in {@link #loadFreshForScenario}.
     */
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

                // ── Document scheme exhausted (advance path, Cash counter-leg, EXHAUSTED scheme mode) ──
                } else if (scenario == ApVoucherScenarioType.NEG_DOC_SCHEME_EXHAUSTED) {
                    if (matchesGroup(activeGroup, ApVoucherExecutionGroup.REGRESSION_PHASE1)) {
                        batch.add(buildPlaceholder(testMethodName, loginName, scenario,
                                ApVoucherFixtureOptions.forExhaustedScheme(CashBankType.CASH),
                                "DB_" + scenario.name(),
                                scenario.name()));
                    }

                // ── T-03 Negative & Boundary (advance path) ─────────────────────────────
                //    Gated to REGRESSION_PHASE1 so mandatory suite stays lean.
                } else if (scenario == ApVoucherScenarioType.NEG_BLANK_CASHBANK
                        || scenario == ApVoucherScenarioType.NEG_BLANK_CASHBANK_ADD_ROW
                        || scenario == ApVoucherScenarioType.NEG_BLANK_CASHBANK_ADD_ROW_RECOVER
                        || scenario == ApVoucherScenarioType.NEG_BLANK_SUBLEDGER
                        || scenario == ApVoucherScenarioType.NEG_BLANK_SUBLEDGER_RECOVER
                        || scenario == ApVoucherScenarioType.BLANK_REFERENCE_ALLOWED
                        || scenario == ApVoucherScenarioType.BOUND_AMOUNT_MIN_VALID
                        || scenario == ApVoucherScenarioType.BOUND_AMOUNT_MIN_INVALID) {
                    if (matchesGroup(activeGroup, ApVoucherExecutionGroup.REGRESSION_PHASE1)) {
                        batch.add(buildPlaceholder(testMethodName, loginName, scenario,
                                ApVoucherFixtureOptions.forAdvance(CashBankType.BANK),
                                "DB_" + scenario.name(),
                                scenario.name()));
                    }
                }

            } else {
                // ── Document Payment ──────────────────────────────────────────────────
                if (scenario == ApVoucherScenarioType.DOC_SELECT_DOCUMENT) {
                    // Full combination matrix — mirrors SelectDocumentCombinations in AR Voucher
                    batch.addAll(buildApSelectDocVariantList(
                            classificationTypes, activeGroup, testMethodName, loginName));

                } else if (scenario == ApVoucherScenarioType.DOC_NO_ADJUSTMENT) {
                    // Phase-1: Cash GL + Bank User SL
                    if (matchesGroup(activeGroup, ApVoucherExecutionGroup.REGRESSION_PHASE1)) {
                        batch.add(buildPlaceholder(testMethodName, loginName, scenario,
                                ApVoucherFixtureOptions.forCashBankType(CashBankType.CASH),
                                "DB_AP_NO_ADJ_CASH", "NO_ADJ | [Cash GL]"));
                        if (userClsCode > 0) {
                            batch.add(buildPlaceholder(testMethodName, loginName, scenario,
                                    ApVoucherFixtureOptions.forCashBankTypeAndClsCode(CashBankType.BANK, userClsCode),
                                    "DB_AP_NO_ADJ_BANK_USER", "NO_ADJ | [Bank GL] | User SL"));
                        }
                    }

                } else if (scenario == ApVoucherScenarioType.DOC_FIFO
                        || scenario == ApVoucherScenarioType.DOC_REFERENCE_SITEWISE_FIFO) {
                    String baseId  = scenarioBaseId(scenario);
                    String scenLbl = scenarioShortLabel(scenario);
                    // MANDATORY: Bank GL + System SL (mirrors AR Voucher MAND-03 / MAND-04)
                    if (systemClsCode > 0 && matchesGroup(activeGroup, ApVoucherExecutionGroup.MANDATORY)) {
                        batch.add(buildPlaceholder(testMethodName, loginName, scenario,
                                ApVoucherFixtureOptions.forCashBankTypeAndClsCode(CashBankType.BANK, systemClsCode),
                                baseId + "_BANK_SYSTEM", scenLbl + " | [Bank GL] | System SL"));
                    }
                    // Phase-1: Cash GL + Bank User SL
                    if (matchesGroup(activeGroup, ApVoucherExecutionGroup.REGRESSION_PHASE1)) {
                        batch.add(buildPlaceholder(testMethodName, loginName, scenario,
                                ApVoucherFixtureOptions.forCashBankType(CashBankType.CASH),
                                baseId + "_CASH", scenLbl + " | [Cash GL]"));
                        if (userClsCode > 0) {
                            batch.add(buildPlaceholder(testMethodName, loginName, scenario,
                                    ApVoucherFixtureOptions.forCashBankTypeAndClsCode(CashBankType.BANK, userClsCode),
                                    baseId + "_BANK_USER", scenLbl + " | [Bank GL] | User SL"));
                        }
                    }
                    // Phase-2: Cash GL subset (partial, multi-doc, multi-SL)
                    if (matchesGroup(activeGroup, ApVoucherExecutionGroup.REGRESSION_PHASE2)) {
                        int docFloor = com.gsl.config.ConfigFactory.getWaitConfig().docSelectorDocumentCountDefault();
                        batch.add(buildPlaceholder(testMethodName, loginName, scenario,
                                ApVoucherFixtureOptions.builder()
                                        .subLedgerCount(1).documentCount(Math.max(1, docFloor))
                                        .glType(CashBankType.CASH).build(),
                                baseId + "_CASH_PART", scenLbl + " | [Cash GL] | Partial"));
                        batch.add(buildPlaceholder(testMethodName, loginName, scenario,
                                ApVoucherFixtureOptions.builder()
                                        .subLedgerCount(1).documentCount(3)
                                        .glType(CashBankType.CASH).build(),
                                baseId + "_CASH_MULTI_DOC", scenLbl + " | [Cash GL] | 1SL-3Doc"));
                        batch.add(buildPlaceholder(testMethodName, loginName, scenario,
                                ApVoucherFixtureOptions.builder()
                                        .subLedgerCount(3).documentCount(Math.max(1, docFloor))
                                        .glType(CashBankType.CASH).build(),
                                baseId + "_CASH_MULTI_SL", scenLbl + " | [Cash GL] | 3SL-1Doc"));
                    }

                } else if (isMismatchScenario(scenario)) {
                    // Adjustment amount-mismatch Confirmation scenarios. Cash GL (no payment-info
                    // dialogs); documentCount = how many pending documents the script needs.
                    ApVoucherExecutionGroup tier = mismatchTier(scenario);
                    if (matchesGroup(activeGroup, tier)) {
                        batch.add(buildPlaceholder(testMethodName, loginName, scenario,
                                ApVoucherFixtureOptions.builder()
                                        .subLedgerCount(1).documentCount(mismatchDocumentCount(scenario))
                                        .glType(CashBankType.CASH).build(),
                                "DB_" + scenario.name(), scenario.name()));
                    }

                } else if (scenario == ApVoucherScenarioType.DOC_AMOUNT_NOT_ADJUSTED_INVALID
                        || scenario == ApVoucherScenarioType.DOC_AMOUNT_READ_ONLY) {
                    // NEG scenarios on the document path — gated to REGRESSION_PHASE1
                    if (matchesGroup(activeGroup, ApVoucherExecutionGroup.REGRESSION_PHASE1)) {
                        batch.add(buildPlaceholder(testMethodName, loginName, scenario,
                                ApVoucherFixtureOptions.forCashBankType(CashBankType.BANK),
                                "DB_" + scenario.name(), scenario.name()));
                    }
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

        com.gsl.database.DbQueryBuffer.start();
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
        // Round 20: resolved once here (reusing the existing FinGlDao.findByGlName lookup) so a
        // "known" cheque number can later be looked up against THIS voucher's own counter-leg bank.
        long cashBankGlcode = jdbi.onDemand(com.gsl.database.dao.finance.FinGlDao.class)
                .findByGlName(cashBankName).getGlcode();
        Log.info("[DB] AP Picked GL: {} (glcode={}, {})", cashBankName, cashBankGlcode, targetCashBankType.label());

        // 5. SL + document resolution — branches on onAccount flag
        List<SlNameWithId> pendingSlNames;
        Map<String, List<PendingSlDocument>> pendingDocsBySlName;

        if (scenarioType.isOnAccount()) {
            // ── Advance Payment: fetch vendor SLs directly from finsl ─────────
            pendingSlNames = findVendorSlsForAdvance(jdbi, d, opts);
            if (pendingSlNames.isEmpty()) {
                // Data-availability shortfall → skip (amber), not fail (red).
                throw new org.testng.SkipException(
                        "Skipped due to lack of data: No vendor SL found for advance payment" +
                        (opts.isTdsApplicable() ? " with TDS deductee configured (tds_app='Y')" : "") +
                        ". Ensure active finsl rows exist for the owner site" +
                        (opts.isTdsApplicable() ? " with tds_app='Y'." : "."));
            }
            pendingDocsBySlName = Collections.emptyMap();

            Log.info("[DB] AP Advance — found {} vendor SL(s): {}",
                    pendingSlNames.size(), pendingSlNames);

        } else {
            // ── Document Payment: fetch pending AP docs (debit_credit='C') ────
            // Shortfall here does NOT reuse DocumentSelector — selectApDocumentsAtomic is AP's
            // own private inline reimplementation (debit_credit='C' vs AR's 'D'). DataAvailabilityGuard
            // takes a Supplier so it wraps this without changing its retry/reservation logic at all.
            int neededQty = Math.max(1, opts.getSubLedgerCount()) * Math.max(1, opts.getDocumentCount());

            // clsCode's clstype decides which document type can actually backfill it: Service
            // Invoice Adhoc's vendor picker (GetVendors) only ever returns Supplier-classified
            // SLs, so a Customer/Jobber/Others/etc. requirement can never be satisfied that way
            // (confirmed live: it silently fell back to an unrelated Supplier vendor and the
            // shortfall never resolved). AR Voucher can be raised against any classification, so
            // it's used instead whenever the required clstype isn't Supplier.
            long slClsCode = opts.getSlClsCode();
            String clstype = slClsCode > 0
                    ? AdmClsQuery.findByClsCode(jdbi, slClsCode).map(AdmCls::getClstype).orElse(null)
                    : null;
            boolean useArVoucherBackfill = slClsCode > 0 && !"S".equalsIgnoreCase(clstype);

            BackfillStrategy backfillStrategy;
            if (useArVoucherBackfill) {
                // No distinct "OU code" source exists in this framework — assumed 1:1 with
                // ownerSiteCode, same convention already established in
                // ServiceInvoiceAdhocApiClient.createServiceInvoiceAdhocWithDefaults. (An earlier
                // version of this line looked up "some" OU code the employee is assigned to via
                // hrdemp_ou — for this employee that's ~920 unrelated OU codes with no ORDER BY —
                // which produced an incoherent connectedOUCode/connectedSite pairing and an HTTP
                // 500 from Finance/api/sites.)
                int ouCode = (int) ownerSite.code;
                ReportLogger.dataBackfill(
                        "[DB] AP backfill combination if shortfall: neededQty={} (subLedgerCount={} x documentCount={}), " +
                                "clsCode={} (clstype={}, not Supplier → AR Voucher backfill), ownerSite={} (code={}), ouCode={}, empCode={}",
                        neededQty, opts.getSubLedgerCount(), opts.getDocumentCount(), slClsCode, clstype,
                        ownerSiteName, ownerSite.code, ouCode, ecode);
                backfillStrategy = new ArVoucherApBackfillStrategy(slClsCode, (int) ownerSite.code, ouCode, ecode);
            } else {
                String vendorSlForBackfill = findFirstUsableVendorSl(jdbi, d, slClsCode);
                ReportLogger.dataBackfill(
                        "[DB] AP backfill combination if shortfall: neededQty={} (subLedgerCount={} x documentCount={}), " +
                                "clsCode={}, ownerSite={} (code={}), vendorSlForBackfill={}",
                        neededQty, opts.getSubLedgerCount(), opts.getDocumentCount(), slClsCode,
                        ownerSiteName, ownerSite.code, vendorSlForBackfill);
                backfillStrategy = new PendingApDocumentBackfillStrategy(vendorSlForBackfill, (int) ownerSite.code);
            }

            DocumentSelectionResult selection = DataAvailabilityGuard.selectOrBackfill(
                    () -> selectApDocumentsAtomic(jdbi, d, ouCodes, ownerSiteCodes, opts,
                            scenarioType == ApVoucherScenarioType.DOC_FIFO),
                    neededQty,
                    backfillStrategy);
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

        // 5d. Resolve admcls classification (clstype, cmpcode) and reference site for all SLs.
        //     defaultSiteName is queried once here to avoid per-SL admcmp+admsite round-trips.
        Map<String, String>  slToRefSiteName = new LinkedHashMap<>();
        Map<String, Integer> slToCmpCode     = new LinkedHashMap<>();
        Map<String, String>  slToClstype     = new LinkedHashMap<>();
        String defaultSiteName = ReferenceSiteQuery.resolveDefaultSiteName(jdbi);
        for (SlNameWithId sl : pendingSlNames) {
            String key  = PendingAdjQuery.compositeKey(sl.getSlName(), sl.getSlId());
            FinSl finSl = finSlDao.findBySlnameAndSlid(sl.getSlName(), sl.getSlId());
            if (finSl == null) continue;
            AdmCls admCls  = AdmClsQuery.findByClsCode(jdbi, finSl.getClscode()).orElse(null);
            String clstype = admCls != null ? admCls.getClstype()         : null;
            int    cmpcode = admCls != null ? admCls.getCmpcode()         : -1;
            String refSite = ReferenceSiteQuery.resolveForSl(jdbi, finSl.getSlcode(), clstype, defaultSiteName);
            slToRefSiteName.put(key, refSite);
            if (clstype != null) slToClstype.put(key, clstype);
            if (cmpcode >= 0)   slToCmpCode.put(key, cmpcode);
            Log.info("[DB] SL '{}': clstype='{}', cmpcode={}, refSite='{}'",
                    sl.getSlName(), clstype, cmpcode, refSite);
        }
        // For DOC_REFERENCE_SITEWISE_FIFO: overwrite with the more precise pending-balance-aware
        // resolution (v_pending_adj grouped by ref_admsite_name, amount ceiling reserved).
        if (scenarioType == ApVoucherScenarioType.DOC_REFERENCE_SITEWISE_FIFO) {
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
                        jdbi, d, finSl.getSlcode(), ouCodes, "C", slPending, sl.getSlName(), sl.getSlId());
                if (refSiteName != null) {
                    slToRefSiteName.put(key, refSiteName);
                    Log.info("[DB] RefSiteFIFO SL '{}': refSite='{}', slPending={}", sl.getSlName(), refSiteName, slPending);
                } else {
                    Log.warn("[DB] RefSiteFIFO SL '{}': no reservable reference site found.", sl.getSlName());
                }
            }
        }

        // 6. AP Voucher doc scheme verified for user's site
        // Mode comes from the scenario's fixture options: AUTO normally, EXHAUSTED for the full-scheme negative
        // scenario (which backfills a full test scheme when none exists).
        DocSchemeSelection docScheme = ExhaustedDocSchemeBackfill.resolve(jdbi,
                VoucherDomain.AP, siteCodes, ownerSite.code, opts.getDocSchemeMode());
        String docSchemeName = docScheme.docName();

        // 7. Package into ApVoucherLiveDbRefs — include tdsBehaviour from fixture options so that
        //    ApVoucherTemplateLoader.buildTdsRows() generates the correct behaviour name
        //    on the TdsAndNetPayable DTO for flow-layer dispatch at test execution time.
        //    slToTdsResolution is only set for TDS-applicable advance scenarios (step 5c above).
        //    slToRefSiteName, slToCmpCode, slToClstype are resolved for all scenarios (step 5d).
        ApVoucherLiveDbRefs refs = ApVoucherLiveDbRefs.builder()
                .cashBankName(cashBankName)
                .cashBankGlcode(cashBankGlcode)
                .selectDocKnownCheque(opts.isKnownCheque())
                .ownerSiteName(ownerSiteName)
                .docSchemeName(docSchemeName)
                .docSchemeNarrative(docScheme.getNarrative())
                .pendingSlNames(pendingSlNames)
                .pendingDocsBySlName(pendingDocsBySlName)
                .slToArApName(slToArApName)
                .cashBankType(targetCashBankType)
                .tdsBehaviour(opts.getTdsBehaviour())
                .slToTdsResolution(slToTdsResolution)
                .slToRefSiteName(slToRefSiteName)
                .slToCmpCode(slToCmpCode)
                .slToClstype(slToClstype)
                .selectDocPaymentMode(opts.getSelectDocPaymentMode())
                .build();

        // 8. Build fully populated test data for this specific scenario
        MergedFinanceApVoucherData data = scenarioType.loadData(refs);

        // 8b. Populate SL Type for each sub-ledger (admcls.clsname via finsl → admcls)
        //     Mirrors the pattern in ExcelApVoucherDataSource and ArVoucherTemplateLoader.
        if (data.getSubLedgerInformationList() != null) {
            data.getSubLedgerInformationList().forEach(sl ->
                    sl.setSlType(AdmClsQuery.findClsnameBySlId(jdbi, sl.getId())));
        }

        // Flush buffered queries and store gathering log (console + Extent Report)
        List<String> executedQueries = com.gsl.database.DbQueryBuffer.flush();
        buildAndSetGatheringLog(loginName, ecode, refs, pendingDocsBySlName, data, executedQueries);

        return data;
    }

    // ─── SELECT_DOCUMENT combination matrix ──────────────────────────────────

    /**
     * Builds AP Voucher placeholder rows for {@code DOC_SELECT_DOCUMENT} by mirroring
     * the full {@link SelectDocumentCombinations} matrix used by AR Voucher.
     *
     * <p>Variant ordering matches {@code DbArVoucherDataSource.buildVariantList()}:
     * <ol>
     *   <li>Group 0 — mandatory baselines per classification type</li>
     *   <li>Group 1 — reference-uniqueness static variants (Phase-1)</li>
     *   <li>Group 2 — per system/user classification type dynamic variants (Phase-2)</li>
     *   <li>Groups 3–8 — static dimensional variants (Phase-1/Phase-2)</li>
     *   <li>Cash GL variants (Mandatory/Phase-2)</li>
     *   <li>Phase-3 — INV document-type filter</li>
     * </ol>
     *
     * <p>{@link ArVoucherExecutionGroup} constants have identical names to
     * {@link ApVoucherExecutionGroup}, so mapping is done via {@code .name()}.
     *
     * @param classificationTypes distinct {@code admcls} rows found in pending AP
     * @param activeGroup         active execution group filter (null = all groups)
     * @param testMethodName      forwarded to {@link #buildPlaceholder}
     * @param loginName           forwarded to {@link #buildPlaceholder}
     * @return list of AP Voucher placeholders for all applicable variants
     */
    private static List<MergedFinanceApVoucherData> buildApSelectDocVariantList(
            List<AdmCls> classificationTypes,
            ApVoucherExecutionGroup activeGroup,
            String testMethodName,
            String loginName) {

        List<SelectDocumentVariant> variants = new ArrayList<>();

        // For MANDATORY runs, restrict to Supplier SLs only (clstype='S').
        // Customer / Jobber / Others / Channel classification types are explicitly omitted
        // from the mandatory smoke suite per AR_AP_Smoke_Test_Case_Combination.xlsx.
        List<AdmCls> eligibleTypes = (activeGroup == ApVoucherExecutionGroup.MANDATORY)
                ? classificationTypes.stream()
                        .filter(c -> "S".equalsIgnoreCase(c.getClstype()))
                        .collect(java.util.stream.Collectors.toList())
                : classificationTypes;

        // Group 0: mandatory baselines per classification type
        for (AdmCls cls : eligibleTypes) {
            variants.add(SelectDocumentCombinations.mandatoryBaselineVariant(cls));
            if (cls.getCmpcode() == 1) {
                variants.add(SelectDocumentCombinations.mandatoryBankRandomVariant(cls));
            }
        }

        // Group 0b: REGRESSION_PHASE1 baselines for non-Supplier system SLs
        // (Customer / Jobber / Others / Channel — omitted from mandatory, belong in REGRESSION_PHASE1)
        List<AdmCls> nonSupplierSystemTypes = classificationTypes.stream()
                .filter(c -> c.getCmpcode() == 1 && !"S".equalsIgnoreCase(c.getClstype()))
                .collect(java.util.stream.Collectors.toList());
        for (AdmCls cls : nonSupplierSystemTypes) {
            variants.add(SelectDocumentCombinations.regressionPhase1BaselineVariant(cls));
            variants.add(SelectDocumentCombinations.regressionPhase1BankRandomVariant(cls));
        }

        // Group 1: reference-uniqueness static variants (Phase-1)
        variants.addAll(SelectDocumentCombinations.group1Variants());

        // Group 2: per classification type — system (10 variants) or user (3 variants) each
        for (AdmCls cls : eligibleTypes) {
            if (cls.getCmpcode() == 1) {
                variants.addAll(SelectDocumentCombinations.forSystemClassificationType(cls));
            } else {
                variants.addAll(SelectDocumentCombinations.forUserDefinedClassificationType(cls));
            }
        }

        // Groups 3–8: static dimensional variants (Phase-1/Phase-2)
        variants.addAll(SelectDocumentCombinations.groups3to8Variants());

        // Cash GL variants (Mandatory/Phase-2)
        variants.addAll(SelectDocumentCombinations.cashGlVariants());

        // Phase-3: INV document-type filter variant
        variants.add(SelectDocumentCombinations.specificDocTypeVariant());

        // Filter by active execution group — ArVoucherExecutionGroup names match ApVoucherExecutionGroup
        if (activeGroup != null) {
            variants.removeIf(v -> {
                try {
                    return ApVoucherExecutionGroup.valueOf(v.getExecutionGroup().name()) != activeGroup;
                } catch (IllegalArgumentException e) {
                    return true;
                }
            });
        }

        // Round 20: the Fixture Factory itself decides known-vs-unknown for every CHEQUE row here,
        // at an exact ratio (com.gsl.utils.WeightedSequenceBuilder), over the final filtered list —
        // once per run, not per row.
        variants = ApVoucherFixtureFactory.assignKnownChequeFlags(variants);

        // Build AP placeholders from the filtered variant list
        List<MergedFinanceApVoucherData> result = new ArrayList<>(variants.size());
        for (SelectDocumentVariant v : variants) {
            ApVoucherFixtureOptions opts = ApVoucherFixtureOptions.forSelectDocVariant(v);
            result.add(buildPlaceholder(testMethodName, loginName,
                    ApVoucherScenarioType.DOC_SELECT_DOCUMENT, opts,
                    "DB_AP_" + v.getVariantId(), v.getTestCaseName()));
        }

        Log.info("[DB] buildApSelectDocVariantList: activeGroup={} → {} variant(s)",
                activeGroup != null ? activeGroup : "ALL", result.size());
        return result;
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
    /** True for the Adjustment amount-mismatch Confirmation scenarios. */
    private static boolean isMismatchScenario(ApVoucherScenarioType s) {
        switch (s) {
            case DOC_PARTIAL_UPDATE_PAYMENT_SELECT_DOC:
            case DOC_PARTIAL_ADJUST_MORE_SELECT_DOC:
            case DOC_PARTIAL_ADJUST_MORE_FIFO:
            case DOC_PARTIAL_ADJUST_MORE_REFSITE_FIFO:
            case DOC_PARTIAL_MULTI_DOC_UPDATE_PAYMENT:
            case DOC_PARTIAL_PER_DOC_AMOUNT_LESS_THAN_PENDING:
            case DOC_PARTIAL_ADJUST_MORE_THEN_UPDATE:
            case DOC_AMOUNT_OVER_PENDING_NEGATIVE:
                return true;
            default:
                return false;
        }
    }

    /** Regression tier per mismatch scenario — a first-pass judgment call: simplest proving case in
     *  MANDATORY, the named dimensions in PHASE1, combined/higher-risk in PHASE2, exploratory in PHASE3. */
    private static ApVoucherExecutionGroup mismatchTier(ApVoucherScenarioType s) {
        switch (s) {
            case DOC_PARTIAL_UPDATE_PAYMENT_SELECT_DOC:
                return ApVoucherExecutionGroup.MANDATORY;
            case DOC_PARTIAL_ADJUST_MORE_SELECT_DOC:
            case DOC_PARTIAL_MULTI_DOC_UPDATE_PAYMENT:
            case DOC_PARTIAL_PER_DOC_AMOUNT_LESS_THAN_PENDING:
                return ApVoucherExecutionGroup.REGRESSION_PHASE1;
            case DOC_PARTIAL_ADJUST_MORE_FIFO:
            case DOC_PARTIAL_ADJUST_MORE_REFSITE_FIFO:
            case DOC_PARTIAL_ADJUST_MORE_THEN_UPDATE:
                return ApVoucherExecutionGroup.REGRESSION_PHASE2;
            default:
                return ApVoucherExecutionGroup.REGRESSION_PHASE3;
        }
    }

    /** Pending documents on the single sub-ledger the script needs. */
    private static int mismatchDocumentCount(ApVoucherScenarioType s) {
        switch (s) {
            case DOC_PARTIAL_UPDATE_PAYMENT_SELECT_DOC:
            case DOC_PARTIAL_PER_DOC_AMOUNT_LESS_THAN_PENDING:
            case DOC_AMOUNT_OVER_PENDING_NEGATIVE:
                return 1;
            default:
                return 2;
        }
    }

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
            default:
                if (isMismatchScenario(scenario)) {
                    tags.addAll(EnumSet.of(ApVoucherTag.DOCUMENT, ApVoucherTag.DOC_MISMATCH));
                }
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

    /**
     * Finds the first active vendor SL in {@code finsl} usable as the Service Invoice Adhoc
     * target when backfilling AP Voucher's document-payment shortfall — replaces the
     * {@code datafallback.ap.defaultVendorSlCode} config placeholder with a genuine live value,
     * the same way {@link #findFirstUsableOwnerSite} already resolves the owner site live.
     *
     * <p>Filters on {@code fs.clscode = clsCode} when {@code clsCode > 0} so the backfilled
     * Service Invoice Adhoc lands under the same SL classification the shortfall check is
     * actually querying for (see {@link #findApSlCandidates}) — otherwise the created document
     * can land under the wrong classification and the post-backfill retry still finds nothing.
     */
    private static String findFirstUsableVendorSl(Jdbi jdbi, DbDialect d, long clsCode) {
        String clsFilter = clsCode > 0 ? "  AND fs.clscode = :clsCode " : "";
        String sql =
            "SELECT fs.slid " +
            "FROM finsl fs " +
            "WHERE fs.ext = 'N' " +
            clsFilter +
            "ORDER BY fs.slname " +
            d.limit1();

        return jdbi.withHandle(handle -> {
            var query = handle.createQuery(sql);
            if (clsCode > 0) query.bind("clsCode", clsCode);
            return query.mapTo(String.class).findFirst();
        }).orElseThrow(() -> new IllegalStateException(
                "No active vendor SL found in finsl (ext='N'"
                        + (clsCode > 0 ? ", clscode=" + clsCode : "")
                        + ") — cannot backfill AP document via Service Invoice Adhoc."));
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
            ApVoucherFixtureOptions opts, boolean requireSingleReferenceSite) {

        int  subLedgerCount = Math.max(1, opts.getSubLedgerCount());
        int  documentCount  = Math.max(1, opts.getDocumentCount());
        long clsCode        = opts.getSlClsCode();

        WaitConfig fcfg = ConfigFactory.getWaitConfig();
        long retryBackoff = fcfg.docSelectorRetryBackoffMs();
        DocumentSelectionMode mode = DocumentSelectionMode.fromString(fcfg.docSelectorMode());

        long distinctCount = countDistinctApSlCodes(jdbi, d, ouCodes, ownerSiteCodes, clsCode);
        int  maxAttempts   = (int) Math.max(1L, distinctCount);
        // Ensure the candidate query retrieves ALL distinct (slname,slid) SLs in one shot — if
        // overfetch is smaller than the candidate universe, some SLs are cut off by LIMIT and never
        // tried, which is the root cause of "did not try all subledgers" EXHAUSTED failures.
        // distinctCount now matches findApSlCandidates' (slname,slid) granularity; add a generous
        // headroom multiplier so the LIMIT never under-fetches.
        int  overfetch = Math.max(
                (int) Math.max(1L, distinctCount),
                subLedgerCount * Math.max(1, fcfg.docSelectorOverfetchMultiplier()));

        Log.info("[DocSelectorAP] distinctSlCount={} → maxAttempts={}, overfetch(LIMIT)={}, documentCount={}",
                distinctCount, maxAttempts, overfetch, documentCount);

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

                // Plain "FIFO Set-Off" is disabled by the app whenever the SL's pending documents
                // span more than one reference site — only "Reference Site wise FIFO set-off"
                // handles that case. Confirmed live via a Jenkins failure screenshot: the Adjusted
                // Document menu's "FIFO Set-Off" option was greyed out for exactly this reason,
                // so the click did nothing and the OK-button wait afterward never had a chance to
                // succeed. Skip such SLs up front for DOC_FIFO so the picked candidate always has
                // the menu option enabled; DOC_REFERENCE_SITEWISE_FIFO doesn't set this flag.
                if (requireSingleReferenceSite && docs.stream()
                        .map(PendingSlDocument::getReferenceSite)
                        .distinct()
                        .count() > 1) {
                    docs.forEach(doc -> { if (doc.getDocumentNo() != null) surplus.add(doc.getDocumentNo()); });
                    continue;
                }

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
            "Skipped due to lack of data: Could not find " + subLedgerCount +
            " AP SL(s) with open documents after " + maxAttempts + " attempt(s)" +
            " (clsCode=" + clsCode + ", documentCount=" + documentCount + ")." +
            " Ensure v_pending_adj has rows with debit_credit='C' for the user's OU." +
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
     * Counts distinct candidate sub-ledgers in pending AP ({@code debit_credit='C'}).
     *
     * <p>Counts distinct {@code (slname, slid)} pairs — the SAME granularity that
     * {@link #findApSlCandidates} selects — NOT {@code slcode}. A single {@code slcode} can map to
     * multiple {@code (slname, slid)} pairs; counting by {@code slcode} undercounts the candidate
     * universe, which made {@code overfetch} (the SQL LIMIT) and {@code maxAttempts} too small so some
     * sub-ledgers were never tried ("EXHAUSTED — no data" despite more SLs existing). When
     * {@code clsCode > 0}, restricts to SLs with that classification.</p>
     */
    private static long countDistinctApSlCodes(
            Jdbi jdbi, DbDialect d,
            List<Long> ouCodes, List<Long> ownerSiteCodes, long clsCode) {

        if (clsCode > 0) {
            String sql =
                "SELECT COUNT(*) FROM (" +
                "  SELECT DISTINCT pa.slname, pa.slid " +
                "  FROM v_pending_adj pa " +
                "  JOIN finsl fs ON fs.slname = pa.slname AND fs.slid = pa.slid " +
                "  WHERE pa.admou_code IN (<ouCodes>) " +
                "    AND pa.ReferenceSiteCode IN (<ownerSiteCodes>) " +
                "    AND pa.debit_credit = 'C' " +
                "    AND pa.pending > 0 " +
                "    AND " + d.castToDate("pa.document_date") + " <= " + d.currentDate() + " " +
                "    AND fs.clscode = :clsCode " +
                "    AND fs.ext = 'N'" +
                ") t";
            return jdbi.withHandle(h ->
                h.createQuery(sql)
                    .bindList("ouCodes", ouCodes)
                    .bindList("ownerSiteCodes", ownerSiteCodes)
                    .bind("clsCode", clsCode)
                    .mapTo(Long.class).one());
        }

        String sql =
            "SELECT COUNT(*) FROM (" +
            "  SELECT DISTINCT pa.slname, pa.slid " +
            "  FROM v_pending_adj pa " +
            "  JOIN finsl fs ON fs.slname = pa.slname AND fs.slid = pa.slid AND fs.ext = 'N' " +
            "  WHERE pa.admou_code IN (<ouCodes>) " +
            "    AND pa.ReferenceSiteCode IN (<ownerSiteCodes>) " +
            "    AND pa.debit_credit = 'C' " +
            "    AND pa.pending > 0 " +
            "    AND " + d.castToDate("pa.document_date") + " <= " + d.currentDate() +
            ") t";
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
            ApVoucherLiveDbRefs refs,
            Map<String, List<PendingSlDocument>> pendingDocsBySlName,
            MergedFinanceApVoucherData data,
            List<String> executedQueries) {

        List<String> log = new ArrayList<>();

        log.add("═══════════════════════════════════════════════════════════");
        log.add("  DB DATA GATHERING — RESOLVED REFERENCES (AP VOUCHER)");
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

        if (!executedQueries.isEmpty()) {
            log.add("");
            log.add("───────────────────────────────────────────────────────────");
            log.add("  QUERIES EXECUTED DURING DATA GATHERING");
            log.add("───────────────────────────────────────────────────────────");
            log.addAll(executedQueries);
        }

        log.add("═══════════════════════════════════════════════════════════");

        data.setDbGatheringLog(log);
        // Single source of truth: emit to console/log file from here
        log.forEach(line -> Log.info("[DB] {}", line));
    }

}
