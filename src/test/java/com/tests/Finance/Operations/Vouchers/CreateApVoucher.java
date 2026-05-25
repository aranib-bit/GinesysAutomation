package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.ApiFlow;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.TestCaseId;
import com.gsl.driver.DriverManager;
import com.gsl.enums.TestAuthor;
import com.gsl.model.finance.FinPost;
import com.gsl.network.pattern.FinanceApiPattern;
import com.gsl.pages.Finance.Operations.ApVoucherAdd;
import com.gsl.pages.Finance.Operations.ApVoucherListPage;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.pages.HomePage;
import com.gsl.pages.LoginPage;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Operations.OperationsMenuType;
import com.gsl.reports.ApVoucherExtentDescriptionLogger;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.SelectDocument;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.MergedFinApVoucherDataSupplier;
import com.gsl.validation.db.finance.APVoucherDbValidator;
import com.gsl.validation.db.finance.ApVoucherDbContext;
import com.gsl.validation.db.finance.StateCapture;
import com.tests.WebTest;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApiFlow(FinanceApiPattern.AP_VOUCHER)
public class CreateApVoucher extends WebTest {

    /**
     * Advance payment scenario (On Account = Yes).
     * SelectDocument and FIFO are never present.
     * TDS & Net Payable section is filled when tdsProvided = true.
     */
    @FrameworkAnnotation(TestAuthor.ARANI)
    @TestCaseId("FIN-AP-ADD-ADV")
    @Test(description = "Create AP Voucher – Advance Payment",
            dataProvider = "FinanceApVoucherData",
            dataProviderClass = MergedFinApVoucherDataSupplier.class)
    public void createApVoucherAdvancePayment(UserCredential user, MergedFinanceApVoucherData data) {

        Log.info("Create AP Voucher (Advance Payment) from UI");
        Log.info("Running test for: {}", data.getMasterTestData().getTestcaseid());

        ApVoucherExtentDescriptionLogger.log(data);

        final MergedFinanceApVoucherData testData = data;

        final HomePage[]            homeRef       = new HomePage[1];
        final ApVoucherListPage[]   listRef       = new ApVoucherListPage[1];
        final ApVoucherAdd[]        addRef        = new ApVoucherAdd[1];
        final String[]              docNoRef      = new String[1];
        final com.gsl.pages.Common.DocumentMessageBox[] msgBoxRef = new com.gsl.pages.Common.DocumentMessageBox[1];
        final LocalDateTime[]       saveStartTime = new LocalDateTime[1];
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

        // ── Step 6: Save ──────────────────────────────────────────────────────────
        StepExecutor.step("Save AP Voucher", () -> {
            saveStartTime[0] = LocalDateTime.now();
            DocumentPopup savePopup = addRef[0].clickSave();
            com.gsl.pages.Common.DocumentMessageBox messageBox = testData.isNeedsIgnoreProceed()
                    ? savePopup.verifyWarningMessage().clickIgnoreProceed()
                    : savePopup.waitForMessageBox();

            com.gsl.pages.Common.DocumentMessageBox.DocumentSaveResult saveResult =
                    messageBox.verifyDocumentSave(softAssert);
            docNoRef[0] = saveResult.getDocumentNo();
            ReportLogger.pass("Voucher saved. Document No: " + docNoRef[0]);
            msgBoxRef[0] = messageBox;
        });

        // ── Step 7: Close and logout ──────────────────────────────────────────────
        StepExecutor.step("Close and Logout", () -> {
            msgBoxRef[0].clickOk();
            addRef[0].close().clickYes();
            new HomePage().logoutFromApplication();
        });

        // ── Step 8: Database Validation ───────────────────────────────────────────
        StepExecutor.step("Database Validation", () -> {
            new APVoucherDbValidator().validate(
                    ApVoucherDbContext.builder()
                            .voucherNo(docNoRef[0])
                            .testData(testData)
                            .saveStartTime(saveStartTime[0])
                            .build());
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
    @FrameworkAnnotation(TestAuthor.ARANI)
    @TestCaseId("FIN-AP-ADD-DOC")
    @Test(description = "Create AP Voucher – Document Payment",
            dataProvider = "FinanceApVoucherData",
            dataProviderClass = MergedFinApVoucherDataSupplier.class)
    public void createApVoucherDocumentPayment(UserCredential user, MergedFinanceApVoucherData data) {

        Log.info("Create AP Voucher (Document Payment) from UI");
        Log.info("Running test for: {}", data.getMasterTestData().getTestcaseid());

        ApVoucherExtentDescriptionLogger.log(data);

        final MergedFinanceApVoucherData testData = data;

        final HomePage[]            homeRef       = new HomePage[1];
        final ApVoucherListPage[]   listRef       = new ApVoucherListPage[1];
        final ApVoucherAdd[]        addRef        = new ApVoucherAdd[1];
        final String[]              docNoRef      = new String[1];
        final com.gsl.pages.Common.DocumentMessageBox[] msgBoxRef = new com.gsl.pages.Common.DocumentMessageBox[1];
        final Map<String, FinPost>[] preRef        = new Map[1];
        final Map<String, FinPost>[] postRef       = new Map[1];
        final LocalDateTime[]        saveStartTime = new LocalDateTime[1];
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

        // ── Step 5: Save ──────────────────────────────────────────────────────────
        StepExecutor.step("Save AP Voucher", () -> {
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

            com.gsl.pages.Common.DocumentMessageBox.DocumentSaveResult saveResult =
                    messageBox.verifyDocumentSave(softAssert);
            docNoRef[0] = saveResult.getDocumentNo();

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
            new APVoucherDbValidator().validate(
                    ApVoucherDbContext.builder()
                            .voucherNo(docNoRef[0])
                            .testData(testData)
                            .preState(preRef[0])
                            .postState(postRef[0])
                            .saveStartTime(saveStartTime[0])
                            .build());
            ReportLogger.pass("DB validated. Document No: " + docNoRef[0]);
        });

        // ── Step 8: Finalize save assertions (deferred header check) ──────────────
        StepExecutor.step("Finalize save assertions", softAssert::assertAll);
    }
}
