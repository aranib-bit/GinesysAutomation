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
import com.gsl.model.finance.AdmCls;
import com.gsl.model.finance.FinGl;
import com.gsl.model.finance.FinSl;
import com.gsl.model.finance.PendingSlDocument;
import com.gsl.model.finance.SlNameWithId;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformation;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.Faker.FakerUtils;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.fixture.ArVoucherFixtureFactory;
import com.gsl.utils.dataprovider.fixture.LiveDbRefs;
import com.gsl.utils.dataprovider.fixture.finance.ArVoucherFixtureOptions;
import com.gsl.utils.dataprovider.fixture.finance.ArVoucherScenarioType;
import com.gsl.utils.dataprovider.fixture.finance.ArVoucherTemplateLoader;
import com.gsl.utils.dataprovider.fixture.finance.SelectDocumentCombinations;
import com.gsl.utils.dataprovider.fixture.finance.SelectDocumentVariant;
import com.gsl.utils.dataprovider.source.TestDataSource;
import org.jdbi.v3.core.Jdbi;

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

    @Override
    public List<MergedFinanceArVoucherData> load(String testMethodName) {
        String loginName = ConfigFactory.getConfig().username();
        String loginPwd  = ConfigFactory.getConfig().password();

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
                // ── One placeholder per variant (Group 0 mandatory + Group 1 + Group 2 + Groups 3-8 + Cash GL)
                List<SelectDocumentVariant> variants = buildVariantList(classificationTypes);
                for (SelectDocumentVariant variant : variants) {
                    ArVoucherFixtureOptions opts = ArVoucherFixtureOptions.builder()
                            .subLedgerCount(variant.getSlCount())
                            .documentCount(variant.getDocsPerSl())
                            .selectDocVariant(variant)
                            .build();
                    placeholders.add(buildPlaceholder(testMethodName, loginName, loginPwd, scenario, opts,
                            variant.getVariantId(), variant.getTestCaseName()));
                }

            } else if (scenario == ArVoucherScenarioType.NO_ADJUSTMENT) {
                // ── NO_ADJUSTMENT: Bank GL — one per SL category (system + user)
                if (systemClsCode > 0) {
                    placeholders.add(buildPlaceholder(testMethodName, loginName, loginPwd, scenario,
                            ArVoucherFixtureOptions.forCashBankTypeAndClsCode(CashBankType.BANK, systemClsCode),
                            "NO_ADJ_SYSTEM", "NO_ADJUSTMENT | [Bank GL] | System SL"));
                }
                if (userClsCode > 0) {
                    placeholders.add(buildPlaceholder(testMethodName, loginName, loginPwd, scenario,
                            ArVoucherFixtureOptions.forCashBankTypeAndClsCode(CashBankType.BANK, userClsCode),
                            "NO_ADJ_USER", "NO_ADJUSTMENT | [Bank GL] | User SL"));
                }

            } else {
                // ── FIFO_SETOFF / REFERENCE_SITEWISE_FIFO:
                //    - Cash GL (any SL, no class filter)
                //    - Bank GL System SL (cmpcode=1)
                //    - Bank GL User SL   (cmpcode=0)
                String baseId  = scenario == ArVoucherScenarioType.FIFO_SETOFF ? "DB_FIFO" : "DB_REF_FIFO";
                String scenLbl = scenario.name();
                placeholders.add(buildPlaceholder(testMethodName, loginName, loginPwd, scenario,
                        ArVoucherFixtureOptions.forCashBankType(CashBankType.CASH),
                        baseId + "_CASH", scenLbl + " | [Cash GL]"));
                if (systemClsCode > 0) {
                    placeholders.add(buildPlaceholder(testMethodName, loginName, loginPwd, scenario,
                            ArVoucherFixtureOptions.forCashBankTypeAndClsCode(CashBankType.BANK, systemClsCode),
                            baseId + "_BANK_SYSTEM", scenLbl + " | [Bank GL] | System SL"));
                }
                if (userClsCode > 0) {
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
     * Builds the full ordered SELECT_DOCUMENT variant list from pre-discovered classification types.
     *
     * <ol>
     *   <li>Group 0 — mandatory baseline per SL category (1 system + 1 user, if present)</li>
     *   <li>Group 1 — static reference variants (SD_REF_FIN, SD_REF_UNIQUE)</li>
     *   <li>Group 2 — per classification type:
     *       system ({@code cmpcode=1}) → 10 variants;
     *       user-defined ({@code cmpcode=0}) → 3 variants</li>
     *   <li>Groups 3–8 — static customer/SL-count/amount/grid/payment/date variants</li>
     *   <li>Cash GL — explicit Cash GL variants</li>
     * </ol>
     *
     * @param classificationTypes active classification types discovered from pending AR
     *                            (already filtered to {@code ext='N'} by the query)
     */
    private static List<SelectDocumentVariant> buildVariantList(List<AdmCls> classificationTypes) {
        List<SelectDocumentVariant> variants = new java.util.ArrayList<>();

        // Group 0 — mandatory baseline: one per SL cmpcode category
        classificationTypes.stream().filter(c -> c.getCmpcode() == 1).findFirst()
                .ifPresent(cls -> variants.add(SelectDocumentCombinations.mandatoryBaselineVariant(cls)));
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
                testCaseName, loginName, loginPwd, null, null, null, testCaseId, 1);

        return MergedFinanceArVoucherData.builder()
                .scenarioType(scenario)
                .masterTestData(master)
                .subLedgerInformationList(List.of())   // empty = signal: placeholder
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(List.of())
                .paymentProvided(false)
                .needsIgnoreProceed(false)
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

        ArVoucherScenarioType scenarioType = placeholder.getScenarioType();
        ArVoucherFixtureOptions opts = placeholder.getFixtureOptions() != null
                ? placeholder.getFixtureOptions()
                : ArVoucherFixtureOptions.defaults();

        Log.info("[DB] loadFreshForScenario: scenario={}, method={}, subLedgerCount={}, documentCount={}",
                scenarioType, methodName, opts.getSubLedgerCount(), opts.getDocumentCount());

        Jdbi jdbi = DatabaseManager.getJdbi();
        DbDialect d = DbDialectFactory.current();

        // 1. Resolve login user → ecode
        String loginName = ConfigFactory.getConfig().username();
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

        // 5. (slName, slId) pairs with pending AR — scoped to ownerSiteCodes via ReferenceSiteCode.
        //    The UI's Select Document popup filters by ReferenceSiteCode = ANY(:siteCodes) where
        //    siteCodes = owner site codes; mirroring that here ensures only documents that will
        //    actually appear in the popup are selected as test data.
        //    Priority: variant.slClsCode > opts.slClsCode > no filter (any pending SL)
        long effectiveClsCode = (variant != null && variant.getSlClsCode() > 0)
                ? variant.getSlClsCode()
                : opts.getSlClsCode();

        List<SlNameWithId> pendingSlNames = (effectiveClsCode > 0)
                ? AdmClsQuery.findSlNamesWithPendingARByClsCode(
                        jdbi, ouCodes, ownerSiteCodes, effectiveClsCode, opts.getSubLedgerCount())
                : PendingAdjQuery.findSlNamesWithPendingAR(jdbi, ouCodes, ownerSiteCodes, opts.getSubLedgerCount());

        Log.info("[DB] SL filter: clsCode={} (0=any), ownerSite='{}' (code={}), found={} SL(s)",
                effectiveClsCode, ownerSite.name, ownerSite.code, pendingSlNames.size());
        if (pendingSlNames.isEmpty()) {
            String slFilterDesc;
            String diagQuery;
            if (effectiveClsCode > 0) {
                java.util.Optional<com.gsl.model.finance.AdmCls> admCls =
                        AdmClsQuery.findByClsCode(jdbi, effectiveClsCode);
                slFilterDesc = admCls
                        .map(ac -> "clsname='" + ac.getClsname() + "' (" + ac.cmpLabel() + " type, clscode=" + ac.getClscode() + ")")
                        .orElse("clscode=" + effectiveClsCode + " (not found in admcls)");
                diagQuery =
                    "SELECT DISTINCT pa.slname, pa.slid, ac.clsname, ac.cmpcode" +
                    " FROM v_pending_adj pa" +
                    " JOIN finsl fs ON fs.slname = pa.slname AND fs.slid = pa.slid" +
                    " JOIN admcls ac ON ac.clscode = fs.clscode" +
                    " WHERE pa.admou_code IN (" + ouCodes + ")" +
                    " AND pa.ReferenceSiteCode IN (" + ownerSiteCodes + ")" +
                    " AND pa.debit_credit = 'D'" +
                    " AND pa.pending > 0" +
                    " AND fs.clscode = " + effectiveClsCode +
                    " AND fs.ext = 'N'" +
                    " ORDER BY pa.slname;";
            } else {
                slFilterDesc = "any (no clscode filter applied)";
                diagQuery =
                    "SELECT DISTINCT pa.slname, pa.slid" +
                    " FROM v_pending_adj pa" +
                    " JOIN finsl fs ON fs.slname = pa.slname AND fs.slid = pa.slid" +
                    " WHERE pa.admou_code IN (" + ouCodes + ")" +
                    " AND pa.ReferenceSiteCode IN (" + ownerSiteCodes + ")" +
                    " AND pa.debit_credit = 'D'" +
                    " AND pa.pending > 0" +
                    " AND fs.ext = 'N'" +
                    " ORDER BY pa.slname;";
            }
            throw new IllegalStateException(
                    "No sub-ledger with pending AR (debit_credit='D', pending>0, ReferenceSiteCode=" +
                    ownerSite.code + ") found in v_pending_adj for owner site '" + ownerSite.name + "'." +
                    " Sub-ledger filter: " + slFilterDesc + "." +
                    " Ensure open AR documents exist for this sub-ledger type at this site." +
                    "\nDiagnostic query (run in DB to verify data):\n" + diagQuery);
        }

        // 5b. Resolve GL (AR/AP ledger) name for each pending SL via finsl → fingl
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

        // 6. Open documents per (slName, slId) — scoped to owner site via ReferenceSiteCode
        Map<String, List<PendingSlDocument>> pendingDocsBySlName =
                PendingAdjQuery.findDocumentsForSlNames(jdbi, ouCodes, ownerSiteCodes, pendingSlNames, opts.getDocumentCount());

        // 7. AR Voucher doc scheme verified for user's site
        String docSchemeName = findArVoucherDocSchemeName(jdbi, d, siteCodes);

        // 8. Package into LiveDbRefs
        LiveDbRefs refs = new LiveDbRefs(
                cashBankName,
                ownerSiteName,
                docSchemeName,
                pendingSlNames,
                pendingDocsBySlName,
                slToArApName,
                targetCashBankType
        );

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

    /**
     * Finds the AR Voucher doc scheme name that is:
     * 1. Linked to 'FinanceARVoucher' menu shortcode in admmenu_docscheme
     * 2. Assigned to at least one of the user's sites in admou_docscheme
     */
    private static String findArVoucherDocSchemeName(Jdbi jdbi, DbDialect d, List<Long> siteCodes) {
        String sql =
            "SELECT d.docname " +
            "FROM admdocscheme d " +
            "WHERE d.doccode IN (" +
            "    SELECT mds.doccode FROM admmenu_docscheme mds " +
            "    WHERE mds.mdmenushortcode = 'FinanceARVoucher'" +
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
                "No AR Voucher doc scheme found for sites: " + siteCodes +
                ". Check admmenu_docscheme WHERE mdmenushortcode='FinanceARVoucher' " +
                "and admou_docscheme for the user's sites."));
    }

    // ─── Logging helpers ──────────────────────────────────────────────────────

    private static void printRefs(String loginName, long ecode,
                                  LiveDbRefs refs,
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

    private static void printGeneratedData(MergedFinanceArVoucherData row) {
        Log.info("[DB] ------------------------------------------------------------");
        Log.info("[DB]   Scenario   = {}", row.getScenarioType());
        Log.info("[DB]   TestCaseId = {}", row.getMasterTestData().getTestcaseid());
        Log.info("[DB]   Username   = {}", row.getMasterTestData().getUsername());
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
