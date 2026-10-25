package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.enums.VoucherDomain;
import com.gsl.flow.Finance.Operations.Common.PostSaveDialogHandler;
import com.gsl.flow.Finance.Operations.Common.VoucherSaveCtx;
import com.gsl.flow.Finance.Operations.Common.VoucherSaveFlowSteps;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Finance.Operations.ArVoucherAdd;
import com.gsl.pages.Finance.Operations.ArVoucherListPage;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.pages.Finance.Operations.Common.PaymentInfoCheckWarning;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.SubLedgerRowLocator;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SelectDocument;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.Log;
import org.testng.asserts.SoftAssert;

import com.gsl.model.finance.FinPost;
import com.gsl.validation.db.finance.ARVoucherDbValidator;
import com.gsl.validation.db.finance.StateCapture;
import com.gsl.validation.db.finance.VoucherDbContext;
import com.gsl.validation.db.finance.VoucherDbContextBuilder;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Behavior predicates for each AR Voucher scenario type — mirrors {@link ApVoucherPredicateFactory}'s
 * shape for the AR domain (AR has no Advance/Document split, so there is a single positive-create
 * behavior instead of AP's two).
 *
 * <p>Each constant is a {@code Supplier<BiPredicate>} — the {@code Supplier} wrapper
 * ensures lazy evaluation, making predicates safe for parallel test execution
 * (no shared mutable state between threads).
 *
 * <p>{@code BiPredicate<MergedFinanceArVoucherData, ArVoucherAdd>}:
 * <ul>
 *   <li>left  = test data built by {@code ArVoucherTemplateLoader}</li>
 *   <li>right = {@link ArVoucherAdd} page component (injected at runtime by the test method)</li>
 *   <li>returns {@code true} when the voucher was saved (or validation error confirmed)</li>
 * </ul>
 */
public final class ArVoucherPredicateFactory {

    // ─── Positive create behavior ─────────────────────────────────────────────
    //
    // POSITIVE_CREATE_BEHAVIOR replaces 4 former per-scenario constants
    // (SELECT_DOCUMENT/FIFO_SETOFF/REF_SITEWISE_FIFO/DOC_NO_ADJUSTMENT_BEHAVIOR) that were
    // dead code — nothing called execute() for any positive scenario, only
    // createArVoucherNegativeBoundary did, and when finally exercised (per the
    // VoucherScenarioType unification) the old bodies were found NOT to be faithful copies
    // of the live inline flow in CreateArVoucher.java (used the older VoucherDbContext
    // builder directly instead of VoucherDbContextBuilder.capturePreState/capturePostState,
    // wrong PostSaveDialogHandler usage, missing ActualResultsHolder UI-echo fields, and
    // fillSubLedgerRows(data, false) for DOC_NO_ADJUSTMENT where the live method always
    // passes true). This is a verbatim extraction of that live flow instead — the
    // per-scenario branching (SELECT_DOCUMENT/FIFO/REF_SITEWISE_FIFO/NO_ADJUSTMENT) that used
    // to live in 4 separate constants now lives inside fillSubLedgerRows itself, driven by
    // data content, exactly like the still-active negative/boundary behaviors below already
    // do via data.isOnAccount()-style branching.

    /**
     * POSITIVE_CREATE_BEHAVIOR — every AR Voucher positive scenario. Verbatim extraction of
     * {@code CreateArVoucher.createArVoucher}'s steps 3-9.
     */
    public static final Supplier<BiPredicate<MergedFinanceArVoucherData, ArVoucherAdd>>
        POSITIVE_CREATE_BEHAVIOR = () -> (data, page) -> {

            final VoucherSaveCtx ctx = new VoucherSaveCtx();

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

            page.beginFillUi(com.gsl.validation.ui.UiValidationLevel.CONTRACT);
            StepExecutor.step("Fill Sub-Ledger Information", () -> {
                page.fillSubLedgerRows(data, true);
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
            VoucherSaveFlowSteps.handlePostSaveDialogs(ctx, VoucherDomain.AR, data);
            VoucherSaveFlowSteps.verifySaveResult(ctx);

            StepExecutor.step("Capture Post-Save DB State (finpost snapshot)", () ->
                    VoucherDbContextBuilder.capturePostState());

            VoucherSaveFlowSteps.closeVoucherPage(ctx,
                    () -> page.close().clickYes(ArVoucherListPage::new), ArVoucherListPage::new);
            VoucherSaveFlowSteps.deferLogout();
            VoucherSaveFlowSteps.databaseValidation(ctx, docNo ->
                    new ARVoucherDbValidator().validate(VoucherDbContextBuilder.build(docNo, data)));
            VoucherSaveFlowSteps.finalizeAsserts(ctx);

            return ctx.docNo != null;
        };

    // ─── T-03 Negative scenario behavior ──────────────────────────────────────

    /**
     * Shared behavior for ALL AR form-validation negative scenarios (mirror of the AP one).
     *
     * <p>Fills the header (potentially with a null/invalid field), fills sub-ledger rows,
     * clicks Save, then asserts the expected validation error popup appears. Cancels without
     * saving and logs out. The only per-scenario variation is the expected error message,
     * carried in {@link MergedFinanceArVoucherData#getExpectedValidation()}.
     */
    public static final Supplier<BiPredicate<MergedFinanceArVoucherData, ArVoucherAdd>>
        NEGATIVE_FORM_VALIDATION_BEHAVIOR = () -> (data, page) -> {

            StepExecutor.step("Fill Document Information (invalid/incomplete)", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                ReportLogger.info("Filled document header with potentially invalid data");
            });

            StepExecutor.step("Fill Sub-Ledger Rows", () -> {
                page.fillSubLedgerRows(data, false);
                ReportLogger.info("Sub-ledger section complete");
            });

            StepExecutor.step("Click Save — expect: " + data.getExpectedValidation().name(), () -> {
                DocumentPopup popup = page.clickSave();
                String expectedMsg = data.getExpectedValidation().expectedMessage();
                String actualMsg = popup.verifyErrorMessage(expectedMsg);
                ReportLogger.pass("Validation OK — Expected: [" + expectedMsg + "] | Received: [" + actualMsg + "]");
            });

            StepExecutor.step("Cancel (Logout deferred)", () -> {
                page.close().clickYes(ArVoucherListPage::new);
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread.");
            });

            return true;    // test passes when expected error was raised
        };

    // ─── Document scheme exhausted ────────────────────────────────────────────

    /**
     * NEG_DOC_SCHEME_EXHAUSTED_BEHAVIOR — fills a complete valid voucher against a document scheme with no
     * numbers left, clicks Save, asserts the proper rejection message, and proves via a before/after
     * {@code finvchmain} count for the scheme that nothing was created. Same dialog handling as
     * {@link #NEGATIVE_FORM_VALIDATION_BEHAVIOR}; the expected text comes from
     * {@code docscheme.exhausted.expected.message} (see {@code NegativeValidation.DOC_SCHEME_EXHAUSTED}).
     */
    public static final Supplier<BiPredicate<MergedFinanceArVoucherData, ArVoucherAdd>>
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
                    page.fillSubLedgerRows(data, false));

            StepExecutor.step("Click Save — expect doc-scheme-exhausted rejection", () -> {
                DocumentPopup popup = page.clickSave();
                String expectedMsg = data.getExpectedValidation().expectedMessage();
                String actualMsg = popup.verifyErrorMessage(expectedMsg);
                ReportLogger.pass("Validation OK — Expected: [" + expectedMsg + "] | Received: [" + actualMsg + "]");
            });

            StepExecutor.step("Verify no voucher was created under '" + scheme + "'", () -> {
                long after = DocSchemeVoucherCounter.count(scheme);
                ReportLogger.db("Vouchers under '" + scheme + "' after: " + after);
                org.testng.Assert.assertEquals(after, countBefore[0],
                        "A rejected Save must not create a voucher under '" + scheme + "'");
            });

            StepExecutor.step("Cancel (Logout deferred)", () -> {
                page.close().clickYes(ArVoucherListPage::new);
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread.");
            });

            return true;
        };

    // ─── T-03 Boundary "pass-at-limit" behavior ───────────────────────────────

    /**
     * Shared behavior for AR boundary {@code *_VALID} scenarios (value exactly at the limit):
     * the full positive flow — fill → Save → verify saved → DB validation → logout — succeeds
     * when the voucher persists. Counterpart to {@link #NEGATIVE_FORM_VALIDATION_BEHAVIOR}.
     */
    public static final Supplier<BiPredicate<MergedFinanceArVoucherData, ArVoucherAdd>>
        BOUNDARY_PASS_BEHAVIOR = () -> (data, page) -> {

            final SoftAssert softAssert          = new SoftAssert();
            final String[] docNoRef              = new String[1];
            final Map<String, FinPost>[] preRef  = new Map[1];
            final Map<String, FinPost>[] postRef = new Map[1];
            final LocalDateTime[] saveStartTime  = new LocalDateTime[1];

            StepExecutor.step("Fill Document Information (boundary value at limit)", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                ReportLogger.info("Cash Bank selected: " + data.getDocumentInformation().getCashbank());
            });

            StepExecutor.step("Sub-Ledger Rows", () -> {
                page.fillSubLedgerRows(data, false);
                ReportLogger.info("Sub-ledger section complete");
            });

            StepExecutor.step("Save AR Voucher — expect success at limit", () -> {
                List<String> invoiceNos = data.getSelectDocumentList() != null
                        ? data.getSelectDocumentList().stream()
                                .map(SelectDocument::getDocumentNumber)
                                .collect(Collectors.toList())
                        : Collections.emptyList();
                StateCapture stateCapture = new StateCapture();
                preRef[0] = stateCapture.captureInvoiceState(invoiceNos);

                saveStartTime[0] = LocalDateTime.now();
                DocumentPopup savePopup = page.clickSave();
                DocumentMessageBox messageBox = data.isNeedsPaymentInfoWarning()
                        ? savePopup.verifyPaymentInfoCheckWarning(PaymentInfoCheckWarning.AR_VOUCHER)
                                   .clickProceed(PaymentInfoCheckWarning.AR_VOUCHER)
                        : savePopup.waitForMessageBox();
                docNoRef[0] = messageBox.verifyDocumentSave(softAssert).getDocumentNo();
                Log.info("Extracted Document No: {}", docNoRef[0]);

                postRef[0] = stateCapture.captureInvoiceState(invoiceNos);

                ReportLogger.pass("Voucher saved at boundary. Document No: " + docNoRef[0]);
                messageBox.clickOk();
                page.close().clickYes(ArVoucherListPage::new);
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread.");
            });

            StepExecutor.step("Database Validation", () -> {
                new ARVoucherDbValidator().validate(
                        VoucherDbContext.builder()
                                .voucherNo(docNoRef[0])
                                .testData(data)
                                .preState(preRef[0])
                                .postState(postRef[0])
                                .saveStartTime(saveStartTime[0])
                                .build());
                ReportLogger.pass("DB validated. Document No: " + docNoRef[0]);
            });

            StepExecutor.step("Finalize save assertions", softAssert::assertAll);

            return docNoRef[0] != null;
        };

    /**
     * INLINE_VALIDATION_BEHAVIOR — for BOUND_AMOUNT_MIN_INVALID.
     *
     * Two-step flow:
     * 1. Click Save → error dialog appears ("Some problems encountered in the detail section.")
     * 2. Click OK on dialog → hover the three-dots icon on the row → inline tooltip asserts
     *    both "Stop:" and "Amount can not be 0".
     */
    public static final Supplier<BiPredicate<MergedFinanceArVoucherData, ArVoucherAdd>>
        INLINE_VALIDATION_BEHAVIOR = () -> (data, page) -> {

            StepExecutor.step("Fill Document Information", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                ReportLogger.info("Filled document header");
            });

            StepExecutor.step("Fill Sub-Ledger Rows (Amount = 0)", () -> {
                page.fillSubLedgerRows(data, false);
                ReportLogger.info("Sub-ledger section complete");
            });

            StepExecutor.step("Click Save — expect detail-section error dialog", () -> {
                DocumentPopup popup = page.clickSave();
                NegativeValidationSteps.assertDetailSectionDialog(popup, PaymentInfoCheckWarning.AR_VOUCHER);
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
                        SubLedgerRowLocator.dotsButton(slName), PaymentInfoCheckWarning.AR_VOUCHER,
                        NegativeValidation.ZERO_AMOUNT_TOOLTIP_AMOUNT.expectedMessage());
            });

            StepExecutor.step("Cancel (Logout deferred)", () -> {
                page.close().clickYes(ArVoucherListPage::new);
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread.");
            });

            return true;
        };

    private ArVoucherPredicateFactory() {}
}
