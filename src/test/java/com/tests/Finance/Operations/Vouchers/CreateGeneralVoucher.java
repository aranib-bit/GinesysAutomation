package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.ApiFlow;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.TestCaseId;
import com.gsl.driver.DriverManager;
import com.gsl.enums.AppOperation;
import com.gsl.enums.TestAuthor;
import com.gsl.network.pattern.FinanceApiPattern;
import com.gsl.pages.Finance.Operations.GeneralVoucherAdd;
import com.gsl.pages.Finance.Operations.GeneralVoucherListPage;
import com.gsl.pages.HomePage;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Operations.OperationsMenuType;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.MergedFinGeneralVoucherDataSupplier;
import com.gsl.utils.dataprovider.source.finance.DbGeneralVoucherDataSource;
import com.tests.WebTest;
import org.testng.annotations.Test;

/**
 * Positive-path tests for General Voucher — mirrors {@code CreateApVoucher}'s flow shape, split
 * into two methods with distinct {@code @TestCaseId} tags (each a genuinely different scenario,
 * same principle as AP's ADV vs DOC split):
 * <ul>
 *   <li>{@link #createGeneralVoucher} ({@code FIN-GV-ADD-COMBO}) — the full ledger-type
 *       combination matrix (Account Type x Type1/2/3 x side x reversal x payment, plus the
 *       GL-count x Site-count breakup — see {@code GeneralVoucherCombinations}). Each row is
 *       exactly ONE ledger type.</li>
 *   <li>{@link #createGeneralVoucherMixedTypes} ({@code FIN-GV-ADD-MIXED}) — the one true "MIXED"
 *       scenario: a single voucher whose rows genuinely span multiple different ledger types
 *       (Type 1: no Site/no Sub Ledger, Type 2: Site only, Type 3: Site + Sub Ledger) in one
 *       save. "MIXED" is reserved for this method only — it is no longer used as a catch-all tag
 *       for the combination matrix's own (single-ledger-type) rows.</li>
 * </ul>
 *
 * <p>Both methods delegate fill/save/verify/logout/DB-validation to
 * {@code GeneralVoucherPredicateFactory.POSITIVE_CREATE_BEHAVIOR} via
 * {@link com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherScenarioType#execute} — see
 * the {@code VoucherScenarioType} unification. Login and the lazy-DB-load block stay here
 * rather than moving into the behavior: GV's {@code DbGeneralVoucherDataSource}
 * {@code GetAllSiteForAUser} lookup executes a relative-URL XHR inside the current Selenium
 * browser's page context and needs the app already loaded, so the lazy load must run after
 * login/navigation — the opposite ordering from AP/AR (see the lazy-load block below for the
 * full root-cause note).
 *
 * <p>Per-row GL-type branching lives in
 * {@code GeneralVoucherExpenseIncomeDetail.fillExpenseIncomeRows}, not here —
 * matching how AP/AR keep row-fill branching inside their
 * {@code *SubLedgerInformation} components and keep the test class itself
 * declarative.
 */
@ApiFlow(FinanceApiPattern.GENERAL_VOUCHER)
public class CreateGeneralVoucher extends WebTest {

    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceGenVoucher", operation = AppOperation.ADD)
    @TestCaseId("FIN-GV-ADD-COMBO")
    @Test(description = "Create General Voucher – ledger-type combination matrix",
            dataProvider = "FinanceGeneralVoucherData",
            dataProviderClass = MergedFinGeneralVoucherDataSupplier.class)
    public void createGeneralVoucher(UserCredential user, MergedFinanceGeneralVoucherData data) {
        Log.info("Create General Voucher (ledger-type combination matrix) from UI");
        runVoucherCreationFlow(data,
                d -> DbGeneralVoucherDataSource.loadFreshForScenario(d, "createGeneralVoucher"));
    }

    /**
     * The one true "MIXED" scenario — a single voucher whose Expense/Income Detail rows span
     * multiple different ledger types (Asset/Type1, Liability/Type2, Income/Type3), fulfilling
     * this class's original claim of "one General Voucher mixing all 3 GL types" that
     * {@link #createGeneralVoucher} itself never actually implements (each of its rows is exactly
     * one ledger type — see {@code GeneralVoucherCombinations}'s "GL count x Site count" breakup).
     * This is now the only method carrying the {@code FIN-GV-ADD-MIXED} tag.
     */
    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceGenVoucher", operation = AppOperation.ADD)
    @TestCaseId("FIN-GV-ADD-MIXED")
    @Test(description = "Create General Voucher – multiple ledger types mixed in one voucher",
            dataProvider = "FinanceGeneralVoucherData",
            dataProviderClass = MergedFinGeneralVoucherDataSupplier.class)
    public void createGeneralVoucherMixedTypes(UserCredential user, MergedFinanceGeneralVoucherData data) {
        Log.info("Create General Voucher (multiple ledger types mixed in one voucher) from UI");
        runVoucherCreationFlow(data,
                d -> DbGeneralVoucherDataSource.loadFreshForMixedTypes(d, "createGeneralVoucherMixedTypes"));
    }

    /**
     * Shared entry flow for both {@link #createGeneralVoucher} and
     * {@link #createGeneralVoucherMixedTypes} — login, resolve test data (lazy DB load),
     * navigate, open the Add form, then delegate the entire fill/save/verify/DB-validate/logout
     * sequence to the scenario's behavior via
     * {@link com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherScenarioType#execute}.
     *
     * @param placeholderOrData the row from the DataProvider — either already-resolved Excel-mode
     *                          data, or a DB-mode placeholder (empty {@code expenseIncomeDetailList})
     * @param lazyLoader        resolves a DB-mode placeholder into full data — either
     *                          {@code DbGeneralVoucherDataSource.loadFreshForScenario} or
     *                          {@code .loadFreshForMixedTypes}, bound by the caller
     */
    private void runVoucherCreationFlow(MergedFinanceGeneralVoucherData placeholderOrData,
            java.util.function.UnaryOperator<MergedFinanceGeneralVoucherData> lazyLoader) {

        final HomePage[]              homeRef = new HomePage[1];
        final GeneralVoucherListPage[] listRef = new GeneralVoucherListPage[1];
        final GeneralVoucherAdd[]      addRef  = new GeneralVoucherAdd[1];

        // ── Step 1: Login ─────────────────────────────────────────────────────────
        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            boolean wasAlreadyLoggedIn = com.gsl.driver.ExecutionContext.current().isLoggedIn();
            long loginStartMs = System.currentTimeMillis();
            homeRef[0] = com.gsl.driver.SessionManager.ensureLoggedIn(u);
            long loginElapsedMs = System.currentTimeMillis() - loginStartMs;
            ReportLogger.business("Logged in as: " + u.username());
            com.gsl.reports.StepSummaryBuffer.record(wasAlreadyLoggedIn
                    ? "Login: reused existing session for " + u.username() + " — landing page confirmed loaded."
                    : "Login: fresh login for " + u.username() + " in " + loginElapsedMs
                            + "ms — landing page confirmed loaded.");
        });

        // ── Lazy DB load — deliberately runs AFTER Step 1 (login/navigation), not before as it
        // originally did. Some data-source live calls (e.g. DbGeneralVoucherDataSource's
        // GetAllSiteForAUser lookup) execute a relative-URL XHR inside the current Selenium
        // browser's page context, which requires the app to already be loaded — see run
        // 20260811_002852's "GetAllSiteForAUser returned success=false" root-cause: every failure
        // there was a "first use" pooled session whose browser hadn't navigated anywhere yet under
        // the old before-Step-1 ordering. Placeholder detection mirrors CreateApVoucher's
        // negative-boundary lazy-load guard: an empty expenseIncomeDetailList signals a Phase 1
        // placeholder from DbGeneralVoucherDataSource.load(), resolved fresh here at
        // test-execution time so DB skip/backfill decisions happen per invocation.
        MergedFinanceGeneralVoucherData data = placeholderOrData;
        if (data.getScenarioType() != null
                && (data.getExpenseIncomeDetailList() == null || data.getExpenseIncomeDetailList().isEmpty())) {
            Log.debug("[DB mode] Placeholder detected for scenario={}", data.getScenarioType());
            data = lazyLoader.apply(data);
        }
        Log.info("Running test for: {}", data.getMasterTestData() != null
                ? data.getMasterTestData().getTestcaseid() : data.getScenarioTypeLabel());

        com.gsl.reports.DbGatheringExtentLogger.log(data);
        com.gsl.reports.GeneralVoucherExtentDescriptionLogger.log(data);

        ActualResultsHolder.get().setVoucherType("GV");
        ActualResultsHolder.get().setTestData(data);

        final MergedFinanceGeneralVoucherData testData = data;

        // ── Step 2: Navigate ──────────────────────────────────────────────────────
        StepExecutor.step("Open Finance → General Voucher List", () -> {
            listRef[0] = NavigationUtils.navigateToFinanceSideMenu(
                    homeRef[0],
                    MainMenuType.OPERATIONS,
                    OperationsMenuType.CATEGORY,
                    GeneralVoucherListPage.class);
            ReportLogger.business("General Voucher list page opened");
            com.gsl.reports.StepSummaryBuffer.record(
                    "Navigated: Finance --&gt; Operations --&gt; General Voucher");
        });

        // ── Step 3: Open Add form ─────────────────────────────────────────────────
        StepExecutor.step("Open Add Form", () -> {
            addRef[0] = listRef[0].clickAdd();
            com.gsl.reports.StepSummaryBuffer.record("Add form opened for General Voucher");
        });

        // ── Delegate fill/save/verify/logout to the scenario behaviour ────────────
        // (GeneralVoucherPredicateFactory.POSITIVE_CREATE_BEHAVIOR — see VoucherScenarioType unification)
        boolean ok = testData.getScenarioType().execute(testData, addRef[0]);
        org.testng.Assert.assertTrue(ok,
                "Scenario behaviour did not complete successfully: " + testData.getScenarioType());
    }
}
