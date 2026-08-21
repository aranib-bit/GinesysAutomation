package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.ApiFlow;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.TestCaseId;
import com.gsl.network.pattern.FinanceApiPattern;
import com.gsl.enums.AppOperation;
import com.gsl.enums.TestAuthor;
import com.gsl.enums.VoucherDomain;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.pages.Finance.Operations.ArVoucherAdd;
import com.gsl.pages.Finance.Operations.ArVoucherListPage;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.flow.Finance.Operations.Common.PostSaveDialogHandler;
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
import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.ExpectedChangeSet;
import com.gsl.validation.snapshot.SnapshotService;
import com.gsl.validation.snapshot.VoucherSnapshot;
import com.gsl.validation.snapshot.VoucherSnapshotValidator;
import com.gsl.validation.snapshot.finance.ARVoucherEditChangeSets;
import com.gsl.validation.snapshot.finance.Aud2SnapshotDao;
import com.gsl.validation.snapshot.finance.VoucherDocumentContextResolver;
import com.tests.WebTest;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

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

        // ── Step 3: Search ────────────────────────────────────────────────────────
        StepExecutor.step("Search AR Voucher by Document Number", () -> {
            listRef[0].searchByVoucherNo(testData.getExistingVoucherNo());
            ReportLogger.business("Searched for voucher: " + testData.getExistingVoucherNo());
        });

        // ── Step 4: Unpost if posted ──────────────────────────────────────────────
        StepExecutor.step("Unpost if Posted (release_status='P')", () -> {
            if ("P".equals(testData.getReleaseStatus())) {
                listRef[0].clickUnpost();
                // TODO: query fintag for this voucher — if rows exist,
                //   navigate to Document Adjustment and remove the tagging before editing.
                //   Until Document Adjustment page object is available, this step will
                //   fail at runtime if the voucher is tagged.
                ReportLogger.business("Voucher unposted (was release_status='P')");
            } else {
                ReportLogger.business("Voucher status='" + testData.getReleaseStatus()
                        + "' — unpost not required");
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
            editRef[0] = listRef[0].clickEditForVoucher();
            ReportLogger.business("Voucher opened for edit: " + testData.getExistingVoucherNo());
        });

        // ── Step 6: Apply changes to document header ──────────────────────────────
        StepExecutor.step("Apply Changes to Document Header", () -> {
            editRef[0].fillDocumentHeader(testData.getDocumentInformation());
            ReportLogger.business("Updated date/reference/remarks via fillDocumentHeader");
        });

        // ── Step 7b: Click Save (edit: no DB capture → no 5a/5e snapshot) ─────────
        StepExecutor.step("Click Save", () ->
                popupRef[0] = editRef[0].clickSave());

        // ── Step 7c: Handle Post-Save Dialogs (type-aware) ────────────────────────
        StepExecutor.step("Handle Post-Save Dialogs", () ->
                msgBoxRef[0] = PostSaveDialogHandler.forDomain(VoucherDomain.AR)
                        .handle(popupRef[0], testData));

        // ── Step 7d: Verify Save Result ───────────────────────────────────────────
        // Plan §15b: previously used the unchecked extractDocumentNo() pair — the exact same
        // gap Part 14 fixed for GV Edit (no assertion on a null document number, no header/
        // success check), which let a genuinely rejected/no-op'd Save silently report Pass.
        // Now adopts the same verifyDocumentSave(SoftAssert) pattern already proven there and
        // already used by every Create flow. No clickOk() here — "Close Voucher Page" below
        // already calls it.
        StepExecutor.step("Verify Save Result", () -> {
            SoftAssert saveSoftAssert = new SoftAssert();
            docNoRef[0] = msgBoxRef[0].verifyDocumentSave(saveSoftAssert).getDocumentNo();
            saveSoftAssert.assertAll();
            Log.info("Extracted Document No: {}", docNoRef[0]);
            ReportLogger.pass("Voucher saved. Document No: " + docNoRef[0]);
        });

        // ── Step 8: Close Voucher Page (per entry; lands back on the list) ────────
        StepExecutor.step("Close Voucher Page", () -> {
            if (msgBoxRef[0] != null) {
                msgBoxRef[0].clickOk();
            }
            listRef[0] = editRef[0].close().clickYes(ArVoucherListPage::new);   // keep the handle
            ReportLogger.business("Voucher page closed, returned to list");
        });

        // ── Step 9: Logout (deferred — session may be reused by a later test on this thread;
        // real logout+driver-quit happens via RecoveryManager or end-of-block cleanup only) ────
        StepExecutor.step("Logout (deferred)", () ->
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread."));

        // ── Step 10: Capture AFTER Snapshot ───────────────────────────────────────
        StepExecutor.step("Capture AFTER Snapshot", () -> afterRef[0] = snapshotService.capture(contextRef[0]));

        // ── Step 11: Database Snapshot Validation ─────────────────────────────────
        // Migrated onto the generic before/after DB snapshot → diff → column-rule engine
        // (com.gsl.validation.snapshot), the same one EditGeneralVoucher uses — not the older
        // hand-written ARVoucherDbValidator (which never looked at aud2_* tables at all). Gets
        // field-level audit assertions for AR Edit "for free" via the shared engine.
        StepExecutor.step("Database Snapshot Validation", () -> {
            ExpectedChangeSet changeSet = ARVoucherEditChangeSets.headerEdit(contextRef[0], beforeRef[0],
                    testData.getDocumentInformation().getReference(),
                    testData.getDocumentInformation().getRemarks(),
                    testData.getDocumentInformation().getDocumentdate());

            VoucherSnapshotValidator validator = new VoucherSnapshotValidator();
            validator.validate("AR Voucher Edit — Snapshot Validation — [" + docNoRef[0] + "]",
                    contextRef[0], beforeRef[0], afterRef[0], changeSet);
            validator.assertAll();
            ReportLogger.pass("Snapshot validation complete. Document No: " + docNoRef[0]);
        });
    }
}
