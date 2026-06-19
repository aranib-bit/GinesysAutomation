package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.ApiFlow;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.TestCaseId;
import com.gsl.driver.DriverManager;
import com.gsl.enums.AppOperation;
import com.gsl.enums.TestAuthor;
import com.gsl.enums.VoucherDomain;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.network.pattern.FinanceApiPattern;
import com.gsl.pages.Finance.Operations.ApVoucherAdd;
import com.gsl.pages.Finance.Operations.ApVoucherListPage;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.flow.Finance.Operations.Common.PostSaveDialogHandler;
import com.gsl.pages.HomePage;
import com.gsl.pages.LoginPage;
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
import com.gsl.validation.db.finance.APVoucherDbValidator;
import com.gsl.validation.db.finance.VoucherDbContextBuilder;
import com.tests.WebTest;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

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

        final HomePage[]            homeRef       = new HomePage[1];
        final ApVoucherListPage[]   listRef       = new ApVoucherListPage[1];
        final ApVoucherAdd[]        addRef        = new ApVoucherAdd[1];
        final String[]              docNoRef      = new String[1];
        final com.gsl.pages.Common.DocumentMessageBox[] msgBoxRef = new com.gsl.pages.Common.DocumentMessageBox[1];
        final DocumentPopup[]       popupRef      = new DocumentPopup[1];
        final SoftAssert softAssert = new SoftAssert();

        // ── Step 1: Login ─────────────────────────────────────────────────────────
        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = new LoginPage().loginToApplication(u.username(), u.password());
            ReportLogger.info("Logged in as: " + u.username());
        });

        // ── Step 2: Navigate ──────────────────────────────────────────────────────
        StepExecutor.step("Open Finance → AP Voucher List", () -> {
            listRef[0] = NavigationUtils.navigateToFinanceSideMenu(
                    homeRef[0],
                    MainMenuType.OPERATIONS,
                    OperationsMenuType.APVOUCHER,
                    ApVoucherListPage.class);
            ReportLogger.info("AP Voucher list page opened");
        });

        // ── Step 3: Document header ───────────────────────────────────────────────
        StepExecutor.step("Fill Document Information", () -> {
            addRef[0] = listRef[0].clickAdd()
                    .fillDocumentHeader(testData.getDocumentInformation());
            ReportLogger.info("Cash Bank selected: " + testData.getDocumentInformation().getCashbank());
        });

        // ── Step 4: Sub-ledger rows (advance: no SelectDocument/FIFO) ────────────
        StepExecutor.step("Fill Sub-Ledger Information", () -> {
            addRef[0].fillSubLedgerRowsForAdvance(testData, true);
            ReportLogger.info("Sub-ledger section complete: "
                    + testData.getSubLedgerInformationList().size() + " rows");
        });

        // ── Step 5: TDS & Net Payable (advance payment only) ─────────────────────
        if (testData.isTdsProvided()) {
            StepExecutor.step("Fill TDS and Net Payable", () -> {
                addRef[0].fillTdsAndNetPayable(testData);
                ReportLogger.info("TDS rows filled: " + testData.getTdsAndNetPayableList().size());
            });
        }

        // ── Step 5b: Click Save (advance: no SelectDocument → no 5a/5e snapshot) ──
        StepExecutor.step("Click Save", () -> {
            VoucherDbContextBuilder.markSaveStart();
            popupRef[0] = addRef[0].clickSave();
        });

        // ── Step 5c: Handle Post-Save Dialogs (type-aware) ────────────────────────
        StepExecutor.step("Handle Post-Save Dialogs", () ->
                msgBoxRef[0] = PostSaveDialogHandler.forDomain(VoucherDomain.AP)
                        .handle(popupRef[0], testData));

        // ── Step 5d: Verify Save Result ─────────────────────────────────────────────
        StepExecutor.step("Verify Save Result", () -> {
            docNoRef[0] = msgBoxRef[0].verifyDocumentSave(softAssert).getDocumentNo();
            ReportLogger.pass("Voucher saved. Document No: " + docNoRef[0]);
        });

        // ── Step 6: Close Voucher Page ────────────────────────────────────────────
        StepExecutor.step("Close Voucher Page", () -> {
            if (msgBoxRef[0] != null) {
                msgBoxRef[0].clickOk();
            }
            addRef[0].close().clickYes();   // returns to AP Voucher list
            ReportLogger.info("Voucher page closed, returned to list");
        });

        // ── Step 7: Logout (terminal) ─────────────────────────────────────────────
        StepExecutor.step("Logout", () -> {
            new HomePage().logoutFromApplication();
            ReportLogger.info("Logged out of application");
        });

        // ── Step 8: Database Validation ───────────────────────────────────────────
        StepExecutor.step("Database Validation", () -> {
            new APVoucherDbValidator().validate(
                    VoucherDbContextBuilder.buildAp(docNoRef[0], testData));
            ReportLogger.pass("DB validated. Document No: " + docNoRef[0]);
        });

        // ── Step 9: Finalize save assertions (deferred header check) ──────────────
        StepExecutor.step("Finalize save assertions", softAssert::assertAll);
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

        final HomePage[]            homeRef       = new HomePage[1];
        final ApVoucherListPage[]   listRef       = new ApVoucherListPage[1];
        final ApVoucherAdd[]        addRef        = new ApVoucherAdd[1];
        final String[]              docNoRef      = new String[1];
        final com.gsl.pages.Common.DocumentMessageBox[] msgBoxRef = new com.gsl.pages.Common.DocumentMessageBox[1];
        final DocumentPopup[]       popupRef      = new DocumentPopup[1];
        final SoftAssert softAssert = new SoftAssert();

        // ── Step 1: Login ─────────────────────────────────────────────────────────
        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = new LoginPage().loginToApplication(u.username(), u.password());
            ReportLogger.info("Logged in as: " + u.username());
        });

        // ── Step 2: Navigate ──────────────────────────────────────────────────────
        StepExecutor.step("Open Finance → AP Voucher List", () -> {
            listRef[0] = NavigationUtils.navigateToFinanceSideMenu(
                    homeRef[0],
                    MainMenuType.OPERATIONS,
                    OperationsMenuType.APVOUCHER,
                    ApVoucherListPage.class);
            ReportLogger.info("AP Voucher list page opened");
        });

        // ── Step 3: Document header ───────────────────────────────────────────────
        StepExecutor.step("Fill Document Information", () -> {
            addRef[0] = listRef[0].clickAdd()
                    .fillDocumentHeader(testData.getDocumentInformation());
            ReportLogger.info("Cash Bank selected: " + testData.getDocumentInformation().getCashbank());
        });

        // ── Step 4: Sub-ledger rows + SelectDocument + FIFO + Payment ────────────
        StepExecutor.step("Fill Sub-Ledger Information", () -> {
            addRef[0].fillSubLedgerRowsForDocument(testData, true);
            ReportLogger.info("Sub-ledger section complete: "
                    + testData.getSubLedgerInformationList().size() + " rows");
        });

        // ── Step 5a: Capture Pre-Save DB State (finpost snapshot) ─────────────────
        StepExecutor.step("Capture Pre-Save DB State (finpost snapshot)", () ->
                VoucherDbContextBuilder.capturePreState(testData));

        // ── Step 5b: Click Save ────────────────────────────────────────────────────
        StepExecutor.step("Click Save", () -> {
            VoucherDbContextBuilder.markSaveStart();
            popupRef[0] = addRef[0].clickSave();
        });

        // ── Step 5c: Handle Post-Save Dialogs (type-aware) ────────────────────────
        StepExecutor.step("Handle Post-Save Dialogs", () ->
                msgBoxRef[0] = PostSaveDialogHandler.forDomain(VoucherDomain.AP)
                        .handle(popupRef[0], testData));

        // ── Step 5d: Verify Save Result ─────────────────────────────────────────────
        StepExecutor.step("Verify Save Result", () -> {
            docNoRef[0] = msgBoxRef[0].verifyDocumentSave(softAssert).getDocumentNo();
            ReportLogger.pass("Voucher saved. Document No: " + docNoRef[0]);
        });

        // ── Step 5e: Capture Post-Save DB State (finpost snapshot) ────────────────
        StepExecutor.step("Capture Post-Save DB State (finpost snapshot)", () ->
                VoucherDbContextBuilder.capturePostState());

        // ── Step 6: Close Voucher Page ────────────────────────────────────────────
        StepExecutor.step("Close Voucher Page", () -> {
            if (msgBoxRef[0] != null) {
                msgBoxRef[0].clickOk();
            }
            addRef[0].close().clickYes();   // returns to AP Voucher list
            ReportLogger.info("Voucher page closed, returned to list");
        });

        // ── Step 7: Logout (terminal) ─────────────────────────────────────────────
        StepExecutor.step("Logout", () -> {
            new HomePage().logoutFromApplication();
            ReportLogger.info("Logged out of application");
        });

        // ── Step 8: Database Validation ───────────────────────────────────────────
        StepExecutor.step("Database Validation", () -> {
            new APVoucherDbValidator().validate(
                    VoucherDbContextBuilder.buildAp(docNoRef[0], testData));
            ReportLogger.pass("DB validated. Document No: " + docNoRef[0]);
        });

        // ── Step 9: Finalize save assertions (deferred header check) ──────────────
        StepExecutor.step("Finalize save assertions", softAssert::assertAll);
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
        // NOTE: NEG_BLANK_SUBLEDGER legitimately has an empty SL list; the single-shot
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
            homeRef[0] = new LoginPage().loginToApplication(u.username(), u.password());
            ReportLogger.info("Logged in as: " + u.username());
        });

        // ── Step 2: Navigate ──────────────────────────────────────────────────────
        StepExecutor.step("Open Finance → AP Voucher List", () -> {
            listRef[0] = NavigationUtils.navigateToFinanceSideMenu(
                    homeRef[0],
                    MainMenuType.OPERATIONS,
                    OperationsMenuType.APVOUCHER,
                    ApVoucherListPage.class);
            ReportLogger.info("AP Voucher list page opened");
        });

        // ── Step 3: Open Add form ─────────────────────────────────────────────────
        StepExecutor.step("Open Add Form", () -> {
            addRef[0] = listRef[0].clickAdd();
            ReportLogger.info("Scenario: " + testData.getScenarioType()
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
