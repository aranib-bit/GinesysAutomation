package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.ApiFlow;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.TestCaseId;
import com.gsl.network.pattern.FinanceApiPattern;
import com.gsl.enums.AppOperation;
import com.gsl.enums.TestAuthor;
import com.gsl.enums.VoucherDomain;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinVchDetDao;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.flow.Finance.Operations.Common.DocumentAdjustmentFlow;
import com.gsl.model.finance.FinVchDet;
import com.gsl.model.finance.FinVchMain;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.pages.Finance.Operations.ArVoucherAdd;
import com.gsl.pages.Finance.Operations.ArVoucherListPage;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.flow.Finance.Operations.Common.PostSaveDialogHandler;
import com.gsl.flow.Finance.Operations.Common.VoucherEditFlowSteps;
import com.gsl.flow.Finance.Operations.Common.VoucherSaveCtx;
import com.gsl.flow.Finance.Operations.Common.VoucherSaveFlowSteps;
import com.gsl.pages.HomePage;
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
import com.gsl.utils.dataprovider.fixture.finance.DocumentSearchStrategy;
import com.gsl.utils.dataprovider.source.finance.common.AccountingPeriodResolver;
import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.ExpectedChangeSet;
import com.gsl.validation.snapshot.SnapshotService;
import com.gsl.validation.snapshot.VoucherSnapshot;
import com.gsl.validation.snapshot.VoucherSnapshotValidator;
import com.gsl.validation.snapshot.finance.ARVoucherEditChangeSets;
import com.gsl.validation.snapshot.finance.Aud2SnapshotDao;
import com.gsl.validation.snapshot.finance.VoucherDocumentContextResolver;
import com.tests.WebTest;
import org.jdbi.v3.core.Jdbi;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.math.BigDecimal;
import java.util.List;

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

    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceARVoucher", operation = AppOperation.MODIFY)
    @TestCaseId("FIN-AR-EDIT")
    @Test(description = "Edit an existing AR Voucher from the Finance UI",
            dataProvider = "dataSupplier", dataProviderClass = MergedFinArVoucherDataSupplier.class)
    public void editArVoucher(MergedFinanceArVoucherData data) {

        Log.info("Edit AR Voucher: {}", data.getExistingVoucherNo());
        Log.info("Release status: {}", data.getReleaseStatus());

        ArVoucherExtentDescriptionLogger.log(data);

        final MergedFinanceArVoucherData testData = data;

        // Cross-step object holders
        final HomePage[]          homeRef    = new HomePage[1];
        final ArVoucherListPage[] listRef    = new ArVoucherListPage[1];
        final ArVoucherAdd[]      editRef    = new ArVoucherAdd[1];
        final String[]            docNoRef   = new String[1];
        final com.gsl.pages.Common.DocumentMessageBox[] msgBoxRef =
                new com.gsl.pages.Common.DocumentMessageBox[1];
        final DocumentPopup[]     popupRef   = new DocumentPopup[1];
        final DocumentContext[]   contextRef = new DocumentContext[1];
        final VoucherSnapshot[]   beforeRef  = new VoucherSnapshot[1];
        final VoucherSnapshot[]   afterRef   = new VoucherSnapshot[1];

        SnapshotService snapshotService = new SnapshotService(new Aud2SnapshotDao());
        final VoucherSaveCtx ctx = new VoucherSaveCtx();

        // ── Step 1: Login ─────────────────────────────────────────────────────────
        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = com.gsl.driver.SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        // ── Step 2: Navigate ──────────────────────────────────────────────────────
        // Business log + "Navigated: ..." summary are now recorded inside
        // NavigationUtils.navigateToFinanceSideMenu itself.
        StepExecutor.step("Open Finance → AR Voucher List", () -> listRef[0] =
                NavigationUtils.navigateToFinanceSideMenu(
                        homeRef[0],
                        MainMenuType.OPERATIONS,
                        OperationsMenuType.ARVOUCHER,
                        ArVoucherListPage.class));

        // ── Step 3: Clear Filter + Select Document — was searchByVoucherNo(...), which is
        // exact-match-only with no clear-filter step and no pagination fallback (same exposure
        // Round 9/10 confirmed live and fixed for DeleteApVoucher — see that class's own
        // equivalent step for the evidence). Now uses the shared common function
        // (VoucherListPageBase.selectDocumentForAction) every other Revert/Delete/Edit test class
        // uses. Its own follow-on action-click step (clickEditForVoucher()/clickUnpost(), both
        // still no-arg/no-retry) is unchanged — a separate, already-flagged, already-deferred gap.
        StepExecutor.step("Select AR Voucher in List Page", () -> {
            listRef[0].selectDocumentForAction(testData.getExistingVoucherNo(), DocumentSearchStrategy.EXACT_MATCH);
            ReportLogger.business("Filter cleared, list refreshed, selected voucher: "
                    + testData.getExistingVoucherNo());
        });

        // ── Step 4: Untag, then unpost if posted ─────────────────────────────────
        // Untag BEFORE unpost — a Posted+tagged document cannot be un-posted until its fintag rows
        // are cleared (the previous order here unposted first). Unpost is the Actions → Revert UI
        // action (AR has no standalone Unpost button; the old //TODO-unpost-button placeholder
        // never existed), using the dialogs AR shares with AP (ArApVoucherDialogs).
        VoucherEditFlowSteps.untagIfTagged(
                testData.getExistingVoucherNo(), testData.getReleaseStatus(), new java.util.ArrayList<>());
        StepExecutor.step("Unpost if Posted (release_status='P') — Actions → Revert", () -> {
            if ("P".equals(testData.getReleaseStatus())) {
                listRef[0].selectDocumentForAction(testData.getExistingVoucherNo(), DocumentSearchStrategy.EXACT_MATCH);
                com.gsl.pages.enums.Finance.Operations.ARVoucher.ARVoucherRevertOutcome outcome =
                        listRef[0].revertSelectedDocument();
                org.testng.Assert.assertEquals(outcome,
                        com.gsl.pages.enums.Finance.Operations.ARVoucher.ARVoucherRevertOutcome.UNPOSTED,
                        "[Unpost] Expected the Posted AR voucher to be un-posted by Revert");
                ReportLogger.business("Voucher unposted via Revert (was release_status='P') for: "
                        + testData.getExistingVoucherNo());
            } else {
                ReportLogger.business("Voucher status=" + testData.getReleaseStatus() + " — unpost not required");
            }
        });

        // ── Step 4b: Resolve Document Context ─────────────────────────────────────
        StepExecutor.step("Resolve Document Context", () -> {
            contextRef[0] = new VoucherDocumentContextResolver().resolve(testData.getExistingVoucherNo(), VoucherDomain.AR);
        });

        // ── Step 4c: Capture BEFORE Snapshot ───────────────────────────────────────
        StepExecutor.step("Capture BEFORE Snapshot", () -> beforeRef[0] = snapshotService.capture(contextRef[0]));

        // ── Step 5: Open for Edit ─────────────────────────────────────────────────
        StepExecutor.step("Open Voucher for Edit", () -> {
            editRef[0] = listRef[0].clickEditFromActions(testData.getExistingVoucherNo(), DocumentSearchStrategy.EXACT_MATCH);
            ReportLogger.business("Voucher opened for edit: " + testData.getExistingVoucherNo());
        });

        // ── Step 6: Apply changes to document header ──────────────────────────────
        StepExecutor.step("Apply Changes to Document Header", () -> {
            editRef[0].fillDocumentHeader(testData.getDocumentInformation());
            ReportLogger.business("Updated date/reference/remarks via fillDocumentHeader");
        });

        // ── Steps 7b-9: Save → dialogs → verify → close → logout (shared tail) ──────
        VoucherSaveFlowSteps.clickSave(ctx, null, () -> editRef[0].clickSave());
        VoucherSaveFlowSteps.handlePostSaveDialogs(ctx, VoucherDomain.AR, testData);
        VoucherSaveFlowSteps.verifySaveResult(ctx);
        StepExecutor.step("Assert save header", ctx.softAssert::assertAll);
        VoucherSaveFlowSteps.closeVoucherPage(ctx,
                () -> editRef[0].close().clickYes(ArVoucherListPage::new), ArVoucherListPage::new);
        VoucherSaveFlowSteps.deferLogout();

        // ── Step 10: Capture AFTER Snapshot ───────────────────────────────────────
        StepExecutor.step("Capture AFTER Snapshot", () -> afterRef[0] = snapshotService.capture(contextRef[0]));

        // ── Step 11: Database Snapshot Validation ─────────────────────────────────
        // Migrated onto the generic before/after DB snapshot → diff → column-rule engine
        // (com.gsl.validation.snapshot), the same one EditGeneralVoucher uses — not the older
        // hand-written ARVoucherDbValidator (which never looked at aud2_* tables at all). Gets
        // field-level audit assertions for AR Edit "for free" via the shared engine.
        StepExecutor.step("Database Snapshot Validation", () -> {
            if (!com.gsl.validation.ValidationSwitches.run(com.gsl.validation.ValidationSwitches.Area.DB)) {
                com.gsl.reports.ExtentLogger.skip("DB validation skipped — validation.db.enabled=no");
                return;
            }
            ExpectedChangeSet changeSet = ARVoucherEditChangeSets.headerEdit(contextRef[0], beforeRef[0],
                    testData.getDocumentInformation().getReference(),
                    testData.getDocumentInformation().getRemarks(),
                    testData.getDocumentInformation().getDocumentdate());

            VoucherSnapshotValidator validator = new VoucherSnapshotValidator();
            validator.validate("AR Voucher Edit — Snapshot Validation — [" + ctx.docNo + "]",
                    contextRef[0], beforeRef[0], afterRef[0], changeSet);
            validator.assertAll();
            ReportLogger.pass("Snapshot validation complete. Document No: " + ctx.docNo);
        });
    }
}
