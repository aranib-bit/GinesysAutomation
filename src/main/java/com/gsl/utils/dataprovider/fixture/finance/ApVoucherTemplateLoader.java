package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.enums.AdjustmentType;
import com.gsl.enums.CashBankType;
import com.gsl.enums.Finance.Operations.APVoucher.TdsApplicableAmountBehaviour;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.model.finance.PendingSlDocument;
import com.gsl.model.finance.SlNameWithId;
import com.gsl.model.finance.TdsResolution;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.ReferenceSitewiseFIFOsetoff;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.SelectDocument;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.SubLedgerInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.TdsAndNetPayable;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.Faker.FakerUtils;
import com.gsl.utils.Log;
import com.gsl.utils.cheque.UniqueChequeGenerator;
import com.gsl.utils.dataprovider.fixture.LiveDbRefs;
import com.gsl.database.query.PendingAdjQuery;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Builds {@link MergedFinanceApVoucherData} instances from live DB values.
 * Each public static method is one named scenario template.
 * Called by {@link ApVoucherScenarioType#loadData(LiveDbRefs)}.
 *
 * <h3>Advance vs Document paths</h3>
 * <ul>
 *   <li><b>Advance</b> ({@code onAccount="Yes"}): vendor SLs fetched directly from {@code finsl};
 *       no pending documents needed; amount is fixed at {@link #ADVANCE_DEFAULT_AMOUNT}.</li>
 *   <li><b>Document</b> ({@code onAccount="No"}): pending AP documents fetched from
 *       {@code v_pending_adj WHERE debit_credit='C'}; amount = sum of pending per SL.</li>
 * </ul>
 */
public final class ApVoucherTemplateLoader {

    /**
     * Default advance payment amount when no pending document constrains the value.
     * Config-driven in a future pass — for now a safe, realistic amount.
     */
    private static final BigDecimal ADVANCE_DEFAULT_AMOUNT = new BigDecimal("100.00");

    private ApVoucherTemplateLoader() {}

    // ─── Advance Payment templates ────────────────────────────────────────────

    /**
     * Builds test data for the "simple vendor advance, no TDS" scenario.
     * One SL line, fixed amount, no adjustment type, no select documents.
     * Bank GL triggers "Ignore &amp; Proceed" on save (no payment rows);
     * Cash GL adds a CASH payment row (no instrument number required).
     */
    public static MergedFinanceApVoucherData advanceNoTdsTemplate(LiveDbRefs refs) {
        String id = "DB_ADV_NO_TDS";
        List<SubLedgerInformation> subLedgers = buildSubLedgersForAdvance(
                refs.getPendingSlNames(), refs.getSlToArApName(), refs.getSlToRefSiteName());
        List<PaymentInformation> payments = refs.getCashBankType() == CashBankType.CASH
                ? buildPayments(id, subLedgers, SelectDocumentVariant.PaymentMode.CASH)
                : List.of();
        return MergedFinanceApVoucherData.builder()
                .masterTestData(buildMaster("createApVoucherAdvancePayment", id,
                        "ADVANCE | " + glLabel(refs) + " | No TDS"))
                .documentInformation(buildDoc(id, refs, FakerUtils.randomRemarks(),
                        FakerUtils.randomReference(), "Yes"))
                .subLedgerInformationList(subLedgers)
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(payments)
                .tdsAndNetPayableList(List.of())
                .onAccount(true)
                .tdsProvided(false)
                .paymentProvided(!payments.isEmpty())
                .needsPaymentInfoWarning(computeNeedsPaymentInfoWarning(subLedgers, payments, refs.getCashBankType()))
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    /**
     * Builds test data for the "vendor advance with TDS deduction" scenario.
     * One TDS-applicable SL line, TDS &amp; Net Payable rows generated from the SL amount.
     * Bank GL triggers "Ignore &amp; Proceed" when no payment rows are present.
     */
    public static MergedFinanceApVoucherData advanceWithTdsTemplate(LiveDbRefs refs) {
        String id = "DB_ADV_TDS";
        List<SubLedgerInformation> subLedgers = buildSubLedgersForAdvance(
                refs.getPendingSlNames(), refs.getSlToArApName(), refs.getSlToRefSiteName());
        List<PaymentInformation> payments = refs.getCashBankType() == CashBankType.CASH
                ? buildPayments(id, subLedgers, SelectDocumentVariant.PaymentMode.CASH)
                : List.of();
        List<TdsAndNetPayable> tdsRows = buildTdsRows(
                id, subLedgers, refs.getTdsBehaviour(), refs.getSlToTdsResolution());
        return MergedFinanceApVoucherData.builder()
                .masterTestData(buildMaster("createApVoucherAdvancePayment", id,
                        "ADVANCE | " + glLabel(refs) + " | With TDS"))
                .documentInformation(buildDoc(id, refs, FakerUtils.randomRemarks(),
                        FakerUtils.randomReference(), "Yes"))
                .subLedgerInformationList(subLedgers)
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(payments)
                .tdsAndNetPayableList(tdsRows)
                .onAccount(true)
                .tdsProvided(true)
                .paymentProvided(!payments.isEmpty())
                .needsPaymentInfoWarning(computeNeedsPaymentInfoWarning(subLedgers, payments, refs.getCashBankType()))
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    // ─── Multi-SL templates ───────────────────────────────────────────────────

    /**
     * Two vendor SL lines, Bank GL, mixed payment rows (NEFT/RTGS for line 1,
     * Cheque for line 2). Verifies that the payment-information popup is filled
     * correctly for each row and that the voucher saves to DB with both lines.
     */
    public static MergedFinanceApVoucherData advanceMultiSlWithPaymentTemplate(LiveDbRefs refs) {
        String id = "DB_ADV_MULTI_SL_PAY";
        // refs.getPendingSlNames() returns 2 entries because opts.subLedgerCount=2
        List<SubLedgerInformation> subLedgers = buildSubLedgersForAdvance(
                refs.getPendingSlNames(), refs.getSlToArApName(), refs.getSlToRefSiteName());
        // MIX: row-0 → NEFT/RTGS, row-1 → CHEQUE
        List<PaymentInformation> payments = buildPayments(id, subLedgers, SelectDocumentVariant.PaymentMode.MIX);
        return MergedFinanceApVoucherData.builder()
                .masterTestData(buildMaster("createApVoucherAdvancePayment", id,
                        "ADVANCE | " + glLabel(refs) + " | Multi-SL | Mixed Payment"))
                .documentInformation(buildDoc(id, refs, FakerUtils.randomRemarks(),
                        FakerUtils.randomReference(), "Yes"))
                .subLedgerInformationList(subLedgers)
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(payments)
                .tdsAndNetPayableList(List.of())
                .onAccount(true)
                .tdsProvided(false)
                .paymentProvided(true)
                .needsPaymentInfoWarning(computeNeedsPaymentInfoWarning(subLedgers, payments, refs.getCashBankType()))
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    // ─── T-03 Negative & Boundary templates (advance path) ────────────────────

    /** NEG_BLANK_CASHBANK — valid vendor SL, Cash/Bank left blank → "Cash/Bank is required". */
    public static MergedFinanceApVoucherData negBlankCashbankTemplate(LiveDbRefs refs) {
        String id = "DB_NEG_BLANK_CASHBANK";
        List<SubLedgerInformation> subLedgers = buildSubLedgersForAdvance(
                refs.getPendingSlNames(), refs.getSlToArApName(), refs.getSlToRefSiteName());
        // Remarks is disabled by the app when Cash/Bank is blank — pass null to skip filling it.
        return negativeAdvanceData(id, "NEGATIVE | Blank Cash/Bank", refs, subLedgers,
                null, FakerUtils.randomReference(), NegativeValidation.BLANK_CASHBANK, null);
    }

    /**
     * NEG_BLANK_CASHBANK_ADD_ROW — Cash/Bank left blank; clicking Add Row triggers
     * "Please select cash/bank ledger first." in an Error popup.
     * T-1: dismisses with OK, then discards the form and logs out.
     */
    public static MergedFinanceApVoucherData negBlankCashbankAddRowTemplate(LiveDbRefs refs) {
        String id = "DB_NEG_CASHBANK_ADDROW";
        List<SubLedgerInformation> subLedgers = buildSubLedgersForAdvance(
                refs.getPendingSlNames(), refs.getSlToArApName(), refs.getSlToRefSiteName());
        // Remarks is disabled when Cash/Bank is blank — pass null to skip filling it.
        return negativeAdvanceData(id, "NEGATIVE | Blank Cash/Bank (Add Row trigger)", refs,
                subLedgers, null, FakerUtils.randomReference(),
                NegativeValidation.BLANK_CASHBANK_ADD_ROW, null);
    }

    /**
     * NEG_BLANK_CASHBANK_ADD_ROW_RECOVER — Cash/Bank left blank (Add Row triggers error popup);
     * after dismissing with OK the test fills Cash/Bank and saves successfully.
     * Proves form recovery without a page reload.
     */
    public static MergedFinanceApVoucherData negBlankCashbankAddRowRecoverTemplate(LiveDbRefs refs) {
        String id = "DB_NEG_CASHBANK_ADDROW_RECOVER";
        List<SubLedgerInformation> subLedgers = buildSubLedgersForAdvance(
                refs.getPendingSlNames(), refs.getSlToArApName(), refs.getSlToRefSiteName());
        List<PaymentInformation> payments = refs.getCashBankType() == CashBankType.CASH
                ? buildPayments(id, subLedgers, SelectDocumentVariant.PaymentMode.CASH)
                : List.of();
        return MergedFinanceApVoucherData.builder()
                .masterTestData(buildMaster("createApVoucherNegativeBoundary", id,
                        "NEGATIVE | Blank Cash/Bank (Add Row) → Recover | " + glLabel(refs)))
                // Remarks is disabled when Cash/Bank is blank — pass null to skip filling it.
                .documentInformation(buildDoc(id, refs, null,
                        FakerUtils.randomReference(), "Yes", null))
                .subLedgerInformationList(subLedgers)
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(payments)
                .tdsAndNetPayableList(List.of())
                .onAccount(true)
                .tdsProvided(false)
                .paymentProvided(!payments.isEmpty())
                .needsPaymentInfoWarning(computeNeedsPaymentInfoWarning(subLedgers, payments, refs.getCashBankType()))
                .expectedValidation(NegativeValidation.BLANK_CASHBANK_ADD_ROW)
                .cashbankForRecovery(refs.getCashBankName())
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    /** NEG_BLANK_SUBLEDGER — valid Cash/Bank, no SL row filled (empty grid) → "Sub-Ledger is required".
     *  T-1: verifies error, dismisses with OK, discards the form and logs out. */
    public static MergedFinanceApVoucherData negBlankSubledgerTemplate(LiveDbRefs refs) {
        String id = "DB_NEG_BLANK_SUBLEDGER";
        return negativeAdvanceData(id, "NEGATIVE | Blank Sub-Ledger", refs, List.of(),
                refs.getCashBankName(), FakerUtils.randomReference(), NegativeValidation.BLANK_SUBLEDGER_AP);
    }

    /**
     * NEG_BLANK_SUBLEDGER_RECOVER — T-2: same error trigger as T-1, but after dismissing the
     * popup the test fills valid SL rows and verifies that the voucher saves successfully.
     * Proves form recovery without a page reload.
     */
    public static MergedFinanceApVoucherData negBlankSubledgerRecoverTemplate(LiveDbRefs refs) {
        String id = "DB_NEG_BLANK_SL_RECOVER";
        List<SubLedgerInformation> subLedgers = buildSubLedgersForAdvance(
                refs.getPendingSlNames(), refs.getSlToArApName(), refs.getSlToRefSiteName());
        List<PaymentInformation> payments = refs.getCashBankType() == CashBankType.CASH
                ? buildPayments(id, subLedgers, SelectDocumentVariant.PaymentMode.CASH)
                : List.of();
        return MergedFinanceApVoucherData.builder()
                .masterTestData(buildMaster("createApVoucherNegativeBoundary", id,
                        "NEGATIVE | Blank SL → Recover | " + glLabel(refs)))
                .documentInformation(buildDoc(id, refs, FakerUtils.randomRemarks(),
                        FakerUtils.randomReference(), "Yes"))
                .subLedgerInformationList(subLedgers)
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(payments)
                .tdsAndNetPayableList(List.of())
                .onAccount(true)
                .tdsProvided(false)
                .paymentProvided(!payments.isEmpty())
                .needsPaymentInfoWarning(computeNeedsPaymentInfoWarning(subLedgers, payments, refs.getCashBankType()))
                .expectedValidation(NegativeValidation.BLANK_SUBLEDGER_AP)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    /** NEG_BLANK_MANDATORY_REFERENCE — Reference left blank → "Reference No. is required"
     *  (only when the resolved scheme makes Reference mandatory). */
    public static MergedFinanceApVoucherData negBlankReferenceTemplate(LiveDbRefs refs) {
        String id = "DB_NEG_BLANK_REF";
        List<SubLedgerInformation> subLedgers = buildSubLedgersForAdvance(
                refs.getPendingSlNames(), refs.getSlToArApName(), refs.getSlToRefSiteName());
        return negativeAdvanceData(id, "NEGATIVE | Blank Mandatory Reference", refs, subLedgers,
                refs.getCashBankName(), null, NegativeValidation.BLANK_MANDATORY_REFERENCE);
    }

    /**
     * BLANK_REFERENCE_ALLOWED — boundary test proving that blank Reference is allowed.
     * Two identical advance vouchers saved with no Reference; both must produce distinct doc numbers.
     * Uses the advance path (On Account = Yes) with a valid vendor SL.
     * The template returns the first invocation; totalInvocations = 2 causes DataProvider to run twice.
     */
    public static MergedFinanceApVoucherData blankReferenceAllowedTemplate(LiveDbRefs refs) {
        String id = "DB_BLANK_REF_ALLOWED";
        List<SubLedgerInformation> subLedgers = buildSubLedgersForAdvance(
                refs.getPendingSlNames(), refs.getSlToArApName(), refs.getSlToRefSiteName());
        List<PaymentInformation> payments = refs.getCashBankType() == CashBankType.CASH
                ? buildPayments(id, subLedgers, SelectDocumentVariant.PaymentMode.CASH)
                : List.of();
        return MergedFinanceApVoucherData.builder()
                .masterTestData(buildMaster("createApVoucherNegativeBoundary", id,
                        "BOUNDARY | Blank Ref Allowed | " + glLabel(refs)))
                .documentInformation(buildDoc(id, refs, FakerUtils.randomRemarks(),
                        null, "Yes"))   // null reference = blank
                .subLedgerInformationList(subLedgers)
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(payments)
                .tdsAndNetPayableList(List.of())
                .onAccount(true)
                .tdsProvided(false)
                .paymentProvided(!payments.isEmpty())
                .needsPaymentInfoWarning(computeNeedsPaymentInfoWarning(subLedgers, payments, refs.getCashBankType()))
                .invocationIndex(1)
                .totalInvocations(2)   // both invocations must save successfully
                .build();
    }

    /** BOUND_AMOUNT_MIN_VALID — smallest positive line amount (0.01) → must save. */
    public static MergedFinanceApVoucherData boundAmountMinValidTemplate(LiveDbRefs refs) {
        String id = "DB_BOUND_AMT_MIN_VALID";
        List<SubLedgerInformation> subLedgers = buildSubLedgersForAdvance(
                refs.getPendingSlNames(), refs.getSlToArApName(), refs.getSlToRefSiteName(),
                new BigDecimal("0.01"));
        List<PaymentInformation> payments = refs.getCashBankType() == CashBankType.CASH
                ? buildPayments(id, subLedgers, SelectDocumentVariant.PaymentMode.CASH)
                : List.of();
        return MergedFinanceApVoucherData.builder()
                .masterTestData(buildMaster("createApVoucherNegativeBoundary", id,
                        "BOUNDARY | Min Amount 0.01 | " + glLabel(refs)))
                .documentInformation(buildDoc(id, refs, FakerUtils.randomRemarks(),
                        FakerUtils.randomReference(), "Yes"))
                .subLedgerInformationList(subLedgers)
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(payments)
                .tdsAndNetPayableList(List.of())
                .onAccount(true)
                .tdsProvided(false)
                .paymentProvided(!payments.isEmpty())
                .needsPaymentInfoWarning(computeNeedsPaymentInfoWarning(subLedgers, payments, refs.getCashBankType()))
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    /** BOUND_AMOUNT_MIN_INVALID — zero line amount → rejected (NegativeValidation.ZERO_AMOUNT). */
    public static MergedFinanceApVoucherData boundAmountMinInvalidTemplate(LiveDbRefs refs) {
        String id = "DB_BOUND_AMT_MIN_INVALID";
        List<SubLedgerInformation> subLedgers = buildSubLedgersForAdvance(
                refs.getPendingSlNames(), refs.getSlToArApName(), refs.getSlToRefSiteName(),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        return negativeAdvanceData(id, "BOUNDARY | Zero Amount (reject)", refs, subLedgers,
                refs.getCashBankName(), FakerUtils.randomReference(), NegativeValidation.ZERO_AMOUNT_DIALOG);
    }

    /**
     * On Account No, sub-ledger with amount=0, no document adjustment performed.
     * Save raises a detail-section error; hovering the row indicator shows a Stop tooltip:
     * "Document adjustment is mandatory for non advance type payment voucher."
     */
    public static MergedFinanceApVoucherData docAmountNotAdjustedTemplate(LiveDbRefs refs) {
        String id = "DB_AP_DOC_NO_ADJ_INVALID";
        List<SubLedgerInformation> subLedgers = buildSubLedgersForAdvance(
                refs.getPendingSlNames(), refs.getSlToArApName(), refs.getSlToRefSiteName(),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        return negativeDocData(id, "BOUNDARY | Doc No Adjustment (reject)", refs, subLedgers,
                NegativeValidation.ZERO_AMOUNT_DIALOG);
    }

    /**
     * On Account No, document adjusted via Select Document (amount auto-populated).
     * Used by DOC_AMOUNT_READ_ONLY scenario to verify the amount cell is read-only.
     */
    public static MergedFinanceApVoucherData docAmountReadOnlyTemplate(LiveDbRefs refs) {
        String id = "DB_AP_AMT_READ_ONLY";
        List<SubLedgerInformation> subLedgers = buildSubLedgersForDocument(
                refs.getPendingSlNames(), AdjustmentType.SELECT_DOCUMENT,
                refs.getPendingDocsBySlName(), refs.getSlToRefSiteName(), refs.getSlToArApName());
        return MergedFinanceApVoucherData.builder()
                .masterTestData(buildMaster("createApVoucherNegativeBoundary", id,
                        "DOC_AMT_READ_ONLY | " + glLabel(refs)))
                .documentInformation(buildDoc(id, refs, FakerUtils.randomRemarks(),
                        FakerUtils.randomReference(), "No"))
                .subLedgerInformationList(subLedgers)
                .selectDocumentList(buildSelectDocuments(id, refs.getPendingSlNames(),
                        refs.getPendingDocsBySlName()))
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(List.of())
                .tdsAndNetPayableList(List.of())
                .onAccount(false)
                .tdsProvided(false)
                .paymentProvided(false)
                .needsPaymentInfoWarning(computeNeedsPaymentInfoWarning(subLedgers, List.of(), refs.getCashBankType()))
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    /**
     * Shared builder for advance-path negative/boundary-invalid data: one valid vendor SL set
     * (or empty for blank-SL), the named field overridden, and {@code expectedValidation} stamped
     * so {@code NEGATIVE_FORM_VALIDATION_BEHAVIOR} asserts the right popup. No payment/TDS.
     */
    private static MergedFinanceApVoucherData negativeAdvanceData(
            String id, String name, LiveDbRefs refs,
            List<SubLedgerInformation> subLedgers,
            String cashbank, String reference, NegativeValidation expected) {
        return negativeAdvanceData(id, name, refs, subLedgers, cashbank, reference, expected,
                FakerUtils.randomRemarks());
    }

    /**
     * Overload that accepts an explicit {@code remarks} value. Pass {@code null} for scenarios
     * where Cash/Bank is intentionally blank — the app disables the Remarks field until
     * Cash/Bank is selected, so attempting to fill it throws ElementNotInteractableException.
     * {@link com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherDocumentInformationBase#setRemarks}
     * already skips null/blank values, so passing null here is safe.
     */
    private static MergedFinanceApVoucherData negativeAdvanceData(
            String id, String name, LiveDbRefs refs,
            List<SubLedgerInformation> subLedgers,
            String cashbank, String reference, NegativeValidation expected,
            String remarks) {
        return MergedFinanceApVoucherData.builder()
                .masterTestData(buildMaster("createApVoucherNegativeBoundary", id,
                        name + " | " + glLabel(refs)))
                .documentInformation(buildDoc(id, refs, remarks,
                        reference, "Yes", cashbank))
                .subLedgerInformationList(subLedgers)
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(List.of())
                .tdsAndNetPayableList(List.of())
                .onAccount(true)
                .tdsProvided(false)
                .paymentProvided(false)
                .needsPaymentInfoWarning(false)
                .expectedValidation(expected)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    /** Shared builder for document-path negative data: On Account No, no payment/TDS, expectedValidation stamped. */
    private static MergedFinanceApVoucherData negativeDocData(
            String id, String name, LiveDbRefs refs,
            List<SubLedgerInformation> subLedgers,
            NegativeValidation expected) {
        return MergedFinanceApVoucherData.builder()
                .masterTestData(buildMaster("createApVoucherNegativeBoundary", id,
                        name + " | " + glLabel(refs)))
                .documentInformation(buildDoc(id, refs, FakerUtils.randomRemarks(),
                        FakerUtils.randomReference(), "No"))
                .subLedgerInformationList(subLedgers)
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(List.of())
                .tdsAndNetPayableList(List.of())
                .onAccount(false)
                .tdsProvided(false)
                .paymentProvided(false)
                .needsPaymentInfoWarning(false)
                .expectedValidation(expected)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    // ─── Document Payment templates ───────────────────────────────────────────

    /**
     * Builds test data for the "document payment, no adjustment type" scenario.
     * Mandatory fields only — no adjustment type, no pre-selected documents.
     * Mirrors AR Voucher's {@code docNoAdjustmentTemplate}.
     * Bank GL triggers "Ignore &amp; Proceed" on save (no payment rows).
     */
    public static MergedFinanceApVoucherData docNoAdjustmentTemplate(LiveDbRefs refs) {
        String id = "DB_AP_NO_ADJ";
        List<SubLedgerInformation> subLedgers = buildSubLedgersForDocument(
                refs.getPendingSlNames(), null,
                refs.getPendingDocsBySlName(), refs.getSlToRefSiteName(), refs.getSlToArApName());
        List<PaymentInformation> payments = refs.getCashBankType() == CashBankType.CASH
                ? buildPayments(id, subLedgers, SelectDocumentVariant.PaymentMode.CASH)
                : List.of();
        return MergedFinanceApVoucherData.builder()
                .masterTestData(buildMaster("createApVoucherDocumentPayment", id,
                        "NO_ADJ | " + glLabel(refs)))
                .documentInformation(buildDoc(id, refs, FakerUtils.randomRemarks(),
                        FakerUtils.randomReference(), "No"))
                .subLedgerInformationList(subLedgers)
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(payments)
                .tdsAndNetPayableList(List.of())
                .onAccount(false)
                .tdsProvided(false)
                .paymentProvided(!payments.isEmpty())
                .needsPaymentInfoWarning(computeNeedsPaymentInfoWarning(subLedgers, payments, refs.getCashBankType()))
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    /**
     * Builds test data for the "document payment via manual Select Document popup" scenario.
     * {@code selectDocumentList} is pre-populated from {@link LiveDbRefs#getPendingDocsBySlName()}
     * so the predicate knows exactly which document numbers to tick in the UI popup.
     *
     * <p>Payment mode is driven by {@link LiveDbRefs#getSelectDocPaymentMode()}:
     * <ul>
     *   <li>NONE — no payment rows; Bank GL will trigger the "Ignore &amp; Proceed" dialog.</li>
     *   <li>CHEQUE / NEFT_RTGS — one payment row per SL with a generated instrument number.</li>
     *   <li>RANDOM — resolved at runtime to CHEQUE or NEFT_RTGS (never NONE — NONE is a
     *       dedicated separate variant).</li>
     *   <li>CASH — overridden automatically when a Cash GL is detected.</li>
     * </ul>
     */
    public static MergedFinanceApVoucherData docSelectDocumentTemplate(LiveDbRefs refs) {
        String id = "DB_AP_SEL_DOC";
        List<SubLedgerInformation> subLedgers = buildSubLedgersForDocument(
                refs.getPendingSlNames(), AdjustmentType.SELECT_DOCUMENT,
                refs.getPendingDocsBySlName(), refs.getSlToRefSiteName(), refs.getSlToArApName());

        // Resolve payment mode — Cash GL always CASH; RANDOM → CHEQUE or NEFT_RTGS only.
        SelectDocumentVariant.PaymentMode paymentMode = refs.getSelectDocPaymentMode();
        if (refs.getCashBankType() == CashBankType.CASH) {
            paymentMode = SelectDocumentVariant.PaymentMode.CASH;
        } else if (paymentMode == SelectDocumentVariant.PaymentMode.RANDOM) {
            java.util.List<SelectDocumentVariant.PaymentMode> pool = List.of(
                    SelectDocumentVariant.PaymentMode.CHEQUE,
                    SelectDocumentVariant.PaymentMode.NEFT_RTGS);
            paymentMode = FakerUtils.randomElement(pool);
            Log.info("[Template] RANDOM payment → resolved to: {} (GL type: Bank)", paymentMode);
        }

        List<PaymentInformation> payments = (paymentMode == SelectDocumentVariant.PaymentMode.NONE)
                ? List.of()
                : buildPayments(id, subLedgers, paymentMode);

        String resolvedPmtLabel = paymentMode == SelectDocumentVariant.PaymentMode.NEFT_RTGS
                ? "NEFT/RTGS" : paymentMode.name();

        return MergedFinanceApVoucherData.builder()
                .masterTestData(buildMaster("createApVoucherDocumentPayment", id,
                        "SEL_DOC | " + glLabel(refs) + " | Pmt:" + resolvedPmtLabel))
                .documentInformation(buildDoc(id, refs, FakerUtils.randomRemarks(),
                        FakerUtils.randomReference(), "No"))
                .subLedgerInformationList(subLedgers)
                .selectDocumentList(buildSelectDocuments(id, refs.getPendingSlNames(),
                        refs.getPendingDocsBySlName()))
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(payments)
                .tdsAndNetPayableList(List.of())
                .onAccount(false)
                .tdsProvided(false)
                .paymentProvided(!payments.isEmpty())
                .needsPaymentInfoWarning(computeNeedsPaymentInfoWarning(subLedgers, payments, refs.getCashBankType()))
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    /**
     * Builds test data for the "document payment via FIFO automatic set-off" scenario.
     * No pre-loaded document list — FIFO auto-selects documents in the UI.
     * Cash GL adds CASH payment rows; Bank GL triggers "Ignore &amp; Proceed".
     */
    public static MergedFinanceApVoucherData docFifoTemplate(LiveDbRefs refs) {
        String id = "DB_AP_FIFO";
        List<SubLedgerInformation> subLedgers = buildSubLedgersForDocument(
                refs.getPendingSlNames(), AdjustmentType.FIFO_SETOFF,
                refs.getPendingDocsBySlName(), refs.getSlToRefSiteName(), refs.getSlToArApName());
        List<PaymentInformation> payments = refs.getCashBankType() == CashBankType.CASH
                ? buildPayments(id, subLedgers, SelectDocumentVariant.PaymentMode.CASH)
                : List.of();
        return MergedFinanceApVoucherData.builder()
                .masterTestData(buildMaster("createApVoucherDocumentPayment", id,
                        "FIFO | " + glLabel(refs)))
                .documentInformation(buildDoc(id, refs, FakerUtils.randomRemarks(),
                        FakerUtils.randomReference(), "No"))
                .subLedgerInformationList(subLedgers)
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(payments)
                .tdsAndNetPayableList(List.of())
                .onAccount(false)
                .tdsProvided(false)
                .paymentProvided(!payments.isEmpty())
                .needsPaymentInfoWarning(computeNeedsPaymentInfoWarning(subLedgers, payments, refs.getCashBankType()))
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    /**
     * Builds test data for the "document payment via Reference Sitewise FIFO" scenario.
     * Cash GL adds CASH payment rows; Bank GL triggers "Ignore &amp; Proceed".
     */
    public static MergedFinanceApVoucherData docRefSitewiseFifoTemplate(LiveDbRefs refs) {
        String id = "DB_AP_REF_FIFO";
        List<SubLedgerInformation> subLedgers = buildSubLedgersForDocument(
                refs.getPendingSlNames(), AdjustmentType.REFERENCE_SITEWISE_FIFO_SETOFF,
                refs.getPendingDocsBySlName(), refs.getSlToRefSiteName(), refs.getSlToArApName());
        List<PaymentInformation> payments = refs.getCashBankType() == CashBankType.CASH
                ? buildPayments(id, subLedgers, SelectDocumentVariant.PaymentMode.CASH)
                : List.of();
        // Build one ReferenceSitewiseFIFOsetoff per SL from the DB-resolved ref site names.
        // Line numbers follow the same 1-based sequential order as buildSubLedgersForDocument.
        List<ReferenceSitewiseFIFOsetoff> fifoList = new ArrayList<>();
        List<SlNameWithId> slNames = refs.getPendingSlNames();
        Map<String, String> slToRefSite = refs.getSlToRefSiteName();
        for (int i = 0; i < slNames.size(); i++) {
            String key = PendingAdjQuery.compositeKey(slNames.get(i).getSlName(), slNames.get(i).getSlId());
            String refSiteName = slToRefSite.get(key);
            if (refSiteName != null) {
                ReferenceSitewiseFIFOsetoff row = new ReferenceSitewiseFIFOsetoff();
                row.setReferenceSiteName(refSiteName);
                row.setSiteName(refSiteName);
                row.setLineNo(String.valueOf(i + 1));
                fifoList.add(row);
            }
        }

        return MergedFinanceApVoucherData.builder()
                .masterTestData(buildMaster("createApVoucherDocumentPayment", id,
                        "REF_FIFO | " + glLabel(refs)))
                .documentInformation(buildDoc(id, refs, FakerUtils.randomRemarks(),
                        FakerUtils.randomReference(), "No"))
                .subLedgerInformationList(subLedgers)
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(fifoList)
                .paymentInformationList(payments)
                .tdsAndNetPayableList(List.of())
                .onAccount(false)
                .tdsProvided(false)
                .paymentProvided(!payments.isEmpty())
                .needsPaymentInfoWarning(computeNeedsPaymentInfoWarning(subLedgers, payments, refs.getCashBankType()))
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    // ─── Shared private builders ──────────────────────────────────────────────

    /**
     * Builds the AP Voucher document header.
     *
     * <p>DocumentInformation constructor covers the 8 @Column-annotated fields:
     * testCaseId, ownersite, voucherno, cashbank, remarks, documentdate, reference, onAccount
     *
     * @param onAccountStr {@code "Yes"} for Advance Payment, {@code "No"} for Document Payment
     */
    private static DocumentInformation buildDoc(
            String id, LiveDbRefs refs, String remarks, String reference, String onAccountStr) {
        return buildDoc(id, refs, remarks, reference, onAccountStr, refs.getCashBankName());
    }

    /**
     * Overload allowing an explicit Cash/Bank value — used by T-03 negative scenarios that
     * deliberately leave Cash/Bank blank ({@code cashbankName = null}) to trigger
     * "Cash/Bank is required" on save.
     */
    private static DocumentInformation buildDoc(
            String id, LiveDbRefs refs, String remarks, String reference, String onAccountStr,
            String cashbankName) {
        return new DocumentInformation(
                id,
                refs.getOwnerSiteName(),
                refs.getDocSchemeName(),
                cashbankName,
                remarks,
                LocalDate.now(),
                reference,
                onAccountStr
        );
    }

    /**
     * Builds sub-ledger rows for <b>Advance Payment</b> scenarios.
     *
     * <p>Amount is fixed at {@link #ADVANCE_DEFAULT_AMOUNT} — no pending document constraint.
     * The vendor SL names are fetched directly from {@code finsl} (not via v_pending_adj).
     *
     * <p>SubLedgerInformation @AllArgsConstructor field order:
     * testCaseID, lineNo, subLedger, id, generalLedger, referenceSite,
     * ledgerNarration, isReversal, deductTds, amountDr, adjustmentType, paymentSelection, deductTdsConfirm
     */
    private static List<SubLedgerInformation> buildSubLedgersForAdvance(
            List<SlNameWithId> slNames,
            Map<String, String> slToArApName,
            Map<String, String> slToRefSiteName) {
        return buildSubLedgersForAdvance(slNames, slToArApName, slToRefSiteName, ADVANCE_DEFAULT_AMOUNT);
    }

    /**
     * Amount-parameterized variant of {@link #buildSubLedgersForAdvance} — used by T-03
     * boundary scenarios that need a specific line amount (e.g. {@code 0.01} at-limit,
     * {@code 0.00} beyond-limit) instead of {@link #ADVANCE_DEFAULT_AMOUNT}.
     */
    private static List<SubLedgerInformation> buildSubLedgersForAdvance(
            List<SlNameWithId> slNames,
            Map<String, String> slToArApName,
            Map<String, String> slToRefSiteName,
            BigDecimal amount) {
        AtomicInteger line = new AtomicInteger(1);
        return slNames.stream()
                .map(slNameWithId -> {
                    String compositeKey = PendingAdjQuery.compositeKey(
                            slNameWithId.getSlName(), slNameWithId.getSlId());
                    String glName  = slToArApName.getOrDefault(compositeKey, null);
                    String refSite = slToRefSiteName.getOrDefault(compositeKey, "");
                    return new SubLedgerInformation(
                            null,
                            String.valueOf(line.getAndIncrement()),
                            slNameWithId.getSlName(),
                            slNameWithId.getSlId(),
                            glName,
                            refSite,        // resolved via 3-way admcls.clstype DB logic
                            null,           // ledgerNarration
                            null,           // isReversal
                            null,           // deductTds — UI auto-populates for TDS-applicable SLs
                            amount,
                            null,           // adjustmentType — not applicable for advance
                            null            // paymentSelection
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Builds sub-ledger rows for <b>Document Payment</b> scenarios.
     *
     * <p>Amount = sum of all pending AP document amounts for each SL
     * (fetched via {@code v_pending_adj WHERE debit_credit='C'}).
     * Falls back to {@code 1.00} if no positive pending amount is found.
     *
     * <p>SubLedgerInformation @AllArgsConstructor field order:
     * testCaseID, lineNo, subLedger, id, generalLedger, referenceSite,
     * ledgerNarration, isReversal, deductTds, amountDr, adjustmentType, paymentSelection, deductTdsConfirm
     */
    private static List<SubLedgerInformation> buildSubLedgersForDocument(
            List<SlNameWithId> slNames,
            AdjustmentType adjType,
            Map<String, List<PendingSlDocument>> pendingDocsBySlName,
            Map<String, String> slToRefSiteName,
            Map<String, String> slToArApName) {
        AtomicInteger line = new AtomicInteger(1);
        return slNames.stream()
                .map(slNameWithId -> {
                    String compositeKey = PendingAdjQuery.compositeKey(
                            slNameWithId.getSlName(), slNameWithId.getSlId());
                    BigDecimal totalPending = pendingDocsBySlName
                            .getOrDefault(compositeKey, List.of())
                            .stream()
                            .map(PendingSlDocument::getPending)
                            .filter(p -> p != null && p.compareTo(BigDecimal.ZERO) > 0)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal amount = totalPending.compareTo(BigDecimal.ZERO) > 0
                            ? totalPending.setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP);
                    String glName  = slToArApName.getOrDefault(compositeKey, null);
                    String refSite = slToRefSiteName.getOrDefault(compositeKey, "");
                    return new SubLedgerInformation(
                            null,
                            String.valueOf(line.getAndIncrement()),
                            slNameWithId.getSlName(),
                            slNameWithId.getSlId(),
                            glName,
                            refSite,        // resolved via 3-way admcls.clstype DB logic
                            null,           // ledgerNarration
                            null,           // isReversal
                            null,           // deductTds
                            amount,
                            adjType,
                            null            // paymentSelection
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Builds TDS &amp; Net Payable rows for Advance-with-TDS scenarios.
     * One row per SL line; {@code tdsName} is resolved from the DB via
     * {@link com.gsl.database.query.TdsNameQuery} and must never be null.
     *
     * <p>The {@code behaviour} parameter drives which TDS Applicable Amount scenario
     * is exercised at test execution time via the business logic → flow → transaction chain.
     *
     * <p>The {@code slToTdsResolution} map carries one {@link TdsResolution} per SL
     * (keyed by {@code "slName|slId"}), resolved in {@code DbApVoucherDataSource} step 5c.
     * {@link Objects#requireNonNull} enforces fail-fast if resolution was missed — a null
     * tdsName would silently leave the popup unset, causing a hard-to-diagnose UI error.
     *
     * <p>TdsAndNetPayable @AllArgsConstructor field order:
     * testCaseID, lineNo, tdsName, grossPayable, tdsApplicableAmount,
     * populateTdsExceptionRate, tdsApplicableAmountBehaviour
     *
     * @param testCaseId         scenario identifier stamped on each row
     * @param subLedgers         SL rows from which gross amounts are derived
     * @param behaviour          which TDS Applicable Amount scenario to generate;
     *                           {@code null} → {@link TdsApplicableAmountBehaviour#GROSS_EQUALS_TDS}
     * @param slToTdsResolution  resolved TDS name+type per SL (key = "slName|slId");
     *                           must contain an entry for every SL in {@code subLedgers}
     */
    private static List<TdsAndNetPayable> buildTdsRows(
            String testCaseId,
            List<SubLedgerInformation> subLedgers,
            TdsApplicableAmountBehaviour behaviour,
            Map<String, TdsResolution> slToTdsResolution) {

        if (behaviour == null) behaviour = TdsApplicableAmountBehaviour.GROSS_EQUALS_TDS;
        final TdsApplicableAmountBehaviour resolvedBehaviour = behaviour;

        return subLedgers.stream()
                .map(sl -> {
                    BigDecimal gross = sl.getAmountDr();
                    // For PARTIAL_EDIT / CLEAR_ERROR_THEN_PARTIAL_EDIT: target differs from gross
                    // For GROSS_EQUALS_TDS: target equals gross (auto-fill is accepted as-is)
                    BigDecimal tdsTarget =
                            (resolvedBehaviour == TdsApplicableAmountBehaviour.GROSS_EQUALS_TDS)
                                    ? gross
                                    : gross.subtract(BigDecimal.ONE).max(BigDecimal.ONE);

                    // Resolve TDS name from DB — fail fast if resolution was not performed
                    String tdsName = Objects.requireNonNull(
                            slToTdsResolution.get(
                                    PendingAdjQuery.compositeKey(sl.getSubLedger(), sl.getId())),
                            "[buildTdsRows] No TDS resolution found for SL '" + sl.getSubLedger() +
                            "' (id=" + sl.getId() + ") — ensure TdsNameQuery ran in step 5c"
                    ).getTdsName();

                    return new TdsAndNetPayable(
                            testCaseId,
                            sl.getLineNo(),
                            tdsName,                    // resolved from fintdstype via TdsNameQuery
                            gross,                      // grossPayable = full SL amount
                            tdsTarget,                  // tdsApplicableAmount — behaviour-specific
                            "No",                       // populateTdsExceptionRate — use configured rate
                            resolvedBehaviour.name()    // tdsApplicableAmountBehaviour — drives flow dispatch
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Converts pending AP document rows into SelectDocument list.
     *
     * <p>All documents belonging to SL at index N carry lineNo = String.valueOf(N+1),
     * matching the parent sub-ledger's lineNo assigned in
     * {@link #buildSubLedgersForDocument}.
     *
     * <p>SelectDocument @AllArgsConstructor field order:
     * testCaseID, lineNo, documentNumber, documentDate, documentType,
     * referenceNo, referenceDate, referenceSite, adjustmentAmount
     */
    private static List<SelectDocument> buildSelectDocuments(
            String testCaseId,
            List<SlNameWithId> pendingSlNames,
            Map<String, List<PendingSlDocument>> pendingDocsBySlName) {
        List<SelectDocument> result = new ArrayList<>();
        for (int i = 0; i < pendingSlNames.size(); i++) {
            String slLineNo = String.valueOf(i + 1);
            SlNameWithId slNameWithId = pendingSlNames.get(i);
            String compositeKey = PendingAdjQuery.compositeKey(
                    slNameWithId.getSlName(), slNameWithId.getSlId());
            List<PendingSlDocument> docs = pendingDocsBySlName.getOrDefault(compositeKey, List.of());
            for (PendingSlDocument doc : docs) {
                if (doc.getPending() == null || doc.getPending().compareTo(BigDecimal.ZERO) <= 0) {
                    Log.warn("[ApVoucherTemplateLoader] Skipping document {} for SL '{}' — pending={} is not positive",
                            doc.getDocumentNo(), slNameWithId.getSlName(), doc.getPending());
                    continue;
                }
                BigDecimal scaledPending = doc.getPending().setScale(2, RoundingMode.HALF_UP);
                if (scaledPending.compareTo(BigDecimal.ZERO) <= 0) {
                    Log.warn("[ApVoucherTemplateLoader] Skipping document {} for SL '{}' — " +
                             "pending {} rounds to 0.00 after scale.",
                             doc.getDocumentNo(), slNameWithId.getSlName(), doc.getPending());
                    continue;
                }
                result.add(new SelectDocument(
                        testCaseId,
                        slLineNo,
                        doc.getDocumentNo(),
                        doc.getDocumentDate(),
                        doc.getEntype(),
                        doc.getRefNo(),
                        doc.getDocumentDate(),   // referenceDate = documentDate
                        doc.getReferenceSite(),
                        scaledPending
                ));
            }
        }
        return result;
    }

    /**
     * Builds payment information rows for the given payment mode.
     *
     * <p>PaymentInformation @AllArgsConstructor field order:
     * testCaseID, lineNo, paymentMode, instrumentNo, instrumentDate, drawnOn, reference
     *
     * <p>Cash payments carry no instrument number.
     * Cheque generates a unique instrument number via {@link UniqueChequeGenerator}.
     * NEFT/RTGS generates a unique reference via {@code PaymentReferenceGenerator}.
     */
    private static List<PaymentInformation> buildPayments(
            String testCaseId,
            List<SubLedgerInformation> subLedgers,
            SelectDocumentVariant.PaymentMode paymentMode) {

        if (paymentMode == SelectDocumentVariant.PaymentMode.NONE || subLedgers.isEmpty()) {
            return List.of();
        }

        List<PaymentInformation> payments = new ArrayList<>();
        String today = LocalDate.now().toString();

        for (int i = 0; i < subLedgers.size(); i++) {
            SubLedgerInformation sl = subLedgers.get(i);
            PaymentSelectionType mode = resolvePaymentType(paymentMode, i);

            String instrumentNo = null;
            if (mode == PaymentSelectionType.CHEQUE) {
                instrumentNo = UniqueChequeGenerator.generateUnique();
            } else if (mode == PaymentSelectionType.NEFT_RTGS) {
                instrumentNo = com.gsl.utils.payment.PaymentReferenceGenerator.generateNEFT();
            }

            payments.add(new PaymentInformation(
                    testCaseId,
                    sl.getLineNo(),
                    mode,
                    instrumentNo,
                    today,
                    null,       // drawnOn
                    null        // reference
            ));
        }
        return payments;
    }

    /**
     * Resolves the actual {@link PaymentSelectionType} for a given SL index.
     * MIX: even index (0, 2, ...) = NEFT/RTGS; odd index (1, 3, ...) = Cheque.
     */
    private static PaymentSelectionType resolvePaymentType(
            SelectDocumentVariant.PaymentMode paymentMode, int slIndex) {
        switch (paymentMode) {
            case CHEQUE:    return PaymentSelectionType.CHEQUE;
            case NEFT_RTGS: return PaymentSelectionType.NEFT_RTGS;
            case CASH:      return PaymentSelectionType.CASH;
            case MIX:       return slIndex % 2 == 0
                                ? PaymentSelectionType.NEFT_RTGS
                                : PaymentSelectionType.CHEQUE;
            case RANDOM:    throw new IllegalStateException(
                                "PaymentMode.RANDOM must be resolved before calling buildPayments — " +
                                "resolve in the template method, not here.");
            default:        return PaymentSelectionType.CHEQUE;
        }
    }

    /**
     * Computes whether the "Ignore &amp; Proceed" instrument-number warning dialog will appear.
     *
     * <p>The popup fires when the GL is Bank type ({@code srctype='B'}) and any SL line
     * is missing a payment row or the payment row has no instrument number.
     * Cash GLs ({@code srctype='C'}) never trigger this popup.
     */
    private static boolean computeNeedsPaymentInfoWarning(
            List<SubLedgerInformation> subLedgers,
            List<PaymentInformation> payments,
            CashBankType cashBankType) {

        if (cashBankType != CashBankType.BANK) {
            return false;   // Cash GL → CASH payments always provided → popup never fires
        }

        Set<String> slLineNos = subLedgers.stream()
                .map(SubLedgerInformation::getLineNo)
                .filter(ln -> ln != null && !ln.isBlank())
                .collect(Collectors.toSet());

        Set<String> paymentLineNos = payments.stream()
                .map(PaymentInformation::getLineNo)
                .filter(ln -> ln != null && !ln.isBlank())
                .collect(Collectors.toSet());

        boolean allLinesHavePayment = !slLineNos.isEmpty()
                && paymentLineNos.containsAll(slLineNos);

        boolean anyInstrumentNoMissing = payments.stream()
                .anyMatch(p -> p.getPaymentMode() != PaymentSelectionType.CASH
                            && (p.getInstrumentNo() == null || p.getInstrumentNo().isBlank()));

        return !allLinesHavePayment || anyInstrumentNoMissing;
    }

    /**
     * Builds the MasterTestData for this AP Voucher scenario.
     *
     * <p>MasterTestData @AllArgsConstructor field order:
     * testcasename, expectedTitle, age, date, testcaseid, invocationcount
     */
    private static MasterTestData buildMaster(String methodName, String id, String testCaseName) {
        return new MasterTestData(testCaseName, null, null, null, id, 1);
    }

    /** Short GL type label for test case names — wrapped in square brackets to match AR Voucher style. */
    private static String glLabel(LiveDbRefs refs) {
        return refs.getCashBankType() == CashBankType.CASH ? "[Cash GL]" : "[Bank GL]";
    }
}
