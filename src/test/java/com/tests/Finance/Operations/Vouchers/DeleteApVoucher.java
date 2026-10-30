package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.ApiFlow;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.TestCaseId;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.HrEmpDao;
import com.gsl.database.dao.finance.FinVchDetDao;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.driver.DriverManager;
import com.gsl.enums.AppOperation;
import com.gsl.enums.TestAuthor;
import com.gsl.enums.VoucherDomain;
import com.gsl.flow.Finance.Operations.Common.DocumentAdjustmentFlow;
import com.gsl.flow.Finance.Operations.Common.VoucherEditFlowSteps;
import com.gsl.model.finance.FinVchDet;
import com.gsl.model.finance.FinVchMain;
import com.gsl.network.pattern.FinanceApiPattern;
import com.gsl.pages.Finance.Operations.ApVoucherListPage;
import com.gsl.pages.HomePage;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Operations.OperationsMenuType;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.MergedFinApVoucherDataSupplier;
import com.gsl.utils.dataprovider.fixture.finance.DocumentSearchStrategy;
import com.gsl.utils.dataprovider.source.finance.common.AccountingPeriodResolver;
import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.ExpectedChangeSet;
import com.gsl.validation.snapshot.SnapshotService;
import com.gsl.validation.snapshot.VoucherSnapshot;
import com.gsl.validation.snapshot.VoucherSnapshotValidator;
import com.gsl.validation.snapshot.finance.APVoucherDeleteChangeSets;
import com.gsl.validation.snapshot.finance.Aud2SnapshotDao;
import com.gsl.validation.snapshot.finance.VoucherDocumentContextResolver;
import com.tests.WebTest;
import org.jdbi.v3.core.Jdbi;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.List;

/**
 * Deletes an existing AP Voucher from the Finance → Operations → AP Voucher list page — mirrors
 * {@code DeleteArVoucher.java}'s structure exactly (login, nav, search, unpost-if-posted, resolve
 * context/capture pre-delete state, delete + confirm, logout-deferred, DB validation), with two
 * differences:
 * <ol>
 *   <li>The "Unpost if Posted" step's {@code // TODO: query fintag...} stub (present in
 *       {@code DeleteArVoucher}/{@code EditArVoucher} at the time this class was written) is
 *       replaced here with a REAL call to {@link DocumentAdjustmentFlow#untagIfNeeded} — this is
 *       the first AP test class to close that gap; AR's own two TODOs are closed in the same pass
 *       (see {@code DeleteArVoucher}/{@code EditArVoucher}'s updated "Unpost if Posted" steps).</li>
 *   <li>The final DB validation step uses the full BEFORE/AFTER snapshot-diff engine
 *       ({@link APVoucherDeleteChangeSets#delete}) rather than AR's own single-table
 *       {@code AuditAssertion.assertAuditDeleted} call — the same fuller coverage
 *       {@code DeleteGeneralVoucher} already gets from {@code GeneralVoucherDeleteChangeSets},
 *       now available for AP too since {@code APVoucherDeleteChangeSets} exists for exactly this
 *       purpose.</li>
 * </ol>
 *
 * <h3>Pre-conditions</h3>
 * <ul>
 *   <li>{@code testdata.source=EXCEL} — an {@code ApDeleteVoucherInfo} sheet row plus a matching
 *       {@code MasterTestData} row with {@code testcasename=deleteApVoucher}.</li>
 *   <li>{@code testdata.source=DB} — {@code DbApVoucherDeleteDataSource} claims one live AP
 *       Voucher per {@code TAGGED}/{@code UNTAGGED} combination cell, filtered by {@code apv.group}.</li>
 * </ul>
 */
@ApiFlow(FinanceApiPattern.AP_VOUCHER)
public class DeleteApVoucher extends WebTest {

    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceAPVoucher", operation = AppOperation.DELETE)
    @TestCaseId("FIN-AP-DELETE")
    @Test(description = "Delete an existing AP Voucher from the Finance UI",
            dataProvider = "FinanceApVoucherData", dataProviderClass = MergedFinApVoucherDataSupplier.class)
    public void deleteApVoucher(UserCredential user, MergedFinanceApVoucherData data) {

        Log.info("Delete AP Voucher: {}", data.getExistingVoucherNo());
        Log.info("Release status: {}", data.getReleaseStatus());

        final MergedFinanceApVoucherData testData = data;

        // Null-safe default, same idiom RevertApVoucher/EditApVoucher already use.
        final DocumentSearchStrategy searchStrategy = testData.getSearchStrategy() == null
                ? DocumentSearchStrategy.EXACT_MATCH
                : testData.getSearchStrategy();

        // Cross-step object holders
        final HomePage[]           homeRef    = new HomePage[1];
        final ApVoucherListPage[]  listRef    = new ApVoucherListPage[1];
        final DocumentContext[]    contextRef = new DocumentContext[1];
        final VoucherSnapshot[]    beforeSnapshotRef = new VoucherSnapshot[1];
        final VoucherSnapshot[]    afterSnapshotRef  = new VoucherSnapshot[1];

        Aud2SnapshotDao auditDao = new Aud2SnapshotDao();
        // db.available=no: the BEFORE/AFTER snapshot steps and the DB validation need the database, so they are skipped; the voucher
        // is instead confirmed gone through the API (rootview). The untag/unpost steps use the voucher the API data source created.
        final boolean noDb = !com.gsl.database.DbAvailability.isAvailable();

        // ── Step 1: Login ─────────────────────────────────────────────────────────
        // Was new LoginPage().loginToApplication(...) — a session-unaware fresh login that only
        // works when the browser happens to already be sitting on the actual login page. Confirmed
        // live (run 20260927_190439, FIN-AP-DELETE-01/02): zero business.log entries for either
        // invocation, meaning it failed at this very first login attempt, before ever printing
        // "Logged in as" — because a previous test on this thread had already left the browser
        // logged in elsewhere (no username field to fill there). SessionManager.ensureLoggedIn(u)
        // is the session-reuse-aware helper every OTHER voucher test class (Revert/Edit/GV Delete)
        // already uses — same fix applied to DeleteArVoucher.java in this same pass.
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

        // ── Step 3: Clear Filter + Select Document — was searchByVoucherNo(...), which is
        // exact-match-only with no clear-filter step and no pagination fallback (Round 4's own
        // documented scope limit). Confirmed live (run 20260929_171715, DELETE-01) that this
        // exposure is real: the search step itself found nothing, most likely a stale grid
        // filter hiding the candidate, exactly the pattern RevertApVoucher already avoids via
        // clearFilterAndRefreshList()+selectDocumentFast(...). Now uses the shared common
        // function (VoucherListPageBase.selectDocumentForAction) every other Revert/Delete/Edit
        // test class uses. ─────────────────────────────────────────────────────────
        StepExecutor.step("Select AP Voucher in List Page", () -> {
            listRef[0].selectDocumentForAction(testData.getExistingVoucherNo(), searchStrategy);
            ReportLogger.business("Filter cleared, list refreshed, selected voucher: "
                    + testData.getExistingVoucherNo());
        });

        // ── Step 4: Untag and Unpost if Posted (API-driven — see class Javadoc) ───
        // Untag runs BEFORE the state-changing revert call, not after (a Posted+tagged document
        // cannot be un-posted until its fintag rows are cleared first, same precondition ordering
        // RevertApVoucher/EditApVoucher already enforce).
        //
        // Unpost itself is API-driven, not UI (clarified: UI-driven Revert is only for the
        // standalone RevertApVoucher test case; both Edit and Delete unpost via the API) — was
        // clickUnpost() (VoucherMainGridBase's still-unconfirmed UNPOST_BUTTON placeholder,
        // //TODO-unpost-button, confirmed live at DeleteApVoucher.java:131, exactly the blocker
        // Round 4 flagged as coming next). Mirrors EditApVoucher's own "Unpost if Posted — via API"
        // step exactly — same real (not stub) ApVoucherApiClient.revertApVoucher call, its own
        // independent self-acquired WEBAPI session (ApiSessionFactory.withWebApiSession), empCode
        // resolved from that session's own credential. Being API-only, this never clicks anything
        // in the browser — but Step 3's row selection is NOT guaranteed to survive it regardless:
        // confirmed live (run 20260929_125107) that the checkbox can be lost independent of any UI
        // action at all, same "exact-match filter's own asynchronous settle behavior" DeleteGeneralVoucher's
        // Step 8 already documents — Step 6 below re-selects before clicking Delete to cover this.
        VoucherEditFlowSteps.untagIfTagged(
                testData.getExistingVoucherNo(), testData.getReleaseStatus(), new java.util.ArrayList<>(), testData.getApiVoucherRef());
        VoucherEditFlowSteps.unpostApVoucherViaApi(testData.getExistingVoucherNo(), testData.getReleaseStatus(), testData.getApiVoucherRef());

        // ── Step 5: Resolve Document Context + Capture BEFORE Snapshot ────────────
        if (!noDb) StepExecutor.step("Resolve Document Context + Capture BEFORE Snapshot", () -> {
            contextRef[0] = new VoucherDocumentContextResolver().resolve(testData.getExistingVoucherNo(), VoucherDomain.AP);
            beforeSnapshotRef[0] = new SnapshotService(auditDao).capture(contextRef[0]);
            if (beforeSnapshotRef[0].getTable("finvchmain") == null
                    || beforeSnapshotRef[0].getTable("finvchmain").getRows().isEmpty()) {
                throw new IllegalStateException("[DeleteApVoucher] finvchmain row not found for scheme_docno="
                        + testData.getExistingVoucherNo() + " immediately before delete.");
            }
            ReportLogger.business("Document context resolved and BEFORE snapshot captured for voucher: "
                    + testData.getExistingVoucherNo());
        });

        // ── Step 6: Re-select and Click Delete and Confirm ────────────────────────
        // Was the plain clickDeleteForVoucher(), on the assumption (stated in this class's own
        // Javadoc/comments above) that being API-only, the untag/unpost step "never touches the
        // browser/grid at all" so Step 3's row selection survives untouched. Confirmed WRONG live
        // (run 20260929_125107, DELETE-01/DELETE-02 both hit "Actions→Delete — the Actions dropdown
        // is not open at all" identically, tagged and untagged alike): checkbox selection can be
        // lost independent of any UI action, exactly as DeleteGeneralVoucher's own Step 8 already
        // documents ("confirmed live... can ALSO be lost with no revert at all, apparently via the
        // exact-match filter's own asynchronous settle behavior"). Switched to the retry+reselect
        // overload (VoucherListPageBase.clickDeleteForVoucher(String, DocumentSearchStrategy)) —
        // re-selects immediately before clicking Actions and retries once more if still disabled —
        // same fix, same call shape, DeleteGeneralVoucher already proved live.
        StepExecutor.step("Re-select and Click Delete and Confirm", () -> {
            listRef[0]
                    .clickDeleteForVoucher(testData.getExistingVoucherNo(), searchStrategy)
                    .confirmDelete();
            ReportLogger.pass("Deletion confirmed for voucher: " + testData.getExistingVoucherNo());
        });

        // ── Step 7: Logout (deferred) ─────────────────────────────────────────────
        StepExecutor.step("Logout (deferred)", () ->
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread."));

        // ── Step 8: Capture AFTER Snapshot ────────────────────────────────────────
        if (!noDb) StepExecutor.step("Capture AFTER Snapshot", () -> {
            afterSnapshotRef[0] = new SnapshotService(auditDao).capture(contextRef[0]);
            ReportLogger.business("AFTER snapshot captured for voucher: " + testData.getExistingVoucherNo());
        });

        // ── Step 9: Database Validation ───────────────────────────────────────────
        // Full BEFORE/AFTER snapshot-diff validation (row-delete expectations on finvchmain/
        // finvchdet/finpost/fincosttag) via APVoucherDeleteChangeSets — see class Javadoc for why
        // this supersedes AR's own single-table AuditAssertion.assertAuditDeleted check.
        if (noDb) {
            StepExecutor.step("API Post-Check — voucher no longer exists", () -> {
                com.gsl.testdata.dto.ApiVoucherRef ref = testData.getApiVoucherRef();
                org.testng.Assert.assertNotNull(ref, "[API-PostCheck] no API voucher reference on the row; cannot confirm the delete without the DB.");
                String status = com.gsl.api.core.ApiSessionFactory.withWebApiSession(session ->
                        session.getApVoucherApiClient().fetchDocumentStatus(ref.getVchcode(), ref.getOuCode(), Long.parseLong(session.getUserId())));
                org.testng.Assert.assertNull(status, "[API-PostCheck] voucher " + testData.getExistingVoucherNo()
                        + " is still readable through the API (status='" + status + "') after Delete.");
                ReportLogger.pass("API validated — voucher " + testData.getExistingVoucherNo() + " no longer exists.");
            });
            ReportLogger.business("DB validation skipped — db.available=no (snapshot diff needs the database).");
            return;
        }
        StepExecutor.step("Database Validation", () -> {
            ExpectedChangeSet changeSet = APVoucherDeleteChangeSets.delete(contextRef[0]);

            VoucherSnapshotValidator validator = new VoucherSnapshotValidator();
            validator.validate("AP Voucher Delete — Snapshot Validation — [" + testData.getExistingVoucherNo() + "]",
                    contextRef[0], beforeSnapshotRef[0], afterSnapshotRef[0], changeSet);
            validator.assertAll();
            ReportLogger.pass("DB validated — document rows removed for voucher: " + testData.getExistingVoucherNo());
        });
    }
}
