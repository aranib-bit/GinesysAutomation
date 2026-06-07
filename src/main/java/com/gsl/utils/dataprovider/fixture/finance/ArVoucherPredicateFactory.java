package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Finance.Operations.ArVoucherAdd;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.pages.Finance.Operations.Common.PaymentInfoCheckWarning;
import com.gsl.pages.HomePage;
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

            StepExecutor.step("Fill Sub-Ledger Information", () -> {
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
                new HomePage().logoutFromApplication();
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

            StepExecutor.step("Fill Sub-Ledger Information", () -> {
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
                new HomePage().logoutFromApplication();
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

            StepExecutor.step("Fill Sub-Ledger Information", () -> {
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
                new HomePage().logoutFromApplication();
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

            StepExecutor.step("Fill Sub-Ledger Information", () -> {
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
                new HomePage().logoutFromApplication();
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

    private ArVoucherPredicateFactory() {}
}
