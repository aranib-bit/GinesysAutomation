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
                refs.getPendingSlNames(), refs.getSlToArApName(), refs.getOwnerSiteName());
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
                refs.getPendingSlNames(), refs.getSlToArApName(), refs.getOwnerSiteName());
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
                refs.getPendingDocsBySlName(), refs.getOwnerSiteName(), refs.getSlToArApName());
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
     */
    public static MergedFinanceApVoucherData docSelectDocumentTemplate(LiveDbRefs refs) {
        String id = "DB_AP_SEL_DOC";
        List<SubLedgerInformation> subLedgers = buildSubLedgersForDocument(
                refs.getPendingSlNames(), AdjustmentType.SELECT_DOCUMENT,
                refs.getPendingDocsBySlName(), refs.getOwnerSiteName(), refs.getSlToArApName());
        List<PaymentInformation> payments = List.of();
        return MergedFinanceApVoucherData.builder()
                .masterTestData(buildMaster("createApVoucherDocumentPayment", id,
                        "SEL_DOC | " + glLabel(refs)))
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
                .paymentProvided(false)
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
                refs.getPendingDocsBySlName(), refs.getOwnerSiteName(), refs.getSlToArApName());
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
                refs.getPendingDocsBySlName(), refs.getOwnerSiteName(), refs.getSlToArApName());
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
        return new DocumentInformation(
                id,
                refs.getOwnerSiteName(),
                refs.getDocSchemeName(),
                refs.getCashBankName(),
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
            String ownerSiteName) {
        AtomicInteger line = new AtomicInteger(1);
        return slNames.stream()
                .map(slNameWithId -> {
                    String compositeKey = PendingAdjQuery.compositeKey(
                            slNameWithId.getSlName(), slNameWithId.getSlId());
                    String glName = slToArApName.getOrDefault(compositeKey, null);
                    return new SubLedgerInformation(
                            null,
                            String.valueOf(line.getAndIncrement()),
                            slNameWithId.getSlName(),
                            slNameWithId.getSlId(),
                            glName,
                            ownerSiteName,
                            null,           // ledgerNarration
                            null,           // isReversal
                            null,           // deductTds — UI auto-populates for TDS-applicable SLs
                            ADVANCE_DEFAULT_AMOUNT,
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
            String ownerSiteName,
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
                    String glName = slToArApName.getOrDefault(compositeKey, null);
                    return new SubLedgerInformation(
                            null,
                            String.valueOf(line.getAndIncrement()),
                            slNameWithId.getSlName(),
                            slNameWithId.getSlId(),
                            glName,
                            ownerSiteName,
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
            case CHEQUE:   return PaymentSelectionType.CHEQUE;
            case NEFT_RTGS: return PaymentSelectionType.NEFT_RTGS;
            case CASH:     return PaymentSelectionType.CASH;
            case MIX:      return slIndex % 2 == 0
                               ? PaymentSelectionType.NEFT_RTGS
                               : PaymentSelectionType.CHEQUE;
            default:       return PaymentSelectionType.CHEQUE;
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
