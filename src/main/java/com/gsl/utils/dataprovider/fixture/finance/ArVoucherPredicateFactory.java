package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Finance.Operations.ArVoucherAdd;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.pages.Finance.Operations.Common.PaymentInfoCheckWarning;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.SubLedgerRowLocator;
import com.gsl.utils.Selenium.ExtJsQuickTipUtils;
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

    /**
     * SELECT_DOCUMENT behavior:
     * Fill header → fill sub-ledger section (rows + SELECT_DOCUMENT adjustments) → Save.
     */
    public static final Supplier<BiPredicate<MergedFinanceArVoucherData, ArVoucherAdd>>
        SELECT_DOCUMENT_BEHAVIOR = () -> (data, page) -> {

            final SoftAssert softAssert          = new SoftAssert();
            final String[] docNoRef              = new String[1];
            final Map<String, FinPost>[] preRef  = new Map[1];
            final Map<String, FinPost>[] postRef = new Map[1];
            final LocalDateTime[] saveStartTime  = new LocalDateTime[1];

            StepExecutor.step("Fill Document Information", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                ReportLogger.info("Cash Bank selected: " + data.getDocumentInformation().getCashbank());
            });

            StepExecutor.step("Sub-Ledger Rows", () -> {
                page.fillSubLedgerRows(data, true);
                ReportLogger.info("Sub-ledger section complete");
            });

            StepExecutor.step("Save AR Voucher", () -> {
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

                ReportLogger.pass("Voucher saved. Document No: " + docNoRef[0]);
                messageBox.clickOk();
                page.close().clickYes();
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
     * FIFO_SETOFF behavior:
     * Fill header → fill sub-ledger section (rows + FIFO adjustments) → Save.
     */
    public static final Supplier<BiPredicate<MergedFinanceArVoucherData, ArVoucherAdd>>
        FIFO_SETOFF_BEHAVIOR = () -> (data, page) -> {

            final SoftAssert softAssert          = new SoftAssert();
            final String[] docNoRef              = new String[1];
            final Map<String, FinPost>[] preRef  = new Map[1];
            final Map<String, FinPost>[] postRef = new Map[1];
            final LocalDateTime[] saveStartTime  = new LocalDateTime[1];

            StepExecutor.step("Fill Document Information", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                ReportLogger.info("Cash Bank selected: " + data.getDocumentInformation().getCashbank());
            });

            StepExecutor.step("Sub-Ledger Rows", () -> {
                page.fillSubLedgerRows(data, true);
                ReportLogger.info("Sub-ledger section complete");
            });

            StepExecutor.step("Save AR Voucher", () -> {
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

                ReportLogger.pass("Voucher saved. Document No: " + docNoRef[0]);
                messageBox.clickOk();
                page.close().clickYes();
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
     * REFERENCE_SITEWISE_FIFO behavior:
     * Fill header → fill sub-ledger section (rows + REF_SITEWISE_FIFO adjustments) → Save.
     */
    public static final Supplier<BiPredicate<MergedFinanceArVoucherData, ArVoucherAdd>>
        REF_SITEWISE_FIFO_BEHAVIOR = () -> (data, page) -> {

            final SoftAssert softAssert          = new SoftAssert();
            final String[] docNoRef              = new String[1];
            final Map<String, FinPost>[] preRef  = new Map[1];
            final Map<String, FinPost>[] postRef = new Map[1];
            final LocalDateTime[] saveStartTime  = new LocalDateTime[1];

            StepExecutor.step("Fill Document Information", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                ReportLogger.info("Cash Bank selected: " + data.getDocumentInformation().getCashbank());
            });

            StepExecutor.step("Sub-Ledger Rows", () -> {
                page.fillSubLedgerRows(data, true);
                ReportLogger.info("Sub-ledger section complete");
            });

            StepExecutor.step("Save AR Voucher", () -> {
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

                ReportLogger.pass("Voucher saved. Document No: " + docNoRef[0]);
                messageBox.clickOk();
                page.close().clickYes();
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
     * DOC_NO_ADJUSTMENT behavior:
     * Fill header → fill sub-ledger rows (no adjustment) → Save.
     * The "Ignore & Proceed" dialog fires when Bank GL and no payment rows are present.
     */
    public static final Supplier<BiPredicate<MergedFinanceArVoucherData, ArVoucherAdd>>
        DOC_NO_ADJUSTMENT_BEHAVIOR = () -> (data, page) -> {

            final SoftAssert softAssert          = new SoftAssert();
            final String[] docNoRef              = new String[1];
            final Map<String, FinPost>[] preRef  = new Map[1];
            final Map<String, FinPost>[] postRef = new Map[1];
            final LocalDateTime[] saveStartTime  = new LocalDateTime[1];

            StepExecutor.step("Fill Document Information", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                ReportLogger.info("Cash Bank selected: " + data.getDocumentInformation().getCashbank());
            });

            StepExecutor.step("Sub-Ledger Rows", () -> {
                page.fillSubLedgerRows(data, false);
                ReportLogger.info("Sub-ledger section complete");
            });

            StepExecutor.step("Save AR Voucher", () -> {
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

                ReportLogger.pass("Voucher saved. Document No: " + docNoRef[0]);
                messageBox.clickOk();
                page.close().clickYes();
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
                page.close().clickYes();
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
                page.close().clickYes();
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
                String expectedDialog = NegativeValidation.ZERO_AMOUNT_DIALOG.expectedMessage();
                String actualDialog = popup.verifyErrorMessage(expectedDialog);
                ReportLogger.pass("Dialog OK — Expected: [" + expectedDialog + "] | Received: [" + actualDialog + "]");
                popup.clickOkOnErrorDialog();
            });

            StepExecutor.step("Hover row indicator — verify inline tooltip", () -> {
                String slName = data.getSubLedgerInformationList() != null
                        && !data.getSubLedgerInformationList().isEmpty()
                        ? data.getSubLedgerInformationList().get(0).getSubLedger()
                        : null;
                if (slName == null || slName.isBlank()) {
                    throw new IllegalStateException("INLINE_VALIDATION_BEHAVIOR requires at least one sub-ledger row with a name");
                }
                String actual = ExtJsQuickTipUtils.hoverAndVerify(
                        SubLedgerRowLocator.dotsButton(slName),
                        NegativeValidation.ZERO_AMOUNT_TOOLTIP_STOP.expectedMessage(),
                        NegativeValidation.ZERO_AMOUNT_TOOLTIP_AMOUNT.expectedMessage());
                ReportLogger.pass("Tooltip OK — [" + actual + "]");
            });

            StepExecutor.step("Cancel (Logout deferred)", () -> {
                page.close().clickYes();
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread.");
            });

            return true;
        };

    private ArVoucherPredicateFactory() {}
}
