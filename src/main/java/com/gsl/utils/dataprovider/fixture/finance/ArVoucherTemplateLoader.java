package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.database.DocumentReservationRegistry;
import com.gsl.database.dialect.DbDialect;
import com.gsl.database.dialect.DbDialectFactory;
import com.gsl.database.query.AdmClsQuery;
import com.gsl.database.query.FinVchRefQuery;
import com.gsl.utils.DateUtils;
import com.gsl.utils.Faker.FakerUtils;
import com.gsl.database.query.PendingAdjQuery;
import com.gsl.model.finance.AdmCls;
import com.gsl.enums.AdjustmentType;
import com.gsl.enums.CashBankType;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.model.finance.PendingSlDocument;
import com.gsl.model.finance.SlNameWithId;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.ReferenceSitewiseFIFOsetoff;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SelectDocument;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformation;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.database.DatabaseManager;
import com.gsl.utils.Log;
import com.gsl.utils.cheque.ChequeInstrumentResolver;
import com.gsl.utils.cheque.UniqueChequeGenerator;
import com.gsl.utils.dataprovider.fixture.ArVoucherLiveDbRefs;
import org.jdbi.v3.core.Jdbi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Builds {@link MergedFinanceArVoucherData} instances from live DB values — the AR analogue of
 * {@code ApVoucherTemplateLoader}. Each public static method is one named scenario template,
 * called by {@link ArVoucherScenarioType#loadData(ArVoucherLiveDbRefs)}.
 *
 * <h3>Template shapes</h3>
 * <ul>
 *   <li><b>Fixed templates</b> ({@link #docNoAdjustmentTemplate}, {@link #fifoTemplate},
 *       {@link #refSitewiseFifoTemplate}, and the T-03 negative/boundary templates) — one
 *       hand-authored scenario each, no combination dimension.</li>
 *   <li><b>SELECT_DOCUMENT</b> — the one template with a genuine combination matrix: the
 *       no-arg {@link #selectDocumentTemplate(ArVoucherLiveDbRefs)} builds the plain scenario,
 *       while the 4-arg {@link #selectDocumentTemplate(ArVoucherLiveDbRefs, Jdbi, List,
 *       SelectDocumentVariant)} builds one {@link SelectDocumentVariant} combination from
 *       {@code SelectDocumentCombinations} — resolving date/reference/reference-site/payment-mode
 *       dimensions via this class's own {@code resolveDocumentDate}/{@code generateReference}/
 *       {@code resolveReferenceSite}/{@code buildPayments} private helpers.</li>
 * </ul>
 */
public final class ArVoucherTemplateLoader {

    // ─── Template 1: SELECT_DOCUMENT ─────────────────────────────────────────
    /**
     * Builds test data for the "manually select open documents" scenario.
     * {@code selectDocumentList} is pre-populated from {@link ArVoucherLiveDbRefs#getPendingDocsBySlName()}
     * so the predicate knows exactly which document numbers to tick in the UI popup.
     */
    public static MergedFinanceArVoucherData selectDocumentTemplate(ArVoucherLiveDbRefs refs) {
        String id = "DB_SEL_DOC";
        List<SubLedgerInformation> subLedgers = buildSubLedgers(
                refs.getPendingSlNames(), AdjustmentType.SELECT_DOCUMENT,
                refs.getPendingDocsBySlName(), refs.getOwnerSiteName(),
                refs.getSlToArApName());
        List<PaymentInformation> payments = List.of();
        return MergedFinanceArVoucherData.builder()
                .masterTestData(buildMaster("createArVoucher", id))
                .documentInformation(buildDoc(id, refs, FakerUtils.randomRemarks(), FakerUtils.randomReference()))
                .subLedgerInformationList(subLedgers)
                .selectDocumentList(buildSelectDocuments(id, refs.getPendingSlNames(), refs.getPendingDocsBySlName()))
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(payments)
                .paymentProvided(false)
                .needsPaymentInfoWarning(computeNeedsPaymentInfoWarning(subLedgers, payments, refs.getCashBankType()))
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    // ─── Template 1 (variant overload): SELECT_DOCUMENT with parameterised combination ──

    /**
     * Builds test data for one specific {@link SelectDocumentVariant} combination.
     *
     * <p>The {@code refs} object already contains the correctly-scoped pending SLs and
     * documents (fetched by {@link com.gsl.utils.dataprovider.source.finance.DbArVoucherDataSource}
     * using the variant's {@code slCount}/{@code docsPerSl}/{@code slClsCode}).
     *
     * @param refs      live DB reference values (SLs and docs already filtered to variant scope)
     * @param jdbi      active JDBI instance (used for reference uniqueness and alt-site queries)
     * @param siteCodes site codes of the test user (used for uniqueness scoping)
     * @param variant   the combination to build data for
     */
    public static MergedFinanceArVoucherData selectDocumentTemplate(
            ArVoucherLiveDbRefs refs, Jdbi jdbi, List<Long> siteCodes, SelectDocumentVariant variant) {

        String id = variant.getVariantId();

        // ── 0. Resolve payment mode — enforce GL-type rules ───────────────────
        // Cash GL: always CASH (payment_mode='H' for all SL lines).
        // Bank GL with RANDOM: resolve to CHEQUE or NEFT/RTGS only — NONE is a dedicated separate variant.
        SelectDocumentVariant.PaymentMode effectivePaymentMode = variant.getPaymentMode();
        if (refs.getCashBankType() == CashBankType.CASH) {
            if (effectivePaymentMode != SelectDocumentVariant.PaymentMode.CASH) {
                Log.info("[Template] Cash GL detected — overriding payment mode from {} to CASH (payment_mode='H')",
                        effectivePaymentMode);
                effectivePaymentMode = SelectDocumentVariant.PaymentMode.CASH;
            }
        } else if (effectivePaymentMode == SelectDocumentVariant.PaymentMode.RANDOM) {
            List<SelectDocumentVariant.PaymentMode> pool = List.of(
                    SelectDocumentVariant.PaymentMode.CHEQUE,
                    SelectDocumentVariant.PaymentMode.NEFT_RTGS);
            effectivePaymentMode = FakerUtils.randomElement(pool);
            Log.info("[Template] RANDOM payment → resolved to: {} (GL type: Bank)", effectivePaymentMode);
        }

        // ── 1. Document date ──────────────────────────────────────────────────
        LocalDate docDate = resolveDocumentDate(variant);

        // ── 2. Reference ──────────────────────────────────────────────────────
        String reference = generateReference(jdbi, siteCodes, variant);

        // ── 3. Reference site for SL rows (customer-type variants only) ───────
        String refSite = resolveReferenceSite(variant, refs, jdbi);

        // ── 4. Select documents with amount-mode-adjusted amounts ─────────────
        List<SelectDocument> selectDocs = buildSelectDocumentsWithAmountMode(
                id, refs.getPendingSlNames(), refs.getPendingDocsBySlName(), variant.getAmountMode());

        if (selectDocs.isEmpty()) {
            // Data-availability shortfall → skip (amber), not fail (red).
            throw new org.testng.SkipException(
                "No selectable documents for SELECT_DOCUMENT variant '" + id + "' — " +
                "all fetched documents had pending amounts that round to 0.00 or were exhausted " +
                "by concurrent reservations. Pending SLs: " + refs.getPendingSlNames() +
                ". Increase test data or reduce parallel thread count.");
        }

        // ── 5. Per-SL sum of adjusted document amounts ────────────────────────
        Map<String, BigDecimal> docTotalBySl = computeDocTotalPerSl(
                refs.getPendingSlNames(), selectDocs);

        // ── 6. Sub-ledger rows: amountCr from doc totals + grid relation shift ─
        List<SubLedgerInformation> subLedgers = buildSubLedgersForVariant(
                refs.getPendingSlNames(), refs.getSlToArApName(),
                refSite, docTotalBySl, variant.getGridRelation());

        // ── 6a. Resolve SL classification type (Customer/Supplier/etc.) ───────
        for (SubLedgerInformation sl : subLedgers) {
            sl.setSlType(AdmClsQuery.findClsnameBySlId(jdbi, sl.getId()));
        }

        // ── 7. Payment rows (uses resolved mode, not the raw RANDOM enum) ─────
        // Round 20: known-vs-unknown (and, for the rare known case, same-bank vs. different-bank)
        // was already decided by ArVoucherFixtureFactory.assignKnownChequeFlags/
        // assignChequeBankMatchFlags (an exact-ratio "controlled sequence" over ALL of this run's
        // CHEQUE rows) — buildPayments' 6-arg overload just resolves the actual chqno for
        // whichever outcome variant already carries.
        long cashBankGlcode = jdbi.onDemand(com.gsl.database.dao.finance.FinGlDao.class)
                .findByGlName(refs.getCashBankName()).getGlcode();
        List<PaymentInformation> payments = buildPayments(id, subLedgers, effectivePaymentMode,
                variant.isKnownCheque(), variant.isKnownChequeDifferentBank(), cashBankGlcode);

        // ── 8. Document header ────────────────────────────────────────────────
        DocumentInformation docInfo = new DocumentInformation(
                id,
                refs.getOwnerSiteName(),
                refs.getDocSchemeName(),
                refs.getCashBankName(),
                FakerUtils.randomRemarks(),
                docDate,
                reference);

        // Dynamic test case name: shows resolved payment mode (never "RANDOM")
        String dynamicTestCaseName = buildDynamicTestCaseName(variant, jdbi, effectivePaymentMode);

        return MergedFinanceArVoucherData.builder()
                .masterTestData(buildMaster("createArVoucher", id, dynamicTestCaseName))
                .documentInformation(docInfo)
                .subLedgerInformationList(subLedgers)
                .selectDocumentList(selectDocs)
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(payments)
                .paymentProvided(!payments.isEmpty())
                .needsPaymentInfoWarning(computeNeedsPaymentInfoWarning(subLedgers, payments, refs.getCashBankType()))
                .gridRelation(variant.getGridRelation())
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    // ─── Template 0: DOC_NO_ADJUSTMENT ──────────────────────────────────────
    /**
     * Builds test data for the "no adjustment" scenario — mandatory fields only.
     * One SL line, amountCr = total pending for that SL, no adjustment type set,
     * no payment rows (Bank GL triggers "Ignore & Proceed" on save).
     */
    public static MergedFinanceArVoucherData docNoAdjustmentTemplate(ArVoucherLiveDbRefs refs) {
        String id = "DB_NO_ADJ";
        List<SubLedgerInformation> subLedgers = buildSubLedgers(
                refs.getPendingSlNames(), null,
                refs.getPendingDocsBySlName(), refs.getOwnerSiteName(),
                refs.getSlToArApName());
        List<PaymentInformation> payments = List.of();
        return MergedFinanceArVoucherData.builder()
                .masterTestData(buildMaster("createArVoucher", id))
                .documentInformation(buildDoc(id, refs, FakerUtils.randomRemarks(), FakerUtils.randomReference()))
                .subLedgerInformationList(subLedgers)
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(payments)
                .paymentProvided(false)
                .needsPaymentInfoWarning(computeNeedsPaymentInfoWarning(subLedgers, payments, refs.getCashBankType()))
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    // ─── Template 2: FIFO_SETOFF ──────────────────────────────────────────────
    /**
     * Builds test data for the FIFO automatic set-off scenario.
     * No pre-loaded document list — FIFO auto-selects documents in the UI.
     */
    public static MergedFinanceArVoucherData fifoTemplate(ArVoucherLiveDbRefs refs) {
        String id = "DB_FIFO";
        List<SubLedgerInformation> subLedgers = buildSubLedgers(
                refs.getPendingSlNames(), AdjustmentType.FIFO_SETOFF,
                refs.getPendingDocsBySlName(), refs.getOwnerSiteName(),
                refs.getSlToArApName());
        // Cash GL → add CASH payment rows so all SL lines carry payment_mode='H'
        List<PaymentInformation> payments = refs.getCashBankType() == CashBankType.CASH
                ? buildPayments(id, subLedgers, SelectDocumentVariant.PaymentMode.CASH)
                : List.of();
        return MergedFinanceArVoucherData.builder()
                .masterTestData(buildMaster("createArVoucher", id))
                .documentInformation(buildDoc(id, refs, FakerUtils.randomRemarks(), FakerUtils.randomReference()))
                .subLedgerInformationList(subLedgers)
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(payments)
                .paymentProvided(!payments.isEmpty())
                .needsPaymentInfoWarning(computeNeedsPaymentInfoWarning(subLedgers, payments, refs.getCashBankType()))
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    // ─── Template 3: REFERENCE_SITEWISE_FIFO ─────────────────────────────────
    /**
     * Builds test data for the Reference Sitewise FIFO scenario.
     */
    public static MergedFinanceArVoucherData refSitewiseFifoTemplate(ArVoucherLiveDbRefs refs) {
        String id = "DB_REF_FIFO";
        List<SubLedgerInformation> subLedgers = buildSubLedgers(
                refs.getPendingSlNames(), AdjustmentType.REFERENCE_SITEWISE_FIFO_SETOFF,
                refs.getPendingDocsBySlName(), refs.getOwnerSiteName(),
                refs.getSlToArApName());
        // Cash GL → add CASH payment rows so all SL lines carry payment_mode='H'
        List<PaymentInformation> payments = refs.getCashBankType() == CashBankType.CASH
                ? buildPayments(id, subLedgers, SelectDocumentVariant.PaymentMode.CASH)
                : List.of();
        // Build one ReferenceSitewiseFIFOsetoff per SL from the DB-resolved ref site names.
        // Line numbers follow the same 1-based sequential order as buildSubLedgers so the
        // ARVoucherSubLedgerInformation.fillSubLedgerRows lineNo filter matches.
        List<ReferenceSitewiseFIFOsetoff> fifoList = new ArrayList<>();
        List<SlNameWithId> slNames = refs.getPendingSlNames();
        Map<String, String> slToRefSite = refs.getSlToRefSiteName();
        for (int i = 0; i < slNames.size(); i++) {
            String key = PendingAdjQuery.compositeKey(slNames.get(i).getSlName(), slNames.get(i).getSlId());
            String refSiteName = slToRefSite.get(key);
            if (refSiteName == null) continue;
            ReferenceSitewiseFIFOsetoff row = new ReferenceSitewiseFIFOsetoff();
            row.setReferenceSiteName(refSiteName);
            row.setSiteName(refSiteName);
            row.setLineNo(String.valueOf(i + 1));
            fifoList.add(row);
        }
        return MergedFinanceArVoucherData.builder()
                .masterTestData(buildMaster("createArVoucher", id))
                .documentInformation(buildDoc(id, refs, FakerUtils.randomRemarks(), FakerUtils.randomReference()))
                .subLedgerInformationList(subLedgers)
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(fifoList)
                .paymentInformationList(payments)
                .paymentProvided(!payments.isEmpty())
                .needsPaymentInfoWarning(computeNeedsPaymentInfoWarning(subLedgers, payments, refs.getCashBankType()))
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    // ─── T-03 Negative & Boundary templates ──────────────────────────────────

    /** NEG_BLANK_CASHBANK — valid SL, Cash/Bank left blank → "Cash/Bank is required". */
    public static MergedFinanceArVoucherData negBlankCashbankTemplate(ArVoucherLiveDbRefs refs) {
        String id = "DB_NEG_BLANK_CASHBANK";
        List<SubLedgerInformation> subLedgers = buildSubLedgers(
                refs.getPendingSlNames(), null, refs.getPendingDocsBySlName(),
                refs.getOwnerSiteName(), refs.getSlToArApName());
        return negativeData(id, "NEGATIVE | Blank Cash/Bank", refs, subLedgers,
                null, FakerUtils.randomReference(), NegativeValidation.BLANK_CASHBANK);
    }

    /** NEG_BLANK_SUBLEDGER — valid Cash/Bank, no SL row filled (empty grid) → "Sub-Ledger is required". */
    public static MergedFinanceArVoucherData negBlankSubledgerTemplate(ArVoucherLiveDbRefs refs) {
        String id = "DB_NEG_BLANK_SUBLEDGER";
        return negativeData(id, "NEGATIVE | Blank Sub-Ledger", refs, List.of(),
                refs.getCashBankName(), FakerUtils.randomReference(), NegativeValidation.BLANK_SUBLEDGER_AR);
    }

    /** NEG_BLANK_MANDATORY_REFERENCE — Reference left blank → "Reference No. is required"
     *  (only when the resolved scheme makes Reference mandatory). */
    public static MergedFinanceArVoucherData negBlankReferenceTemplate(ArVoucherLiveDbRefs refs) {
        String id = "DB_NEG_BLANK_REF";
        List<SubLedgerInformation> subLedgers = buildSubLedgers(
                refs.getPendingSlNames(), null, refs.getPendingDocsBySlName(),
                refs.getOwnerSiteName(), refs.getSlToArApName());
        return negativeData(id, "NEGATIVE | Blank Mandatory Reference", refs, subLedgers,
                refs.getCashBankName(), null, NegativeValidation.BLANK_MANDATORY_REFERENCE);
    }

    /** BOUND_AMOUNT_MIN_VALID — smallest positive line amount (0.01) → must save. */
    public static MergedFinanceArVoucherData boundAmountMinValidTemplate(ArVoucherLiveDbRefs refs) {
        String id = "DB_BOUND_AMT_MIN_VALID";
        List<SubLedgerInformation> subLedgers = buildSubLedgersWithAmount(
                refs.getPendingSlNames(), refs.getOwnerSiteName(), refs.getSlToArApName(),
                new BigDecimal("0.01"));
        List<PaymentInformation> payments = refs.getCashBankType() == CashBankType.CASH
                ? buildPayments(id, subLedgers, SelectDocumentVariant.PaymentMode.CASH)
                : List.of();
        return MergedFinanceArVoucherData.builder()
                .masterTestData(buildMaster("createArVoucherNegativeBoundary", id, "BOUNDARY | Min Amount 0.01"))
                .documentInformation(buildDoc(id, refs, FakerUtils.randomRemarks(), FakerUtils.randomReference()))
                .subLedgerInformationList(subLedgers)
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(payments)
                .paymentProvided(!payments.isEmpty())
                .needsPaymentInfoWarning(computeNeedsPaymentInfoWarning(subLedgers, payments, refs.getCashBankType()))
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    /** BOUND_AMOUNT_MIN_INVALID — zero line amount → rejected (NegativeValidation.ZERO_AMOUNT). */
    public static MergedFinanceArVoucherData boundAmountMinInvalidTemplate(ArVoucherLiveDbRefs refs) {
        String id = "DB_BOUND_AMT_MIN_INVALID";
        List<SubLedgerInformation> subLedgers = buildSubLedgersWithAmount(
                refs.getPendingSlNames(), refs.getOwnerSiteName(), refs.getSlToArApName(),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        return negativeData(id, "BOUNDARY | Zero Amount (reject)", refs, subLedgers,
                refs.getCashBankName(), FakerUtils.randomReference(), NegativeValidation.ZERO_AMOUNT_DIALOG);
    }

    /**
     * Shared builder for negative/boundary-invalid AR data: a valid SL set (or empty for blank-SL),
     * the named field overridden, and {@code expectedValidation} stamped so
     * {@code NEGATIVE_FORM_VALIDATION_BEHAVIOR} asserts the right popup. No payment.
     */
    private static MergedFinanceArVoucherData negativeData(
            String id, String name, ArVoucherLiveDbRefs refs,
            List<SubLedgerInformation> subLedgers,
            String cashbank, String reference, NegativeValidation expected) {
        return MergedFinanceArVoucherData.builder()
                .masterTestData(buildMaster("createArVoucherNegativeBoundary", id, name))
                .documentInformation(buildDoc(id, refs, FakerUtils.randomRemarks(), reference, cashbank))
                .subLedgerInformationList(subLedgers)
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(List.of())
                .paymentProvided(false)
                .needsPaymentInfoWarning(false)
                .expectedValidation(expected)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    /** Cash/Bank override variant of {@link #buildDoc(String, ArVoucherLiveDbRefs, String, String)} — used by
     *  NEG_BLANK_CASHBANK ({@code cashbankName = null}). */
    private static DocumentInformation buildDoc(
            String id, ArVoucherLiveDbRefs refs, String remarks, String reference, String cashbankName) {
        return new DocumentInformation(
                id,
                refs.getOwnerSiteName(),
                refs.getDocSchemeName(),
                cashbankName,
                remarks,
                LocalDate.now(),
                reference
        );
    }

    /** Builds one SL row per pending SL with an explicit {@code amountCr} and no adjustment —
     *  used by T-03 boundary amount scenarios. */
    private static List<SubLedgerInformation> buildSubLedgersWithAmount(
            List<SlNameWithId> slNames, String ownerSiteName,
            Map<String, String> slToArApName, BigDecimal amount) {
        AtomicInteger line = new AtomicInteger(1);
        return slNames.stream()
                .map(sl -> {
                    String key = PendingAdjQuery.compositeKey(sl.getSlName(), sl.getSlId());
                    String glName = slToArApName.getOrDefault(key, null);
                    return new SubLedgerInformation(
                            null,
                            String.valueOf(line.getAndIncrement()),
                            sl.getSlName(),
                            sl.getSlId(),
                            glName, ownerSiteName, null, null,
                            amount,
                            null,
                            null
                    );
                })
                .collect(Collectors.toList());
    }

    // ─── Shared private builders ──────────────────────────────────────────────

    /**
     * DocumentInformation @AllArgsConstructor field order:
     * testCaseId, ownersite, voucherno, cashbank, remarks, documentdate, reference
     *
     * Note: voucherno carries the doc scheme name (e.g. "AR VOUCHER") — this is what
     * ArVoucherAdd.setDocScheme() receives.
     */
    private static DocumentInformation buildDoc(
            String id, ArVoucherLiveDbRefs refs, String remarks, String reference) {
        return new DocumentInformation(
                id,
                refs.getOwnerSiteName(),
                refs.getDocSchemeName(),   // voucherno = doc scheme selector in the UI
                refs.getCashBankName(),
                remarks,
                LocalDate.now(),
                reference
        );
    }

    /**
     * SubLedgerInformation @AllArgsConstructor field order:
     * testCaseID, lineNo, subLedger, id, generalLedger, referenceSite,
     * ledgerNarration, isReversal, amountCr, adjustmentType, paymentSelection
     *
     * <p>{@code subLedger} is set to {@code slNameWithId.getSlName()} and
     * {@code id} is set to {@code slNameWithId.getSlId()} so the UI can
     * uniquely identify the correct SL when two SLs share the same display name.
     *
     * <p>amountCr is generated as a random value less than the total pending amount
     * for each SL (sum of doc.getPending() across all selected documents).
     * This ensures the voucher amount is realistic and passes server-side validation.
     */
    private static List<SubLedgerInformation> buildSubLedgers(
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
                    // Sum all pending amounts for this SL's documents
                    BigDecimal totalPending = pendingDocsBySlName
                            .getOrDefault(compositeKey, List.of())
                            .stream()
                            .map(PendingSlDocument::getPending)
                            .filter(p -> p != null && p.compareTo(BigDecimal.ZERO) > 0)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    // amountCr = total pending so it exactly matches the sum of the
                    // individual document adjustment amounts in selectDocumentList.
                    BigDecimal amount = totalPending.compareTo(BigDecimal.ZERO) > 0
                            ? totalPending.setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP);
                    String glName = slToArApName.getOrDefault(compositeKey, null);
                    return new SubLedgerInformation(
                            null,
                            String.valueOf(line.getAndIncrement()),
                            slNameWithId.getSlName(),
                            slNameWithId.getSlId(),   // slId for unique SL selection in the UI
                            glName, ownerSiteName, null, null,
                            amount,
                            adjType,
                            null
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Converts PendingSlDocument rows → SelectDocument list.
     *
     * IMPORTANT: The existing test filters SelectDocuments by lineNo matching the
     * sub-ledger's lineNo:
     *   data.getSelectDocumentList().stream()
     *       .filter(d -> subLedgerLineNo.equalsIgnoreCase(d.getLineNo()))
     *
     * Therefore, all documents belonging to SL at index N must carry lineNo = String.valueOf(N+1),
     * matching the sub-ledger's lineNo assigned in {@link #buildSubLedgers}.
     *
     * <p>The pending document map is keyed by {@code "slName|slId"} (composite key).
     *
     * SelectDocument @AllArgsConstructor field order:
     * testCaseID, lineNo, documentNumber, documentDate, documentType,
     * referenceNo, referenceDate, referenceSite, adjustmentAmount
     */
    private static List<SelectDocument> buildSelectDocuments(
            String testCaseId,
            List<SlNameWithId> pendingSlNames,
            Map<String, List<PendingSlDocument>> pendingDocsBySlName) {
        List<SelectDocument> result = new ArrayList<>();
        for (int i = 0; i < pendingSlNames.size(); i++) {
            String slLineNo = String.valueOf(i + 1);   // matches sub-ledger lineNo
            SlNameWithId slNameWithId = pendingSlNames.get(i);
            String compositeKey = PendingAdjQuery.compositeKey(
                    slNameWithId.getSlName(), slNameWithId.getSlId());
            List<PendingSlDocument> docs = pendingDocsBySlName.getOrDefault(compositeKey, List.of());
            for (PendingSlDocument doc : docs) {
                if (doc.getPending() == null || doc.getPending().compareTo(BigDecimal.ZERO) <= 0) {
                    Log.warn("[ArVoucherTemplateLoader] Skipping document {} for SL '{}' — pending={} is not positive",
                            doc.getDocumentNo(), slNameWithId.getSlName(), doc.getPending());
                    continue;
                }
                BigDecimal scaledPending = doc.getPending().setScale(2, RoundingMode.HALF_UP);
                if (scaledPending.compareTo(BigDecimal.ZERO) <= 0) {
                    Log.warn("[ArVoucherTemplateLoader] Skipping document {} for SL '{}' — " +
                             "pending {} rounds to 0.00 after scale.",
                             doc.getDocumentNo(), slNameWithId.getSlName(), doc.getPending());
                    continue;
                }
                result.add(new SelectDocument(
                        testCaseId,
                        slLineNo,                       // same lineNo as the parent sub-ledger row
                        doc.getDocumentNo(),
                        doc.getDocumentDate(),
                        doc.getEntype(),
                        doc.getRefNo(),
                        doc.getDocumentDate(),           // referenceDate = documentDate
                        doc.getReferenceSite(),
                        scaledPending
                ));
            }
        }
        return result;
    }

    /**
     * MasterTestData @AllArgsConstructor field order:
     * testcasename, username, password, expectedTitle, age, date, testcaseid, invocationcount
     */
    private static MasterTestData buildMaster(String methodName, String id) {
        return new MasterTestData(methodName, null, null, null, id, 1);
    }

    /**
     * Overloaded buildMaster that sets a dynamic human-readable test case name for
     * variant-driven SELECT_DOCUMENT combinations.
     *
     * @param methodName  the test method name (e.g. "createArVoucher")
     * @param id          the variant ID used as testcaseid (e.g. "SD_1SL_1DOC_FULL")
     * @param testCaseName the descriptive name shown in reports (from SelectDocumentVariant)
     */
    private static MasterTestData buildMaster(String methodName, String id, String testCaseName) {
        return new MasterTestData(testCaseName, null, null, null, id, 1);
    }

    /**
     * Computes whether the "Ignore & Proceed" instrument-number warning dialog will appear
     * after Save.
     *
     * <p>The popup fires when the GL is Bank type ({@code srctype='B'}) and any SL line
     * is missing a payment row or the payment row has no instrument number.
     * Cash GLs ({@code srctype='C'}) do not require instrument numbers — the popup does
     * not appear, and Cash payment rows intentionally have no instrument number.
     *
     * @param cashBankType the GL type — {@link CashBankType#CASH} or {@link CashBankType#BANK}
     */
    private static boolean computeNeedsPaymentInfoWarning(
            List<SubLedgerInformation> subLedgers,
            List<PaymentInformation> payments,
            CashBankType cashBankType) {

        boolean isBank = cashBankType == CashBankType.BANK;  // Cash GL → CASH payments always provided → popup never fires

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

        // Cash payment rows have no instrument number by design — exempt them from the check
        boolean anyInstrumentNoMissing = payments.stream()
                .anyMatch(p -> p.getPaymentMode() != com.gsl.enums.PaymentSelectionType.CASH
                            && (p.getInstrumentNo() == null || p.getInstrumentNo().isBlank()));

        return isBank && (!allLinesHavePayment || anyInstrumentNoMissing);
    }

    // ─── Variant-specific private helpers ────────────────────────────────────

    /**
     * Resolves the document date for the AR Voucher header.
     *
     * <p>For BACK_DATED variants the requested {@code backDateDays} is clamped to
     * {@code finance.backdate.max.days} from {@code config.properties} (default 58).
     * If the variant's offset exceeds the ceiling the test self-skips with a clear
     * message rather than submitting a date that falls outside the open posting window.
     *
     * <p>Full posting-window validation (finparams lock-days, open Finance Month,
     * open Accounting Year) is left as a future improvement once the DB schema for
     * these boundaries is confirmed.
     */
    private static LocalDate resolveDocumentDate(SelectDocumentVariant variant) {
        if (variant.getDateMode() != SelectDocumentVariant.DateMode.BACK_DATED) {
            return LocalDate.now();
        }

        int requested = variant.getBackDateDays();
        int maxDays   = ConfigFactory.getFinanceConfig().financeBackdateMaxDays();

        if (requested > maxDays) {
            throw new org.testng.SkipException(String.format(
                    "[ArVoucherTemplateLoader] Skipping variant '%s' — requested backDateDays=%d " +
                    "exceeds finance.backdate.max.days=%d. Either increase the property or add " +
                    "open-period documents that are older than %d days.",
                    variant.getVariantId(), requested, maxDays, maxDays));
        }

        return LocalDate.now().minusDays(requested);
    }

    /**
     * Generates the reference string for the AR Voucher header.
     *
     * <ul>
     *   <li>If {@code allowDuplicateReference = true}: returns a fixed Finance-duplicate reference
     *       string — it is intentionally reused to verify the application accepts duplicates.</li>
     *   <li>If {@code allowDuplicateReference = false}: generates a short UUID-based reference
     *       and retries up to 10 times until it is not found in {@code finvchmain.refno}.</li>
     * </ul>
     */
    private static String generateReference(
            Jdbi jdbi, List<Long> siteCodes, SelectDocumentVariant variant) {

        if (variant.isAllowDuplicateReference()) {
            // Finance module: duplicate reference is accepted — use a fixed repeatable value
            return "REF-FIN-DUP";
        }

        // Non-Finance: must be unique in finvchmain — retry up to 10 times
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = FakerUtils.randomReference();
            if (!FinVchRefQuery.isReferenceUsed(jdbi, candidate, siteCodes)) {
                return candidate;
            }
        }
        // Fallback: timestamp-based reference (virtually guaranteed unique)
        return "REF-" + System.currentTimeMillis();
    }

    /**
     * Resolves the {@code referenceSite} string to set on each SubLedgerInformation row.
     *
     * <ul>
     *   <li>Non-customer-type SL or {@code null} mode: returns {@code ownerSiteName} (auto-populate).</li>
     *   <li>{@code OS_OO_CM_OWNER}: returns {@code ownerSiteName}.</li>
     *   <li>{@code OS_OO_CM_ALT}: queries for a different active OS-OO-CM site (OU-matched).</li>
     *   <li>{@code TAGGED_OU_MATCH}: queries tagged sites of type MS-CO-CM / US-CO-CM (OU-matched).
     *       TODO: confirm site-tagging join table name before enabling this variant.</li>
     *   <li>{@code TAGGED_OM_TS}: queries tagged sites of type US-CO-OM / US-CO-OM-TS / MS-CO-OM-TS.
     *       TODO: confirm site-tagging join table name before enabling this variant.</li>
     * </ul>
     */
    private static String resolveReferenceSite(
            SelectDocumentVariant variant, ArVoucherLiveDbRefs refs, Jdbi jdbi) {

        if (!variant.isCustomerTypeSl() || variant.getRefSiteMode() == null) {
            return refs.getOwnerSiteName();
        }

        DbDialect d = DbDialectFactory.current();

        switch (variant.getRefSiteMode()) {

            case OS_OO_CM_OWNER:
                return refs.getOwnerSiteName();

            case OS_OO_CM_ALT: {
                String sql =
                    "SELECT a.name FROM admsite a " +
                    "WHERE a.sitetype = 'OS-OO-CM' " +
                    "  AND a.ext = 'N' " +
                    "  AND a.name <> :ownerSite " +
                    "ORDER BY a.code " +
                    d.limit1();
                Optional<String> altSite = jdbi.withHandle(handle ->
                    handle.createQuery(sql)
                        .bind("ownerSite", refs.getOwnerSiteName())
                        .mapTo(String.class)
                        .findFirst()
                );
                return altSite.orElseGet(() -> {
                    Log.warn("[Template] No alternative OS-OO-CM site found; falling back to ownerSiteName={}",
                            refs.getOwnerSiteName());
                    return refs.getOwnerSiteName();
                });
            }

            case TAGGED_OU_MATCH: {
                // TODO: implement — query tagged sites of type MS-CO-CM or US-CO-CM (OU-matched)
                //   Requires: site-tagging join table (e.g. admcustsite or finsl_site) — confirm with DBA
                //   SQL skeleton: SELECT a.name FROM admsite a
                //                 JOIN <tagging_table> t ON t.admsite_code = a.code
                //                 WHERE t.finsl_slid = :slId
                //                   AND a.sitetype IN ('MS-CO-CM','US-CO-CM')
                //                   AND a.ext = 'N' LIMIT 1
                Log.warn("[Template] TAGGED_OU_MATCH: tagged-site query not yet implemented — falling back to ownerSiteName. Confirm tagging table name with DBA.");
                return refs.getOwnerSiteName();
            }

            case TAGGED_OM_TS: {
                // TODO: implement — query tagged sites of type US-CO-OM, US-CO-OM-TS, or MS-CO-OM-TS
                //   Requires: site-tagging join table — confirm with DBA
                //   SQL skeleton: SELECT a.name FROM admsite a
                //                 JOIN <tagging_table> t ON t.admsite_code = a.code
                //                 WHERE t.finsl_slid = :slId
                //                   AND a.sitetype IN ('US-CO-OM','US-CO-OM-TS','MS-CO-OM-TS')
                //                   AND a.ext = 'N' LIMIT 1
                Log.warn("[Template] TAGGED_OM_TS: tagged-site query not yet implemented — falling back to ownerSiteName. Confirm tagging table name with DBA.");
                return refs.getOwnerSiteName();
            }

            default:
                return refs.getOwnerSiteName();
        }
    }

    /**
     * Builds the SelectDocument list with per-document adjustment amounts computed
     * according to the variant's {@link SelectDocumentVariant.AmountMode}.
     *
     * <ul>
     *   <li>{@code FULL}    — adjustmentAmount = doc.getPending() (full pending amount)</li>
     *   <li>{@code PARTIAL} — adjustmentAmount = 50% of doc.getPending()</li>
     *   <li>{@code MIX}     — first document per SL: full; remaining documents: 50%</li>
     * </ul>
     */
    private static List<SelectDocument> buildSelectDocumentsWithAmountMode(
            String testCaseId,
            List<SlNameWithId> pendingSlNames,
            Map<String, List<PendingSlDocument>> pendingDocsBySlName,
            SelectDocumentVariant.AmountMode amountMode) {

        List<SelectDocument> result = new ArrayList<>();
        for (int i = 0; i < pendingSlNames.size(); i++) {
            String slLineNo = String.valueOf(i + 1);
            SlNameWithId sl = pendingSlNames.get(i);
            String compositeKey = PendingAdjQuery.compositeKey(sl.getSlName(), sl.getSlId());
            List<PendingSlDocument> docs = pendingDocsBySlName.getOrDefault(compositeKey, List.of());

            int docIndexForAmount = 0;
            for (int j = 0; j < docs.size(); j++) {
                PendingSlDocument doc = docs.get(j);
                BigDecimal pending = doc.getPending() != null
                        ? doc.getPending()
                        : BigDecimal.ZERO;
                if (pending.compareTo(BigDecimal.ZERO) <= 0) {
                    Log.warn("[ArVoucherTemplateLoader] Skipping document {} for SL '{}' — pending={} is not positive",
                            doc.getDocumentNo(), sl.getSlName(), doc.getPending());
                    continue;
                }
                BigDecimal adjAmount = computeDocAmount(pending, amountMode, docIndexForAmount++);
                if (adjAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    Log.warn("[ArVoucherTemplateLoader] Skipping document {} for SL '{}' — " +
                             "adjAmount rounds to {} (pending={}, mode={}). " +
                             "Document excluded from selectDocumentList.",
                             doc.getDocumentNo(), sl.getSlName(), adjAmount, pending, amountMode);
                    continue;
                }

                result.add(new SelectDocument(
                        testCaseId,
                        slLineNo,
                        doc.getDocumentNo(),
                        doc.getDocumentDate(),
                        doc.getEntype(),
                        doc.getRefNo(),
                        doc.getDocumentDate(),
                        doc.getReferenceSite(),
                        adjAmount
                ));
            }
        }
        return result;
    }

    /**
     * Computes the adjustment amount for a single document based on the amount mode.
     *
     * @param pending    the full pending amount of the document
     * @param amountMode the mode from the variant
     * @param docIndex   0-based index of the document within its SL (used for MIX mode)
     * @return the adjusted BigDecimal amount (2 decimal places)
     */
    private static BigDecimal computeDocAmount(
            BigDecimal pending, SelectDocumentVariant.AmountMode amountMode, int docIndex) {

        BigDecimal safePending = (pending == null || pending.compareTo(BigDecimal.ZERO) <= 0)
                ? BigDecimal.ONE
                : pending;

        switch (amountMode) {
            case FULL:
                return safePending.setScale(2, RoundingMode.HALF_UP);
            case PARTIAL:
                return safePending.multiply(new BigDecimal("0.5")).setScale(2, RoundingMode.HALF_UP);
            case MIX:
                // First document (index 0): full; subsequent documents: 50%
                return docIndex == 0
                        ? safePending.setScale(2, RoundingMode.HALF_UP)
                        : safePending.multiply(new BigDecimal("0.5")).setScale(2, RoundingMode.HALF_UP);
            default:
                return safePending.setScale(2, RoundingMode.HALF_UP);
        }
    }

    /**
     * Computes the total adjustment amount per SL from the already-built SelectDocument list.
     * The result feeds into {@link #buildSubLedgersForVariant} as the baseline amountCr.
     *
     * @param pendingSlNames ordered list of SLs (provides the composite-key + lineNo ordering)
     * @param selectDocs     the SelectDocument list built by {@link #buildSelectDocumentsWithAmountMode}
     * @return map from {@code "slName|slId"} composite key → sum of adjustmentAmount for that SL
     */
    private static Map<String, BigDecimal> computeDocTotalPerSl(
            List<SlNameWithId> pendingSlNames,
            List<SelectDocument> selectDocs) {

        // Build a lineNo → compositeKey reverse map
        Map<String, String> lineNoToKey = new HashMap<>();
        for (int i = 0; i < pendingSlNames.size(); i++) {
            SlNameWithId sl = pendingSlNames.get(i);
            lineNoToKey.put(String.valueOf(i + 1),
                    PendingAdjQuery.compositeKey(sl.getSlName(), sl.getSlId()));
        }

        Map<String, BigDecimal> totals = new HashMap<>();
        for (SelectDocument sd : selectDocs) {
            String key = lineNoToKey.get(sd.getLineNo());
            if (key != null) {
                totals.merge(key,
                        sd.getAdjustmentAmount() != null ? sd.getAdjustmentAmount() : BigDecimal.ZERO,
                        BigDecimal::add);
            }
        }
        return totals;
    }

    /**
     * Builds SubLedgerInformation rows for the variant overload.
     * Uses per-SL document totals (already adjusted for amount mode) as the base amountCr,
     * then applies the grid relation shift.
     *
     * <ul>
     *   <li>{@code EQUAL}   — amountCr = docTotal for that SL</li>
     *   <li>{@code LESS}    — amountCr = docTotal - 1.00 (tests UI validation)</li>
     *   <li>{@code GREATER} — amountCr = docTotal + 1.00 (tests UI validation)</li>
     * </ul>
     */
    private static List<SubLedgerInformation> buildSubLedgersForVariant(
            List<SlNameWithId> slNames,
            Map<String, String> slToArApName,
            String referenceSite,
            Map<String, BigDecimal> docTotalBySl,
            SelectDocumentVariant.GridRelation gridRelation) {

        AtomicInteger line = new AtomicInteger(1);
        return slNames.stream()
                .map(sl -> {
                    String key = PendingAdjQuery.compositeKey(sl.getSlName(), sl.getSlId());
                    // Invariant — DocumentSelector guarantees every selected SL has a positive docTotal.
                    // If this check ever fires, an upstream consistency bug has been re-introduced
                    // (e.g. SL list and reserved-doc map drifted apart). Fail loudly *before* the UI
                    // step so the cryptic "Total adjustment amount can not be in negative" popup
                    // never appears.
                    if (!docTotalBySl.containsKey(key)) {
                        throw new IllegalStateException(
                                "[ArVoucherTemplate] Invariant broken — SL '" + key + "' has no docTotal entry. " +
                                "Selected SLs: " + docTotalBySl.keySet() +
                                ". DocumentSelector returned an inconsistent (slList, docMap) pair, " +
                                "or upstream code dropped an SL after selection.\n" +
                                DocumentReservationRegistry.snapshotAsString());
                    }
                    BigDecimal docTotal = docTotalBySl.get(key);
                    if (docTotal == null || docTotal.signum() <= 0) {
                        throw new IllegalStateException(
                                "[ArVoucherTemplate] Invariant broken — SL '" + key + "' has non-positive docTotal " +
                                docTotal + ". DocumentSelector should never select an SL whose reserved docs " +
                                "sum to zero. Increase doc.selector.documentcount.default in config.properties.\n" +
                                DocumentReservationRegistry.snapshotAsString());
                    }
                    BigDecimal amountCr = applyGridRelation(docTotal, gridRelation);
                    String glName = slToArApName.getOrDefault(key, null);
                    return new SubLedgerInformation(
                            null,
                            String.valueOf(line.getAndIncrement()),
                            sl.getSlName(),
                            sl.getSlId(),
                            glName,
                            referenceSite,
                            null, null,
                            amountCr,
                            AdjustmentType.SELECT_DOCUMENT,
                            null
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Applies the grid-relation shift to the baseline document total to produce the
     * final {@code amountCr} for a SubLedgerInformation row.
     *
     * @param docTotal     sum of selected document amounts for this SL
     * @param gridRelation the desired relationship between grid Cr and document total
     * @return adjusted amountCr, rounded to 2 decimal places
     */
    private static BigDecimal applyGridRelation(
            BigDecimal docTotal, SelectDocumentVariant.GridRelation gridRelation) {

        // DocumentSelector guarantees a positive docTotal; this guard is defensive only.
        if (docTotal == null || docTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(
                    "[ArVoucherTemplate] applyGridRelation got non-positive docTotal=" + docTotal +
                    ". DocumentSelector should have caught this upstream.");
        }
        BigDecimal base = docTotal;

        switch (gridRelation) {
            case LESS:
                // Grid Cr is 1.00 less than doc total. If the document pool is so small that
                // base ≤ 1.00, the LESS variant cannot be satisfied — surface this loudly
                // rather than masking it with 0.01 and letting the UI reject the unbalanced math.
                BigDecimal less = base.subtract(BigDecimal.ONE).setScale(2, RoundingMode.HALF_UP);
                if (less.signum() <= 0) {
                    throw new IllegalStateException(
                            "[ArVoucherTemplate] LESS grid relation produced non-positive value " + less +
                            " from base=" + base + ". The selected document pool is too small for this " +
                            "scenario — increase doc.selector.documentcount.default in config.properties " +
                            "or pick a sub-ledger with larger pending amounts.");
                }
                return less;
            case GREATER:
                // Grid Cr is greater than doc total — automation uses 1.00 as a fixed offset
                return base.add(BigDecimal.ONE).setScale(2, RoundingMode.HALF_UP);
            case EQUAL:
            default:
                return base.setScale(2, RoundingMode.HALF_UP);
        }
    }

    /**
     * Builds PaymentInformation rows based on the variant's payment mode.
     *
     * <ul>
     *   <li>{@code NONE}      — returns empty list (causes "Ignore &amp; Proceed" popup)</li>
     *   <li>{@code CHEQUE}    — one Cheque row per SL line</li>
     *   <li>{@code NEFT_RTGS} — one NEFT/RTGS row per SL line</li>
     *   <li>{@code MIX}       — alternates Cheque / NEFT_RTGS (odd SL index = Cheque,
     *                           even SL index = NEFT/RTGS)</li>
     * </ul>
     *
     * <p>Instrument numbers are generated as "{PREFIX}-{5-digit random}" for uniqueness.
     * Instrument date is set to today in ISO format (yyyy-MM-dd).
     */
    private static List<PaymentInformation> buildPayments(
            String testCaseId,
            List<SubLedgerInformation> subLedgers,
            SelectDocumentVariant.PaymentMode paymentMode) {

        if (paymentMode == SelectDocumentVariant.PaymentMode.NONE || subLedgers.isEmpty()) {
            return List.of();
        }

        List<PaymentInformation> payments = new ArrayList<>();
        // dd/MM/yyyy — matches the UI's own Document Date format (DateUtils.UI_DATE_FORMAT).
        // Previously LocalDate.now().toString() (ISO yyyy-MM-dd) — a latent version of the same
        // format-mismatch bug confirmed live for GV's FIN-GV-ADD-COMBO-05 (see Round 22 GV plan);
        // not yet manifesting here since AR's Instrument Date field is usually left disabled.
        String today = LocalDate.now().format(DateUtils.UI_DATE_FORMAT);

        for (int i = 0; i < subLedgers.size(); i++) {
            SubLedgerInformation sl = subLedgers.get(i);
            PaymentSelectionType mode = resolvePaymentType(paymentMode, i);

            // Cash payments carry no instrument number (not required by the application)
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
                    null,
                    null
            ));
        }
        return payments;
    }

    /**
     * Round 20 — overload used ONLY by the 4-arg {@link #selectDocumentTemplate}, AR's own
     * ratio-driven CHEQUE scenario. Every other caller of the 3-arg
     * {@link #buildPayments(String, List, SelectDocumentVariant.PaymentMode)} always passes a
     * hardcoded {@code CASH} and is left unchanged.
     *
     * @param knownCheque              the decision {@code ArVoucherFixtureFactory
     *                                 .assignKnownChequeFlags} already made for this run
     * @param knownChequeDifferentBank within the (rare) known bucket, whether this row should
     *                                 deliberately resolve a cross-bank cheque (see {@code
     *                                 ArVoucherFixtureFactory.assignChequeBankMatchFlags})
     * @param cashBankGlcode           this voucher's own counter-leg Cash/Bank GL
     */
    private static List<PaymentInformation> buildPayments(
            String testCaseId,
            List<SubLedgerInformation> subLedgers,
            SelectDocumentVariant.PaymentMode paymentMode,
            boolean knownCheque,
            boolean knownChequeDifferentBank,
            long cashBankGlcode) {

        if (paymentMode == SelectDocumentVariant.PaymentMode.NONE || subLedgers.isEmpty()) {
            return List.of();
        }
        if (paymentMode != SelectDocumentVariant.PaymentMode.CHEQUE) {
            // Only CHEQUE is ratio-driven — NEFT_RTGS/CASH/MIX behave exactly like the 3-arg overload.
            return buildPayments(testCaseId, subLedgers, paymentMode);
        }

        List<PaymentInformation> payments = new ArrayList<>();
        String today = LocalDate.now().format(DateUtils.UI_DATE_FORMAT);
        var resolution = !knownCheque
                ? ChequeInstrumentResolver.resolveUnknown()
                : knownChequeDifferentBank
                        ? ChequeInstrumentResolver.resolveKnownDifferentBank(DatabaseManager.getJdbi(), cashBankGlcode)
                        : ChequeInstrumentResolver.resolveKnownSameBank(DatabaseManager.getJdbi(), cashBankGlcode);
        Log.info("[ArVoucherTemplateLoader] Cheque instrument resolved: bankMatch={}, chqno={}",
                resolution.bankMatch(), resolution.chqno());
        for (int i = 0; i < subLedgers.size(); i++) {
            SubLedgerInformation sl = subLedgers.get(i);
            payments.add(new PaymentInformation(
                    testCaseId, sl.getLineNo(), PaymentSelectionType.CHEQUE,
                    resolution.chqno(), today, null, null));
        }
        return payments;
    }

    /**
     * Resolves the actual {@link PaymentSelectionType} for a given SL index
     * based on the variant's payment mode.
     *
     * @param paymentMode the variant's payment mode
     * @param slIndex     0-based index of the SL line
     * @return the payment type to use for this line
     */
    private static PaymentSelectionType resolvePaymentType(
            SelectDocumentVariant.PaymentMode paymentMode, int slIndex) {

        switch (paymentMode) {
            case CHEQUE:
                return PaymentSelectionType.CHEQUE;
            case NEFT_RTGS:
                return PaymentSelectionType.NEFT_RTGS;
            case CASH:
                return PaymentSelectionType.CASH;
            case MIX:
                // Odd index (1, 3, ...) = Cheque; even index (0, 2, ...) = NEFT/RTGS
                return slIndex % 2 == 0
                        ? PaymentSelectionType.NEFT_RTGS
                        : PaymentSelectionType.CHEQUE;
            default:
                return PaymentSelectionType.CHEQUE;
        }
    }

    /**
     * Builds the human-readable test case name shown in TestNG / ExtentReports
     * for a SELECT_DOCUMENT variant.
     *
     * <p>Format:
     * <pre>
     * SEL_DOC [| Ref:Finance-Dup] [| {clsname}({System/User})] [| CustSL:{OwnerSite/AltSite}]
     *         | {N}SL-{M}Doc | Amt:{mode} | Grid:{relation} | Pmt:{resolvedMode} | Date:{date}
     * </pre>
     *
     * <p>The SL classification segment is resolved at runtime via {@link AdmClsQuery#findByClsCode}
     * so the name shows "Customer(System)" rather than a numeric code.
     *
     * <p>{@code effectivePaymentMode} is the resolved concrete mode — for RANDOM variants this is
     * the mode actually applied (NONE / CHEQUE / NEFT_RTGS), never the RANDOM enum value itself.
     *
     * @param variant             the combination being tested
     * @param jdbi                active JDBI instance (used only when {@code slClsCode > 0})
     * @param effectivePaymentMode the concrete payment mode used (RANDOM already resolved)
     * @return fully assembled, human-readable test case name
     */
    private static String buildDynamicTestCaseName(
            SelectDocumentVariant variant, Jdbi jdbi,
            SelectDocumentVariant.PaymentMode effectivePaymentMode) {

        StringBuilder sb = new StringBuilder("SEL_DOC");

        // GL type segment — always shown so Cash and Bank runs are immediately distinguishable
        if (variant.getGlType() != null) {
            sb.append(" | [").append(variant.getGlType().label()).append(" GL]");
        }

        // Reference uniqueness segment (only for Finance-duplicate variants)
        if (variant.isAllowDuplicateReference()) {
            sb.append(" | Ref:Finance-Dup");
        }

        // SL classification segment (only when a specific admcls.clscode is selected)
        if (variant.getSlClsCode() > 0) {
            AdmCls cls = AdmClsQuery.findByClsCode(jdbi, variant.getSlClsCode()).orElse(null);
            if (cls != null) {
                sb.append(" | ").append(cls.getClsname())
                  .append("(").append(cls.cmpLabel()).append(")");
            } else {
                sb.append(" | ClsCode:").append(variant.getSlClsCode());
            }
        }

        // Customer type / reference site segment
        if (variant.isCustomerTypeSl() && variant.getRefSiteMode() != null) {
            sb.append(" | CustSL:");
            switch (variant.getRefSiteMode()) {
                case OS_OO_CM_OWNER:  sb.append("OS-OO-CM:Owner");  break;
                case OS_OO_CM_ALT:    sb.append("OS-OO-CM:Alt");    break;
                case TAGGED_OU_MATCH: sb.append("Tagged:OU-Match"); break;
                case TAGGED_OM_TS:    sb.append("Tagged:OM-TS");    break;
                default:              sb.append(variant.getRefSiteMode()); break;
            }
        }

        // SL × Doc count
        sb.append(" | ").append(variant.getSlCount()).append("SL-")
          .append(variant.getDocsPerSl()).append("Doc");

        // Amount mode
        sb.append(" | Amt:").append(variant.getAmountMode());

        // Grid relation
        sb.append(" | Grid:").append(variant.getGridRelation());

        // Payment mode — use the resolved concrete mode (never shows "RANDOM")
        sb.append(" | Pmt:");
        if (effectivePaymentMode == SelectDocumentVariant.PaymentMode.NEFT_RTGS) {
            sb.append("NEFT/RTGS");
        } else {
            sb.append(effectivePaymentMode);
        }

        // Date mode — backdate shows actual day count from the variant
        sb.append(" | Date:");
        if (variant.getDateMode() == SelectDocumentVariant.DateMode.BACK_DATED) {
            sb.append("BACKDATE(-").append(variant.getBackDateDays()).append(")");
        } else {
            sb.append("TODAY");
        }

        return sb.toString();
    }

    private ArVoucherTemplateLoader() {}
}
