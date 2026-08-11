package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.ApiFlow;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.TestCaseId;
import com.gsl.driver.DriverManager;
import com.gsl.enums.AppOperation;
import com.gsl.enums.TestAuthor;
import com.gsl.enums.VoucherDomain;
import com.gsl.flow.Finance.Operations.Common.PostSaveDialogHandler;
import com.gsl.network.pattern.FinanceApiPattern;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
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
import com.gsl.validation.db.finance.GeneralVoucherDbValidator;
import com.tests.WebTest;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

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
     * Shared UI flow for both {@link #createGeneralVoucher} and {@link #createGeneralVoucherMixedTypes}
     * — login, resolve test data, fill header, fill Expense/Income Detail rows (per-row Type1/2/3
     * branching lives inside {@code GeneralVoucherExpenseIncomeDetail}, not here), save, verify,
     * DB-validate.
     *
     * @param placeholderOrData the row from the DataProvider — either already-resolved Excel-mode
     *                          data, or a DB-mode placeholder (empty {@code expenseIncomeDetailList})
     * @param lazyLoader        resolves a DB-mode placeholder into full data — either
     *                          {@code DbGeneralVoucherDataSource.loadFreshForScenario} or
     *                          {@code .loadFreshForMixedTypes}, bound by the caller
     */
    private void runVoucherCreationFlow(MergedFinanceGeneralVoucherData placeholderOrData,
            java.util.function.UnaryOperator<MergedFinanceGeneralVoucherData> lazyLoader) {

        final HomePage[]                 homeRef  = new HomePage[1];
        final GeneralVoucherListPage[]    listRef  = new GeneralVoucherListPage[1];
        final GeneralVoucherAdd[]         addRef   = new GeneralVoucherAdd[1];
        final String[]                    docNoRef = new String[1];
        final DocumentMessageBox[]         msgBoxRef = new DocumentMessageBox[1];
        final DocumentPopup[]              popupRef = new DocumentPopup[1];
        final java.time.LocalDateTime[]    saveStartTimeRef = new java.time.LocalDateTime[1];
        final SoftAssert softAssert = new SoftAssert();

        // ── Step 1: Login ─────────────────────────────────────────────────────────
        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = com.gsl.driver.SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
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
        });

        // ── Step 3: Document header ───────────────────────────────────────────────
        StepExecutor.step("Fill Document Information", () -> {
            addRef[0] = listRef[0].clickAdd()
                    .fillDocumentHeader(testData.getDocumentInformation());
            var docInfo = addRef[0].getDocumentInformation();
            ReportLogger.business("Doc Scheme  (UI): " + docInfo.getDocScheme());
            ReportLogger.business("Doc Date    (UI): " + docInfo.getDocumentDate());
            ReportLogger.business("Reference   (UI): " + docInfo.getReference());
            ReportLogger.business("Cash / Bank (UI): " + docInfo.getCashBank());
            ReportLogger.business("Remarks     (UI): " + docInfo.getRemarks());
            ActualResultsHolder.get().setUiOwnerSite(docInfo.getOwnerSite());
            ActualResultsHolder.get().setUiCashBank(docInfo.getCashBank());
        });

        // ── Step 4: Fill Expense/Income Detail rows (per-row Type1/2/3 branching
        //            lives inside GeneralVoucherExpenseIncomeDetail, not here) ─────
        StepExecutor.step("Fill Expense/Income Detail", () -> {
            addRef[0].fillExpenseIncomeRows(testData);
            ReportLogger.business("Expense/Income Detail section complete: "
                    + testData.getExpenseIncomeDetailList().size() + " rows");
        });

        // ── Step 4b: Cash/Bank counter-leg Payment Window (CHEQUE/NEFT_RTGS only —
        //             no-op for null/CASH, see GeneralVoucherExpenseIncomeDetail#handlePaymentInformation) ──
        StepExecutor.step("Fill Cash/Bank Payment Information", () -> {
            addRef[0].handlePaymentInformation(testData.getCashBankPaymentInfo());
        });

        // ── Step 5: Click Save ────────────────────────────────────────────────────
        StepExecutor.step("Click Save", () -> {
            saveStartTimeRef[0] = java.time.LocalDateTime.now();
            popupRef[0] = addRef[0].clickSave();
        });

        // ── Step 6: Handle Post-Save Dialogs ──────────────────────────────────────
        StepExecutor.step("Handle Post-Save Dialogs", () ->
                msgBoxRef[0] = PostSaveDialogHandler.forDomain(VoucherDomain.GV)
                        .handle(popupRef[0], testData));

        // ── Step 7: Verify Save Result ─────────────────────────────────────────────
        StepExecutor.step("Verify Save Result", () -> {
            docNoRef[0] = msgBoxRef[0].verifyDocumentSave(softAssert).getDocumentNo();
            ReportLogger.pass("Voucher saved. Document No: " + docNoRef[0]);
        });

        // ── Step 8: Close Voucher Page ────────────────────────────────────────────
        StepExecutor.step("Close Voucher Page", () -> {
            if (msgBoxRef[0] != null) {
                msgBoxRef[0].clickOk();
            }
            addRef[0].close().clickYes();   // returns to General Voucher list
            ReportLogger.business("Voucher page closed, returned to list");
        });

        // ── Step 9: Logout (deferred — session may be reused by a later test on this thread;
        // real logout+driver-quit happens via RecoveryManager or end-of-block cleanup only) ────
        StepExecutor.step("Logout (deferred)", () ->
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread."));

        // ── Step 10: Database Validation (fincosttag site-allocation checks — see plan §2.1/§2.2) ──
        StepExecutor.step("Database Validation", () -> {
            new GeneralVoucherDbValidator().validate(testData, docNoRef[0], saveStartTimeRef[0]);
            ReportLogger.pass("DB validated. Document No: " + docNoRef[0]);
        });

        // ── Step 11: Finalize save assertions (deferred header check) ─────────────
        StepExecutor.step("Finalize save assertions", softAssert::assertAll);
    }
}
