package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.ApiFlow;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.TestCaseId;
import com.gsl.network.pattern.FinanceApiPattern;
import com.gsl.enums.TestAuthor;
import com.gsl.model.finance.FinPost;
import com.gsl.pages.Finance.Operations.ArVoucherListPage;
import com.gsl.pages.HomePage;
import com.gsl.pages.LoginPage;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Operations.OperationsMenuType;
import com.gsl.pages.Finance.Operations.ArVoucherAdd;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.driver.DriverManager;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.flow.Finance.Operations.ARVoucher.TdsAutoReleaseFlow;
import com.gsl.pages.Finance.Operations.Common.PaymentInfoCheckWarning;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SelectDocument;
import com.gsl.testdata.UserCredential;
import com.gsl.reports.ArVoucherExtentDescriptionLogger;
import com.gsl.reports.DbGatheringExtentLogger;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.utils.Log;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.MergedFinArVoucherDataSupplier;
import com.gsl.utils.dataprovider.source.finance.DbArVoucherDataSource;
import com.gsl.validation.db.finance.ARVoucherDbValidator;
import com.gsl.validation.db.finance.StateCapture;
import com.gsl.validation.db.finance.VoucherDbContext;
import com.tests.WebTest;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@ApiFlow(FinanceApiPattern.AR_VOUCHER)
public class CreateArVoucher extends WebTest {

    @FrameworkAnnotation(TestAuthor.ARANI)
    @TestCaseId("FIN-AR-ADD")
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
            try {
                data = DbArVoucherDataSource.loadFreshForScenario(data, "createArVoucher");
            } catch (IllegalStateException e) {
                // No pending documents or SL data available for this variant — skip cleanly
                // rather than failing with a misleading error. The DB needs more test data.
                throw new org.testng.SkipException(
                        "[CreateArVoucher] Skipping — insufficient test data for scenario '"
                        + data.getScenarioType() + "' / variant '"
                        + data.getMasterTestData().getTestcaseid() + "': " + e.getMessage());
            }
        }

        if (data.getScenarioType() != null) {
            Log.debug("[DB mode] Scenario type: {} | testCaseId: {}",
                    data.getScenarioType(), data.getMasterTestData().getTestcaseid());
        }

        // ── P3: Log DB data-gathering section (DB mode only; no-op in Excel mode) ─
        DbGatheringExtentLogger.log(data);

        // ── Log rich test data to Extent Report (must be before first step) ─────
        ArVoucherExtentDescriptionLogger.log(data);

        // ── Effectively-final references for lambda use ──────────────────────────
        final MergedFinanceArVoucherData testData = data;

        // Cross-step object holders
        final SoftAssert softAssert = new SoftAssert();
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
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = new LoginPage().loginToApplication(u.username(), u.password());
            ReportLogger.info("Logged in as: " + u.username());
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
            com.gsl.pages.Common.DocumentMessageBox messageBox = testData.isNeedsPaymentInfoWarning()
                    ? savePopup.verifyPaymentInfoCheckWarning(PaymentInfoCheckWarning.AR_VOUCHER)
                               .clickProceed(PaymentInfoCheckWarning.AR_VOUCHER)
                    : savePopup.waitForMessageBox();

            if (testData.isTdsProvided()) {
                messageBox = TdsAutoReleaseFlow.execute(messageBox);
            }

            docNoRef[0] = messageBox.verifyDocumentSave(softAssert).getDocumentNo();
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

        StepExecutor.step("Finalize save assertions", softAssert::assertAll);
    }
}
