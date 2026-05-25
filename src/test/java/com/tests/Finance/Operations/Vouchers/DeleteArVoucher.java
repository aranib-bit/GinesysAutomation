package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.ApiFlow;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.TestCaseId;
import com.gsl.network.pattern.FinanceApiPattern;
import com.gsl.enums.TestAuthor;
import com.gsl.pages.Finance.Operations.ArVoucherListPage;
import com.gsl.pages.HomePage;
import com.gsl.pages.LoginPage;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Operations.OperationsMenuType;
import com.gsl.driver.DriverManager;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.MergedFinArVoucherDataSupplier;
import com.tests.WebTest;
import org.testng.annotations.Test;

/**
 * Deletes an existing AR Voucher from the Finance → Operations → AR Voucher list page.
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
 * must remove the tag before deleting — that step is marked TODO until the
 * Document Adjustment page object is available.
 */
@ApiFlow(FinanceApiPattern.AR_VOUCHER)
public class DeleteArVoucher extends WebTest {

    @FrameworkAnnotation(TestAuthor.ARANI)
    @TestCaseId("FIN-AR-DELETE")
    @Test(description = "Delete an existing AR Voucher from the Finance UI",
            dataProvider = "dataSupplier", dataProviderClass = MergedFinArVoucherDataSupplier.class)
    public void deleteArVoucher(MergedFinanceArVoucherData data) {

        Log.info("Delete AR Voucher: {}", data.getExistingVoucherNo());
        Log.info("Release status: {}", data.getReleaseStatus());

        final MergedFinanceArVoucherData testData = data;

        // Cross-step object holders
        final HomePage[]          homeRef = new HomePage[1];
        final ArVoucherListPage[] listRef = new ArVoucherListPage[1];

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

        // ── Step 4: Unpost + untag if posted ──────────────────────────────────────
        StepExecutor.step("Unpost and Untag if Posted (release_status='P')", () -> {
            if ("P".equals(testData.getReleaseStatus())) {
                listRef[0].clickUnpost();
                // TODO: query fintag for this voucher (SELECT COUNT(*) FROM fintag WHERE vchcode = ?)
                //   If count > 0 → navigate to Document Adjustment and remove the tagging.
                //   Until the Document Adjustment page object is available, this step will
                //   fail at runtime if the voucher is tagged. Add the fintag query and
                //   DocumentAdjustmentPage.removeTagForVoucher(voucherNo) call here.
                ReportLogger.info("Voucher unposted (was release_status='P'). Fintag check: TODO.");
            } else {
                ReportLogger.info("Voucher status='" + testData.getReleaseStatus()
                        + "' — unpost not required");
            }
        });

        // ── Step 5: Delete and Confirm ────────────────────────────────────────────
        StepExecutor.step("Click Delete and Confirm", () -> {
            listRef[0]
                    .clickDeleteForVoucher()
                    .confirmDelete();
            ReportLogger.pass("Deletion confirmed for voucher: " + testData.getExistingVoucherNo());
        });

        // ── Step 6: Logout ────────────────────────────────────────────────────────
        StepExecutor.step("Logout", () -> {
            new HomePage().logoutFromApplication();
        });

        // ── Step 7: Database Validation ───────────────────────────────────────────
        StepExecutor.step("Database Validation", () -> {
            // TODO: replace with ARVoucherDbValidator.validateDeleted(existingVoucherNo)
            //   once the validator supports delete assertions (check finvchmain row is
            //   absent or has status='D').
            Log.info("[DB-Validate] Delete validation TODO for voucher: {}",
                    testData.getExistingVoucherNo());
            ReportLogger.pass("DB validation TODO — voucher targeted for deletion: "
                    + testData.getExistingVoucherNo());
        });
    }
}
