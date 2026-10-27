package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.enums.VoucherDomain;
import com.gsl.flow.Finance.Operations.Common.PostSaveDialogHandler;
import com.gsl.flow.Finance.Operations.Common.VoucherSaveCtx;
import com.gsl.flow.Finance.Operations.Common.VoucherSaveFlowSteps;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Finance.Operations.ApVoucherAdd;
import com.gsl.pages.Finance.Operations.ApVoucherListPage;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.pages.Finance.Operations.Common.PaymentInfoCheckWarning;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.SubLedgerRowLocator;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.SelectDocument;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.model.finance.FinPost;
import com.gsl.utils.Log;
import com.gsl.validation.db.finance.APVoucherDbValidator;
import com.gsl.validation.db.finance.AdjustmentTotalsValidator;
import com.gsl.validation.db.finance.ApVoucherDbContext;
import com.gsl.validation.db.finance.StateCapture;
import com.gsl.validation.db.finance.VoucherDbContextBuilder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.testng.asserts.SoftAssert;

/**
 * Behavior predicates for each AP Voucher scenario type.
 *
 * <p>Each constant is a {@code Supplier<BiPredicate>} — the {@code Supplier} wrapper
 * ensures lazy evaluation, making predicates safe for parallel test execution
 * (no shared mutable state between threads).
 *
 * <p>{@code BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>}:
 * <ul>
 *   <li>left  = test data built by {@link ApVoucherTemplateLoader}</li>
 *   <li>right = {@link ApVoucherAdd} page component (injected at runtime by the test method)</li>
 *   <li>returns {@code true} when the voucher was saved (or validation error confirmed)</li>
 * </ul>
 *
 * <h3>Advance vs Document dispatch</h3>
 * <ul>
 *   <li>{@code ADVANCE_*} behaviors call {@code fillSubLedgerRowsForAdvance} — no pre/post state.</li>
 *   <li>{@code DOC_*} behaviors call {@code fillSubLedgerRowsForDocument} — pre/post state captured.</li>
 * </ul>
 */
public final class ApVoucherPredicateFactory {

    // ─── Positive create behaviors ────────────────────────────────────────────
    //
    // ADVANCE_POSITIVE_BEHAVIOR / DOC_POSITIVE_BEHAVIOR replace 6 former per-scenario
    // constants (ADVANCE_NO_TDS/ADVANCE_WITH_TDS/DOC_NO_ADJUSTMENT/DOC_SELECT_DOCUMENT/
    // DOC_FIFO/DOC_REFERENCE_SITEWISE_FIFO_BEHAVIOR) that were dead code — nothing called
    // execute() for any positive scenario, only createApVoucherNegativeBoundary did, and
    // when finally exercised (per the VoucherScenarioType unification) the old bodies were
    // found NOT to be faithful copies of the live inline flow in CreateApVoucher.java (wrong
    // PostSaveDialogHandler usage, wrong DB-validation context, wrong boolean args to
    // fillSubLedgerRowsFor*, missing ActualResultsHolder UI-echo fields). These two are
    // verbatim extractions of that live flow instead — the per-scenario branching that used
    // to live in 6 separate constants now lives inside fillSubLedgerRowsForDocument/Advance
    // itself (driven by data content), exactly like the still-active negative/boundary
    // behaviors below already do via data.isOnAccount().

    /**
     * ADVANCE_POSITIVE_BEHAVIOR — every Advance Payment positive scenario (On Account = Yes;
     * SelectDocument/FIFO never present; TDS &amp; Net Payable filled when
     * {@code data.isTdsProvided()}). Verbatim extraction of
     * {@code CreateApVoucher.createApVoucherAdvancePayment}'s steps 3-9.
     */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        ADVANCE_POSITIVE_BEHAVIOR = () -> (data, page) -> {

            final VoucherSaveCtx ctx = new VoucherSaveCtx();

            StepExecutor.step("Fill Document Information", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                var docInfo = page.getDocumentInformation();
                ReportLogger.business("Doc Scheme  (UI): " + docInfo.getDocScheme());
                ReportLogger.business("Doc Date    (UI): " + docInfo.getDocumentDate());
                ReportLogger.business("Reference   (UI): " + docInfo.getReference());
                ReportLogger.business("Cash / Bank (UI): " + docInfo.getCashBank());
                ReportLogger.business("On Account  (UI): " + docInfo.getOnAccount());
                ReportLogger.business("Remarks     (UI): " + docInfo.getRemarks());
                ActualResultsHolder.get().setUiOwnerSite(docInfo.getOwnerSite());
                ActualResultsHolder.get().setUiCashBank(docInfo.getCashBank());
                ActualResultsHolder.get().setUiOnAccount(docInfo.getOnAccount());
            });

            page.beginFillUi(com.gsl.validation.ui.UiValidationLevel.CONTRACT);
            StepExecutor.step("Fill Sub-Ledger Information", () -> {
                page.fillSubLedgerRowsForAdvance(data, true);
                ReportLogger.business("Sub-ledger section complete: "
                        + data.getSubLedgerInformationList().size() + " rows");
                data.getSubLedgerInformationList().forEach(sl -> {
                    String key = (sl.getId() != null && !sl.getId().isBlank()) ? sl.getId() : sl.getSubLedger();
                    String uiVal = page.readDeductTds(
                            sl.getSubLedger(),
                            sl.getReferenceSite() != null ? sl.getReferenceSite() : "",
                            sl.getId());
                    ActualResultsHolder.get().getUiDeductTdsMap().put(key, uiVal);
                });
                if (data.isTdsProvided()) {
                    page.fillTdsAndNetPayable(data);
                    ReportLogger.business("TDS rows filled: " + data.getTdsAndNetPayableList().size());
                }
            });

            // UI validation of the REAL fill (soft - deferred, never stops Save / DB validation): header
            // and footer before/after fill, each filled row identified by Sub Ledger / Reference Site.
            StepExecutor.step("UI Validation", () ->
                    page.endFillUiDeferred(com.gsl.validation.ui.UiValidationLevel.CONTRACT));

            VoucherSaveFlowSteps.clickSave(ctx, VoucherDbContextBuilder::markSaveStart, page::clickSave);
            VoucherSaveFlowSteps.handlePostSaveDialogs(ctx, VoucherDomain.AP, data);
            VoucherSaveFlowSteps.verifySaveResult(ctx);
            VoucherSaveFlowSteps.closeVoucherPage(ctx,
                    () -> page.close().clickYes(ApVoucherListPage::new), ApVoucherListPage::new);
            VoucherSaveFlowSteps.deferLogout();
            VoucherSaveFlowSteps.databaseValidation(ctx, docNo ->
                    new APVoucherDbValidator().validate(VoucherDbContextBuilder.buildAp(docNo, data)));
            VoucherSaveFlowSteps.finalizeAsserts(ctx);

            return ctx.docNo != null;
        };

    /**
     * DOC_POSITIVE_BEHAVIOR — every Document Payment positive scenario (On Account = No;
     * SelectDocument always present via {@code fillSubLedgerRowsForDocument(data, true)}; FIFO
     * setoff filled when {@code referenceSitewiseFIFOList} is non-empty; TDS never present).
     * Verbatim extraction of {@code CreateApVoucher.createApVoucherDocumentPayment}'s
     * steps 3-9.
     */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        DOC_POSITIVE_BEHAVIOR = () -> (data, page) -> runDocumentPayment(data, page, false);

    /**
     * DOC_MISMATCH_BEHAVIOR — Document Payment scenarios that deliberately adjust a total different
     * from the row's payment amount so Adjustment Entry's OK raises the amount-mismatch
     * Confirmation; {@code data.getAdjustmentMismatchPlan()} scripts how it is answered ("Update
     * payment amount" / "I will adjust more"). Same step sequence as {@link #DOC_POSITIVE_BEHAVIOR}
     * (the plan is consumed inside {@code fillSubLedgerRowsForDocument → fillAdjustments}), except
     * DB validation: the Create-shaped {@code APVoucherDbValidator} compares row amount and per-document
     * tags against the ORIGINAL test data, which by design no longer holds after Update-payment /
     * extra documents — so the invariant is asserted instead via {@link AdjustmentTotalsValidator}
     * (payment == expected, payment == fintag total, tagged documents == selected + added).
     */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        DOC_MISMATCH_BEHAVIOR = () -> (data, page) -> runDocumentPayment(data, page, true);

    private static boolean runDocumentPayment(MergedFinanceApVoucherData data, ApVoucherAdd page,
                                              boolean mismatchScenario) {

            final VoucherSaveCtx ctx = new VoucherSaveCtx();

            StepExecutor.step("Fill Document Information", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                var docInfo = page.getDocumentInformation();
                ReportLogger.business("Doc Scheme  (UI): " + docInfo.getDocScheme());
                ReportLogger.business("Doc Date    (UI): " + docInfo.getDocumentDate());
                ReportLogger.business("Reference   (UI): " + docInfo.getReference());
                ReportLogger.business("Cash / Bank (UI): " + docInfo.getCashBank());
                ReportLogger.business("On Account  (UI): " + docInfo.getOnAccount());
                ReportLogger.business("Remarks     (UI): " + docInfo.getRemarks());
                ActualResultsHolder.get().setUiOwnerSite(docInfo.getOwnerSite());
                ActualResultsHolder.get().setUiCashBank(docInfo.getCashBank());
                ActualResultsHolder.get().setUiOnAccount(docInfo.getOnAccount());
            });

            page.beginFillUi(com.gsl.validation.ui.UiValidationLevel.CONTRACT);
            StepExecutor.step("Fill Sub-Ledger Information", () -> {
                page.fillSubLedgerRowsForDocument(data, true);
                ReportLogger.business("Sub-ledger section complete: "
                        + data.getSubLedgerInformationList().size() + " rows");
            });

            StepExecutor.step("Capture Pre-Save DB State (finpost snapshot)", () ->
                    VoucherDbContextBuilder.capturePreState(data));

            // UI validation of the REAL fill (soft - deferred, never stops Save / DB validation): header
            // and footer before/after fill, each filled row identified by Sub Ledger / Reference Site.
            StepExecutor.step("UI Validation", () ->
                    page.endFillUiDeferred(com.gsl.validation.ui.UiValidationLevel.CONTRACT));

            VoucherSaveFlowSteps.clickSave(ctx, VoucherDbContextBuilder::markSaveStart, page::clickSave);
            VoucherSaveFlowSteps.handlePostSaveDialogs(ctx, VoucherDomain.AP, data);
            VoucherSaveFlowSteps.verifySaveResult(ctx);

            StepExecutor.step("Capture Post-Save DB State (finpost snapshot)", () ->
                    VoucherDbContextBuilder.capturePostState());

            VoucherSaveFlowSteps.closeVoucherPage(ctx,
                    () -> page.close().clickYes(ApVoucherListPage::new), ApVoucherListPage::new);
            VoucherSaveFlowSteps.deferLogout();
            VoucherSaveFlowSteps.databaseValidation(ctx, docNo ->
                    new APVoucherDbValidator().validate(VoucherDbContextBuilder.buildAp(docNo, data)));
            VoucherSaveFlowSteps.finalizeAsserts(ctx);
            VoucherSaveFlowSteps.databaseValidation(ctx, docNo -> {
                if (mismatchScenario) {
                    AdjustmentTotalsValidator.assertPaymentMatchesTags(docNo, data.getExpectedPaymentAmount());
                    if (!hasServerChosenDocuments(data)) {
                        AdjustmentTotalsValidator.assertTaggedDocuments(docNo, expectedTaggedDocuments(data));
                    }
                } else {
                    new APVoucherDbValidator().validate(VoucherDbContextBuilder.buildAp(docNo, data));
                }
            });
            VoucherSaveFlowSteps.finalizeAsserts(ctx);

            return ctx.docNo != null;
    }

    /** True when any adjustment in the scenario lets the server pick documents (FIFO / Ref-Site FIFO),
     *  so the exact tagged-document set cannot be predicted — only the totals are asserted then. */
    private static boolean hasServerChosenDocuments(MergedFinanceApVoucherData data) {
        boolean rowFifo = data.getSubLedgerInformationList() != null && data.getSubLedgerInformationList().stream()
                .anyMatch(r -> r.getAdjustmentType() != null
                        && r.getAdjustmentType() != com.gsl.enums.AdjustmentType.SELECT_DOCUMENT);
        boolean extraFifo = data.getAdjustmentMismatchPlan() != null && data.getAdjustmentMismatchPlan().getSteps().stream()
                .anyMatch(s -> s.getExtra() != null
                        && s.getExtra().getType() != com.gsl.enums.AdjustmentType.SELECT_DOCUMENT);
        return rowFifo || extraFifo;
    }

    /** Documents expected tagged after a mismatch scenario: the selected list plus any documents
     *  added via "I will adjust more" (SELECT_DOCUMENT extras only — FIFO-added documents are
     *  server-chosen, so when any FIFO extra is present the exact-set check is skipped by
     *  returning the selected list only and relying on the totals assertion). */
    private static java.util.Set<String> expectedTaggedDocuments(MergedFinanceApVoucherData data) {
        java.util.Set<String> docs = new java.util.TreeSet<>();
        if (data.getSelectDocumentList() != null) {
            data.getSelectDocumentList().forEach(d -> docs.add(d.getDocumentNumber()));
        }
        if (data.getAdjustmentMismatchPlan() != null) {
            data.getAdjustmentMismatchPlan().getSteps().forEach(s -> {
                if (s.getExtra() != null && s.getExtra().getDocs() != null) {
                    s.getExtra().getDocs().forEach(d -> docs.add(d.getDocumentNumber()));
                }
            });
        }
        return docs;
    }

    // ─── Negative scenario behavior ───────────────────────────────────────────

    /**
     * Shared behavior for ALL form-validation negative scenarios.
     *
     * <p>Fills the header (potentially with a null/invalid field), fills sub-ledger rows,
     * clicks Save, then asserts that the expected validation error popup appears.
     * Cancels without saving and logs out.
     *
     * <p>The only per-scenario variation is the expected error message, carried in
     * {@link MergedFinanceApVoucherData#getExpectedValidation()}.
     * Adding a new negative test requires:
     * <ol>
     *   <li>A new {@link NegativeValidation} constant.</li>
     *   <li>A new template method in {@link ApVoucherTemplateLoader}.</li>
     *   <li>A new {@link ApVoucherScenarioType} constant pointing here.</li>
     *   <li>One new case entry in {@link com.gsl.utils.dataprovider.fixture.ApVoucherFixtureFactory#SHOULD_RUN}.</li>
     * </ol>
     * No changes to the test class, DataSource, DataSupplier, or DB validator.
     */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        NEGATIVE_FORM_VALIDATION_BEHAVIOR = () -> (data, page) -> {

            StepExecutor.step("Fill Document Information (invalid/incomplete)", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                ReportLogger.info("Filled document header with potentially invalid data");
            });

            StepExecutor.step("Fill Sub-Ledger Rows", () -> {
                if (data.isOnAccount()) {
                    page.fillSubLedgerRowsForAdvance(data, false);
                } else {
                    page.fillSubLedgerRowsForDocument(data, false);
                }
                ReportLogger.info("Sub-ledger section complete");
            });

            StepExecutor.step("Click Save — expect: " + data.getExpectedValidation().name(), () -> {
                DocumentPopup popup = page.clickSave();
                String expectedMsg = data.getExpectedValidation().expectedMessage();
                String actualMsg = popup.verifyErrorMessage(expectedMsg);
                ReportLogger.pass("Validation OK — Expected: [" + expectedMsg + "] | Received: [" + actualMsg + "]");
                popup.clickOkOnErrorDialog();
            });

            StepExecutor.step("Cancel (Logout deferred)", () -> {
                page.close().clickYes(ApVoucherListPage::new);
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread.");
            });

            return true;    // test passes when expected error was raised
        };

    // ─── Document scheme exhausted ────────────────────────────────────────────

    /**
     * NEG_DOC_SCHEME_EXHAUSTED_BEHAVIOR — fills a complete valid advance voucher against a document scheme with
     * no numbers left, clicks Save, asserts the proper rejection message, dismisses it, and proves via a
     * before/after {@code finvchmain} count for the scheme that nothing was created. Same dialog handling as
     * {@link #NEGATIVE_FORM_VALIDATION_BEHAVIOR}; the expected text comes from
     * {@code docscheme.exhausted.expected.message} (see {@code NegativeValidation.DOC_SCHEME_EXHAUSTED}).
     */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        NEG_DOC_SCHEME_EXHAUSTED_BEHAVIOR = () -> (data, page) -> {

            final String scheme = data.getDocumentInformation().getVoucherno();
            final long[] countBefore = new long[1];

            StepExecutor.step("Count vouchers under '" + scheme + "' before Save", () -> {
                countBefore[0] = DocSchemeVoucherCounter.count(scheme);
                ReportLogger.db("Vouchers under '" + scheme + "' before: " + countBefore[0]);
            });

            StepExecutor.step("Fill Document Information", () ->
                    page.fillDocumentHeader(data.getDocumentInformation()));

            StepExecutor.step("Fill Sub-Ledger Rows", () ->
                    page.fillSubLedgerRowsForAdvance(data, false));

            StepExecutor.step("Click Save — expect doc-scheme-exhausted rejection", () -> {
                DocumentPopup popup = page.clickSave();
                String expectedMsg = data.getExpectedValidation().expectedMessage();
                String actualMsg = popup.verifyErrorMessage(expectedMsg);
                ReportLogger.pass("Validation OK — Expected: [" + expectedMsg + "] | Received: [" + actualMsg + "]");
                popup.clickOkOnErrorDialog();
            });

            StepExecutor.step("Verify no voucher was created under '" + scheme + "'", () -> {
                long after = DocSchemeVoucherCounter.count(scheme);
                ReportLogger.db("Vouchers under '" + scheme + "' after: " + after);
                org.testng.Assert.assertEquals(after, countBefore[0],
                        "A rejected Save must not create a voucher under '" + scheme + "'");
            });

            StepExecutor.step("Cancel (Logout deferred)", () -> {
                page.close().clickYes(ApVoucherListPage::new);
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread.");
            });

            return true;
        };

    // ─── Negative "blank SL → recover → save" behavior ───────────────────────

    /**
     * NEG_BLANK_SUBLEDGER_RECOVER behavior (T-2):
     * <ol>
     *   <li>Fill document header with valid Cash/Bank.</li>
     *   <li>Skip sub-ledger fill — grid is intentionally empty.</li>
     *   <li>Click Save → assert sub-ledger required error popup → click OK to dismiss.</li>
     *   <li>Fill sub-ledger rows from data (recovery).</li>
     *   <li>Click Save again → expect success → verify document number.</li>
     *   <li>Close and logout.</li>
     * </ol>
     *
     * <p>Proves the form is recoverable after the sub-ledger validation error:
     * a user who dismisses the error can add rows and save successfully without reloading.
     */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        NEG_BLANK_SUBLEDGER_RECOVER_BEHAVIOR = () -> (data, page) -> {

            final String[] docNoRef = new String[1];

            StepExecutor.step("Fill Document Information (valid header, SL grid intentionally empty)", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                ReportLogger.info("Header filled. Sub-ledger grid left empty to trigger validation error.");
            });

            StepExecutor.step("Click Save — expect sub-ledger required error", () -> {
                DocumentPopup popup = page.clickSave();
                String expectedMsg = data.getExpectedValidation().expectedMessage();
                String actualMsg = popup.verifyErrorMessage(expectedMsg);
                ReportLogger.pass("Validation OK — Expected: [" + expectedMsg + "] | Received: [" + actualMsg + "]");
                popup.clickOkOnErrorDialog();
                ReportLogger.info("Error dismissed. Proceeding to fill sub-ledger rows.");
            });

            StepExecutor.step("Fill Sub-Ledger Rows (recovery after error)", () -> {
                page.fillSubLedgerRowsForAdvance(data, false);
                ReportLogger.info("Sub-ledger rows filled after error recovery.");
            });

            StepExecutor.step("Save AP Voucher — expect success after recovery", () -> {
                DocumentPopup savePopup = page.clickSave();
                DocumentMessageBox messageBox = data.isNeedsPaymentInfoWarning()
                        ? savePopup.verifyPaymentInfoCheckWarning(PaymentInfoCheckWarning.AP_VOUCHER)
                                   .clickProceed(PaymentInfoCheckWarning.AP_VOUCHER)
                        : savePopup.waitForMessageBox();
                docNoRef[0] = messageBox.extractDocumentNo();
                Log.info("AP Voucher saved after recovery. Document No: {}", docNoRef[0]);
                ReportLogger.pass("Voucher saved after SL error recovery. Document No: " + docNoRef[0]);
                messageBox.clickOk();
                page.close().clickYes(ApVoucherListPage::new);
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread.");
            });

            return docNoRef[0] != null;
        };

    // ─── Negative "blank cashbank → Add Row triggers popup" behavior ─────────

    /**
     * NEG_BLANK_CASHBANK save-path behavior (FIN-AP-ADD-NEG-01):
     * <ol>
     *   <li>Fill header with Cash/Bank = null (skipped by null-guard).</li>
     *   <li>Click Add Row → app shows "Please select cash/bank ledger first." immediately
     *       (no row is added). Dismiss the popup — this is not the asserted error.</li>
     *   <li>Click Save with an empty sub-ledger grid → app shows "Cash/Bank is required."
     *       (Cash/Bank is validated at header level before sub-ledger content).</li>
     *   <li>Assert and dismiss, then discard and log out.</li>
     * </ol>
     * No sub-ledger JS fill — avoids async GL AJAX and the ExtJS mask that blocks the Save click.
     */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        NEG_BLANK_CASHBANK_SAVE_BEHAVIOR = () -> (data, page) -> {

            StepExecutor.step("Fill Document Information (Cash/Bank intentionally blank)", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                ReportLogger.info("Cash/Bank skipped — null-guard in fillDocumentHeader leaves the field empty.");
            });

            StepExecutor.step("Click Add Sub-Ledger Row — dismiss 'Please select cash/bank' popup", () -> {
                DocumentPopup addRowPopup = page.clickAddSubLedgerRow();
                addRowPopup.clickOkOnErrorDialog();
                ReportLogger.info("Add Row popup dismissed. Sub-ledger grid remains empty — Cash/Bank error expected on Save.");
            });

            StepExecutor.step("Click Save — expect Cash/Bank required error", () -> {
                DocumentPopup popup = page.clickSave();
                String expectedMsg = data.getExpectedValidation().expectedMessage();
                String actualMsg = popup.verifyErrorMessage(expectedMsg);
                ReportLogger.pass("Validation OK — Expected: [" + expectedMsg + "] | Received: [" + actualMsg + "]");
                popup.clickOkOnErrorDialog();
            });

            StepExecutor.step("Cancel (Logout deferred)", () -> {
                page.close().clickYes(ApVoucherListPage::new);
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread.");
            });

            return true;
        };

    /**
     * NEG_BLANK_CASHBANK_ADD_ROW behavior (FIN-AP-ADD-NEG-01, T-1):
     * <ol>
     *   <li>Fill document header with Cash/Bank = null (skipped by the null-guard).</li>
     *   <li>Click Add Row in the sub-ledger grid — the app raises an Error popup:
     *       "Please select cash/bank ledger first."</li>
     *   <li>Assert popup header contains "Error" and body contains the expected message.</li>
     *   <li>Click OK to dismiss, then discard the form and log out.</li>
     * </ol>
     */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        NEG_BLANK_CASHBANK_ADD_ROW_BEHAVIOR = () -> (data, page) -> {

            StepExecutor.step("Fill Document Information (Date, Voucher No, On Account=Yes only)", () -> {
                page.fillHeaderForAddRowValidation(data.getDocumentInformation());
                ReportLogger.info("Minimal header: Date → Voucher No → On Account=Yes "
                        + "(Reference, Cash/Bank, Remarks intentionally skipped)");
            });

            StepExecutor.step("Click Add Row — expect Error popup (Cash/Bank not selected)", () -> {
                DocumentPopup popup = page.clickAddSubLedgerRow();
                String expectedMsg = data.getExpectedValidation().expectedMessage();
                String actualHeader = popup.verifyErrorHeader("Error");
                String actualMsg = popup.verifyErrorMessage(expectedMsg);
                ReportLogger.pass("Popup header: [" + actualHeader + "] | Expected body: ["
                        + expectedMsg + "] | Actual body: [" + actualMsg + "]");
                popup.clickOkOnErrorDialog();
            });

            StepExecutor.step("Cancel (Logout deferred)", () -> {
                page.close().clickYes(ApVoucherListPage::new);
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread.");
            });

            return true;
        };

    /**
     * NEG_BLANK_CASHBANK_ADD_ROW_RECOVER behavior (FIN-AP-ADD-NEG-01, T-2):
     * <ol>
     *   <li>Fill document header with Cash/Bank = null.</li>
     *   <li>Click Add Row → assert Error popup → click OK to dismiss.</li>
     *   <li>Fill Cash/Bank from {@code data.getCashbankForRecovery()}.</li>
     *   <li>Fill sub-ledger rows and save successfully.</li>
     *   <li>Close and log out.</li>
     * </ol>
     */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        NEG_BLANK_CASHBANK_ADD_ROW_RECOVER_BEHAVIOR = () -> (data, page) -> {

            final String[] docNoRef = new String[1];

            StepExecutor.step("Fill Document Information (Cash/Bank left blank)", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                ReportLogger.info("Cash/Bank skipped — will be filled after dismissing the error popup.");
            });

            StepExecutor.step("Click Add Row — expect Error popup, then dismiss", () -> {
                DocumentPopup popup = page.clickAddSubLedgerRow();
                String expectedMsg = data.getExpectedValidation().expectedMessage();
                String actualHeader = popup.verifyErrorHeader("Error");
                String actualMsg = popup.verifyErrorMessage(expectedMsg);
                ReportLogger.pass("Popup header: [" + actualHeader + "] | Expected body: ["
                        + expectedMsg + "] | Actual body: [" + actualMsg + "]");
                popup.clickOkOnErrorDialog();
                ReportLogger.info("Error dismissed. Proceeding to fill Cash/Bank for recovery.");
            });

            StepExecutor.step("Fill Cash/Bank (recovery after error)", () -> {
                page.fillCashBankOnly(data.getCashbankForRecovery());
                ReportLogger.info("Cash/Bank set to: " + data.getCashbankForRecovery());
            });

            StepExecutor.step("Fill Sub-Ledger Rows (recovery)", () -> {
                page.fillSubLedgerRowsForAdvance(data, false);
                ReportLogger.info("Sub-ledger rows filled after Cash/Bank recovery.");
            });

            StepExecutor.step("Save AP Voucher — expect success after recovery", () -> {
                DocumentPopup savePopup = page.clickSave();
                DocumentMessageBox messageBox = data.isNeedsPaymentInfoWarning()
                        ? savePopup.verifyPaymentInfoCheckWarning(PaymentInfoCheckWarning.AP_VOUCHER)
                                   .clickProceed(PaymentInfoCheckWarning.AP_VOUCHER)
                        : savePopup.waitForMessageBox();
                docNoRef[0] = messageBox.extractDocumentNo();
                ReportLogger.pass("Voucher saved after Cash/Bank recovery. Document No: " + docNoRef[0]);
                messageBox.clickOk();
                page.close().clickYes(ApVoucherListPage::new);
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread.");
            });

            return docNoRef[0] != null;
        };

    // ─── Boundary "pass-at-limit" behavior ────────────────────────────────────

    /**
     * Shared behavior for ALL boundary {@code *_VALID} scenarios (value exactly at the limit).
     *
     * <p>Performs the full positive flow — fill header → fill sub-ledger rows → Save →
     * verify the document saved → DB validation → logout — and succeeds when the voucher
     * persists. Branches on {@link MergedFinanceApVoucherData#isOnAccount()} for advance vs
     * document sub-ledger fill (document path captures pre/post invoice state for delta validation).
     *
     * <p>This is the counterpart to {@link #NEGATIVE_FORM_VALIDATION_BEHAVIOR}: a boundary concept
     * is modelled as a pair — {@code *_VALID} routes here (must save), {@code *_INVALID} routes to
     * the negative behavior (must raise the matching {@link NegativeValidation} error).
     */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        BOUNDARY_PASS_BEHAVIOR = () -> (data, page) -> {

            final String[] docNoRef              = new String[1];
            final Map<String, FinPost>[] preRef  = new Map[1];
            final Map<String, FinPost>[] postRef = new Map[1];
            final LocalDateTime[] saveStartTime  = new LocalDateTime[1];

            StepExecutor.step("Fill Document Information (boundary value at limit)", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                ReportLogger.info("Cash Bank: " + data.getDocumentInformation().getCashbank()
                        + " | On Account: " + data.getDocumentInformation().getOnAccount());
            });

            StepExecutor.step("Fill Sub-Ledger Rows", () -> {
                if (data.isOnAccount()) {
                    page.fillSubLedgerRowsForAdvance(data, false);
                } else {
                    page.fillSubLedgerRowsForDocument(data, false);
                }
                ReportLogger.info("Sub-ledger section complete");
            });

            StepExecutor.step("Save AP Voucher — expect success at limit", () -> {
                List<String> invoiceNos = collectInvoiceNos(data);
                StateCapture stateCapture = new StateCapture();
                preRef[0] = data.isOnAccount() ? Collections.emptyMap()
                                               : stateCapture.captureInvoiceState(invoiceNos);

                saveStartTime[0] = LocalDateTime.now();
                DocumentPopup savePopup = page.clickSave();
                DocumentMessageBox messageBox = data.isNeedsPaymentInfoWarning()
                        ? savePopup.verifyPaymentInfoCheckWarning(PaymentInfoCheckWarning.AP_VOUCHER)
                                   .clickProceed(PaymentInfoCheckWarning.AP_VOUCHER)
                        : savePopup.waitForMessageBox();
                docNoRef[0] = messageBox.extractDocumentNo();
                Log.info("AP Voucher (boundary) saved. Document No: {}", docNoRef[0]);

                postRef[0] = data.isOnAccount() ? Collections.emptyMap()
                                                : stateCapture.captureInvoiceState(invoiceNos);
                ReportLogger.pass("Voucher saved at boundary. Document No: " + docNoRef[0]);
                messageBox.clickOk();
                page.close().clickYes(ApVoucherListPage::new);
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread.");
            });

            StepExecutor.step("Database Validation", () -> {
            if (!com.gsl.validation.ValidationSwitches.run(com.gsl.validation.ValidationSwitches.Area.DB)) {
                com.gsl.reports.ExtentLogger.skip("DB validation skipped — validation.db.enabled=no");
                return;
            }
                new APVoucherDbValidator().validate(
                        ApVoucherDbContext.builder()
                                .voucherNo(docNoRef[0])
                                .testData(data)
                                .preState(preRef[0])
                                .postState(postRef[0])
                                .saveStartTime(saveStartTime[0])
                                .build());
                ReportLogger.pass("DB validated. Document No: " + docNoRef[0]);
            });

            return docNoRef[0] != null;
        };

    // ─── Private helper ───────────────────────────────────────────────────────

    /**
     * INLINE_VALIDATION_BEHAVIOR — for BOUND_AMOUNT_MIN_INVALID (AP Voucher).
     *
     * Two-step flow:
     * 1. Click Save → error dialog ("Some problems encountered in the detail section.")
     * 2. Click OK → hover the three-dots icon on the sub-ledger row → assert inline tooltip
     *    contains "Stop:" and "Amount can not be 0".
     */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        INLINE_VALIDATION_BEHAVIOR = () -> (data, page) -> {

            StepExecutor.step("Fill Document Information", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                ReportLogger.info("Filled document header");
            });

            StepExecutor.step("Fill Sub-Ledger Rows (Amount = 0)", () -> {
                if (data.isOnAccount()) {
                    page.fillSubLedgerRowsForAdvance(data, false);
                } else {
                    page.fillSubLedgerRowsForDocument(data, false);
                }
                ReportLogger.info("Sub-ledger section complete");
            });

            StepExecutor.step("Click Save — expect detail-section error dialog", () -> {
                DocumentPopup popup = page.clickSave();
                NegativeValidationSteps.assertDetailSectionDialog(popup, PaymentInfoCheckWarning.AP_VOUCHER);
            });

            StepExecutor.step("Hover row indicator — verify inline tooltip", () -> {
                String slName = data.getSubLedgerInformationList() != null
                        && !data.getSubLedgerInformationList().isEmpty()
                        ? data.getSubLedgerInformationList().get(0).getSubLedger()
                        : null;
                if (slName == null || slName.isBlank()) {
                    throw new IllegalStateException("INLINE_VALIDATION_BEHAVIOR requires at least one sub-ledger row with a name");
                }
                NegativeValidationSteps.assertRowTooltip(
                        SubLedgerRowLocator.dotsButton(slName), PaymentInfoCheckWarning.AP_VOUCHER,
                        NegativeValidation.ZERO_AMOUNT_TOOLTIP_AMOUNT.expectedMessage());
            });

            StepExecutor.step("Cancel (Logout deferred)", () -> {
                page.close().clickYes(ApVoucherListPage::new);
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread.");
            });

            return true;
        };

    /**
     * DOC_INLINE_VALIDATION_BEHAVIOR — for DOC_AMOUNT_NOT_ADJUSTED_INVALID (On Account No).
     *
     * Two-step flow:
     * 1. Click Save → error dialog ("Some problems encountered in the detail section.")
     * 2. Click OK → hover the three-dots icon on the sub-ledger row → assert inline tooltip
     *    contains "Stop:" (bold) and the document-adjustment mandatory message.
     */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        DOC_INLINE_VALIDATION_BEHAVIOR = () -> (data, page) -> {

            StepExecutor.step("Fill Document Information", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                ReportLogger.info("Filled document header — On Account: No");
            });

            StepExecutor.step("Fill Sub-Ledger Rows (No Adjustment, Amount = 0)", () -> {
                page.fillSubLedgerRowsForDocument(data, false);
                ReportLogger.info("Sub-ledger section complete");
            });

            StepExecutor.step("Click Save — expect detail-section error dialog", () -> {
                DocumentPopup popup = page.clickSave();
                NegativeValidationSteps.assertDetailSectionDialog(popup, PaymentInfoCheckWarning.AP_VOUCHER);
            });

            StepExecutor.step("Hover row indicator — verify inline tooltip", () -> {
                String slName = data.getSubLedgerInformationList() != null
                        && !data.getSubLedgerInformationList().isEmpty()
                        ? data.getSubLedgerInformationList().get(0).getSubLedger()
                        : null;
                if (slName == null || slName.isBlank()) {
                    throw new IllegalStateException(
                            "DOC_INLINE_VALIDATION_BEHAVIOR requires at least one sub-ledger row with a name");
                }
                NegativeValidationSteps.assertRowTooltip(
                        SubLedgerRowLocator.dotsButton(slName), PaymentInfoCheckWarning.AP_VOUCHER,
                        NegativeValidation.DOC_NOT_ADJUSTED_TOOLTIP_AMOUNT.expectedMessage());
            });

            StepExecutor.step("Cancel (Logout deferred)", () -> {
                page.close().clickYes(ApVoucherListPage::new);
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread.");
            });

            return true;
        };

    /**
     * DOC_AMOUNT_READ_ONLY_BEHAVIOR — for DOC_AMOUNT_READ_ONLY (On Account No).
     *
     * Flow:
     * 1. Fill header + fill sub-ledger rows (document adjusted via Select Document → amount auto-populated).
     * 2. Click the Amount (Dr) cell → Information dialog confirms the field is read-only.
     * 3. Assert dialog text, click OK, close, logout.
     */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        DOC_AMOUNT_READ_ONLY_BEHAVIOR = () -> (data, page) -> {

            StepExecutor.step("Fill Document Information", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                ReportLogger.info("Filled document header — On Account: No");
            });

            StepExecutor.step("Fill Sub-Ledger Rows + Adjust Document", () -> {
                page.fillSubLedgerRowsForDocument(data, false);
                ReportLogger.info("Sub-ledger rows filled with document adjustment");
            });

            StepExecutor.step("Click Amount cell — expect read-only Information dialog", () -> {
                String slName = data.getSubLedgerInformationList().get(0).getSubLedger();
                String slId   = data.getSubLedgerInformationList().get(0).getId();
                DocumentPopup infoDialog = page.clickAmountCell(slName, slId);
                String expected = NegativeValidation.DOC_AMOUNT_READ_ONLY.expectedMessage();
                String actual   = infoDialog.verifyErrorMessage(expected);
                ReportLogger.pass("Read-only dialog confirmed — [" + actual + "]");
                infoDialog.clickOkOnErrorDialog();
            });

            StepExecutor.step("Cancel (Logout deferred)", () -> {
                page.close().clickYes(ApVoucherListPage::new);
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread.");
            });

            return true;
        };

    private ApVoucherPredicateFactory() {}

    /**
     * Extracts document numbers from the selectDocumentList for pre/post state capture.
     * Returns an empty list when no documents are pre-selected (FIFO, REF_FIFO, NO_ADJ).
     */
    private static List<String> collectInvoiceNos(MergedFinanceApVoucherData data) {
        return data.getSelectDocumentList() != null
                ? data.getSelectDocumentList().stream()
                        .map(SelectDocument::getDocumentNumber)
                        .collect(Collectors.toList())
                : Collections.emptyList();
    }
}
