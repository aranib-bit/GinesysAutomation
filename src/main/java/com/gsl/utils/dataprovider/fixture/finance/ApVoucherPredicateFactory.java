package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Finance.Operations.ApVoucherAdd;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.pages.Finance.Operations.Common.PaymentInfoCheckWarning;
import com.gsl.pages.HomePage;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.SelectDocument;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.model.finance.FinPost;
import com.gsl.utils.Log;
import com.gsl.validation.db.finance.APVoucherDbValidator;
import com.gsl.validation.db.finance.ApVoucherDbContext;
import com.gsl.validation.db.finance.StateCapture;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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

    // ─── Advance Payment behaviors ────────────────────────────────────────────

    /**
     * ADVANCE_NO_TDS behavior:
     * Fill header → fill sub-ledger rows (advance, no TDS) → Save.
     * Bank GL triggers "Ignore &amp; Proceed" when no payment rows are present.
     * No pre/post state capture (advance creates a fresh AP entry, not settling a document).
     */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        ADVANCE_NO_TDS_BEHAVIOR = () -> (data, page) -> {

            final String[] docNoRef             = new String[1];
            final LocalDateTime[] saveStartTime = new LocalDateTime[1];

            StepExecutor.step("Fill Document Information", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                ReportLogger.info("Cash Bank: " + data.getDocumentInformation().getCashbank()
                        + " | On Account: " + data.getDocumentInformation().getOnAccount());
            });

            StepExecutor.step("Fill Sub-Ledger Information (Advance)", () -> {
                page.fillSubLedgerRowsForAdvance(data, false);
                ReportLogger.info("Advance sub-ledger section complete");
            });

            StepExecutor.step("Save AP Voucher", () -> {
                saveStartTime[0] = LocalDateTime.now();
                DocumentPopup savePopup = page.clickSave();
                DocumentMessageBox messageBox = data.isNeedsPaymentInfoWarning()
                        ? savePopup.verifyPaymentInfoCheckWarning(PaymentInfoCheckWarning.AP_VOUCHER)
                                   .clickProceed(PaymentInfoCheckWarning.AP_VOUCHER)
                        : savePopup.waitForMessageBox();
                docNoRef[0] = messageBox.extractDocumentNo();
                Log.info("AP Voucher saved. Document No: {}", docNoRef[0]);
                ReportLogger.pass("Voucher saved. Document No: " + docNoRef[0]);
                messageBox.clickOk();
                page.close().clickYes();
                new HomePage().logoutFromApplication();
            });

            StepExecutor.step("Database Validation", () -> {
                new APVoucherDbValidator().validate(
                        ApVoucherDbContext.builder()
                                .voucherNo(docNoRef[0])
                                .testData(data)
                                // preState / postState = empty maps (default) for advance
                                .saveStartTime(saveStartTime[0])
                                .build());
                ReportLogger.pass("DB validated. Document No: " + docNoRef[0]);
            });

            return docNoRef[0] != null;
        };

    /**
     * ADVANCE_WITH_TDS behavior:
     * Fill header → fill sub-ledger rows → fill TDS &amp; Net Payable rows → Save.
     * The TDS rows are auto-populated from the SL's TDS configuration;
     * the predicate verifies the populated values and confirms with "Proceed".
     */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        ADVANCE_WITH_TDS_BEHAVIOR = () -> (data, page) -> {

            final String[] docNoRef             = new String[1];
            final LocalDateTime[] saveStartTime = new LocalDateTime[1];

            StepExecutor.step("Fill Document Information", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                ReportLogger.info("Cash Bank: " + data.getDocumentInformation().getCashbank()
                        + " | On Account: " + data.getDocumentInformation().getOnAccount());
            });

            StepExecutor.step("Fill Sub-Ledger Information (Advance + TDS)", () -> {
                page.fillSubLedgerRowsForAdvance(data, false);
                ReportLogger.info("Advance sub-ledger section complete");
            });

            StepExecutor.step("Fill TDS and Net Payable", () -> {
                page.fillTdsAndNetPayable(data);
                ReportLogger.info("TDS & Net Payable section complete");
            });

            StepExecutor.step("Save AP Voucher", () -> {
                saveStartTime[0] = LocalDateTime.now();
                DocumentPopup savePopup = page.clickSave();
                DocumentMessageBox messageBox = data.isNeedsPaymentInfoWarning()
                        ? savePopup.verifyPaymentInfoCheckWarning(PaymentInfoCheckWarning.AP_VOUCHER)
                                   .clickProceed(PaymentInfoCheckWarning.AP_VOUCHER)
                        : savePopup.waitForMessageBox();
                docNoRef[0] = messageBox.extractDocumentNo();
                Log.info("AP Voucher (TDS) saved. Document No: {}", docNoRef[0]);
                ReportLogger.pass("Voucher saved. Document No: " + docNoRef[0]);
                messageBox.clickOk();
                page.close().clickYes();
                new HomePage().logoutFromApplication();
            });

            StepExecutor.step("Database Validation", () -> {
                new APVoucherDbValidator().validate(
                        ApVoucherDbContext.builder()
                                .voucherNo(docNoRef[0])
                                .testData(data)
                                .saveStartTime(saveStartTime[0])
                                .build());
                ReportLogger.pass("DB validated. Document No: " + docNoRef[0]);
            });

            return docNoRef[0] != null;
        };

    // ─── Document Payment behaviors ───────────────────────────────────────────

    /**
     * DOC_NO_ADJUSTMENT behavior:
     * Fill header → fill sub-ledger rows (no adjustment) → Save.
     * Pre/post invoice state is captured around Save for DB delta validation.
     * Bank GL triggers "Ignore &amp; Proceed" when no payment rows are present.
     */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        DOC_NO_ADJUSTMENT_BEHAVIOR = () -> (data, page) -> {

            final String[] docNoRef              = new String[1];
            final Map<String, FinPost>[] preRef  = new Map[1];
            final Map<String, FinPost>[] postRef = new Map[1];
            final LocalDateTime[] saveStartTime  = new LocalDateTime[1];

            StepExecutor.step("Fill Document Information", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                ReportLogger.info("Cash Bank: " + data.getDocumentInformation().getCashbank());
            });

            StepExecutor.step("Fill Sub-Ledger Information (No Adjustment)", () -> {
                page.fillSubLedgerRowsForDocument(data, false);
                ReportLogger.info("Document sub-ledger section complete (no adjustment)");
            });

            StepExecutor.step("Save AP Voucher", () -> {
                List<String> invoiceNos = collectInvoiceNos(data);
                StateCapture stateCapture = new StateCapture();
                preRef[0] = stateCapture.captureInvoiceState(invoiceNos);

                saveStartTime[0] = LocalDateTime.now();
                DocumentPopup savePopup = page.clickSave();
                DocumentMessageBox messageBox = data.isNeedsPaymentInfoWarning()
                        ? savePopup.verifyPaymentInfoCheckWarning(PaymentInfoCheckWarning.AP_VOUCHER)
                                   .clickProceed(PaymentInfoCheckWarning.AP_VOUCHER)
                        : savePopup.waitForMessageBox();
                docNoRef[0] = messageBox.extractDocumentNo();
                Log.info("AP Voucher saved. Document No: {}", docNoRef[0]);

                postRef[0] = stateCapture.captureInvoiceState(invoiceNos);
                ReportLogger.pass("Voucher saved. Document No: " + docNoRef[0]);
                messageBox.clickOk();
                page.close().clickYes();
                new HomePage().logoutFromApplication();
            });

            StepExecutor.step("Database Validation", () -> {
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

    /**
     * DOC_SELECT_DOCUMENT behavior:
     * Fill header → fill sub-ledger rows (Select Document popup) → Save.
     * Pre/post state captured for DB delta validation.
     */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        DOC_SELECT_DOCUMENT_BEHAVIOR = () -> (data, page) -> {

            final String[] docNoRef              = new String[1];
            final Map<String, FinPost>[] preRef  = new Map[1];
            final Map<String, FinPost>[] postRef = new Map[1];
            final LocalDateTime[] saveStartTime  = new LocalDateTime[1];

            StepExecutor.step("Fill Document Information", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                ReportLogger.info("Cash Bank: " + data.getDocumentInformation().getCashbank());
            });

            StepExecutor.step("Fill Sub-Ledger Information (Select Document)", () -> {
                page.fillSubLedgerRowsForDocument(data, true);
                ReportLogger.info("Document sub-ledger section complete (Select Document)");
            });

            StepExecutor.step("Save AP Voucher", () -> {
                List<String> invoiceNos = collectInvoiceNos(data);
                StateCapture stateCapture = new StateCapture();
                preRef[0] = stateCapture.captureInvoiceState(invoiceNos);

                saveStartTime[0] = LocalDateTime.now();
                DocumentPopup savePopup = page.clickSave();
                DocumentMessageBox messageBox = data.isNeedsPaymentInfoWarning()
                        ? savePopup.verifyPaymentInfoCheckWarning(PaymentInfoCheckWarning.AP_VOUCHER)
                                   .clickProceed(PaymentInfoCheckWarning.AP_VOUCHER)
                        : savePopup.waitForMessageBox();
                docNoRef[0] = messageBox.extractDocumentNo();
                Log.info("AP Voucher saved. Document No: {}", docNoRef[0]);

                postRef[0] = stateCapture.captureInvoiceState(invoiceNos);
                ReportLogger.pass("Voucher saved. Document No: " + docNoRef[0]);
                messageBox.clickOk();
                page.close().clickYes();
                new HomePage().logoutFromApplication();
            });

            StepExecutor.step("Database Validation", () -> {
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

    /**
     * DOC_FIFO_BEHAVIOR:
     * Fill header → fill sub-ledger rows (FIFO auto set-off) → Save.
     * Pre/post state captured for DB delta validation.
     * Cash GL adds CASH payment rows; Bank GL triggers "Ignore &amp; Proceed".
     */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        DOC_FIFO_BEHAVIOR = () -> (data, page) -> {

            final String[] docNoRef              = new String[1];
            final Map<String, FinPost>[] preRef  = new Map[1];
            final Map<String, FinPost>[] postRef = new Map[1];
            final LocalDateTime[] saveStartTime  = new LocalDateTime[1];

            StepExecutor.step("Fill Document Information", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                ReportLogger.info("Cash Bank: " + data.getDocumentInformation().getCashbank());
            });

            StepExecutor.step("Fill Sub-Ledger Information (FIFO)", () -> {
                page.fillSubLedgerRowsForDocument(data, true);
                ReportLogger.info("Document sub-ledger section complete (FIFO)");
            });

            StepExecutor.step("Save AP Voucher", () -> {
                List<String> invoiceNos = collectInvoiceNos(data);
                StateCapture stateCapture = new StateCapture();
                preRef[0] = stateCapture.captureInvoiceState(invoiceNos);

                saveStartTime[0] = LocalDateTime.now();
                DocumentPopup savePopup = page.clickSave();
                DocumentMessageBox messageBox = data.isNeedsPaymentInfoWarning()
                        ? savePopup.verifyPaymentInfoCheckWarning(PaymentInfoCheckWarning.AP_VOUCHER)
                                   .clickProceed(PaymentInfoCheckWarning.AP_VOUCHER)
                        : savePopup.waitForMessageBox();
                docNoRef[0] = messageBox.extractDocumentNo();
                Log.info("AP Voucher saved. Document No: {}", docNoRef[0]);

                postRef[0] = stateCapture.captureInvoiceState(invoiceNos);
                ReportLogger.pass("Voucher saved. Document No: " + docNoRef[0]);
                messageBox.clickOk();
                page.close().clickYes();
                new HomePage().logoutFromApplication();
            });

            StepExecutor.step("Database Validation", () -> {
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

    /**
     * DOC_REFERENCE_SITEWISE_FIFO_BEHAVIOR:
     * Fill header → fill sub-ledger rows (Reference Sitewise FIFO) → Save.
     * Pre/post state captured for DB delta validation.
     */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        DOC_REFERENCE_SITEWISE_FIFO_BEHAVIOR = () -> (data, page) -> {

            final String[] docNoRef              = new String[1];
            final Map<String, FinPost>[] preRef  = new Map[1];
            final Map<String, FinPost>[] postRef = new Map[1];
            final LocalDateTime[] saveStartTime  = new LocalDateTime[1];

            StepExecutor.step("Fill Document Information", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                ReportLogger.info("Cash Bank: " + data.getDocumentInformation().getCashbank());
            });

            StepExecutor.step("Fill Sub-Ledger Information (Reference Sitewise FIFO)", () -> {
                page.fillSubLedgerRowsForDocument(data, true);
                ReportLogger.info("Document sub-ledger section complete (Ref-Sitewise FIFO)");
            });

            StepExecutor.step("Save AP Voucher", () -> {
                List<String> invoiceNos = collectInvoiceNos(data);
                StateCapture stateCapture = new StateCapture();
                preRef[0] = stateCapture.captureInvoiceState(invoiceNos);

                saveStartTime[0] = LocalDateTime.now();
                DocumentPopup savePopup = page.clickSave();
                DocumentMessageBox messageBox = data.isNeedsPaymentInfoWarning()
                        ? savePopup.verifyPaymentInfoCheckWarning(PaymentInfoCheckWarning.AP_VOUCHER)
                                   .clickProceed(PaymentInfoCheckWarning.AP_VOUCHER)
                        : savePopup.waitForMessageBox();
                docNoRef[0] = messageBox.extractDocumentNo();
                Log.info("AP Voucher saved. Document No: {}", docNoRef[0]);

                postRef[0] = stateCapture.captureInvoiceState(invoiceNos);
                ReportLogger.pass("Voucher saved. Document No: " + docNoRef[0]);
                messageBox.clickOk();
                page.close().clickYes();
                new HomePage().logoutFromApplication();
            });

            StepExecutor.step("Database Validation", () -> {
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

            StepExecutor.step("Click Save — expect validation error", () -> {
                DocumentPopup popup = page.clickSave();
                String expectedMsg = data.getExpectedValidation().expectedMessage();
                popup.verifyErrorMessage(expectedMsg);
                ReportLogger.pass("Validation error confirmed: " + expectedMsg);
            });

            StepExecutor.step("Cancel and Logout", () -> {
                page.close().clickYes();
                new HomePage().logoutFromApplication();
            });

            return true;    // test passes when expected error was raised
        };

    // ─── Private helper ───────────────────────────────────────────────────────

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
