package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.ApiFlow;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.TestCaseId;
import com.gsl.network.pattern.FinanceApiPattern;
import com.gsl.enums.TestAuthor;
import com.gsl.pages.Finance.Operations.ArVoucherAdd;
import com.gsl.pages.Finance.Operations.ArVoucherListPage;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.pages.HomePage;
import com.gsl.pages.LoginPage;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Operations.OperationsMenuType;
import com.gsl.driver.DriverManager;
import com.gsl.reports.ArVoucherExtentDescriptionLogger;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.MergedFinArVoucherDataSupplier;
import com.gsl.validation.db.finance.ARVoucherDbValidator;
import com.gsl.validation.db.finance.VoucherDbContext;
import com.tests.WebTest;
import org.testng.annotations.Test;

/**
 * Edits an existing AR Voucher from the Finance → Operations → AR Voucher list page.
 *
 * <h3>Pre-conditions</h3>
 * <ul>
 *   <li>{@code testdata.source=DB} in {@code config.properties}</li>
 *   <li>At least one row in {@code finvchmain} with
 *       {@code vchtype='R' AND release_status IN ('N','U','P')}</li>
 * </ul>
 *
 * <h3>P-status handling</h3>
 * When {@code releaseStatus='P'}, the test unposted the voucher first.
 * If the voucher has active tagging in {@code fintag}, Document Adjustment
 * must remove the tag before editing — that step is marked TODO until the
 * Document Adjustment page object is available.
 */
@ApiFlow(FinanceApiPattern.AR_VOUCHER)
public class EditArVoucher extends WebTest {

    @FrameworkAnnotation(TestAuthor.ARANI)
    @TestCaseId("FIN-AR-EDIT")
    @Test(description = "Edit an existing AR Voucher from the Finance UI",
            dataProvider = "dataSupplier", dataProviderClass = MergedFinArVoucherDataSupplier.class)
    public void editArVoucher(MergedFinanceArVoucherData data) {

        Log.info("Edit AR Voucher: {}", data.getExistingVoucherNo());
        Log.info("Release status: {}", data.getReleaseStatus());

        ArVoucherExtentDescriptionLogger.log(data);

        final MergedFinanceArVoucherData testData = data;

        // Cross-step object holders
        final HomePage[]          homeRef  = new HomePage[1];
        final ArVoucherListPage[] listRef  = new ArVoucherListPage[1];
        final ArVoucherAdd[]      editRef  = new ArVoucherAdd[1];
        final String[]            docNoRef = new String[1];
        final com.gsl.pages.Common.DocumentMessageBox[] msgBoxRef =
                new com.gsl.pages.Common.DocumentMessageBox[1];

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

        // ── Step 3: Search ────────────────────────────────────────────────────────
        StepExecutor.step("Search AR Voucher by Document Number", () -> {
            listRef[0].searchByVoucherNo(testData.getExistingVoucherNo());
            ReportLogger.info("Searched for voucher: " + testData.getExistingVoucherNo());
        });

        // ── Step 4: Unpost if posted ──────────────────────────────────────────────
        StepExecutor.step("Unpost if Posted (release_status='P')", () -> {
            if ("P".equals(testData.getReleaseStatus())) {
                listRef[0].clickUnpost();
                // TODO: query fintag for this voucher — if rows exist,
                //   navigate to Document Adjustment and remove the tagging before editing.
                //   Until Document Adjustment page object is available, this step will
                //   fail at runtime if the voucher is tagged.
                ReportLogger.info("Voucher unposted (was release_status='P')");
            } else {
                ReportLogger.info("Voucher status='" + testData.getReleaseStatus()
                        + "' — unpost not required");
            }
        });

        // ── Step 5: Open for Edit ─────────────────────────────────────────────────
        StepExecutor.step("Open Voucher for Edit", () -> {
            editRef[0] = listRef[0].clickEditForVoucher();
            ReportLogger.info("Voucher opened for edit: " + testData.getExistingVoucherNo());
        });

        // ── Step 6: Apply changes to document header ──────────────────────────────
        StepExecutor.step("Apply Changes to Document Header", () -> {
            editRef[0].fillDocumentHeader(testData.getDocumentInformation());
            ReportLogger.info("Updated date/reference/remarks via fillDocumentHeader");
        });

        // ── Step 7: Save ──────────────────────────────────────────────────────────
        StepExecutor.step("Save Edited AR Voucher", () -> {
            DocumentPopup savePopup = editRef[0].clickSave();
            com.gsl.pages.Common.DocumentMessageBox messageBox = testData.isNeedsIgnoreProceed()
                    ? savePopup.verifyWarningMessage().clickIgnoreProceed()
                    : savePopup.waitForMessageBox();

            docNoRef[0] = messageBox.extractDocumentNo();
            Log.info("Saved. Document No: {}", docNoRef[0]);
            ReportLogger.pass("Voucher saved. Document No: " + docNoRef[0]);
            msgBoxRef[0] = messageBox;
        });

        // ── Step 8: Close and Logout ──────────────────────────────────────────────
        StepExecutor.step("Close and Logout", () -> {
            msgBoxRef[0].clickOk();
            editRef[0].close().clickYes();
            new HomePage().logoutFromApplication();
        });

        // ── Step 9: Database Validation ───────────────────────────────────────────
        StepExecutor.step("Database Validation", () -> {
            new ARVoucherDbValidator().validate(
                    VoucherDbContext.builder()
                            .voucherNo(docNoRef[0])
                            .testData(testData)
                            .build());
            ReportLogger.pass("DB validated. Document No: " + docNoRef[0]);
        });
    }
}
