package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.enums.VoucherDomain;
import com.gsl.flow.Finance.Operations.Common.PostSaveDialogHandler;
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
 * Behavior predicates for each AR Voucher scenario type.
 *
 * Each constant is a {@code Supplier<BiPredicate>} — the Supplier provides lazy
 * evaluation, making predicates safe for parallel test execution (no shared state).
 *
 * {@code BiPredicate<MergedFinanceArVoucherData, ArVoucherAdd>}:
 *   left  = test data built by ArVoucherTemplateLoader
 *   right = ArVoucherAdd page component (injected at runtime by the test method)
 *   returns true if the voucher was saved successfully
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

            final String[] docNoRef = new String[1];
            final DocumentMessageBox[] msgBoxRef = new DocumentMessageBox[1];
            final DocumentPopup[] popupRef = new DocumentPopup[1];
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

            StepExecutor.step("Fill Sub-Ledger Information", () -> {
                page.fillSubLedgerRows(data, true);
                ReportLogger.business("Sub-ledger section complete: "
                        + data.getSubLedgerInformationList().size() + " rows");
            });

            StepExecutor.step("Capture Pre-Save DB State (finpost snapshot)", () ->
                    VoucherDbContextBuilder.capturePreState(data));

            StepExecutor.step("Click Save", () -> {
                VoucherDbContextBuilder.markSaveStart();
                popupRef[0] = page.clickSave();
            });

            StepExecutor.step("Handle Post-Save Dialogs", () ->
                    msgBoxRef[0] = PostSaveDialogHandler.forDomain(VoucherDomain.AR)
                            .handle(popupRef[0], data));

            StepExecutor.step("Verify Save Result", () -> {
                docNoRef[0] = msgBoxRef[0].verifyDocumentSave(softAssert).getDocumentNo();
                Log.info("Extracted Document No: {}", docNoRef[0]);
                ReportLogger.pass("Voucher saved. Document No: " + docNoRef[0]);
            });

            StepExecutor.step("Capture Post-Save DB State (finpost snapshot)", () ->
                    VoucherDbContextBuilder.capturePostState());

            StepExecutor.step("Close Voucher Page", () -> {
                if (msgBoxRef[0] != null) {
                    msgBoxRef[0].clickOk();
                }
                page.close().clickYes(ArVoucherListPage::new);
                ReportLogger.business("Voucher page closed, returned to list");
            });

            StepExecutor.step("Logout (deferred)", () ->
                    ReportLogger.session("Logout deferred — session may be reused by a later test on this thread."));

            StepExecutor.step("Database Validation", () -> {
                new ARVoucherDbValidator().validate(
                        VoucherDbContextBuilder.build(docNoRef[0], data));
                ReportLogger.pass("DB validated. Document No: " + docNoRef[0]);
            });

            StepExecutor.step("Finalize save assertions", softAssert::assertAll);

            return docNoRef[0] != null;
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
