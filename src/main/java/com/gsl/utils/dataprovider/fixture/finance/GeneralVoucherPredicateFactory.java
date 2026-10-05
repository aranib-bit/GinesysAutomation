package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.enums.PaymentSelectionType;
import com.gsl.enums.VoucherDomain;
import com.gsl.flow.Finance.Operations.Common.PostSaveDialogHandler;
import com.gsl.flow.Finance.Operations.Common.VoucherSaveCtx;
import com.gsl.flow.Finance.Operations.Common.VoucherSaveFlowSteps;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Finance.Operations.Common.AllocateSitesPopup;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.pages.Finance.Operations.Common.PaymentInfoCheckWarning;
import com.gsl.pages.Finance.Operations.GeneralVoucherAdd;
import com.gsl.pages.Finance.Operations.GeneralVoucherListPage;
import com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherExpenseIncomeDetail;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.validation.db.finance.GeneralVoucherDbValidator;
import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.ExpectedChangeSet;
import com.gsl.validation.snapshot.SnapshotService;
import com.gsl.validation.snapshot.VoucherSnapshot;
import com.gsl.validation.snapshot.VoucherSnapshotValidator;
import com.gsl.validation.snapshot.finance.Aud2SnapshotDao;
import com.gsl.validation.snapshot.finance.AuditStateSummary;
import com.gsl.validation.snapshot.finance.GeneralVoucherCreateChangeSets;
import com.gsl.validation.snapshot.finance.VoucherDocumentContextResolver;
import com.gsl.validation.snapshot.finance.VoucherPersistenceMapFactory;
import org.testng.asserts.SoftAssert;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * Shared behavior for General Voucher — mirrors {@code ApVoucherPredicateFactory}'s shape:
 * pure UI-interaction/assertion {@code BiPredicate} suppliers, no test-data construction.
 *
 * <p>All test-data building — including the 3 negative scenarios this class's behaviors serve
 * ({@code NEG_SITE_ALLOCATION_PENDING}, {@code NEG_SUBLEDGER_REQUIRED},
 * {@code NEG_SITE_NOT_ALLOCATED}, plus {@code NEG_CASH_POST_DATED}) — lives in
 * {@link GeneralVoucherTemplateLoader}, resolved live via {@link
 * com.gsl.utils.dataprovider.fixture.LiveGvDbRefs} exactly like the positive combination
 * matrix, rather than hardcoded here (see that class's {@code negXxxTemplate} methods).
 */
public final class GeneralVoucherPredicateFactory {

    private GeneralVoucherPredicateFactory() {}

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

            final VoucherSaveCtx ctx = new VoucherSaveCtx();
            final LocalDateTime[] saveStartTimeRef = new LocalDateTime[1];

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
                recordDocumentInformationSummary(data.getDocumentInformation(), docInfo);
            });

            // Item AC (Round 5): "Fill Expense/Income Detail" and "Fill Cash/Bank Payment
            // Information" used to be two sibling stepUnder calls under one group node — each its
            // own step() invocation, so each rendered its own separate Summary box. Collapsed into
            // ONE step so both actions share the same StepSummaryBuffer scope and render as a
            // single consolidated Summary box, with payment info as the last entry.
            // UI validation of the REAL fill (deferred - never stops Save / DB / snapshot validation):
            // the page observes this fill, checking each row once filled and each popup while it is
            // already open; "UI Validation — After Fill" below renders the result.
            page.beginFilledFormUi(data, com.gsl.validation.ui.UiValidationLevel.CONTRACT);

            StepExecutor.step("Fill Expense/Income Detail", () -> {
                page.fillExpenseIncomeRows(data);
                ReportLogger.business("Expense/Income Detail section complete: "
                        + data.getExpenseIncomeDetailList().size() + " rows");
                recordExpenseIncomeDetailSummary(data, page);

                // Round 6: every grid row that carries a payment fills its own Payment Window.
                page.handleRowPayments(data);
                com.gsl.reports.StepSummaryBuffer.record(buildPaymentInformationSummary(data));

                // Folded in per explicit request — this must not render as its own separate
                // step/section; it belongs inside "Fill Expense/Income Detail"'s own consolidated
                // Summary, right before Save (checking "is audit even on" reads naturally as a
                // last check right before the state-changing action). Item AH: still deposits the
                // resolved state into ActualResultsHolder so "Actual Results" can render its own
                // compact audit-summary line without a second DB round-trip.
                AuditStateSummary auditState =
                        AuditStateSummary.resolveFor(VoucherPersistenceMapFactory.build(VoucherDomain.GV));
                com.gsl.reports.StepSummaryBuffer.record(auditState.toSummaryLine("Audit State — Before Save"));
                ActualResultsHolder.get().setAuditState(auditState);

                // Explicit request: the Summary must appear AFTER the per-line "Line N" detail
                // nodes fillExpenseIncomeRows just created above, not before them. The ordinary
                // end-of-step auto-render can't do this — the ExtentReports Spark template always
                // renders a node's own log entries (including the auto-summary) ABOVE its child
                // nodes, regardless of call order. Rendering explicitly here, as a trailing CHILD
                // node instead of an own-log entry, is what actually places it last — see
                // StepExecutor.renderSummaryAsChildNode's own Javadoc.
                // Item L (Round 9): pass the BARE step name — buildSummaryHtml already appends
                // " — Summary" itself (same as every other renderStepSummary/
                // renderSummaryAsChildNode caller); passing an already-suffixed heading here
                // produced a doubled "Fill Expense/Income Detail — Summary — Summary" in the box.
                StepExecutor.renderSummaryAsChildNode("Fill Expense/Income Detail");
            });

            StepExecutor.step("UI Validation — After Fill", page::endFilledFormUiDeferred);

            // Fails fast, naming the still-open Net Amount, instead of clicking a Save button
            // ExtJS has silently greyed out and cascading into an opaque messagebox-1001-msg
            // timeout 4 wait-layers later (confirmed live, reports/runs/20260913_133337: Save
            // fired while visibly disabled, voucher unbalanced — Dr != Cr). Save is an ExtJS
            // anchor greyed via aria-disabled, not the plain HTML disabled attribute, so
            // WaitType.CLICKABLE alone never catches this — see VoucherFooterBase
            // #waitUntilSaveEnabled's Javadoc.
            StepExecutor.step("Verify balanced before Save", () -> {
                boolean saveEnabled = page.waitUntilSaveEnabled(
                        (int) com.gsl.config.ConfigFactory.getWaitConfig().interactionWaitSeconds());
                if (!saveEnabled) {
                    // Best-effort diagnostic only (GeneralVoucherDocumentInformation.getNetAmount's
                    // own Javadoc flags its locator as unverified) — a failure reading it must not
                    // swallow the real, already-confirmed "Save disabled" finding below.
                    String netAmount;
                    try {
                        netAmount = page.getDocumentInformation().getNetAmount();
                    } catch (RuntimeException e) {
                        netAmount = "<unreadable: " + e.getClass().getSimpleName() + ">";
                    }
                    throw new IllegalStateException(
                            "[GV Add] Save is still disabled after filling all Expense/Income rows and "
                            + "Payment Information — voucher does not balance. Net Amount reads: '"
                            + netAmount + "'. Check the row/site-allocation test data for a missing or "
                            + "short split line rather than clicking Save regardless.");
                }
            });

            VoucherSaveFlowSteps.clickSave(ctx, () -> saveStartTimeRef[0] = LocalDateTime.now(), () -> {
                DocumentPopup popup = page.clickSave();
                com.gsl.reports.StepSummaryBuffer.record("Save clicked at <b>"
                        + saveStartTimeRef[0].toLocalTime().withNano(0) + "</b>");
                return popup;
            });

            VoucherSaveFlowSteps.handlePostSaveDialogs(ctx, VoucherDomain.GV, data, () ->
                    com.gsl.reports.StepSummaryBuffer.record(data.isNeedsPaymentInfoWarning()
                            ? "Post-save dialogs seen: payment-info-check warning (acknowledged) --&gt; message box."
                            : "Post-save dialogs seen: none (no payment-info warning expected) --&gt; message box."));

            VoucherSaveFlowSteps.verifySaveResult(ctx);

            VoucherSaveFlowSteps.closeVoucherPage(ctx,
                    () -> page.close().clickYes(GeneralVoucherListPage::new), GeneralVoucherListPage::new,
                    () -> com.gsl.reports.StepSummaryBuffer.record(
                            "Voucher page closed — Document No <b>" + ctx.docNo + "</b> returned to list"));

            VoucherSaveFlowSteps.deferLogout();

            VoucherSaveFlowSteps.databaseValidation(ctx, docNo -> {
                new GeneralVoucherDbValidator().validate(data, docNo, saveStartTimeRef[0]);
                int detailRows = data.getExpenseIncomeDetailList() == null ? 0 : data.getExpenseIncomeDetailList().size();
                int siteRows = data.getSiteApplicableRows().size();
                com.gsl.reports.StepSummaryBuffer.record("DB validation: Levels 1-4 passed — "
                        + detailRows + " detail row(s), " + siteRows + " site-applicable row(s) — Document No <b>"
                        + docNo + "</b>");
            });

            // Runs ALONGSIDE (not replacing) the hand-written Level 1-4 checks above — brings
            // Create's DB validation up to the same snapshot-diff-engine coverage Edit already has
            // (generic unexpected-row detection + aud2_* audit-trail INSERT checks), which the
            // Level 1-4 checklist has never covered. See GeneralVoucherCreateChangeSets' Javadoc
            // for why the two layers are complementary rather than redundant. No BEFORE snapshot is
            // resolved from the DB — the document didn't exist before Save, so an empty
            // VoucherSnapshot stands in for it; DiffEngine treats a missing table as zero rows, so
            // every row genuinely present AFTER is, by construction, an INSERT.
            // softStep: a failure here (or in "Database Validation" above, or in the UI contract)
            // must not hide the other areas — everything is reported together by finalizeAsserts.
            VoucherSaveFlowSteps.softStep(ctx, "Database Snapshot Validation",
                    com.gsl.validation.CombinedValidationError.Kind.SNAPSHOT, () -> {
                DocumentContext context = new VoucherDocumentContextResolver().resolve(ctx.docNo, VoucherDomain.GV);
                VoucherSnapshot before = new VoucherSnapshot(Collections.emptyMap());
                VoucherSnapshot after = new SnapshotService(new Aud2SnapshotDao()).capture(context);
                ExpectedChangeSet changeSet = GeneralVoucherCreateChangeSets.build(context, data);

                VoucherSnapshotValidator validator = new VoucherSnapshotValidator();
                validator.validate("General Voucher Create — Snapshot Validation — [" + ctx.docNo + "]",
                        context, before, after, changeSet);
                validator.assertAll();
                ReportLogger.pass("Snapshot validation complete. Document No: " + ctx.docNo);
                recordSnapshotDiffSummary(after);
            });

            VoucherSaveFlowSteps.finalizeAsserts(ctx, () ->
                    com.gsl.reports.StepSummaryBuffer.record(
                            "All soft assertions passed — Document No <b>" + ctx.docNo + "</b> finalized"));

            return ctx.docNo != null;
        };

    /**
     * Point 9 summary helper — "Fill Document Information": one tick/cross line per field
     * (matches the report's established "Level 1 — finvchmain field checks" house style —
     * {@code FinVchMainBaseValidator#buildLevel1Summary} — rather than a wide multi-column table,
     * which wrapped illegibly at the report's fixed panel width), comparing what was originally
     * supplied (test data) against what the UI reads back after entry. Owner Site has no
     * test-data input counterpart (it's resolved live from the logged-in user's site), so it's
     * shown informationally (always tick) rather than compared.
     */
    private static void recordDocumentInformationSummary(
            com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation input,
            com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherDocumentInformation uiReadBack) {
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String expectedDate = input.getDocumentdate() == null ? null : input.getDocumentdate().format(fmt);

        recordFieldCheck("Document Date", expectedDate, uiReadBack.getDocumentDate());
        recordFieldCheck("Reference", input.getReference(), uiReadBack.getReference());
        recordFieldCheck("Cash/Bank", input.getCashbank(), uiReadBack.getCashBank());
        recordFieldCheck("Remarks", input.getRemarks(), uiReadBack.getRemarks());
        com.gsl.reports.StepSummaryBuffer.record("Owner Site (UI): <b>" + nullSafe(uiReadBack.getOwnerSite())
                + "</b> — resolved live, no test-data input");
    }

    /** One "label — filled with X, validated in UI as Y" line, tick when they match (or there's
     *  nothing to compare), cross when they genuinely differ — see
     *  {@link #recordDocumentInformationSummary}. Point 7: records nothing when the test-data
     *  input was blank — a row with nothing to say carries no information (mirrors
     *  {@code FinVchMainBaseValidator.buildLevel1Summary}'s own skip-if-blank precedent for the
     *  refno line). Point C: reworded from "Input: X | UI: Y" to an explicit filled-then-validated
     *  framing, and both sides bolded to match the rest of the report's summary boxes. */
    private static void recordFieldCheck(String label, String expected, String actual) {
        if (expected == null || expected.isBlank()) return;
        boolean match = expected.equalsIgnoreCase(actual == null ? null : actual.trim());
        com.gsl.reports.StepSummaryBuffer.record(label + " — filled with <b>" + nullSafe(expected)
                + "</b>, validated in UI as <b>" + nullSafe(actual) + "</b>", match);
    }

    private static String nullSafe(String value) {
        return (value == null || value.isBlank()) ? "&mdash;" : value;
    }

    /**
     * Point 9 summary helper — "Fill Expense/Income Detail": one line per row filled, naming GL,
     * resolved ledger type, amount, allocated sites (if any), and Sub Ledger (if any) — the
     * currently least-visible section of the flow (plan's own finding). One line per row (not a
     * multi-column table) for the same readability reason as
     * {@link #recordDocumentInformationSummary}.
     */
    private static void recordExpenseIncomeDetailSummary(MergedFinanceGeneralVoucherData data,
            GeneralVoucherAdd page) {
        List<ExpenseIncomeDetail> rows = data.getExpenseIncomeDetailList();
        if (rows == null || rows.isEmpty()) return;

        // Item N: each row's own "Verify Row — UI read-back" rollup, folded into this ONE
        // consolidated per-line summary instead of only being visible in each row's own
        // (separately-rendered) drill-down box — collected during fillExpenseIncomeRows.
        java.util.Map<String, String> verifications = page.getExpenseIncomeDetail().getLastRowVerificationSummaries();

        for (ExpenseIncomeDetail row : rows) {
            java.math.BigDecimal amount = row.getExpenseDr() != null ? row.getExpenseDr() : row.getIncomeCr();
            String sites = data.getSiteAllocationsForLine(row.getLineNo()).stream()
                    .map(sa -> sa.getSiteName() + " (" + sa.getAmount() + ")")
                    .reduce((a, b) -> a + ", " + b).orElse("&mdash;");
            String lineKey = nullSafe(row.getLineNo());
            String verification = verifications.get(row.getLineNo());
            // Values bolded, matching the bold-key-values convention used everywhere else in this
            // report (e.g. recordDocumentInformationSummary's "filled with <b>X</b>..." lines) —
            // previously only the "Line N" label itself was bold, not the values after it.
            com.gsl.reports.StepSummaryBuffer.record("<b>Line " + lineKey + "</b>"
                    + " — GL: <b>" + nullSafe(row.getGeneralLedger()) + "</b>"
                    + " | Type: <b>" + (row.getResolvedGlType() == null ? "&mdash;" : row.getResolvedGlType().label()) + "</b>"
                    + " | Amount: <b>" + (amount == null ? "&mdash;" : amount) + "</b>"
                    + " | Sites: <b>" + sites + "</b>"
                    + " | Sub Ledger: <b>" + nullSafe(row.getSubLedger()) + "</b>"
                    // Was missing entirely — "Verify Row" already shows this per-row, but the
                    // consolidated summary at the top never restated it.
                    + " | Ledger Narration: <b>" + nullSafe(row.getLedgerNarration()) + "</b>"
                    + " | UI Validation: <b>" + (verification == null ? "&mdash;" : verification) + "</b>");
        }
    }

    /**
     * Point 9 summary helper — "Fill Cash/Bank Payment Information": the CASH-mode happy path
     * previously left this section as an empty accordion (plan's flagged worst existing gap) —
     * now always states the payment mode, plus instrument details when applicable.
     */
    private static String buildPaymentInformationSummary(MergedFinanceGeneralVoucherData data) {
        List<com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInformation> payments = data.allPayments();
        // Single payment (every pre-Round-6 voucher): the original one-liner.
        if (payments.size() <= 1 && data.getExpenseIncomeDetailList().size() <= 1) {
            return paymentLine(payments.isEmpty() ? null : payments.get(0));
        }
        // Several rows: one line per grid row, stating what that row carries (or that it has none).
        StringBuilder sb = new StringBuilder();
        for (var row : data.getExpenseIncomeDetailList()) {
            if (sb.length() > 0) sb.append("<br>");
            var info = data.paymentForLine(row.getLineNo());
            sb.append("Row ").append(row.getLineNo()).append(" — ")
              .append(info == null ? "no payment information" : paymentLine(info));
        }
        return sb.toString();
    }

    private static String paymentLine(com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInformation info) {
        // Point D fix: a real, explicit CASH payment mode (non-null info) previously fell through
        // to the generic branch below and lost the "no instrument entry required" clarification —
        // only the info==null/paymentMode==null case caught it. Mirrors the exact CASH check
        // GeneralVoucherExpenseIncomeDetail.handlePaymentInformation already uses for the same
        // distinction.
        if (info == null || info.getPaymentMode() == null || info.getPaymentMode() == PaymentSelectionType.CASH) {
            return "Payment mode: <b>CASH</b> — no instrument entry required";
        }
        return "Payment mode: <b>" + info.getPaymentMode() + "</b>"
                + (info.getInstrumentNo() == null ? "" : " | Instrument No: <b>" + info.getInstrumentNo() + "</b>")
                + (info.getInstrumentDate() == null ? "" : " | Instrument Date: <b>" + info.getInstrumentDate() + "</b>")
                + (info.getDrawnOn() == null ? "" : " | Drawn On: <b>" + info.getDrawnOn() + "</b>");
    }

    /**
     * Point 9 summary helper — "Database Snapshot Validation": names the tables actually captured
     * in the AFTER snapshot and their row counts, so the step states what was diffed instead of
     * just a generic "complete" line. {@code VoucherSnapshotValidator.validate(...)} doesn't
     * itself return the {@link com.gsl.validation.snapshot.DiffResult} to the caller, so this
     * reads counts straight off the already-captured {@code after} snapshot in scope — no new
     * instrumentation needed.
     */
    private static void recordSnapshotDiffSummary(VoucherSnapshot after) {
        if (after == null || after.getTables() == null || after.getTables().isEmpty()) {
            com.gsl.reports.StepSummaryBuffer.record("Snapshot validation complete — no tables captured");
            return;
        }
        for (var entry : after.getTables().entrySet()) {
            com.gsl.reports.StepSummaryBuffer.record(
                    "Snapshot diffed — " + entry.getKey() + ": " + entry.getValue().rowCount() + " row(s)");
        }
    }

    /**
     * Shared negative-scenario behavior: fill header + rows → Save → assert the
     * expected validation-error popup → dismiss → cancel/logout. Mirrors
     * {@code ApVoucherPredicateFactory.NEGATIVE_FORM_VALIDATION_BEHAVIOR}'s shape.
     */
    public static final Supplier<BiPredicate<MergedFinanceGeneralVoucherData, GeneralVoucherAdd>>
        NEGATIVE_FORM_VALIDATION_BEHAVIOR = () -> (data, page) -> {

            StepExecutor.step("Fill Document Information", () ->
                    page.fillDocumentHeader(data.getDocumentInformation()));

            // expectSiteAllocationComplete=false: NEG_SITE_ALLOCATION_PENDING/NEG_SITE_NOT_ALLOCATED
            // deliberately leave the row's site allocation incomplete so Save raises its own
            // validation below — see GeneralVoucherExpenseIncomeDetail.allocateSites's Javadoc.
            StepExecutor.step("Fill Expense/Income Detail", () ->
                    page.fillExpenseIncomeRows(data, false));

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
                // ExpenseIncomeDetail row (see GeneralVoucherTemplateLoader's negSiteAllocationPendingTemplate/
                // negSiteNotAllocatedTemplate), same single-row assumption AP/AR's own hover step makes
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
     * NEG_SUBLEDGER_REQUIRED_BEHAVIOR — for {@link GeneralVoucherTemplateLoader#negSubledgerRequiredTemplate}.
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

    /**
     * NEG_CASH_POST_DATED_BEHAVIOR — for {@link GeneralVoucherTemplateLoader#negCashPostDatedTemplate}
     * (plan §Round 21).
     *
     * <p>Reaches Save the same way {@link #POSITIVE_CREATE_BEHAVIOR} does (fill header + row +
     * Cash/Bank payment info — a documented no-op for a Cash-type ledger, see
     * {@code GeneralVoucherAdd#handlePaymentInformation}'s Javadoc) — but the app rejects the Save
     * itself rather than returning a success dialog, so this asserts the rejection popup directly
     * off {@code clickSave()}'s own return value, mirroring
     * {@link #NEG_SUBLEDGER_REQUIRED_BEHAVIOR}'s dialog-assertion shape rather than
     * {@code POSITIVE_CREATE_BEHAVIOR}'s success path.
     */
    public static final Supplier<BiPredicate<MergedFinanceGeneralVoucherData, GeneralVoucherAdd>>
        NEG_CASH_POST_DATED_BEHAVIOR = () -> (data, page) -> {

            StepExecutor.step("Fill Document Information", () ->
                    page.fillDocumentHeader(data.getDocumentInformation()));

            StepExecutor.step("Fill Expense/Income Detail", () ->
                    page.fillExpenseIncomeRows(data));

            StepExecutor.step("Fill Cash/Bank Payment Information", () ->
                    page.handlePaymentInformation(data.getCashBankPaymentInfo(), data.isCashBankPaymentDebit()));

            StepExecutor.step("Click Save — expect Cash+post-dated rejection dialog", () -> {
                DocumentPopup popup = page.clickSave();
                String expectedMsg = data.getExpectedValidation().expectedMessage();
                String actualHeader = popup.verifyErrorHeader("Error");
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

    /**
     * NEG_DOC_SCHEME_EXHAUSTED_BEHAVIOR — fills a complete valid voucher against a document scheme that has
     * no numbers left, clicks Save, asserts the proper rejection message, and proves via a before/after
     * {@code finvchmain} count for the scheme that nothing was created. Same dialog assertion shape as
     * {@link #NEG_CASH_POST_DATED_BEHAVIOR}; the expected text comes from
     * {@code docscheme.exhausted.expected.message} (see {@code NegativeValidation.DOC_SCHEME_EXHAUSTED}).
     */
    public static final Supplier<BiPredicate<MergedFinanceGeneralVoucherData, GeneralVoucherAdd>>
        NEG_DOC_SCHEME_EXHAUSTED_BEHAVIOR = () -> (data, page) -> {

            final String scheme = data.getDocumentInformation().getVoucherno();
            final long[] countBefore = new long[1];

            StepExecutor.step("Count vouchers under '" + scheme + "' before Save", () -> {
                countBefore[0] = DocSchemeVoucherCounter.count(scheme);
                ReportLogger.db("Vouchers under '" + scheme + "' before: " + countBefore[0]);
            });

            StepExecutor.step("Fill Document Information", () ->
                    page.fillDocumentHeader(data.getDocumentInformation()));

            StepExecutor.step("Fill Expense/Income Detail", () ->
                    page.fillExpenseIncomeRows(data));

            StepExecutor.step("Fill Cash/Bank Payment Information", () ->
                    page.handlePaymentInformation(data.getCashBankPaymentInfo(), data.isCashBankPaymentDebit()));

            StepExecutor.step("Click Save — expect doc-scheme-exhausted rejection", () -> {
                DocumentPopup popup = page.clickSave();
                String expectedMsg = data.getExpectedValidation().expectedMessage();
                String actualHeader = popup.verifyErrorHeader("Error");
                String actualMsg = popup.verifyErrorMessage(expectedMsg);
                ReportLogger.pass("Popup header: [" + actualHeader + "] | Expected body: ["
                        + expectedMsg + "] | Actual body: [" + actualMsg + "]");
                popup.clickOkOnErrorDialog();
            });

            StepExecutor.step("Verify no voucher was created under '" + scheme + "'", () -> {
                long after = DocSchemeVoucherCounter.count(scheme);
                ReportLogger.db("Vouchers under '" + scheme + "' after: " + after);
                org.testng.Assert.assertEquals(after, countBefore[0],
                        "A rejected Save must not create a voucher under '" + scheme + "'");
            });

            StepExecutor.step("Cancel (Logout deferred)", () -> {
                page.close().clickYes(GeneralVoucherListPage::new);
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread.");
            });

            return true;
        };

    /**
     * NEG_SITE_ALLOCATION_CROSS_OU_BEHAVIOR — for
     * {@link GeneralVoucherTemplateLoader#negSiteAllocationCrossOuTemplate} (Round 16).
     *
     * <p>Deliberately NOT {@link #NEGATIVE_FORM_VALIDATION_BEHAVIOR}/{@link
     * #NEG_CASH_POST_DATED_BEHAVIOR}'s dialog-assertion shape — this scenario asserts row
     * ABSENCE inside the Allocate Sites picker itself ({@link AllocateSitesPopup#assertSiteNotFound}),
     * not a Save-time or Site-button-click dialog. Mirrors {@link #NEG_SUBLEDGER_REQUIRED_BEHAVIOR}'s
     * overall shape (fill header + row basics, click Site, assert, discard) since both scenarios
     * stop short of ever completing a real Site allocation.
     *
     * <ol>
     *   <li>Fill document header.</li>
     *   <li>Fill the row's GL/Narration/Is Reversal/Amount — stopping short of allocating a Site.</li>
     *   <li>Click Site — open Allocate Sites normally (Type2 has no Sub-Ledger gate, unlike
     *       {@code NEG_SUBLEDGER_REQUIRED}'s Type3 row), search for the resolved cross-OU site,
     *       assert NO row appears, close the picker without selecting anything.</li>
     *   <li>Close the (still site-less) Allocate Sites window, discard the form, and log out.</li>
     * </ol>
     */
    public static final Supplier<BiPredicate<MergedFinanceGeneralVoucherData, GeneralVoucherAdd>>
        NEG_SITE_ALLOCATION_CROSS_OU_BEHAVIOR = () -> (data, page) -> {

            StepExecutor.step("Fill Document Information", () ->
                    page.fillDocumentHeader(data.getDocumentInformation()));

            ExpenseIncomeDetail row = data.getExpenseIncomeDetailList().get(0);
            StepExecutor.step("Fill row basic fields (GL/Narration/Is Reversal/Amount) — "
                    + "Site left unallocated", () ->
                    page.getExpenseIncomeDetail().fillRowBasicFields(0, row));

            StepExecutor.step("Click Site — search for a cross-OU site, expect NO results", () -> {
                String crossOuSiteName = data.getSiteAllocationList().get(0).getSiteName();
                AllocateSitesPopup popup = page.getExpenseIncomeDetail().clickSiteButton(0);
                popup.assertSiteNotFound(crossOuSiteName);
                popup.clickCancel();
                ReportLogger.pass("Allocate Sites picker correctly returned NO row for cross-OU site '"
                        + crossOuSiteName + "'.");
            });

            StepExecutor.step("Cancel (Logout deferred)", () -> {
                page.close().clickYes(GeneralVoucherListPage::new);
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread.");
            });

            return true;
        };
}
