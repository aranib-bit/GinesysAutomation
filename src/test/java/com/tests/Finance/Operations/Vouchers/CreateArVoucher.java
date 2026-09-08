package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.ApiFlow;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.TestCaseId;
import com.gsl.network.pattern.FinanceApiPattern;
import com.gsl.enums.AppOperation;
import com.gsl.enums.TestAuthor;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.pages.Finance.Operations.ArVoucherListPage;
import com.gsl.pages.HomePage;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Operations.OperationsMenuType;
import com.gsl.pages.Finance.Operations.ArVoucherAdd;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.driver.DriverManager;
import com.gsl.testdata.UserCredential;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.reports.ArVoucherExtentDescriptionLogger;
import com.gsl.reports.DbGatheringExtentLogger;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.utils.Log;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.MergedFinArVoucherDataSupplier;
import com.gsl.utils.dataprovider.source.finance.DbArVoucherDataSource;
import com.tests.WebTest;
import org.testng.annotations.Test;


@ApiFlow(FinanceApiPattern.AR_VOUCHER)
public class CreateArVoucher extends WebTest {

    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceARVoucher", operation = AppOperation.ADD)
    @TestCaseId("FIN-AR-ADD-DOC")
    @Test(description = "Create AR Voucher",   // P2: human-readable sentence set dynamically in TestListener
            dataProvider = "dataSupplier", dataProviderClass = MergedFinArVoucherDataSupplier.class)
    public void createArVoucher(UserCredential user, MergedFinanceArVoucherData data) {

        Log.info("Create AR Voucher of Finance from UI");
        Log.info("Running test for: {}", data.getMasterTestData().getTestcaseid());

        // ── Lazy DB load — runs before steps so full data is ready ──────────────
        if (data.getScenarioType() != null
                && (data.getSubLedgerInformationList() == null
                    || data.getSubLedgerInformationList().isEmpty())) {
            Log.debug("[DB mode] Placeholder detected for scenario={}, fetching fresh data from DB",
                    data.getScenarioType());
            // Data-availability shortfalls are raised as a SkipException inside the data source
            // (DbArVoucherDataSource.loadFreshForScenario, guarded by DataLoadScope) so they render
            // as an amber Skip rather than a red Fail. No try/catch conversion needed here.
            data = DbArVoucherDataSource.loadFreshForScenario(data, "createArVoucher");
        }

        if (data.getScenarioType() != null) {
            Log.debug("[DB mode] Scenario type: {} | testCaseId: {}",
                    data.getScenarioType(), data.getMasterTestData().getTestcaseid());
        }

        // ── P3: Log DB data-gathering section (DB mode only; no-op in Excel mode) ─
        DbGatheringExtentLogger.log(data);

        // ── Log rich test data to Extent Report (must be before first step) ─────
        ArVoucherExtentDescriptionLogger.log(data);

        // ── Seed ActualResultsHolder with test-input snapshot for the Actual Results table ──
        ActualResultsHolder.get().setVoucherType("AR");
        ActualResultsHolder.get().setTestData(data);

        // ── Effectively-final references for lambda use ──────────────────────────
        final MergedFinanceArVoucherData testData = data;

        final HomePage[]          homeRef = new HomePage[1];
        final ArVoucherListPage[] listRef = new ArVoucherListPage[1];
        final ArVoucherAdd[]      addRef  = new ArVoucherAdd[1];

        // ── Step 1: Login ─────────────────────────────────────────────────────────
        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = com.gsl.driver.SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        // ── Step 2: Navigate ──────────────────────────────────────────────────────
        StepExecutor.step("Open Finance → AR Voucher List", () -> {
            listRef[0] = NavigationUtils.navigateToFinanceSideMenu(
                    homeRef[0],
                    MainMenuType.OPERATIONS,
                    OperationsMenuType.ARVOUCHER,
                    ArVoucherListPage.class);
            ReportLogger.business("AR Voucher list page opened");
        });

        // ── Step 3: Open Add form ─────────────────────────────────────────────────
        StepExecutor.step("Open Add Form", () -> addRef[0] = listRef[0].clickAdd());

        // ── Delegate fill/save/verify/logout to the scenario behaviour ────────────
        // (ArVoucherScenarioType.POSITIVE_CREATE_BEHAVIOR — see VoucherScenarioType unification)
        boolean ok = testData.getScenarioType().execute(testData, addRef[0]);
        org.testng.Assert.assertTrue(ok,
                "Scenario behaviour did not complete successfully: " + testData.getScenarioType());
    }

    /**
     * T-03 — Negative &amp; Boundary scenarios for AR Voucher.
     *
     * <p>Delegates the entire fill/save/verify/logout flow to the scenario's behaviour via
     * {@link com.gsl.utils.dataprovider.fixture.finance.ArVoucherScenarioType#execute}:
     * <ul>
     *   <li><b>NEG_*</b> / boundary <b>*_INVALID</b> → {@code NEGATIVE_FORM_VALIDATION_BEHAVIOR}
     *       (Save, assert the expected validation-error popup, cancel, logout).</li>
     *   <li>boundary <b>*_VALID</b> → {@code BOUNDARY_PASS_BEHAVIOR} (full positive save + DB validation).</li>
     * </ul>
     */
    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceARVoucher", operation = AppOperation.ADD)
    @TestCaseId("FIN-AR-ADD-NEG")
    @Test(description = "Create AR Voucher – Negative & Boundary",
            dataProvider = "dataSupplier", dataProviderClass = MergedFinArVoucherDataSupplier.class)
    public void createArVoucherNegativeBoundary(UserCredential user, MergedFinanceArVoucherData data) {

        Log.info("Create AR Voucher (Negative & Boundary) from UI");
        Log.info("Running test for: {}", data.getMasterTestData().getTestcaseid());

        // ── Lazy DB load — DbArVoucherDataSource raises SkipException internally on shortfall ───
        // * NOTE: NEG_BLANK_SUBLEDGER legitimately has an empty SL list; the single-shot guard
        // still loads it correctly (loadFreshForScenario returns it once).
        if (data.getScenarioType() != null
                && (data.getSubLedgerInformationList() == null
                    || data.getSubLedgerInformationList().isEmpty())) {
            Log.debug("[DB mode] Placeholder detected for scenario={}", data.getScenarioType());
            data = DbArVoucherDataSource.loadFreshForScenario(data, "createArVoucherNegativeBoundary");
        }

        DbGatheringExtentLogger.log(data);
        ArVoucherExtentDescriptionLogger.log(data);

        ActualResultsHolder.get().setVoucherType("AR");
        ActualResultsHolder.get().setNegativeScenario(true);
        ActualResultsHolder.get().setTestData(data);

        final MergedFinanceArVoucherData testData = data;

        final HomePage[]          homeRef = new HomePage[1];
        final ArVoucherListPage[] listRef = new ArVoucherListPage[1];
        final ArVoucherAdd[]      addRef  = new ArVoucherAdd[1];

        // ── Step 1: Login ─────────────────────────────────────────────────────────
        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = com.gsl.driver.SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        // ── Step 2: Navigate ──────────────────────────────────────────────────────
        StepExecutor.step("Open Finance → AR Voucher List", () -> {
            listRef[0] = NavigationUtils.navigateToFinanceSideMenu(
                    homeRef[0],
                    MainMenuType.OPERATIONS,
                    OperationsMenuType.ARVOUCHER,
                    ArVoucherListPage.class);
            ReportLogger.business("AR Voucher list page opened");
        });

        // ── Step 3: Open Add form ─────────────────────────────────────────────────
        StepExecutor.step("Open Add Form", () -> {
            addRef[0] = listRef[0].clickAdd();
            ReportLogger.business("Scenario: " + testData.getScenarioType()
                    + (testData.getExpectedValidation() != null
                        ? " | expect error: " + testData.getExpectedValidation().expectedMessage()
                        : " | expect: successful save"));
        });

        // ── Delegate fill/save/verify/logout to the scenario behaviour ────────────
        boolean ok = testData.getScenarioType().execute(testData, addRef[0]);
        org.testng.Assert.assertTrue(ok,
                "Scenario behaviour did not complete successfully: " + testData.getScenarioType());
    }
}
