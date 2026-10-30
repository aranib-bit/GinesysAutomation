package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.ApiFlow;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.TestCaseId;
import com.gsl.api.model.generalvoucher.GeneralVoucherRootView;
import com.gsl.database.DatabaseManager;
import com.gsl.database.DbAvailability;
import com.gsl.database.dao.adm.AdmSiteDao;
import com.gsl.database.dao.finance.FinCostTagDao;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.database.dao.finance.FinPostDao;
import com.gsl.database.dao.finance.FinVchDetDao;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.driver.DriverManager;
import com.gsl.enums.AppOperation;
import com.gsl.enums.TestAuthor;
import com.gsl.enums.VoucherDomain;
import com.gsl.flow.Finance.Operations.Common.PostSaveDialogHandler;
import com.gsl.model.adm.AdmSite;
import com.gsl.model.finance.FinCostTag;
import com.gsl.model.finance.FinGl;
import com.gsl.model.finance.FinPost;
import com.gsl.model.finance.FinVchDet;
import com.gsl.model.finance.FinVchMain;
import com.gsl.network.pattern.FinanceApiPattern;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.pages.Finance.Operations.GeneralVoucherAdd;
import com.gsl.pages.Finance.Operations.GeneralVoucherListPage;
import com.gsl.pages.HomePage;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Operations.Common.VoucherRevertOutcome;
import com.gsl.pages.enums.Finance.Operations.OperationsMenuType;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.reports.DbGatheringExtentLogger;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.GeneralVoucherExtentDescriptionLogger;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DetailLineEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.MergedFinGeneralVoucherDataSupplier;
import com.gsl.utils.dataprovider.fixture.finance.DocumentSearchStrategy;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditVariant;
import com.gsl.utils.dataprovider.fixture.finance.VoucherEditEntryMethod;
import com.gsl.utils.ui.UiFieldState;
import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.ExpectedChangeSet;
import com.gsl.validation.api.AuditApiReader;
import com.gsl.validation.api.GeneralVoucherApiAuditChecks;
import com.gsl.validation.api.GeneralVoucherEditApiValidator;
import com.gsl.validation.api.NoDbApiSession;
import com.gsl.validation.snapshot.SnapshotService;
import com.gsl.validation.snapshot.TableSnapshot;
import com.gsl.validation.snapshot.VoucherSnapshot;
import com.gsl.validation.ui.UiValidationLevel;
import com.gsl.validation.snapshot.VoucherSnapshotValidator;
import com.gsl.validation.snapshot.finance.Aud2SnapshotDao;
import com.gsl.validation.snapshot.finance.VoucherDocumentContextResolver;
import com.gsl.validation.snapshot.finance.GeneralVoucherEditChangeSets;
import com.tests.WebTest;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Edits an existing General Voucher from the Finance → Operations → General Voucher list page —
 * plan §6. Same UI form as Create, except Owner Site/Voucher No. stay unchanged (nothing to
 * disable — see plan §3: Owner Site is getter-only, Voucher No.'s setter no-ops on an unchanged
 * value when the data source sets it to the existing {@code scheme_docno}).
 *
 * <p>Validated via the new generic before/after DB snapshot → diff → column-rule engine
 * ({@code com.gsl.validation.snapshot}), not the hand-written {@code GeneralVoucherDbValidator}
 * (that validator stays Create/Revert-only, untouched).
 *
 * <h3>Pre-conditions</h3>
 * <ul>
 *   <li>{@code testdata.source=EXCEL} (default) reads the {@code EditVoucherInfo} sheet's sample
 *       rows — <b>replace their placeholder "Existing Voucher No." values with a real voucher
 *       from your environment first</b> (see {@code EditVoucherInfo}'s Javadoc).</li>
 *   <li>{@code testdata.source=DB} claims a live voucher per plan §4's selection rule.</li>
 * </ul>
 *
 * <h3>Pre-existing blocker (not introduced by this class)</h3>
 * {@code VoucherMainGridBase.EDIT_ICON}/{@code SEARCH_BOX}/{@code SEARCH_BUTTON}/
 * {@code UNPOST_BUTTON} are still {@code //TODO-} placeholder locators — must be confirmed
 * against the live DOM before this test (or {@code EditArVoucher}) can run end-to-end (plan §3).
 */
@ApiFlow(FinanceApiPattern.GENERAL_VOUCHER)
public class EditGeneralVoucher extends WebTest {

    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceGenVoucher", operation = AppOperation.MODIFY)
    @TestCaseId("FIN-GV-EDIT-HEADER")
    @Test(description = "Edit an existing General Voucher's document header (reference/remarks/date)",
            dataProvider = "FinanceGeneralVoucherData", dataProviderClass = MergedFinGeneralVoucherDataSupplier.class)
    public void editGeneralVoucherHeader(UserCredential user, MergedFinanceGeneralVoucherData data) {
        Log.info("Edit General Voucher — header edit: {}", data.getExistingVoucherNo());
        runEditFlow(data, GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY);
    }

    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceGenVoucher", operation = AppOperation.MODIFY)
    @TestCaseId("FIN-GV-EDIT-DETAIL")
    @Test(description = "Edit an existing General Voucher's Expense/Income Detail line (amount/reversal)",
            dataProvider = "FinanceGeneralVoucherData", dataProviderClass = MergedFinGeneralVoucherDataSupplier.class)
    public void editGeneralVoucherDetailLine(UserCredential user, MergedFinanceGeneralVoucherData data) {
        Log.info("Edit General Voucher — detail-line edit: {}", data.getExistingVoucherNo());
        runEditFlow(data, GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE);
    }

    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceGenVoucher", operation = AppOperation.MODIFY)
    @TestCaseId("FIN-GV-EDIT-PAYMENT")
    @Test(description = "Edit an existing General Voucher's Payment Information (Cheque instrument number)",
            dataProvider = "FinanceGeneralVoucherData", dataProviderClass = MergedFinGeneralVoucherDataSupplier.class)
    public void editGeneralVoucherPaymentInfo(UserCredential user, MergedFinanceGeneralVoucherData data) {
        Log.info("Edit General Voucher — payment-info edit: {}", data.getExistingVoucherNo());
        runEditFlow(data, GeneralVoucherEditVariant.ScenarioType.PAYMENT_INFO);
    }

    private void runEditFlow(MergedFinanceGeneralVoucherData data, GeneralVoucherEditVariant.ScenarioType scenarioType) {
        try {
            runEditFlowBody(data, scenarioType);
        } finally {
            NoDbApiSession.closeQuietly();
        }
    }

    private void runEditFlowBody(MergedFinanceGeneralVoucherData data, GeneralVoucherEditVariant.ScenarioType scenarioType) {

        Log.info("Existing Voucher No.: {}, Release Status: {}", data.getExistingVoucherNo(), data.getReleaseStatus());

        DbGatheringExtentLogger.log(data);
        // A data source that could not produce this row's fixture keeps the row (so the variant stays visible) and fails it here, by name.
        if (data.getFixtureFailure() != null) {
            throw new AssertionError("[Fixture] " + data.getFixtureFailure());
        }
        GeneralVoucherExtentDescriptionLogger.log(data);
        ActualResultsHolder.get().setVoucherType("GV");
        ActualResultsHolder.get().setTestData(data);

        final MergedFinanceGeneralVoucherData testData = data;

        // Null-safe default, same idiom as testData.getEntryMethod() below — a test only sets
        // this explicitly when it already knows an earlier tier won't help for that scenario.
        final DocumentSearchStrategy searchStrategy = testData.getSearchStrategy() == null
                ? DocumentSearchStrategy.EXACT_MATCH
                : testData.getSearchStrategy();

        final HomePage[]              homeRef    = new HomePage[1];
        final GeneralVoucherListPage[] listRef    = new GeneralVoucherListPage[1];
        final GeneralVoucherAdd[]      editRef    = new GeneralVoucherAdd[1];
        final String[]                 docNoRef   = new String[1];
        final DocumentMessageBox[]     msgBoxRef  = new DocumentMessageBox[1];
        final DocumentPopup[]          popupRef   = new DocumentPopup[1];
        final DocumentContext[]        contextRef = new DocumentContext[1];
        final VoucherSnapshot[]        beforeRef  = new VoucherSnapshot[1];
        final VoucherSnapshot[]        afterRef   = new VoucherSnapshot[1];
        final String[]                 liveReleaseStatusRef = new String[1];
        final String[]                 immediateBeforeReleaseStatusRef = new String[1];
        final VoucherRevertOutcome[] unpostOutcomeRef = new VoucherRevertOutcome[1];

        // db.available=no: nothing below may touch the database — DB reads/snapshots are replaced by rootview reads (validation.nodb.mode=API)
        // or reported SKIPPED (validation.nodb.mode=SKIP). db.available=yes keeps the original path unchanged.
        final boolean noDb = !DbAvailability.isAvailable();
        final boolean apiValidation = noDb && DbAvailability.noDbValidation() == DbAvailability.NoDbValidation.API
                && com.gsl.validation.ValidationSwitches.run(com.gsl.validation.ValidationSwitches.Area.API);
        final GeneralVoucherRootView[] beforeView = new GeneralVoucherRootView[1];
        final GeneralVoucherRootView[] afterView  = new GeneralVoucherRootView[1];
        SnapshotService snapshotService = noDb ? null : new SnapshotService(new Aud2SnapshotDao());
        final com.gsl.flow.Finance.Operations.Common.VoucherSaveCtx ctx = new com.gsl.flow.Finance.Operations.Common.VoucherSaveCtx();

        // ── Step 0: DB Pre-Check — Verify Document Exists & Release Status ───────────
        // Live release_status is the ONLY source of truth for "Unpost if Posted" below — plan
        // §16. Excel/DB data sources no longer declare a "Release Status" value at all (the
        // column was removed entirely, not merely downgraded to a cross-check) since a
        // repeatedly-run Excel voucher's real status can drift on its own once the
        // Release-after-save confirmation (§14) starts firing Yes. Mirrors
        // RevertGeneralVoucher's own Step 1 "DB Pre-Check" — same position, before Login.
        StepExecutor.step(noDb ? "API Pre-Check — Verify Document Exists & Release Status"
                : "DB Pre-Check — Verify Document Exists & Release Status", () -> {
            if (noDb) {
                GeneralVoucherRootView view = NoDbApiSession.current().rootView(Long.parseLong(testData.getReservedDocumentKey()));
                Assert.assertNotNull(view, "[API-PreCheck] rootview not found for vchcode=" + testData.getReservedDocumentKey()
                        + " — target document does not exist; cannot proceed with Edit.");
                liveReleaseStatusRef[0] = view.isPosted() ? "P" : "U";
                ReportLogger.business("Captured live release status='" + liveReleaseStatusRef[0] + "' (rootview) for voucher: "
                        + testData.getExistingVoucherNo());
                return;
            }
            FinVchMain header = DatabaseManager.getJdbi().onDemand(FinVchMainDao.class)
                    .findBySchemeDocno(testData.getExistingVoucherNo())
                    .orElse(null);
            Assert.assertNotNull(header,
                    "[DB-PreCheck] finvchmain row not found for scheme_docno=" + testData.getExistingVoucherNo()
                    + " — target document does not exist in DB; cannot proceed with Edit.");
            liveReleaseStatusRef[0] = header.getReleaseStatus();
            ReportLogger.business("Captured live release_status='" + liveReleaseStatusRef[0]
                    + "' for voucher: " + testData.getExistingVoucherNo());
        });

        // ── Step 1: Login ─────────────────────────────────────────────────────────
        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = com.gsl.driver.SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        // ── Step 2: Navigate ──────────────────────────────────────────────────────
        // Business log + "Navigated: ..." summary are now recorded inside
        // NavigationUtils.navigateToFinanceSideMenu itself.
        StepExecutor.step("Open Finance → General Voucher List", () -> listRef[0] =
                NavigationUtils.navigateToFinanceSideMenu(
                        homeRef[0],
                        MainMenuType.OPERATIONS,
                        OperationsMenuType.CATEGORY,
                        GeneralVoucherListPage.class));

        // ── Step 3: Select voucher in list page (paginated search, plan §9.1) ────────
        // Only needed when something downstream actually depends on the row's checkbox being
        // checked: Step 4's Revert (Actions→Revert, when the voucher is Posted) and/or Step 7's
        // Actions→Edit. DOUBLE_CLICK_EDIT on an already-unposted voucher needs neither — this
        // step is skipped purely to avoid clicking a checkbox nothing downstream will use.
        //
        // Was preceded by its own separate, unconditional "Clear Filter and Refresh List" step
        // (ran regardless of branch, since Step 7's double-click path relied on it too) — now
        // folded away: selectDocumentForAction (checkbox branch, below) and doubleClickForAction
        // (Step 7's double-click branch) each clear the filter themselves via the shared
        // VoucherListPageBase common function, so neither branch depends on this step running
        // first anymore, and the old unconditional call (which used to run even when this whole
        // step was skipped) is no longer needed at all.
        StepExecutor.step("Select Voucher in List Page", () -> {
            boolean checkboxNeeded = testData.getEntryMethod() != VoucherEditEntryMethod.DOUBLE_CLICK_EDIT
                    || "P".equals(liveReleaseStatusRef[0]);
            if (checkboxNeeded) {
                listRef[0].selectDocumentForAction(testData.getExistingVoucherNo(), searchStrategy);
                ReportLogger.business("Filter cleared, list refreshed, selected voucher in list: "
                        + testData.getExistingVoucherNo());
            } else {
                ReportLogger.business("Entry method is DOUBLE_CLICK_EDIT and voucher is not Posted — "
                        + "checkbox selection not required, skipping.");
            }
        });

        // ── Step 3b: DB Check — Release Status Immediately Before Unpost ─────────────
        // A fresh, log-only read taken right before the state-changing click — Step 0's precheck
        // ran before login/navigation/selection, leaving a race window in which another
        // concurrent process can legitimately change the voucher's live release_status. This
        // narrower read is the true pre-action snapshot; it drives Step 4's actual branch below
        // (not Step 0's now-possibly-stale liveReleaseStatusRef[0]), and its divergence from
        // Step 0's value (when it occurs) explains an otherwise-confusing later mismatch instead
        // of masquerading as a product defect. Mirrors RevertGeneralVoucher's own Step 7 exactly
        // (see the "GV Revert picks documents in a now-closed accounting period, + closed-period
        // negative-test coverage" plan, follow-up round 3).
        StepExecutor.step(noDb ? "API Check — Release Status Immediately Before Unpost"
                : "DB Check — Release Status Immediately Before Unpost", () -> {
            if (noDb) {
                GeneralVoucherRootView view = NoDbApiSession.current().rootView(Long.parseLong(testData.getReservedDocumentKey()));
                Assert.assertNotNull(view, "[API-Check] rootview not found for " + testData.getExistingVoucherNo()
                        + " — target document disappeared before Unpost/Edit could be attempted.");
                immediateBeforeReleaseStatusRef[0] = view.isPosted() ? "P" : "U";
                ReportLogger.business("Release status immediately before Unpost: '" + immediateBeforeReleaseStatusRef[0]
                        + "' (rootview) for voucher: " + testData.getExistingVoucherNo());
                return;
            }
            FinVchMain header = DatabaseManager.getJdbi().onDemand(FinVchMainDao.class)
                    .findBySchemeDocno(testData.getExistingVoucherNo())
                    .orElse(null);
            Assert.assertNotNull(header,
                    "[DB-Check] finvchmain row not found for scheme_docno=" + testData.getExistingVoucherNo()
                    + " — target document disappeared before Unpost/Edit could be attempted.");
            immediateBeforeReleaseStatusRef[0] = header.getReleaseStatus();
            ReportLogger.business("Release status immediately before Unpost: '"
                    + immediateBeforeReleaseStatusRef[0] + "' for voucher: " + testData.getExistingVoucherNo());
            if (!immediateBeforeReleaseStatusRef[0].equals(liveReleaseStatusRef[0])) {
                ReportLogger.warn("release_status changed between Step-0 precheck ('" + liveReleaseStatusRef[0]
                        + "') and the immediate-before read ('" + immediateBeforeReleaseStatusRef[0] + "') for "
                        + "voucher " + testData.getExistingVoucherNo() + " — a concurrent process modified this "
                        + "document mid-test; this is expected to be benign as long as Edit still behaves "
                        + "correctly for whichever state it actually landed in.");
            }
        });

        // ── Step 4: Unpost if posted ──────────────────────────────────────────────
        // General Voucher has no standalone "Unpost" button — confirmed on a live run
        // (VoucherMainGridBase.clickUnpost()'s UNPOST_BUTTON is an unconfirmed //TODO-
        // placeholder that doesn't exist in the DOM). Un-posting IS the Revert action here —
        // centralized as listRef[0].revertSelectedDocument() (plan §10), the same
        // Actions->Revert->confirm sequence RevertGeneralVoucher proves works, collapsed into
        // one composite page-object method rather than re-derived inline. Branches on Step 3b's
        // immediate-before read, not Step 0's now-possibly-stale liveReleaseStatusRef[0].
        StepExecutor.step("Unpost if Posted (release_status='P')", () -> {
            if ("P".equals(immediateBeforeReleaseStatusRef[0])) {
                VoucherRevertOutcome outcome = listRef[0].revertSelectedDocument();
                unpostOutcomeRef[0] = outcome;
                if (outcome == VoucherRevertOutcome.CANNOT_REVERT_BRS_DONE) {
                    ReportLogger.warn("Voucher's line(s) are already bank-reconciled — Revert (un-post) was "
                            + "blocked; voucher remains Posted. The subsequent Edit steps may behave "
                            + "differently against a still-Posted voucher.");
                } else if (outcome == VoucherRevertOutcome.CANNOT_REVERT_MONTH_CLOSED) {
                    // GV_EDIT_HEADER_POSTED_MONTH_CLOSED_NEGATIVE's actual assertion point — see the
                    // early-return right after this step. Same shape as the BRS branch above: the
                    // live app refused to un-post, so the voucher remains Posted.
                    ReportLogger.warn("Voucher's own accounting year/financial month is already closed — "
                            + "Revert (un-post) was blocked; voucher remains Posted.");
                } else {
                    ReportLogger.business("Voucher unposted via Actions→Revert (was release_status='P'), outcome=" + outcome);
                }
            } else {
                ReportLogger.business("Voucher status='" + immediateBeforeReleaseStatusRef[0] + "' — unpost not required");
            }
        });

        // ── Step 4b: Negative-outcome branch (closed accounting period) ──────────────
        // A variant carrying an expectedRevertOutcome deliberately claimed a candidate whose own
        // accounting year/financial month is already closed (GV_EDIT_HEADER_POSTED_MONTH_CLOSED_
        // NEGATIVE) — Step 4 above is expected to hit that exact rejection while trying to un-post
        // it, and Edit never gets any further: nothing was actually opened/changed, so Steps 5-14
        // (document context, snapshots, opening the form, Save, post-save validation) don't apply.
        // Mirrors Step 9b's expectedValidation early-return below, one prep step earlier.
        if (testData.getExpectedRevertOutcome() != null) {
            StepExecutor.step("Verify Negative Outcome (Unpost-if-Posted)", () -> {
                Assert.assertEquals(unpostOutcomeRef[0], testData.getExpectedRevertOutcome(),
                        "Expected \"Unpost if Posted\" outcome=" + testData.getExpectedRevertOutcome()
                        + " for voucher " + testData.getExistingVoucherNo()
                        + " (deliberately claimed to exercise that outcome), but got "
                        + unpostOutcomeRef[0] + " instead.");
                ReportLogger.pass("Negative outcome correctly triggered: " + unpostOutcomeRef[0]
                        + " for voucher: " + testData.getExistingVoucherNo());
            });
            return;
        }

        // ── Step 5: Resolve Document Context ──────────────────────────────────────
        StepExecutor.step("Resolve Document Context", () -> {
            if (noDb) {
                ExtentLogger.infoLabeledTable("Document Context", new String[][]{
                        {"Field", "Value"},
                        {"Voucher No.", testData.getExistingVoucherNo()},
                        {"vchcode", testData.getReservedDocumentKey()},
                        {"Source", "db.available=no — resolved from the API, no persistence-map/audit tables"},
                        {"Validation mode", apiValidation ? "API (rootview before/after diff)" : "SKIP (DB-derived checks skipped)"},
                });
                return;
            }
            contextRef[0] = new VoucherDocumentContextResolver().resolve(testData.getExistingVoucherNo(), VoucherDomain.GV);
            ExtentLogger.infoLabeledTable("Document Context", new String[][]{
                    {"Field", "Value"},
                    {"Voucher No.", contextRef[0].getVoucherNo()},
                    {"vchcode", String.valueOf(contextRef[0].getVchcode())},
                    {"Persistence-Map Tables", "finvchmain, finvchdet, finpost, fincosttag"},
                    {"Audit-Active Tables", contextRef[0].getAuditActiveTables().isEmpty()
                            ? "none, audit inactive" : String.join(", ", contextRef[0].getAuditActiveTables())},
                    {"fincosttag participates", String.valueOf(contextRef[0].isCostTagApplicable())},
                    {"Entry Method", testData.getEntryMethod() == null
                            ? VoucherEditEntryMethod.ACTIONS_EDIT.name() + " (default)"
                            : testData.getEntryMethod().name()},
            });
            // Item U: the "Document Context" table above only ever showed Audit-Active Tables,
            // never Audit Trigger Disabled (only visible later, at Step 13's AFTER-snapshot check)
            // — so the BEFORE-Save audit check was only half there. Both sets are already resolved
            // on contextRef[0]; this just renders them in the same dedicated shape the AFTER
            // snapshot's own "... — Audit State" table uses, so the two are directly comparable.
            new com.gsl.validation.snapshot.finance.AuditStateSummary(
                    contextRef[0].getAuditActiveTables(), contextRef[0].getAuditTriggerDisabledTables())
                    .logTable("BEFORE Save — Audit State");
        });

        // ── Step 6: Capture BEFORE Snapshot ────────────────────────────────────────
        StepExecutor.step("Capture BEFORE Snapshot", () -> {
            if (noDb) {
                if (apiValidation) {
                    beforeView[0] = NoDbApiSession.current().rootView(Long.parseLong(testData.getReservedDocumentKey()));
                    ReportLogger.business("BEFORE rootview captured: " + beforeView[0].getExpenseIncomeDetail().size()
                            + " line(s), status=" + beforeView[0].getDocumentStatus());
                    AuditApiReader.captureBefore(testData.getExistingVoucherNo());
                } else {
                    ExtentLogger.skip("BEFORE snapshot skipped — db.available=no and validation.nodb.mode=SKIP");
                }
                return;
            }
            beforeRef[0] = snapshotService.capture(contextRef[0]);
            logSnapshotTable("📸 BEFORE Snapshot", beforeRef[0], contextRef[0]);
        });

        // ── Step 7: Open for Edit — branches on entry method (plan §9.5/9.6) ─────────
        StepExecutor.step("Open Voucher for Edit", () -> {
            if (testData.getEntryMethod() == VoucherEditEntryMethod.DOUBLE_CLICK_EDIT) {
                editRef[0] = listRef[0].doubleClickForAction(testData.getExistingVoucherNo(), searchStrategy);
                ReportLogger.business("Voucher opened for edit via double-click: " + testData.getExistingVoucherNo());
            } else {
                // Re-select immediately before Actions→Edit rather than trusting Step 3's earlier
                // selection to have survived: Step 4's Revert (when it ran) reloads the grid, which
                // clears row selection, and a stale Step-3 selection would otherwise leave Edit
                // disabled here. selectDocumentFast() is idempotent (no-ops if already checked),
                // so this is safe to call again even when Step 4 didn't run.
                //
                // Round 10 (RC-11): even with that immediate re-select, DOM forensics confirmed the
                // row can still be found unselected when Actions opens (an async grid reload racing
                // the click, not a code-level gap this method's own zero-gap sequence already
                // closes) — clickEditFromActions(documentNumber, strategy) now re-selects and
                // retries once itself on that failure, instead of failing outright.
                editRef[0] = listRef[0].clickEditFromActions(testData.getExistingVoucherNo(), searchStrategy);
                ReportLogger.business("Voucher opened for edit via Actions→Edit: " + testData.getExistingVoucherNo());
            }
        });

        // ── Step 7b: Verify Edit form fully loaded (Round 23) ────────────────────────
        // The click above already confirms the window title flipped (VoucherListPageBase
        // .verifyFormOpened → GeneralVoucherAdd.verifyOnThisPage), but ExtJS flips the title
        // before the form's own data-bound fields have finished loading — this deeper check
        // waits out the loading mask and confirms the "Voucher No." field genuinely shows this
        // document's number before Step 8 touches any field. Root-caused live: FIN-GV-EDIT-
        // PAYMENT-01, run 20260926_231950, hit a NoSuchElementException filling a field on a form
        // that looked open by title alone but hadn't actually bound its data yet. Also measures
        // and asserts this round's page-styling (font-size/font-family) regression checks.
        StepExecutor.step("Verify Edit form fully loaded", () ->
                editRef[0].verifyEditFormFullyLoaded(testData.getExistingVoucherNo()));

        // Deferred: UI findings are reported at the END of the test (assertNoPending below), after the
        // edit, Save and snapshot validation have all run — never instead of them.
        StepExecutor.step("UI Validation", () ->
                editRef[0].validateUiContractDeferred(UiValidationLevel.CONTRACT, UiFieldState.EDITABLE, true));

        // ── Step 8/8b/8c: Apply changes to the open Edit form (plan §4K) ────────────
        // Dispatches to GeneralVoucherEditPredicateFactory's HEADER_EDIT_BEHAVIOR/
        // DETAIL_LINE_EDIT_BEHAVIOR/PAYMENT_INFO_EDIT_BEHAVIOR via ScenarioType.execute() — same
        // "named behavior constant + single dispatch point" shape ArVoucherScenarioType#execute
        // already uses. A detail-line/payment-info edit still touches ONLY its own target: the
        // data source already leaves testData.getDocumentInformation()'s date/reference/remarks
        // unchanged for those scenarios, so HEADER_EDIT_BEHAVIOR is simply never dispatched for
        // them.
        scenarioType.execute(testData, editRef[0]);

        // Plan §9c: populate ActualResultsHolder's UI-observed fields — mirrors
        // CreateGeneralVoucher's own Step 3 read-back exactly (same getters, same holder). Fixes
        // the "Owner site"/"Cash / bank GL" rows of the report's "Actual Results" → "UI
        // Validation" table, which previously showed "— (not reached)"/Skip for every GV Edit run
        // (EditGeneralVoucher never touched ActualResultsHolder at all before this). Reference/
        // Remarks/Doc date/Doc no in that same table read from ActualResultsHolder.getHeader()/
        // .getVoucherNo() instead (see ActualResultsLogger.buildPositiveHtmlBody) — populated by
        // the new DB read-back step below, not here.
        StepExecutor.step("Populate Actual Results (UI read-back)", () -> {
            var docInfo = editRef[0].getDocumentInformation();
            ActualResultsHolder.get().setUiOwnerSite(docInfo.getOwnerSite());
            ActualResultsHolder.get().setUiCashBank(docInfo.getCashBank());
        });

        // ── Step 9: Click Save ────────────────────────────────────────────────────
        com.gsl.flow.Finance.Operations.Common.VoucherSaveFlowSteps.clickSave(ctx, null, () -> editRef[0].clickSave());
        popupRef[0] = ctx.popup;

        // ── Step 9b: Negative-outcome branch ──────────────────────────────────────
        // A variant carrying an expectedValidation deliberately set an invalid date (crosses
        // the voucher's accounting year, or is a future date within the same year) — the Save
        // above is expected to be REJECTED, not to succeed. Assert the exact validation message
        // and stop here: nothing was actually saved, so Steps 10-14 (post-save dialog handling,
        // snapshot capture/validation) don't apply. See the "GV Edit: accounting-year-aware
        // dates..." plan.
        if (testData.getExpectedValidation() != null) {
            StepExecutor.step("Verify Negative Validation", () -> {
                popupRef[0].verifyErrorMessage(testData.getExpectedValidation().expectedMessage());
                popupRef[0].clickOkOnErrorDialog();
                ReportLogger.pass("Negative validation correctly triggered: "
                        + testData.getExpectedValidation().expectedMessage());
            });
            com.gsl.validation.ui.UiFailureCollector.assertNoPending();
            return;
        }

        // ── Step 10: Handle Post-Save Dialogs ─────────────────────────────────────
        com.gsl.flow.Finance.Operations.Common.VoucherSaveFlowSteps.handlePostSaveDialogs(ctx, VoucherDomain.GV, testData);
        msgBoxRef[0] = ctx.msgBox;

        // ── Step 11: Verify Save Result ───────────────────────────────────────────
        // Clicking OK on the post-save "Result — Updated Successfully" dialog is what returns
        // control to the General Voucher list page — confirmed via a live screenshot. There is
        // no separate page-level Close step for Edit (plan §15): the previous "Close Voucher
        // Page" step's editRef[0].close().clickYes() call was dead code by the time it ran (the
        // Edit page, and any footer Close button, was already gone).
        // Plan §14: previously called the unchecked extractDocumentNo()+clickOk() pair — nothing
        // ever asserted the document number was non-null or checked the dialog's header status,
        // so a genuinely rejected/silently-no-op'd Save (confirmed live, vchcode=530879,
        // 2026-08-21: "Document No: null" logged here, yet this step still reported Pass) flowed
        // straight through and was only caught indirectly, later, by Step 14's unrelated snapshot
        // diff. Now adopts the same verifyDocumentSave(SoftAssert) pattern CreateGeneralVoucher's
        // own "Verify Save Result" step already uses (DocumentMessageBox.java) — hard-asserts a
        // non-null document number and soft-asserts an acceptable success header, right here,
        // before Save is ever treated as having succeeded.
        com.gsl.flow.Finance.Operations.Common.VoucherSaveFlowSteps.verifySaveResult(ctx);
        docNoRef[0] = ctx.docNo;
        StepExecutor.step("Dismiss Save Dialog", () -> {
            ctx.softAssert.assertAll();
            msgBoxRef[0].clickOk();
            ReportLogger.business("Voucher saved and dialog dismissed, returned to list. Document No: " + docNoRef[0]);
        });

        // ── Step 12: Logout (deferred — session may be reused by a later test on this thread;
        // real logout+driver-quit happens via RecoveryManager or end-of-block cleanup only) ────
        com.gsl.flow.Finance.Operations.Common.VoucherSaveFlowSteps.deferLogout();

        // ── Step 13: Capture AFTER Snapshot ───────────────────────────────────────
        StepExecutor.step("Capture AFTER Snapshot", () -> {
            if (noDb) {
                if (apiValidation) {
                    afterView[0] = NoDbApiSession.current().rootView(Long.parseLong(testData.getReservedDocumentKey()));
                    ReportLogger.business("AFTER rootview captured: " + afterView[0].getExpenseIncomeDetail().size()
                            + " line(s), status=" + afterView[0].getDocumentStatus());
                } else {
                    ExtentLogger.skip("AFTER snapshot skipped — db.available=no and validation.nodb.mode=SKIP");
                }
                return;
            }
            afterRef[0] = snapshotService.capture(contextRef[0]);
            logSnapshotTable("📸 AFTER Snapshot", afterRef[0], contextRef[0]);
        });

        // ── Step 13b: Populate Actual Results (DB read-back) ──────────────────────
        // Plan §9c: a fresh, targeted re-query mirroring GeneralVoucherDbValidator's OWN setter
        // calls (holder.setHeader/.setVoucherNo/.setDetRowCount/.setPostEntryCount/.setPostEntries)
        // — NOT a call to that validator itself, which is the wrong shape for Edit (it asserts a
        // brand-new row count; Edit's own correctness assertions already run separately at Step
        // 14 via the snapshot-diff engine). This is what actually fixes the "DB Validation" →
        // "finvchmain"/"finvchdet"/"finpost" tables showing "— (not reached)"/Skip for every row —
        // EditGeneralVoucher never called any DB validator at all before this, since it uses the
        // newer snapshot-diff engine (plan Parts 2/3/7) instead of GeneralVoucherDbValidator,
        // which is the only thing that ever populated ActualResultsHolder for Create.
        // Plan §14: looks up by the already-known vchcode (contextRef[0], reliable since before
        // Save) instead of the UI-parsed docNoRef[0] — a null/mis-parsed document number used to
        // make this step silently query "WHERE scheme_docno = NULL" (zero rows, no exception),
        // cascading into every "Actual Results" → "DB Validation" row rendering as "— (not
        // reached)"/Skip. findByVoucherCode(long) already exists on FinVchMainDao for exactly
        // this purpose.
        StepExecutor.step(noDb ? "Populate Actual Results (API read-back)" : "Populate Actual Results (DB read-back)", () -> {
            if (noDb) {
                // ActualResultsHolder's header/det/post fields are DB row models; with no DB only the voucher number is known.
                ActualResultsHolder.get().setVoucherNo(docNoRef[0]);
                ReportLogger.business("db.available=no — DB-derived Actual Results rows (finvchmain/finvchdet/finpost) not populated; "
                        + "voucher no. recorded: " + docNoRef[0]);
                return;
            }
            FinVchMain freshHeader = DatabaseManager.getJdbi().onDemand(FinVchMainDao.class)
                    .findByVoucherCode(contextRef[0].getVchcode()).orElse(null);
            ActualResultsHolder.get().setHeader(freshHeader);
            ActualResultsHolder.get().setVoucherNo(docNoRef[0]);
            if (freshHeader != null) {
                // finnar ("narration registered") is normally deposited by the Create-side DB
                // validator; Edit never runs it, so without this the Actual Results "Registered"
                // row always read "not registered" (a false red row on every Edit run).
                ActualResultsHolder.get().setFinnar(DatabaseManager.getJdbi()
                        .onDemand(com.gsl.database.dao.finance.FinNarDao.class)
                        .findByGlcodeAndNartext(freshHeader.getGlcode(), freshHeader.getNartext()).orElse(null));
                List<FinVchDet> detRows = DatabaseManager.getJdbi().onDemand(FinVchDetDao.class)
                        .findByVoucherCode(freshHeader.getVchcode());
                ActualResultsHolder.get().setDetRowCount(detRows.size());

                List<FinPost> postEntries = DatabaseManager.getJdbi().onDemand(FinPostDao.class)
                        .findByEntCode(String.valueOf(freshHeader.getVchcode()));
                ActualResultsHolder.get().setPostEntryCount(postEntries.size());
                ActualResultsHolder.get().setPostEntries(postEntries);
            }
            ReportLogger.business("Actual Results populated from fresh DB read-back for Document No: " + docNoRef[0]);
        });

        // ── Step 14: Database Snapshot Validation ─────────────────────────────────
        // Branches on the edited detail line's shape (plan §4B) — GL_TYPE_TRANSITION/
        // ADD_NEW_LINE need their own row-insert/delete-aware change sets; IN_PLACE and
        // SITE_SPLIT_ONLY both reuse the original detailLineEdit (SITE_SPLIT_ONLY's newValues is
        // an exact copy of beforeRow, so its amount naturally reads as unchanged — the actual
        // site-redistribution check is Step 14b below, not this snapshot-diff pass).
        StepExecutor.step(noDb ? "API Rootview Validation" : "Database Snapshot Validation", () -> {
            if (noDb) {
                if (!apiValidation) {
                    ExtentLogger.skip("Validation skipped — db.available=no and validation.nodb.mode=SKIP");
                    return;
                }
                GeneralVoucherEditApiValidator.Result result = GeneralVoucherEditApiValidator.validate(
                        testData, scenarioType, beforeView[0], afterView[0]);
                com.gsl.reports.ApiValidationReporter.report(result.rows(), result.titles(), null);
                Assert.assertTrue(result.ok(), "[API validation] " + result.failed().size() + " check(s) failed for "
                        + docNoRef[0] + ":\n  " + String.join("\n  ", result.failed()));
                ReportLogger.pass("API rootview validation complete (" + result.passed().size() + " checks). Document No: " + docNoRef[0]);
                return;
            }
            if (!com.gsl.validation.ValidationSwitches.run(com.gsl.validation.ValidationSwitches.Area.DB)) {
                com.gsl.reports.ExtentLogger.skip("DB validation skipped — validation.db.enabled=no");
                return;
            }
            ExpectedChangeSet changeSet;
            if (scenarioType == GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE) {
                DetailLineEditTarget target = testData.getEditedDetailLine();
                changeSet = switch (target.getShape()) {
                    case GL_TYPE_TRANSITION -> GeneralVoucherEditChangeSets.detailLineGlTransition(
                            contextRef[0], beforeRef[0], testData.getDocumentInformation(),
                            target.getBeforeRow(), target.getNewValues());
                    case ADD_NEW_LINE -> GeneralVoucherEditChangeSets.addNewDetailLine(
                            contextRef[0], beforeRef[0], testData.getDocumentInformation(), target.getNewValues());
                    default -> GeneralVoucherEditChangeSets.detailLineEdit( // IN_PLACE, SITE_SPLIT_ONLY
                            contextRef[0], beforeRef[0], testData.getDocumentInformation(), target.getNewValues());
                };
            } else if (scenarioType == GeneralVoucherEditVariant.ScenarioType.PAYMENT_INFO) {
                changeSet = GeneralVoucherEditChangeSets.paymentInfoEdit(contextRef[0], beforeRef[0],
                        testData.getDocumentInformation(), testData.getPaymentEditTarget());
            } else {
                changeSet = GeneralVoucherEditChangeSets.headerEdit(contextRef[0], beforeRef[0],
                        testData.getDocumentInformation());
            }

            VoucherSnapshotValidator validator = new VoucherSnapshotValidator();
            validator.validate("General Voucher Edit — Snapshot Validation — [" + docNoRef[0] + "]",
                    contextRef[0], beforeRef[0], afterRef[0], changeSet);
            validator.assertAll();
            ReportLogger.pass("Snapshot validation complete. Document No: " + docNoRef[0]);
        });

        if (noDb && apiValidation) {
            StepExecutor.step("API Audit Validation", () -> {
                var before = AuditApiReader.before();
                if (before == null) {
                    ExtentLogger.skip("BEFORE audit trail was not captured (voucher not listed)");
                    return;
                }
                var after = AuditApiReader.awaitNewVersion(testData.getExistingVoucherNo(), before.vchcode(), before.from(),
                        c -> c.maxVersion() > before.capture().maxVersion());
                var result = GeneralVoucherApiAuditChecks.edited(testData, scenarioType, before.capture(), after);
                com.gsl.reports.ApiValidationReporter.append(result.rows(), result.titles(), "AuditLog FinVoucher / Finance Site Allocation");
                Assert.assertTrue(result.ok(), "[API audit] " + result.failed().size() + " check(s) failed for "
                        + docNoRef[0] + ":\n  " + String.join("\n  ", result.failed()));
                ReportLogger.pass("API audit validated (" + result.passed().size() + " checks). Document No: " + docNoRef[0]);
            });
        }

        // ── Step 14b: Verify Site Reallocation (SITE_SPLIT_ONLY only) ─────────────
        // A direct, fresh DB re-query of fincosttag — same idiom as Step 0's DB pre-check — rather
        // than forced through ExpectedChangeSet's row predicates: headerEdit's existing blanket
        // expectRowDelete/Insert("fincosttag", row -> true) (needed for every other cost-tag-
        // applicable line on the same document) would swallow a more specific per-site predicate
        // via anyMatch, making "same table, more specific rule" impossible to layer on top — see
        // VoucherEditChangeSets' plan §4B Javadoc for the same reasoning.
        if (scenarioType == GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE
                && testData.getEditedDetailLine().getShape() == DetailLineEditTarget.EditShape.SITE_SPLIT_ONLY) {
            StepExecutor.step("Verify Site Reallocation", () -> {
                if (noDb) {
                    // Covered by "API Rootview Validation" (the edited line's cost-centre allocations are compared there).
                    ExtentLogger.skip(apiValidation ? "Covered by API Rootview Validation (site allocation of the edited line)"
                            : "Skipped — db.available=no and validation.nodb.mode=SKIP");
                    return;
                }
                DetailLineEditTarget target = testData.getEditedDetailLine();
                String entcode = String.valueOf(contextRef[0].getVchcode());
                FinGl gl = DatabaseManager.getJdbi().onDemand(FinGlDao.class)
                        .findByGlName(target.getNewValues().getGeneralLedger());

                List<FinCostTag> tags = DatabaseManager.getJdbi().onDemand(FinCostTagDao.class)
                        .findByEntcodeAndGlcode(entcode, gl.getGlcode());
                AdmSiteDao admSiteDao = DatabaseManager.getJdbi().onDemand(AdmSiteDao.class);

                Map<String, BigDecimal> actualBySite = new LinkedHashMap<>();
                BigDecimal actualTotal = BigDecimal.ZERO;
                for (FinCostTag tag : tags) {
                    AdmSite site = admSiteDao.findByCode(tag.getRefAdmsiteCode());
                    String siteName = site != null ? site.getName() : ("SITE-" + tag.getRefAdmsiteCode());
                    BigDecimal amount = tag.getDamount() != null && tag.getDamount().signum() != 0
                            ? tag.getDamount() : tag.getCamount();
                    actualBySite.merge(siteName, amount, BigDecimal::add);
                    actualTotal = actualTotal.add(amount);
                }

                BigDecimal expectedTotal = target.getNewSiteAllocations().stream()
                        .map(SiteAllocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

                SoftAssert softAssert = new SoftAssert();
                for (SiteAllocation expected : target.getNewSiteAllocations()) {
                    BigDecimal actual = actualBySite.get(expected.getSiteName());
                    softAssert.assertTrue(actual != null && actual.compareTo(expected.getAmount()) == 0,
                            "[Site Reallocation] Site '" + expected.getSiteName() + "' expected amount="
                            + expected.getAmount() + ", actual=" + (actual == null ? "<not found>" : actual));
                }
                softAssert.assertEquals(actualTotal, expectedTotal,
                        "[Site Reallocation] Total allocated amount changed — expected the SAME total ("
                        + expectedTotal + "), got " + actualTotal);
                softAssert.assertAll();
                ReportLogger.pass("Site reallocation verified: " + actualBySite);
            });
        }

        // Reports the deferred UI-contract failures (if any) now that every other area has run.
        StepExecutor.step("Finalize UI assertions", com.gsl.validation.ui.UiFailureCollector::assertNoPending);
    }

    /** {@code context} is passed alongside the snapshot so BOTH the BEFORE and AFTER capture
     *  steps show audit-active/trigger-disabled state directly, rather than only once at Step 5
     *  — a reader previously had to jump all the way to Step 14's DiffEngine output to learn a
     *  registry-active table's trigger was actually physically disabled on this environment. */
    private static void logSnapshotTable(String label, VoucherSnapshot snapshot, DocumentContext context) {
        String[][] table = new String[snapshot.getTables().size() + 1][2];
        table[0] = new String[]{"Table", "Rows Captured"};
        int i = 1;
        for (Map.Entry<String, TableSnapshot> entry : snapshot.getTables().entrySet()) {
            table[i++] = new String[]{entry.getKey(), String.valueOf(entry.getValue().rowCount())};
        }
        ExtentLogger.infoLabeledTable(label, table);
        ExtentLogger.infoLabeledTable(label + " — Audit State", new String[][]{
                {"Field", "Value"},
                {"Audit-Active Tables", context.getAuditActiveTables().isEmpty()
                        ? "none, audit inactive" : String.join(", ", context.getAuditActiveTables())},
                {"Audit Trigger Disabled", context.getAuditTriggerDisabledTables().isEmpty()
                        ? "none" : String.join(", ", context.getAuditTriggerDisabledTables())},
        });

        // Plan §12: a single derived roll-up line, computed purely from the two sets above (no
        // new DB queries) — the two existing rows leave a reader to mentally diff "registered
        // active" against "trigger disabled" themselves to answer "is audit actually working for
        // this document"; this states it directly. auditTriggerDisabledTables is always a subset
        // of auditActiveTables by construction (VoucherDocumentContextResolver only checks
        // trigger status for tables already registered active), so ENABLED = active - disabled.
        Set<String> enabled = new LinkedHashSet<>(context.getAuditActiveTables());
        enabled.removeAll(context.getAuditTriggerDisabledTables());
        Set<String> disabled = context.getAuditTriggerDisabledTables();
        Set<String> notAudited = new LinkedHashSet<>(
                List.of("finvchmain", "finvchdet", "finpost", "fincosttag"));
        notAudited.removeAll(context.getAuditActiveTables());

        ReportLogger.info(String.format(
                "Audit inferred — ENABLED: %s; DISABLED/trigger-off: %s; NOT AUDITED: %s",
                enabled.isEmpty() ? "none" : String.join(", ", enabled),
                disabled.isEmpty() ? "none" : String.join(", ", disabled),
                notAudited.isEmpty() ? "none" : String.join(", ", notAudited)));
    }
}
