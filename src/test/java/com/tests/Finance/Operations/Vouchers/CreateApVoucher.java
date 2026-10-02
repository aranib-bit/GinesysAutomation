package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.ApiFlow;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.TestCaseId;
import com.gsl.driver.DriverManager;
import com.gsl.enums.AppOperation;
import com.gsl.enums.TestAuthor;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.network.pattern.FinanceApiPattern;
import com.gsl.pages.Finance.Operations.ApVoucherAdd;
import com.gsl.pages.Finance.Operations.ApVoucherListPage;
import com.gsl.pages.HomePage;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Operations.OperationsMenuType;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.reports.ApVoucherExtentDescriptionLogger;
import com.gsl.reports.DbGatheringExtentLogger;
import com.gsl.utils.dataprovider.source.finance.DbApVoucherDataSource;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.MergedFinApVoucherDataSupplier;
import com.gsl.utils.ui.UiFieldState;
import com.gsl.validation.ui.UiValidationLevel;
import com.tests.WebTest;
import org.testng.annotations.Test;

@ApiFlow(FinanceApiPattern.AP_VOUCHER)
public class CreateApVoucher extends WebTest {

    /**
     * Advance payment scenario (On Account = Yes).
     * SelectDocument and FIFO are never present.
     * TDS & Net Payable section is filled when tdsProvided = true.
     */
    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceAPVoucher", operation = AppOperation.ADD)
    @TestCaseId("FIN-AP-ADD-ADV")
    @Test(description = "Create AP Voucher – Advance Payment",
            dataProvider = "FinanceApVoucherData",
            dataProviderClass = MergedFinApVoucherDataSupplier.class)
    public void createApVoucherAdvancePayment(UserCredential user, MergedFinanceApVoucherData data) {

        Log.info("Create AP Voucher (Advance Payment) from UI");
        Log.info("Running test for: {}", data.getMasterTestData().getTestcaseid());

        // ── Lazy DB load — runs before steps so full data is ready ───────────────
        if (data.getScenarioType() != null
                && (data.getSubLedgerInformationList() == null
                    || data.getSubLedgerInformationList().isEmpty())) {
            Log.debug("[DB mode] Placeholder detected for scenario={}", data.getScenarioType());
            try {
                data = DbApVoucherDataSource.loadFreshForScenario(data, "createApVoucherAdvancePayment");
            } catch (IllegalStateException e) {
                throw new org.testng.SkipException(
                        "[CreateApVoucher] Skipping — insufficient test data for scenario '"
                        + data.getScenarioType() + "': " + e.getMessage());
            }
        }

        // ── P3: Log DB data-gathering section (no-op in Excel mode) ─────────────
        DbGatheringExtentLogger.log(data);

        ApVoucherExtentDescriptionLogger.log(data);

        ActualResultsHolder.get().setVoucherType("AP");
        ActualResultsHolder.get().setTestData(data);

        final MergedFinanceApVoucherData testData = data;

        final HomePage[]            homeRef = new HomePage[1];
        final ApVoucherListPage[]   listRef = new ApVoucherListPage[1];
        final ApVoucherAdd[]        addRef  = new ApVoucherAdd[1];

        // ── Step 1: Login ─────────────────────────────────────────────────────────
        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = com.gsl.driver.SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        // ── Step 2: Navigate ──────────────────────────────────────────────────────
        // Business log + "Navigated: ..." summary are now recorded inside
        // NavigationUtils.navigateToFinanceSideMenu itself.
        StepExecutor.step("Open Finance → AP Voucher List", () -> listRef[0] =
                NavigationUtils.navigateToFinanceSideMenu(
                        homeRef[0],
                        MainMenuType.OPERATIONS,
                        OperationsMenuType.APVOUCHER,
                        ApVoucherListPage.class));

        // ── Step 3: Open Add form ─────────────────────────────────────────────────
        StepExecutor.step("Open Add Form", () -> addRef[0] = listRef[0].clickAdd());

        StepExecutor.step("UI Validation", () ->
                addRef[0].validateUiContract(UiValidationLevel.CONTRACT, UiFieldState.EDITABLE, false));

        // ── Delegate fill/save/verify/logout to the scenario behaviour ────────────
        // (ApVoucherScenarioType.ADVANCE_POSITIVE_BEHAVIOR — see VoucherScenarioType unification)
        boolean ok = testData.getScenarioType().execute(testData, addRef[0]);
        org.testng.Assert.assertTrue(ok,
                "Scenario behaviour did not complete successfully: " + testData.getScenarioType());
    }

    /**
     * Document payment scenario (On Account = No).
     * SelectDocument is mandatory and always present.
     * FIFO setoff is filled when referenceSitewiseFIFOList is non-empty.
     * TDS & Net Payable is never present.
     */
    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceAPVoucher", operation = AppOperation.ADD)
    @TestCaseId("FIN-AP-ADD-DOC")
    @Test(description = "Create AP Voucher – Document Payment",
            dataProvider = "FinanceApVoucherData",
            dataProviderClass = MergedFinApVoucherDataSupplier.class)
    public void createApVoucherDocumentPayment(UserCredential user, MergedFinanceApVoucherData data) {

        Log.info("Create AP Voucher (Document Payment) from UI");
        Log.info("Running test for: {}", data.getMasterTestData().getTestcaseid());

        // ── Lazy DB load — runs before steps so full data is ready ───────────────
        if (data.getScenarioType() != null
                && (data.getSubLedgerInformationList() == null
                    || data.getSubLedgerInformationList().isEmpty())) {
            Log.debug("[DB mode] Placeholder detected for scenario={}", data.getScenarioType());
            try {
                data = DbApVoucherDataSource.loadFreshForScenario(data, "createApVoucherDocumentPayment");
            } catch (IllegalStateException e) {
                throw new org.testng.SkipException(
                        "[CreateApVoucher] Skipping — insufficient test data for scenario '"
                        + data.getScenarioType() + "': " + e.getMessage());
            }
        }

        // ── P3: Log DB data-gathering section (no-op in Excel mode) ─────────────
        DbGatheringExtentLogger.log(data);

        ApVoucherExtentDescriptionLogger.log(data);

        ActualResultsHolder.get().setVoucherType("AP");
        ActualResultsHolder.get().setTestData(data);

        final MergedFinanceApVoucherData testData = data;

        final HomePage[]            homeRef = new HomePage[1];
        final ApVoucherListPage[]   listRef = new ApVoucherListPage[1];
        final ApVoucherAdd[]        addRef  = new ApVoucherAdd[1];

        // ── Step 1: Login ─────────────────────────────────────────────────────────
        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = com.gsl.driver.SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        // ── Step 2: Navigate ──────────────────────────────────────────────────────
        // Business log + "Navigated: ..." summary are now recorded inside
        // NavigationUtils.navigateToFinanceSideMenu itself.
        StepExecutor.step("Open Finance → AP Voucher List", () -> listRef[0] =
                NavigationUtils.navigateToFinanceSideMenu(
                        homeRef[0],
                        MainMenuType.OPERATIONS,
                        OperationsMenuType.APVOUCHER,
                        ApVoucherListPage.class));

        // ── Step 3: Open Add form ─────────────────────────────────────────────────
        StepExecutor.step("Open Add Form", () -> addRef[0] = listRef[0].clickAdd());

        StepExecutor.step("UI Validation", () ->
                addRef[0].validateUiContract(UiValidationLevel.CONTRACT, UiFieldState.EDITABLE, false));

        // ── Delegate fill/save/verify/logout to the scenario behaviour ────────────
        // (ApVoucherScenarioType.DOC_POSITIVE_BEHAVIOR — see VoucherScenarioType unification)
        boolean ok = testData.getScenarioType().execute(testData, addRef[0]);
        org.testng.Assert.assertTrue(ok,
                "Scenario behaviour did not complete successfully: " + testData.getScenarioType());
    }

    /**
     * Document payment where the adjusted total deliberately differs from the row's payment amount,
     * so Adjustment Entry's OK raises the amount-mismatch Confirmation ("Update payment amount" /
     * "I will adjust more"). Same flow as {@link #createApVoucherDocumentPayment}; the scenario's
     * {@code adjustmentMismatchPlan} (carried on the data, see {@code docPartialTemplate}) scripts
     * how the popup is answered and DB validation asserts payment == fintag total (see
     * {@code ApVoucherPredicateFactory#DOC_MISMATCH_BEHAVIOR}).
     */
    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceAPVoucher", operation = AppOperation.ADD)
    @TestCaseId("FIN-AP-ADD-DOC-MISMATCH")
    @Test(description = "Create AP Voucher – Document Payment with adjustment amount mismatch (Update payment / Adjust more)",
            dataProvider = "FinanceApVoucherData",
            dataProviderClass = MergedFinApVoucherDataSupplier.class)
    public void createApVoucherDocumentMismatch(UserCredential user, MergedFinanceApVoucherData data) {

        Log.info("Create AP Voucher (Document Payment — amount mismatch) from UI");
        Log.info("Running test for: {}", data.getMasterTestData().getTestcaseid());

        if (data.getScenarioType() != null
                && (data.getSubLedgerInformationList() == null
                    || data.getSubLedgerInformationList().isEmpty())) {
            try {
                data = DbApVoucherDataSource.loadFreshForScenario(data, "createApVoucherDocumentMismatch");
            } catch (IllegalStateException e) {
                throw new org.testng.SkipException(
                        "[CreateApVoucher] Skipping — insufficient test data for scenario '"
                        + data.getScenarioType() + "': " + e.getMessage());
            }
        }

        DbGatheringExtentLogger.log(data);
        ApVoucherExtentDescriptionLogger.log(data);

        ActualResultsHolder.get().setVoucherType("AP");
        ActualResultsHolder.get().setTestData(data);

        final MergedFinanceApVoucherData testData = data;

        final HomePage[]            homeRef = new HomePage[1];
        final ApVoucherListPage[]   listRef = new ApVoucherListPage[1];
        final ApVoucherAdd[]        addRef  = new ApVoucherAdd[1];

        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = com.gsl.driver.SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        StepExecutor.step("Open Finance → AP Voucher List", () -> listRef[0] =
                NavigationUtils.navigateToFinanceSideMenu(
                        homeRef[0],
                        MainMenuType.OPERATIONS,
                        OperationsMenuType.APVOUCHER,
                        ApVoucherListPage.class));

        StepExecutor.step("Open Add Form", () -> addRef[0] = listRef[0].clickAdd());

        StepExecutor.step("UI Validation", () ->
                addRef[0].validateUiContract(UiValidationLevel.CONTRACT, UiFieldState.EDITABLE, false));

        boolean ok = testData.getScenarioType().execute(testData, addRef[0]);
        org.testng.Assert.assertTrue(ok,
                "Scenario behaviour did not complete successfully: " + testData.getScenarioType());
    }

    /**
     * T-03 — Negative &amp; Boundary scenarios.
     *
     * <p>Unlike the positive create methods above, this method delegates the entire
     * fill/save/verify/logout flow to the scenario's behaviour via
     * {@link com.gsl.utils.dataprovider.fixture.finance.ApVoucherScenarioType#execute}:
     * <ul>
     *   <li><b>NEG_*</b> / boundary <b>*_INVALID</b> → {@code NEGATIVE_FORM_VALIDATION_BEHAVIOR}
     *       (clicks Save, asserts the expected validation-error popup, cancels, logs out).</li>
     *   <li>boundary <b>*_VALID</b> → {@code BOUNDARY_PASS_BEHAVIOR} (full positive save + DB validation).</li>
     * </ul>
     * No inline Save/DB-validation steps here — the behaviour owns them.
     */
    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceAPVoucher", operation = AppOperation.ADD)
    @TestCaseId("FIN-AP-ADD-NEG")
    @Test(description = "Create AP Voucher – Negative & Boundary",
            dataProvider = "FinanceApVoucherData",
            dataProviderClass = MergedFinApVoucherDataSupplier.class)
    public void createApVoucherNegativeBoundary(UserCredential user, MergedFinanceApVoucherData data) {

        Log.info("Create AP Voucher (Negative & Boundary) from UI");
        Log.info("Running test for: {}", data.getMasterTestData().getTestcaseid());

        // ── Lazy DB load — runs before steps so full data is ready ───────────────
        // * NOTE: NEG_BLANK_SUBLEDGER legitimately has an empty SL list; the single-shot
        // guard still loads it correctly (loadFreshForScenario returns it once).
        if (data.getScenarioType() != null
                && (data.getSubLedgerInformationList() == null
                    || data.getSubLedgerInformationList().isEmpty())) {
            Log.debug("[DB mode] Placeholder detected for scenario={}", data.getScenarioType());
            try {
                data = DbApVoucherDataSource.loadFreshForScenario(data, "createApVoucherNegativeBoundary");
            } catch (IllegalStateException e) {
                throw new org.testng.SkipException(
                        "[CreateApVoucher] Skipping — insufficient test data for scenario '"
                        + data.getScenarioType() + "': " + e.getMessage());
            }
        }

        DbGatheringExtentLogger.log(data);
        ApVoucherExtentDescriptionLogger.log(data);

        ActualResultsHolder.get().setVoucherType("AP");
        ActualResultsHolder.get().setNegativeScenario(true);
        ActualResultsHolder.get().setTestData(data);

        final MergedFinanceApVoucherData testData = data;

        final HomePage[]          homeRef = new HomePage[1];
        final ApVoucherListPage[] listRef = new ApVoucherListPage[1];
        final ApVoucherAdd[]      addRef  = new ApVoucherAdd[1];

        // ── Step 1: Login ─────────────────────────────────────────────────────────
        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = com.gsl.driver.SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        // ── Step 2: Navigate ──────────────────────────────────────────────────────
        // Business log + "Navigated: ..." summary are now recorded inside
        // NavigationUtils.navigateToFinanceSideMenu itself.
        StepExecutor.step("Open Finance → AP Voucher List", () -> listRef[0] =
                NavigationUtils.navigateToFinanceSideMenu(
                        homeRef[0],
                        MainMenuType.OPERATIONS,
                        OperationsMenuType.APVOUCHER,
                        ApVoucherListPage.class));

        // ── Step 3: Open Add form ─────────────────────────────────────────────────
        StepExecutor.step("Open Add Form", () -> {
            addRef[0] = listRef[0].clickAdd();
            ReportLogger.business("Scenario: " + testData.getScenarioType()
                    + (testData.getExpectedValidation() != null
                        ? " | expect error: " + testData.getExpectedValidation().expectedMessage()
                        : " | expect: successful save"));
        });

        StepExecutor.step("UI Validation", () ->
                addRef[0].validateUiContract(UiValidationLevel.CONTRACT, UiFieldState.EDITABLE, false));

        // ── Delegate fill/save/verify/logout to the scenario behaviour ────────────
        boolean ok = testData.getScenarioType().execute(testData, addRef[0]);
        org.testng.Assert.assertTrue(ok,
                "Scenario behaviour did not complete successfully: " + testData.getScenarioType());
    }
}
