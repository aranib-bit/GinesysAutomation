package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.TestCaseId;
import com.gsl.enums.TestAuthor;
import com.gsl.model.finance.FinPost;
import com.gsl.pages.Finance.Operations.ArVoucherListPage;
import com.gsl.pages.HomePage;
import com.gsl.pages.LoginPage;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Operations.OperationsMenuType;
import com.gsl.pages.Finance.Operations.ArVoucherAdd;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SelectDocument;
import com.gsl.reports.ArVoucherExtentDescriptionLogger;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.utils.Log;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.MergedFinArVoucherDataSupplier;
import com.gsl.utils.dataprovider.source.finance.DbArVoucherDataSource;
import com.gsl.validation.db.finance.ARVoucherDbValidator;
import com.gsl.validation.db.finance.StateCapture;
import com.gsl.validation.db.finance.VoucherDbContext;
import com.tests.BaseTest;
import org.testng.annotations.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class CreateArVoucher extends BaseTest {

    @FrameworkAnnotation(TestAuthor.ARANI)
    @TestCaseId("FIN-AR-ADD")
    @Test(description = "To create new AR Voucher of Finance from UI",
            dataProvider = "dataSupplier", dataProviderClass = MergedFinArVoucherDataSupplier.class)
    public void createArVoucher(MergedFinanceArVoucherData data) {

        Log.info("Create AR Voucher of Finance from UI");
        Log.info("Running test for: {}", data.getMasterTestData().getTestcaseid());

        // ── Lazy DB load — runs before steps so full data is ready ──────────────
        if (data.getScenarioType() != null
                && (data.getSubLedgerInformationList() == null
                    || data.getSubLedgerInformationList().isEmpty())) {
            Log.debug("[DB mode] Placeholder detected for scenario={}, fetching fresh data from DB",
                    data.getScenarioType());
            data = DbArVoucherDataSource.loadFreshForScenario(data, "createArVoucher");
        }

        if (data.getScenarioType() != null) {
            Log.debug("[DB mode] Scenario type: {} | testCaseId: {}",
                    data.getScenarioType(), data.getMasterTestData().getTestcaseid());
        }

        // ── Log rich test data to Extent Report (must be before first step) ─────
        ArVoucherExtentDescriptionLogger.log(data);

        // ── Effectively-final references for lambda use ──────────────────────────
        final MergedFinanceArVoucherData testData = data;

        // Cross-step object holders
        final HomePage[]          homeRef       = new HomePage[1];
        final ArVoucherListPage[] listRef       = new ArVoucherListPage[1];
        final ArVoucherAdd[]      addRef        = new ArVoucherAdd[1];
        final String[]            docNoRef      = new String[1];
        final com.gsl.pages.Common.DocumentMessageBox[] msgBoxRef = new com.gsl.pages.Common.DocumentMessageBox[1];
        final Map<String, FinPost>[] preRef      = new Map[1];
        final Map<String, FinPost>[] postRef     = new Map[1];
        final LocalDateTime[]        saveStartTime = new LocalDateTime[1];

        // ── Step 1: Login ─────────────────────────────────────────────────────────
        StepExecutor.step("Login to Application", () -> {
            homeRef[0] = new LoginPage()
                    .loginToApplication(
                            testData.getMasterTestData().getUsername(),
                            testData.getMasterTestData().getPassword());
            ReportLogger.info("Logged in as: " + testData.getMasterTestData().getUsername());
        });

        // ── Step 2: Navigate ──────────────────────────────────────────────────────
        StepExecutor.step("Open Finance → AR Voucher List", () -> {
            listRef[0] = NavigationUtils.navigateToFinanceSideMenu(
                    homeRef[0],
                    MainMenuType.OPERATIONS,
                    OperationsMenuType.ARVOUCHER,
                    ArVoucherListPage.class);
            ReportLogger.info("AR Voucher list page opened");
        });

        // ── Step 3: Document header ───────────────────────────────────────────────
        StepExecutor.step("Fill Document Information", () -> {
            addRef[0] = listRef[0].clickAdd()
                    .fillDocumentHeader(testData.getDocumentInformation());
            ReportLogger.info("Cash Bank selected: " + testData.getDocumentInformation().getCashbank());
        });

        // ── Step 4: Sub-ledger rows + adjustments + payment ───────────────────────
        StepExecutor.step("Fill Sub-Ledger Information", () -> {
            addRef[0].fillSubLedgerRows(testData, true);
            ReportLogger.info("Sub-ledger section complete: "
                    + testData.getSubLedgerInformationList().size() + " rows");
        });

        // ── Step 5: Save AR Voucher ────────────────────────────────────────────────
        StepExecutor.step("Save AR Voucher", () -> {
            List<String> invoiceNos = testData.getSelectDocumentList() != null
                    ? testData.getSelectDocumentList().stream()
                            .map(SelectDocument::getDocumentNumber)
                            .collect(Collectors.toList())
                    : Collections.emptyList();

            StateCapture stateCapture = new StateCapture();
            preRef[0] = stateCapture.captureInvoiceState(invoiceNos);

            saveStartTime[0] = LocalDateTime.now();
            DocumentPopup savePopup = addRef[0].clickSave();
            com.gsl.pages.Common.DocumentMessageBox messageBox = testData.isNeedsIgnoreProceed()
                    ? savePopup.verifyWarningMessage().clickIgnoreProceed()
                    : savePopup.waitForMessageBox();

            docNoRef[0] = messageBox.extractDocumentNo();
            Log.info("Extracted Document No: {}", docNoRef[0]);

            postRef[0] = stateCapture.captureInvoiceState(invoiceNos);

            ReportLogger.pass("Voucher saved. Document No: " + docNoRef[0]);
            msgBoxRef[0] = messageBox;
        });

        // ── Step 6: Close and logout ──────────────────────────────────────────────
        StepExecutor.step("Close and Logout", () -> {
            msgBoxRef[0].clickOk();
            addRef[0].close().clickYes();
            new HomePage().logoutFromApplication();
        });

        // ── Step 7: Database Validation ───────────────────────────────────────────
        StepExecutor.step("Database Validation", () -> {
            new ARVoucherDbValidator().validate(
                    VoucherDbContext.builder()
                            .voucherNo(docNoRef[0])
                            .testData(testData)
                            .preState(preRef[0])
                            .postState(postRef[0])
                            .saveStartTime(saveStartTime[0])
                            .build());
            ReportLogger.pass("DB validated. Document No: " + docNoRef[0]);
        });
    }
}
