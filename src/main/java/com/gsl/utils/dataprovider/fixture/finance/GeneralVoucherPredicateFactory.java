package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType;
import com.gsl.enums.VoucherDomain;
import com.gsl.flow.Finance.Operations.Common.PostSaveDialogHandler;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.pages.Finance.Operations.Common.PaymentInfoCheckWarning;
import com.gsl.pages.Finance.Operations.GeneralVoucherAdd;
import com.gsl.pages.Finance.Operations.GeneralVoucherListPage;
import com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherExpenseIncomeDetail;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.validation.db.finance.GeneralVoucherDbValidator;
import org.testng.asserts.SoftAssert;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * Negative/boundary test data + shared behavior for General Voucher — mirrors
 * {@code ApVoucherPredicateFactory}'s shape, scoped to the 3 candidate
 * scenarios the plan calls out (§4):
 * <ol>
 *     <li>{@link #siteAllocationPending()} — Type2 row where the Allocate Sites
 *         Tagging Summary's Pending Amount != 0 (under-allocation).</li>
 *     <li>{@link #subLedgerRequired()} — Type3 row with Sub Ledger deliberately
 *         left blank (exercises {@code GeneralVoucherExpenseIncomeDetail.setSubLedger}'s
 *         blank-guard, which skips the fill and lets the app's own validation fire).</li>
 *     <li>{@link #siteNotAllocated()} — Type2 row whose Site button is clicked but
 *         nothing is added in the popup (empty {@code SiteAllocation} list).</li>
 * </ol>
 *
 * <p>Built directly in Java (not via {@code ExcelGeneralVoucherDataSource}/
 * {@code DbGeneralVoucherDataSource}) using {@code idealdb}-verified GL/Site/Sub-Ledger
 * names — "Advertisement Expenses"+"CNSStore" (Type 2) and "JHIL_01"+"HAPPY SPROUTS-KP Road"
 * (Type 3, {@link #subLedgerRequired()} only — see that method for why its GL specifically
 * needs more than one eligible Sub Ledger, unlike the DB-sourced positive test's Type3 pick).
 *
 * <p><b>UNVERIFIED — see plan's "Verification / Open items":</b> the expected
 * error-message text on each {@link NegativeValidation} constant used here is
 * a best-effort guess, not confirmed against the live UI.
 */
public final class GeneralVoucherPredicateFactory {

    private GeneralVoucherPredicateFactory() {}

    /** idealdb-verified, srctype='B' — see plan §1's DB-verified sample. */
    private static final String CASH_BANK_GL = "HDFC Bank";

    public static MergedFinanceGeneralVoucherData siteAllocationPending() {
        String tcId = "GV_NEG_PENDING";
        DocumentInformation doc = new DocumentInformation(tcId, null, LocalDate.now(), null, CASH_BANK_GL,
                "Negative: Allocate Sites Pending Amount != 0");

        ExpenseIncomeDetail line = new ExpenseIncomeDetail(tcId, "1", "Advertisement Expenses", null, null,
                "Under-allocated site", "No", null, new BigDecimal("900.00"));
        line.setResolvedGlType(GlLineType.TYPE2_SITE_ONLY);

        SiteAllocation partial = new SiteAllocation();
        partial.setTestCaseID(tcId);
        partial.setLineNo("1");
        partial.setSiteName("CNSStore");
        partial.setAmount(new BigDecimal("500.00")); // deliberately less than 900.00 → Pending != 0

        return MergedFinanceGeneralVoucherData.builder()
                .documentInformation(doc)
                .expenseIncomeDetailList(List.of(line))
                .siteAllocationList(List.of(partial))
                .needsPaymentInfoWarning(false)
                .expectedValidation(NegativeValidation.GV_SITE_ALLOCATION_PENDING)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    public static MergedFinanceGeneralVoucherData subLedgerRequired() {
        String tcId = "GV_NEG_SUBLEDGER";
        DocumentInformation doc = new DocumentInformation(tcId, null, LocalDate.now(), null, CASH_BANK_GL,
                "Negative: Type3 row missing Sub Ledger");

        // Sub Ledger deliberately left null — GeneralVoucherExpenseIncomeDetail.setSubLedger's
        // blank-guard skips the fill, so Save should raise the app's own "Sub Ledger required".
        //
        // GL must have MORE THAN ONE eligible Sub Ledger for that to actually happen. Per the
        // app's own auto-populate rule (user-confirmed): a Type3 GL with exactly one eligible Sub
        // Ledger (finsl_gl, ext='N') has it filled automatically, with no validation to trigger.
        // The original GL here — "(sp)Contracter Charges" (glcode 1746) — has exactly one
        // (idealdb: finsl_gl → slcode 36 "WB-INPUT IGST", confirmed live 2026-08-07), the very
        // pair the class-level comment below cites as the *positive*-test Type3 example. Left in
        // place, this scenario could never see "Sub Ledger required" regardless of any UI fix.
        //
        // "JHIL_01" (glcode 1697) has 4 eligible Sub Ledgers (idealdb: WB-INPUT IGST, JHIL_SL_01,
        // Test, AJEHAR SHAIKH) — genuinely ambiguous, so the app cannot auto-pick and must ask.
        // Same Site allocate-list as before: "HAPPY SPROUTS-KP Road" is in fingl_site for 1697 too.
        ExpenseIncomeDetail line = new ExpenseIncomeDetail(tcId, "1", "JHIL_01", null, null,
                "Missing sub ledger", "No", null, new BigDecimal("800.00"));
        line.setResolvedGlType(GlLineType.TYPE3_SITE_AND_SUBLEDGER);

        SiteAllocation alloc = new SiteAllocation();
        alloc.setTestCaseID(tcId);
        alloc.setLineNo("1");
        alloc.setSiteName("HAPPY SPROUTS-KP Road");
        alloc.setAmount(new BigDecimal("800.00"));

        return MergedFinanceGeneralVoucherData.builder()
                .documentInformation(doc)
                .expenseIncomeDetailList(List.of(line))
                .siteAllocationList(List.of(alloc))
                .needsPaymentInfoWarning(false)
                .expectedValidation(NegativeValidation.GV_SUBLEDGER_REQUIRED)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    public static MergedFinanceGeneralVoucherData siteNotAllocated() {
        String tcId = "GV_NEG_NOSITE";
        DocumentInformation doc = new DocumentInformation(tcId, null, LocalDate.now(), null, CASH_BANK_GL,
                "Negative: Type2 row, Site button clicked but nothing allocated");

        ExpenseIncomeDetail line = new ExpenseIncomeDetail(tcId, "1", "Advertisement Expenses", null, null,
                "Unallocated site", "No", null, new BigDecimal("900.00"));
        line.setResolvedGlType(GlLineType.TYPE2_SITE_ONLY);

        return MergedFinanceGeneralVoucherData.builder()
                .documentInformation(doc)
                .expenseIncomeDetailList(List.of(line))
                .siteAllocationList(List.of()) // empty — Site button opens popup, nothing added, OK clicked
                .needsPaymentInfoWarning(false)
                .expectedValidation(NegativeValidation.GV_SITE_NOT_ALLOCATED)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    /**
     * Positive create behavior — fill header + Expense/Income rows + Cash/Bank payment info →
     * Save → handle post-save dialogs → verify → close → logout (deferred) → DB validate →
     * finalize assertions. Verbatim extraction of {@code CreateGeneralVoucher}'s former
     * {@code runVoucherCreationFlow} steps 3–11 (see {@code VoucherScenarioType} unification) —
     * Step 1 (Login) and the lazy-DB-load block stay in the test method, since GV's
     * {@code GetAllSiteForAUser} lookup must run after login/navigation, unlike AP/AR.
     */
    public static final Supplier<BiPredicate<MergedFinanceGeneralVoucherData, GeneralVoucherAdd>>
        POSITIVE_CREATE_BEHAVIOR = () -> (data, page) -> {

            final String[] docNoRef = new String[1];
            final DocumentMessageBox[] msgBoxRef = new DocumentMessageBox[1];
            final DocumentPopup[] popupRef = new DocumentPopup[1];
            final LocalDateTime[] saveStartTimeRef = new LocalDateTime[1];
            final SoftAssert softAssert = new SoftAssert();

            StepExecutor.step("Fill Document Information", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                var docInfo = page.getDocumentInformation();
                ReportLogger.business("Doc Scheme  (UI): " + docInfo.getDocScheme());
                ReportLogger.business("Doc Date    (UI): " + docInfo.getDocumentDate());
                ReportLogger.business("Reference   (UI): " + docInfo.getReference());
                ReportLogger.business("Cash / Bank (UI): " + docInfo.getCashBank());
                ReportLogger.business("Remarks     (UI): " + docInfo.getRemarks());
                ActualResultsHolder.get().setUiOwnerSite(docInfo.getOwnerSite());
                ActualResultsHolder.get().setUiCashBank(docInfo.getCashBank());
            });

            StepExecutor.step("Fill Expense/Income Detail", () -> {
                page.fillExpenseIncomeRows(data);
                ReportLogger.business("Expense/Income Detail section complete: "
                        + data.getExpenseIncomeDetailList().size() + " rows");
            });

            StepExecutor.step("Fill Cash/Bank Payment Information", () ->
                    page.handlePaymentInformation(data.getCashBankPaymentInfo()));

            StepExecutor.step("Click Save", () -> {
                saveStartTimeRef[0] = LocalDateTime.now();
                popupRef[0] = page.clickSave();
            });

            StepExecutor.step("Handle Post-Save Dialogs", () ->
                    msgBoxRef[0] = PostSaveDialogHandler.forDomain(VoucherDomain.GV)
                            .handle(popupRef[0], data));

            StepExecutor.step("Verify Save Result", () -> {
                docNoRef[0] = msgBoxRef[0].verifyDocumentSave(softAssert).getDocumentNo();
                ReportLogger.pass("Voucher saved. Document No: " + docNoRef[0]);
            });

            StepExecutor.step("Close Voucher Page", () -> {
                if (msgBoxRef[0] != null) {
                    msgBoxRef[0].clickOk();
                }
                page.close().clickYes(GeneralVoucherListPage::new);
                ReportLogger.business("Voucher page closed, returned to list");
            });

            StepExecutor.step("Logout (deferred)", () ->
                    ReportLogger.session("Logout deferred — session may be reused by a later test on this thread."));

            StepExecutor.step("Database Validation", () -> {
                new GeneralVoucherDbValidator().validate(data, docNoRef[0], saveStartTimeRef[0]);
                ReportLogger.pass("DB validated. Document No: " + docNoRef[0]);
            });

            StepExecutor.step("Finalize save assertions", softAssert::assertAll);

            return docNoRef[0] != null;
        };

    /**
     * Shared negative-scenario behavior: fill header + rows → Save → assert the
     * expected validation-error popup → dismiss → cancel/logout. Mirrors
     * {@code ApVoucherPredicateFactory.NEGATIVE_FORM_VALIDATION_BEHAVIOR}'s shape.
     */
    public static final Supplier<BiPredicate<MergedFinanceGeneralVoucherData, GeneralVoucherAdd>>
        NEGATIVE_FORM_VALIDATION_BEHAVIOR = () -> (data, page) -> {

            StepExecutor.step("Fill Document Information", () ->
                    page.fillDocumentHeader(data.getDocumentInformation()));

            StepExecutor.step("Fill Expense/Income Detail", () ->
                    page.fillExpenseIncomeRows(data));

            final boolean[] ok = new boolean[1];

            // Two-step flow, matching AP/AR's own INLINE_VALIDATION_BEHAVIOR
            // (Ap/ArVoucherPredicateFactory): the Save dialog itself only ever shows a GENERIC
            // wrapper for a detail-section row error — confirmed live, run 20260807_171711:
            // "Some problems encountered in the detail section. Please view the detail on the left
            // pane tooltip." — never the scenario-specific text. The scenario-specific message
            // (plus the mandatory "Stop:" severity marker) lives in the row's own hover tooltip —
            // read via GeneralVoucherExpenseIncomeDetail.rowIndicatorButton, the same
            // mdi-dots-horizontal + ExtJsQuickTipUtils mechanism AP/AR drive via
            // SubLedgerRowLocator.dotsButton. Both steps are centralized in
            // NegativeValidationSteps — see its javadoc (run 20260809_000447 caught this GV
            // behavior omitting the "Stop:" assertion AP/AR already required).
            StepExecutor.step("Click Save — expect detail-section error dialog", () -> {
                DocumentPopup popup = page.clickSave();
                NegativeValidationSteps.assertDetailSectionDialog(popup, PaymentInfoCheckWarning.GV_VOUCHER);
            });

            StepExecutor.step("Hover row indicator — verify inline tooltip: "
                    + data.getExpectedValidation().name(), () -> {
                // rowIndex 0 — every NEGATIVE_FORM_VALIDATION_BEHAVIOR fixture builds exactly one
                // ExpenseIncomeDetail row (see siteAllocationPending/subLedgerRequired/
                // siteNotAllocated below), same single-row assumption AP/AR's own hover step makes
                // via data.getSubLedgerInformationList().get(0).
                NegativeValidationSteps.assertRowTooltip(
                        GeneralVoucherExpenseIncomeDetail.rowIndicatorButton(0),
                        PaymentInfoCheckWarning.GV_VOUCHER,
                        data.getExpectedValidation().expectedMessage());
                ok[0] = true;
            });

            StepExecutor.step("Cancel (Logout deferred)", () -> {
                page.close().clickYes(GeneralVoucherListPage::new);
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread.");
            });

            return ok[0];
        };

    /**
     * NEG_SUBLEDGER_REQUIRED_BEHAVIOR — for {@link #subLedgerRequired()}.
     *
     * <p>Deliberately NOT {@link #NEGATIVE_FORM_VALIDATION_BEHAVIOR} (Save → generic dialog →
     * hover tooltip): this scenario's validation doesn't happen at Save at all. Confirmed live,
     * run 20260807_171711 — clicking a Type3 row's "Site" button while Sub Ledger is blank makes
     * the app refuse immediately with its own dialog ("No sub-ledger selected" /
     * "Please select sub-ledger."), before Allocate Sites ever opens and long before Save is
     * reached; the earlier attempt to run this scenario through the shared Save-based flow just
     * timed out waiting for a Site-allocation window that was never going to appear.
     *
     * <p>Mirrors {@code ApVoucherPredicateFactory.NEG_BLANK_CASHBANK_ADD_ROW_BEHAVIOR}'s shape —
     * a validation that fires on a specific grid action gets its own dedicated behavior, built on
     * the same primitive that behavior uses ({@code ApVoucherAdd.clickAddSubLedgerRow()} /
     * {@link GeneralVoucherExpenseIncomeDetail#clickSiteButtonExpectingBlock(int)}: click, then
     * return a bare {@link DocumentPopup} rather than assuming which popup opened).
     *
     * <ol>
     *   <li>Fill document header.</li>
     *   <li>Fill the row's GL/Narration/Is Reversal/Amount — everything
     *       {@code fillExpenseIncomeRows} fills too, stopping short of the Site button (Sub
     *       Ledger is deliberately never set).</li>
     *   <li>Click Site — assert the blocking dialog's header and body, click OK.</li>
     *   <li>Discard the form and log out.</li>
     * </ol>
     */
    public static final Supplier<BiPredicate<MergedFinanceGeneralVoucherData, GeneralVoucherAdd>>
        NEG_SUBLEDGER_REQUIRED_BEHAVIOR = () -> (data, page) -> {

            StepExecutor.step("Fill Document Information", () ->
                    page.fillDocumentHeader(data.getDocumentInformation()));

            ExpenseIncomeDetail row = data.getExpenseIncomeDetailList().get(0);
            StepExecutor.step("Fill row basic fields (GL/Narration/Is Reversal/Amount) — "
                    + "Sub Ledger left blank", () ->
                    page.getExpenseIncomeDetail().fillRowBasicFields(0, row));

            StepExecutor.step("Click Site — expect blocking dialog (no Sub Ledger selected)", () -> {
                DocumentPopup popup = page.getExpenseIncomeDetail().clickSiteButtonExpectingBlock(0);
                String expectedMsg = data.getExpectedValidation().expectedMessage();
                String actualHeader = popup.verifyErrorHeader("No sub-ledger selected");
                String actualMsg = popup.verifyErrorMessage(expectedMsg);
                ReportLogger.pass("Popup header: [" + actualHeader + "] | Expected body: ["
                        + expectedMsg + "] | Actual body: [" + actualMsg + "]");
                popup.clickOkOnErrorDialog();
            });

            StepExecutor.step("Cancel (Logout deferred)", () -> {
                page.close().clickYes(GeneralVoucherListPage::new);
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread.");
            });

            return true;
        };
}
