package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.ApiFlow;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.TestCaseId;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinVchDetDao;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.driver.DriverManager;
import com.gsl.enums.AppOperation;
import com.gsl.enums.TestAuthor;
import com.gsl.enums.VoucherDomain;
import com.gsl.flow.Finance.Operations.Common.VoucherEditFlowSteps;
import com.gsl.flow.Finance.Operations.Common.VoucherSaveCtx;
import com.gsl.flow.Finance.Operations.Common.VoucherSaveFlowSteps;
import com.gsl.model.finance.FinVchDet;
import com.gsl.model.finance.FinVchMain;
import com.gsl.network.pattern.FinanceApiPattern;
import com.gsl.pages.Finance.Operations.ApVoucherAdd;
import com.gsl.pages.Finance.Operations.ApVoucherListPage;
import com.gsl.pages.HomePage;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Operations.OperationsMenuType;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.reports.ApVoucherExtentDescriptionLogger;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.ApVoucherDetailLineEditTarget;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.MergedFinApVoucherDataSupplier;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherEditScenarioType;
import com.gsl.utils.dataprovider.fixture.finance.DocumentSearchStrategy;
import com.gsl.utils.ui.UiFieldState;
import com.gsl.validation.db.finance.AdjustmentTotalsValidator;
import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.ExpectedChangeSet;
import com.gsl.validation.snapshot.SnapshotService;
import com.gsl.validation.snapshot.VoucherSnapshot;
import com.gsl.validation.snapshot.VoucherSnapshotValidator;
import com.gsl.validation.snapshot.finance.APVoucherEditChangeSets;
import com.gsl.validation.snapshot.finance.Aud2SnapshotDao;
import com.gsl.validation.snapshot.finance.VoucherDocumentContextResolver;
import com.gsl.validation.ui.UiValidationFailure;
import com.gsl.validation.ui.UiValidationLevel;
import com.tests.WebTest;
import org.jdbi.v3.core.Jdbi;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Edits an existing AP Voucher — document header (reference/remarks/date), detail-line amount
 * (Advance direct-edit or Document adjustment-change), or payment info — from the Finance →
 * Operations → AP Voucher list page. All scenario types share one flow
 * ({@link #runEditFlow}), dispatching only the "apply changes" step via
 * {@link ApVoucherEditScenarioType#execute}, mirroring {@code EditGeneralVoucher}'s own
 * architecture (see {@code ApVoucherEditPredicateFactory}'s class Javadoc for the full design).
 *
 * <p><b>Two deliberate differences from {@code EditArVoucher}/{@code EditGeneralVoucher}</b>, per
 * the user's explicit instruction:
 * <ol>
 *   <li><b>"Unpost if Posted" is API-driven, not UI-driven.</b> AP Voucher calls the
 *       newly-real {@link com.gsl.api.client.ApVoucherApiClient#revertApVoucher} — a confirmed
 *       HAR capture exists for this exact call.</li>
 *   <li><b>The untag step runs immediately BEFORE the API revert call</b>, not after — a
 *       Posted+tagged document cannot be unposted via the API until untagged first.</li>
 * </ol>
 *
 * <p><b>Mandatory tagging rule for On-Account=No.</b> Confirmed live (run 20260930_130535):
 * untagging a Document-mode voucher to unpost it, then never re-tagging, leaves it in a state Save
 * correctly rejects. The capture/untag step ({@link VoucherEditFlowSteps#untagIfTagged}) therefore
 * records EVERY live tag (any number of counter-documents, of any entity type — e.g. purchase
 * invoices, run 20260930_163632) into {@code data.getCapturedTags()} BEFORE untagging, and the
 * shared "Apply/Restore Sub-Ledger Adjustment" step re-applies them (optionally changed per the
 * scenario) through the UI before Save, answering Adjustment Entry's amount-mismatch Confirmation
 * ("Update payment amount" / "I will adjust more") per the scenario's plan. The save tail
 * (Save → dialogs → verify → close → logout) is the shared {@link VoucherSaveFlowSteps}.
 *
 * <h3>Pre-conditions</h3>
 * <ul>
 *   <li>{@code testdata.source=EXCEL} — an {@code ApEditVoucherInfo} sheet row plus a matching
 *       {@code MasterTestData} row with {@code testcasename=editApVoucherHeader}.</li>
 *   <li>{@code testdata.source=DB} — {@code DbApVoucherEditDataSource} claims one live AP Voucher
 *       per scenario, filtered by {@code apv.group}.</li>
 * </ul>
 */
@ApiFlow(FinanceApiPattern.AP_VOUCHER)
public class EditApVoucher extends WebTest {

    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceAPVoucher", operation = AppOperation.MODIFY)
    @TestCaseId("FIN-AP-EDIT-HEADER")
    @Test(description = "Edit an existing AP Voucher's document header (reference/remarks/date)",
            dataProvider = "FinanceApVoucherData", dataProviderClass = MergedFinApVoucherDataSupplier.class)
    public void editApVoucherHeader(UserCredential user, MergedFinanceApVoucherData data) {
        runEditFlow(data, ApVoucherEditScenarioType.HEADER_ONLY);
    }

    /**
     * Edits an existing AP Voucher's Sub-Ledger row amount — Advance mode (plain, directly-
     * editable field, {@link ApVoucherEditScenarioType#DETAIL_AMOUNT_ADVANCE}) or Document mode
     * (read-only field, changed via the Adjustment popup instead — {@code DETAIL_AMOUNT_DOCUMENT}
     * and the multi-document / mismatch-Confirmation variants) — which one applies is resolved by
     * the data source per claimed candidate, not fixed by this method (see
     * {@code MergedFinanceApVoucherData#getEditScenarioType()}).
     */
    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceAPVoucher", operation = AppOperation.MODIFY)
    @TestCaseId("FIN-AP-EDIT-DETAIL-LINE")
    @Test(description = "Edit an existing AP Voucher's Sub-Ledger row amount (Advance or Document mode)",
            dataProvider = "FinanceApVoucherData", dataProviderClass = MergedFinApVoucherDataSupplier.class)
    public void editApVoucherDetailLine(UserCredential user, MergedFinanceApVoucherData data) {
        runEditFlow(data, data.getEditScenarioType());
    }

    /** Edits an existing AP Voucher's Cheque/RTGS payment instrument number (see
     *  {@code ApVoucherEditPredicateFactory.PAYMENT_INFO_EDIT_BEHAVIOR}). */
    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceAPVoucher", operation = AppOperation.MODIFY)
    @TestCaseId("FIN-AP-EDIT-PAYMENT-INFO")
    @Test(description = "Edit an existing AP Voucher's Payment Information (Cheque/RTGS instrument no.)",
            dataProvider = "FinanceApVoucherData", dataProviderClass = MergedFinApVoucherDataSupplier.class)
    public void editApVoucherPaymentInfo(UserCredential user, MergedFinanceApVoucherData data) {
        runEditFlow(data, ApVoucherEditScenarioType.DETAIL_PAYMENT_INFO);
    }

    private void runEditFlow(MergedFinanceApVoucherData data, ApVoucherEditScenarioType scenarioType) {

        Log.info("Edit AP Voucher: {}", data.getExistingVoucherNo());
        Log.info("Release status: {}", data.getReleaseStatus());
        Log.info("Scenario type: {}", scenarioType);

        ApVoucherExtentDescriptionLogger.log(data);

        final MergedFinanceApVoucherData testData = data;
        if (testData.getCapturedTags() == null) {
            testData.setCapturedTags(new ArrayList<>());
        }

        // Null-safe default, same idiom RevertApVoucher/EditGeneralVoucher already use.
        final DocumentSearchStrategy searchStrategy = testData.getSearchStrategy() == null
                ? DocumentSearchStrategy.EXACT_MATCH
                : testData.getSearchStrategy();

        // Cross-step object holders
        final HomePage[]          homeRef    = new HomePage[1];
        final ApVoucherListPage[] listRef    = new ApVoucherListPage[1];
        final ApVoucherAdd[]      editRef    = new ApVoucherAdd[1];
        final DocumentContext[]   contextRef = new DocumentContext[1];
        final VoucherSnapshot[]   beforeRef  = new VoucherSnapshot[1];
        final VoucherSnapshot[]   afterRef   = new VoucherSnapshot[1];
        final VoucherSaveCtx      ctx        = new VoucherSaveCtx();

        SnapshotService snapshotService = new SnapshotService(new Aud2SnapshotDao());

        // ── Step 1: Login ─────────────────────────────────────────────────────────
        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = com.gsl.driver.SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        // ── Step 2: Navigate ──────────────────────────────────────────────────────
        StepExecutor.step("Open Finance → AP Voucher List", () -> listRef[0] =
                NavigationUtils.navigateToFinanceSideMenu(
                        homeRef[0],
                        MainMenuType.OPERATIONS,
                        OperationsMenuType.APVOUCHER,
                        ApVoucherListPage.class));

        // ── Step 3: Clear Filter + Select AP Voucher in List Page (tiered fast search) ──
        StepExecutor.step("Select AP Voucher in List Page", () -> {
            listRef[0].selectDocumentForAction(testData.getExistingVoucherNo(), searchStrategy);
            ReportLogger.business("Filter cleared, list refreshed, selected voucher: "
                    + testData.getExistingVoucherNo());
        });

        // ── Step 4: Capture ALL live tags + untag (shared — capture runs first, tags die on untag) ──
        VoucherEditFlowSteps.untagIfTagged(
                testData.getExistingVoucherNo(), testData.getReleaseStatus(), testData.getCapturedTags());

        // ── Step 5: Unpost if Posted (API-driven — see class Javadoc) ─────────────
        VoucherEditFlowSteps.unpostApVoucherViaApi(testData.getExistingVoucherNo(), testData.getReleaseStatus());

        // ── Step 6: Resolve Document Context ──────────────────────────────────────
        StepExecutor.step("Resolve Document Context", () -> {
            contextRef[0] = new VoucherDocumentContextResolver().resolve(testData.getExistingVoucherNo(), VoucherDomain.AP);
        });

        // ── Step 7: Capture BEFORE Snapshot ────────────────────────────────────────
        StepExecutor.step("Capture BEFORE Snapshot", () -> beforeRef[0] = snapshotService.capture(contextRef[0]));

        // ── Step 8: Open for Edit ──────────────────────────────────────────────────
        StepExecutor.step("Open Voucher for Edit", () -> {
            editRef[0] = listRef[0].clickEditFromActions(testData.getExistingVoucherNo(), searchStrategy);
            ReportLogger.business("Voucher opened for edit: " + testData.getExistingVoucherNo());
        });

        StepExecutor.step("UI Validation", () ->
                editRef[0].validateUiContract(UiValidationLevel.CONTRACT, UiFieldState.EDITABLE, true));

        // ── Step 9: Apply changes (dispatch) ──────────────────────────────────────
        StepExecutor.step("Apply Changes (dispatch: " + scenarioType + ")", () -> {
            boolean ok = scenarioType.execute(testData, editRef[0]);
            Assert.assertTrue(ok, "[" + scenarioType + "] apply-changes behavior returned false");
        });

        // ── Step 9b: Apply/Restore Sub-Ledger Adjustment (shared; no-op when nothing captured) ──
        com.gsl.utils.dataprovider.fixture.finance.ApVoucherEditPredicateFactory
                .ADJUSTMENT_RESTORE_OR_CHANGE_BEHAVIOR.get().test(testData, editRef[0]);

        // ── Steps 10-14: Save → dialogs → verify → close → logout (shared tail) ────
        VoucherSaveFlowSteps.clickSave(ctx, null, () -> editRef[0].clickSave());
        VoucherSaveFlowSteps.handlePostSaveDialogs(ctx, VoucherDomain.AP, testData);
        VoucherSaveFlowSteps.verifySaveResult(ctx);
        // Edit wants a hard failure at this step (a rejected Save must not flow on to DB checks).
        StepExecutor.step("Assert save header", ctx.softAssert::assertAll);
        VoucherSaveFlowSteps.closeVoucherPage(ctx,
                () -> editRef[0].close().clickYes(ApVoucherListPage::new), ApVoucherListPage::new);
        VoucherSaveFlowSteps.deferLogout();

        // ── Step 15: Capture AFTER Snapshot ─────────────────────────────────────────
        StepExecutor.step("Capture AFTER Snapshot", () -> afterRef[0] = snapshotService.capture(contextRef[0]));

        // ── Step 16: Database Validation ──────────────────────────────────────────
        // HEADER_ONLY reuses the snapshot/ExpectedChangeSet engine. Detail-line/payment-info types
        // use a direct DB read-back; Document-mode adjustment scenarios assert the adjustment
        // invariant via AdjustmentTotalsValidator (payment == fintag total, documents as expected).
        // Wiring fintag into the snapshot engine itself is flagged follow-up work.
        StepExecutor.step("Database Validation", () -> {
            final String docNo = ctx.docNo;
            if (scenarioType == ApVoucherEditScenarioType.HEADER_ONLY) {
                ExpectedChangeSet changeSet = APVoucherEditChangeSets.headerEdit(contextRef[0], beforeRef[0],
                        testData.getDocumentInformation().getReference(),
                        testData.getDocumentInformation().getRemarks(),
                        testData.getDocumentInformation().getDocumentdate());

                VoucherSnapshotValidator validator = new VoucherSnapshotValidator();
                validator.validate("AP Voucher Edit — Snapshot Validation — [" + docNo + "]",
                        contextRef[0], beforeRef[0], afterRef[0], changeSet);
                validator.assertAll();
                ReportLogger.pass("Snapshot validation complete. Document No: " + docNo);
                // A tagged On-Account=No candidate was re-tagged above — confirm the original
                // documents are back at their original amounts (HEADER_ONLY changes neither).
                if (!testData.getCapturedTags().isEmpty()) {
                    Set<String> expectedDocs = new TreeSet<>();
                    BigDecimal expectedTotal = BigDecimal.ZERO;
                    for (var c : testData.getCapturedTags()) {
                        expectedDocs.add(c.getCounterDocNo());
                        expectedTotal = expectedTotal.add(c.getAmount());
                    }
                    AdjustmentTotalsValidator.assertTaggedDocuments(testData.getExistingVoucherNo(), expectedDocs);
                    AdjustmentTotalsValidator.assertPaymentMatchesTags(testData.getExistingVoucherNo(), expectedTotal);
                }
                return;
            }

            Jdbi jdbi = DatabaseManager.getJdbi();
            FinVchMain header = jdbi.onDemand(FinVchMainDao.class)
                    .findBySchemeDocno(testData.getExistingVoucherNo())
                    .orElseThrow(() -> new IllegalStateException(
                            "[DB Validation] finvchmain row not found for scheme_docno=" + testData.getExistingVoucherNo()));

            if (scenarioType == ApVoucherEditScenarioType.DETAIL_AMOUNT_ADVANCE
                    || scenarioType == ApVoucherEditScenarioType.DETAIL_AMOUNT_DOCUMENT) {
                ApVoucherDetailLineEditTarget target = testData.getEditedDetailLine();
                List<FinVchDet> lines = jdbi.onDemand(FinVchDetDao.class).findByVoucherCode(header.getVchcode());
                BigDecimal actualAmount = AdjustmentTotalsValidator.paymentTotal(lines);
                Assert.assertEquals(0, target.getNewAmount().compareTo(actualAmount),
                        "[DB Validation] Expected finvchdet amount=" + target.getNewAmount()
                        + " but found " + actualAmount + " for scheme_docno=" + testData.getExistingVoucherNo());

                if (scenarioType == ApVoucherEditScenarioType.DETAIL_AMOUNT_DOCUMENT) {
                    AdjustmentTotalsValidator.assertPaymentMatchesTags(
                            testData.getExistingVoucherNo(), target.getNewAmount());
                }
                ReportLogger.pass("DB validated — Sub-Ledger amount matches target for voucher: "
                        + testData.getExistingVoucherNo());
            } else if (scenarioType.isDocumentAdjustmentScenario()) {
                ApVoucherDetailLineEditTarget target = testData.getEditedDetailLine();
                BigDecimal expectedPayment = target != null ? target.getExpectedPaymentAmount() : null;
                if (expectedPayment == null) {
                    // Unchanged payment: the captured tags' total (restore-original scenarios).
                    expectedPayment = testData.getCapturedTags().stream()
                            .map(c -> c.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
                }
                AdjustmentTotalsValidator.assertPaymentMatchesTags(testData.getExistingVoucherNo(), expectedPayment);

                Set<String> baseDocs = new TreeSet<>();
                testData.getCapturedTags().forEach(c -> baseDocs.add(c.getCounterDocNo()));
                if (target != null) target.getTargets().forEach(t -> baseDocs.add(t.getDocumentNumber()));
                if (target != null && target.getTargetDocumentNumber() != null) {
                    baseDocs.add(target.getTargetDocumentNumber());
                }
                AdjustmentTotalsValidator.expectedDocumentsAfter(baseDocs,
                        target != null ? target.mismatchPlanOrNone() : null, false)
                        .ifPresent(expected -> AdjustmentTotalsValidator
                                .assertTaggedDocuments(testData.getExistingVoucherNo(), expected));
            } else if (scenarioType == ApVoucherEditScenarioType.DETAIL_PAYMENT_INFO) {
                List<FinVchDet> lines = jdbi.onDemand(FinVchDetDao.class).findByVoucherCode(header.getVchcode());
                Assert.assertFalse(lines.isEmpty(),
                        "[DB Validation] finvchdet has no lines for scheme_docno=" + testData.getExistingVoucherNo());
                String expectedInstrumentNo = testData.getPaymentEditTarget().getNewPayment().getInstrumentNo();
                Assert.assertEquals(lines.get(0).getChqno(), expectedInstrumentNo,
                        "[DB Validation] Expected finvchdet.chqno=" + expectedInstrumentNo + " but found "
                        + lines.get(0).getChqno() + " for scheme_docno=" + testData.getExistingVoucherNo());
                ReportLogger.pass("DB validated — Instrument No. matches target for voucher: "
                        + testData.getExistingVoucherNo());
            }
        });
    }

    /**
     * Negative case: confirms AP Voucher's Date field is genuinely <b>disabled</b> on the Edit
     * form — a business rule, not a bug (confirmed live DOM, run {@code 20260930_012130}:
     * {@code disabled="" aria-disabled="true"}). Gated under {@code REGRESSION_PHASE1}
     * ({@code DbApVoucherEditDataSource}), not {@code MANDATORY}, per explicit instruction.
     *
     * <p>Shares Steps 1-8 with {@code runEditFlow} (login/nav/select/untag/unpost if Posted/
     * open-for-edit) — the same preconditions are needed just to reach an open Edit form to
     * check. Diverges at Step 9: no field is changed, no Save is clicked, no DB validation
     * follows — a pure UI-state assertion, so the form is simply closed once the check completes.
     */
    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceAPVoucher", operation = AppOperation.MODIFY)
    @TestCaseId("FIN-AP-EDIT-DATE-DISABLED")
    @Test(description = "Confirm AP Voucher's Date field is disabled on Edit (business rule, negative case)",
            dataProvider = "FinanceApVoucherData", dataProviderClass = MergedFinApVoucherDataSupplier.class)
    public void editApVoucherDateFieldDisabled(UserCredential user, MergedFinanceApVoucherData data) {

        Log.info("Verify Date field disabled — AP Voucher: {}", data.getExistingVoucherNo());
        Log.info("Release status: {}", data.getReleaseStatus());

        final MergedFinanceApVoucherData testData = data;
        final List<com.gsl.testdata.Finance.Operations.Vouchers.Common.CapturedTag> discardedTags = new ArrayList<>();

        final DocumentSearchStrategy searchStrategy = testData.getSearchStrategy() == null
                ? DocumentSearchStrategy.EXACT_MATCH
                : testData.getSearchStrategy();

        final HomePage[]          homeRef = new HomePage[1];
        final ApVoucherListPage[] listRef = new ApVoucherListPage[1];
        final ApVoucherAdd[]      editRef = new ApVoucherAdd[1];

        // ── Step 1: Login ─────────────────────────────────────────────────────────
        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = com.gsl.driver.SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        // ── Step 2: Navigate ──────────────────────────────────────────────────────
        StepExecutor.step("Open Finance → AP Voucher List", () -> listRef[0] =
                NavigationUtils.navigateToFinanceSideMenu(
                        homeRef[0],
                        MainMenuType.OPERATIONS,
                        OperationsMenuType.APVOUCHER,
                        ApVoucherListPage.class));

        // ── Step 3: Clear Filter + Select AP Voucher in List Page ─────────────────
        StepExecutor.step("Select AP Voucher in List Page", () -> {
            listRef[0].selectDocumentForAction(testData.getExistingVoucherNo(), searchStrategy);
            ReportLogger.business("Filter cleared, list refreshed, selected voucher: "
                    + testData.getExistingVoucherNo());
        });

        // ── Steps 4-5: untag + API unpost (shared) ────────────────────────────────
        VoucherEditFlowSteps.untagIfTagged(testData.getExistingVoucherNo(), testData.getReleaseStatus(), discardedTags);
        VoucherEditFlowSteps.unpostApVoucherViaApi(testData.getExistingVoucherNo(), testData.getReleaseStatus());

        // ── Step 6: Open for Edit ──────────────────────────────────────────────────
        StepExecutor.step("Open Voucher for Edit", () -> {
            editRef[0] = listRef[0].clickEditFromActions(testData.getExistingVoucherNo(), searchStrategy);
            ReportLogger.business("Voucher opened for edit: " + testData.getExistingVoucherNo());
        });

        // ── Step 7: Verify Date field is disabled (business rule) ─────────────────
        StepExecutor.step("Verify Date field is disabled (business rule)", () -> {
            List<UiValidationFailure> failures = editRef[0].getDocumentInformation()
                    .validateDateUi(UiValidationLevel.SMOKE, UiFieldState.DISABLED, true);
            Assert.assertTrue(failures.isEmpty(),
                    "Expected AP Voucher's Date field to be disabled on Edit (business rule), but found: " + failures);
            ReportLogger.pass("Confirmed Date field is disabled on AP Voucher Edit form, as expected.");
        });

        // ── Step 8: Close Voucher Page (discard — nothing was changed) ────────────
        StepExecutor.step("Close Voucher Page", () -> {
            if (com.gsl.driver.HealthChecker.isModalWindowOpen()) {
                listRef[0] = editRef[0].close().clickYes(ApVoucherListPage::new);
            } else {
                listRef[0] = new ApVoucherListPage();
            }
            ReportLogger.business("Voucher page closed (discarded), returned to list");
        });

        // ── Step 9: Logout (deferred) ──────────────────────────────────────────────
        VoucherSaveFlowSteps.deferLogout();
    }
}
