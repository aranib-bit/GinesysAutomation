package com.gsl.testdata.dto;

import com.gsl.flow.Finance.Operations.Common.PostSaveDialogHandler;
import com.gsl.pages.enums.Finance.Operations.APVoucher.APVoucherRevertOutcome;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.*;
import com.gsl.testdata.InvocationData;
import com.gsl.testdata.MasterTestData;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherExecutionGroup;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherFixtureOptions;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherScenarioType;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherTag;
import com.gsl.utils.dataprovider.fixture.finance.DocumentSearchStrategy;
import com.gsl.utils.dataprovider.fixture.finance.NegativeValidation;
import com.gsl.validation.db.finance.VoucherDbContextBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Merged AP Voucher test data — the AP analogue of {@link MergedFinanceArVoucherData}, produced
 * by {@code MergedFinApVoucherDataSupplier} (Excel mode via {@code ExcelApVoucherDataSource}, DB
 * mode via {@code DbApVoucherDataSource}). Carries the header, sub-ledger, select-document,
 * FIFO-setoff, payment-information and TDS/net-payable rows a single AP Voucher test invocation
 * needs, plus DB-mode-only scenario/fixture metadata, tag filtering, and runtime-captured
 * TDS/Net-Payable amounts (see the individual field Javadocs below).
 *
 * <p>Implements {@link VoucherReportData} directly, so the report layer ({@code
 * VoucherExtentDescriptionLogger}) can work against it uniformly alongside AR/GV.
 */
@Data
@Builder
@AllArgsConstructor
public class MergedFinanceApVoucherData
        implements InvocationData,
                   PostSaveDialogHandler.PostSaveSpec,
                   VoucherDbContextBuilder.InvoiceScoped,
                   VoucherReportData {

    private MasterTestData masterTestData;

    private DocumentInformation documentInformation;

    private List<SubLedgerInformation> subLedgerInformationList;

    private List<SelectDocument> selectDocumentList;

    private List<ReferenceSitewiseFIFOsetoff> referenceSitewiseFIFOList;

    private List<PaymentInformation> paymentInformationList;

    private List<TdsAndNetPayable> tdsAndNetPayableList;

    // Derived flags
    private boolean onAccount;
    private boolean tdsProvided;
    private boolean paymentProvided;
    private boolean needsPaymentInfoWarning;

    // ─── DB mode fields ───────────────────────────────────────────────────────

    /**
     * DB mode scenario type. {@code null} in Excel mode.
     * Set by {@link ApVoucherScenarioType#loadData(com.gsl.utils.dataprovider.fixture.ApVoucherLiveDbRefs)}
     * so the test method can detect a placeholder vs. fully-loaded data.
     */
    private ApVoucherScenarioType scenarioType;

    /**
     * DB query parameters carried from placeholder generation (DataSupplier time)
     * through to lazy execution-time loading ({@code DbApVoucherDataSource.loadFreshForScenario}).
     * {@code null} in Excel mode.
     */
    private ApVoucherFixtureOptions fixtureOptions;

    /**
     * Structured log of DB data-gathering steps, rendered by
     * {@code DbGatheringExtentLogger} in the Extent Report before test steps begin.
     */
    @Builder.Default
    private List<String> dbGatheringLog = new ArrayList<>();

    /** TDS Amount per sub-ledger lineNo, captured at runtime from the TDS popup. Keyed by lineNo. */
    @Builder.Default
    private Map<String, BigDecimal> capturedTdsAmounts = new HashMap<>();

    /** Net Payable per sub-ledger lineNo, captured at runtime from the TDS popup. Keyed by lineNo. */
    @Builder.Default
    private Map<String, BigDecimal> capturedNetPayables = new HashMap<>();

    /**
     * Non-null for negative-scenario variants; {@code null} for positive scenarios.
     * Carries the expected UI validation error message.
     * Used by {@link com.gsl.utils.dataprovider.fixture.finance.ApVoucherPredicateFactory#NEGATIVE_FORM_VALIDATION_BEHAVIOR}.
     */
    private NegativeValidation expectedValidation;

    /**
     * Cash/Bank GL name used in recovery behaviors (e.g. NEG_BLANK_CASHBANK_ADD_ROW_RECOVER).
     * {@code null} for non-recovery scenarios.
     * The header is filled with cashbank=null to trigger the Add Row error; this value is
     * then set after the popup is dismissed.
     */
    private String cashbankForRecovery;

    /**
     * Tag set assigned at placeholder-build time by {@code DbApVoucherDataSource.tagsFor()}.
     * Used by the {@code apv.tag} filter to include/exclude this placeholder.
     * Empty set = no filtering applied (all tags match).
     */
    @Builder.Default
    private Set<ApVoucherTag> tags = Collections.emptySet();

    // ─── Revert/Delete/Edit fields (mirrors MergedFinanceArVoucherData's own) ─────────

    /**
     * The {@code scheme_docno} of an existing AP Voucher to operate on — populated by the
     * Revert/Delete/Edit data sources (Excel {@code ExcelApVoucherRevertDataSource}/
     * {@code ExcelApVoucherDeleteDataSource}/{@code ExcelApVoucherEditDataSource}, DB
     * {@code DbApVoucherRevertDataSource}/{@code DbApVoucherDeleteDataSource}/
     * {@code DbApVoucherEditDataSource}); {@code null} in Create mode. Used by the test classes to
     * locate the voucher in the list page. Mirrors {@code MergedFinanceArVoucherData
     * #getExistingVoucherNo()}'s exact semantics.
     */
    private String existingVoucherNo;

    /**
     * {@code release_status} of the voucher as read from {@code finvchmain}.
     * <ul>
     *   <li>{@code 'N'} — never posted → directly editable/deletable</li>
     *   <li>{@code 'U'} — unposted     → directly editable/deletable</li>
     *   <li>{@code 'P'} — posted       → test class must unpost, then check {@code fintag}; if
     *       tagged, remove via {@code DocumentAdjustmentFlow.untagIfNeeded} first</li>
     * </ul>
     * Null in Create mode (not relevant). Mirrors {@code MergedFinanceArVoucherData
     * #getReleaseStatus()}'s exact semantics.
     */
    private String releaseStatus;

    /**
     * Non-null only for a Revert row that was deliberately claimed to hit a specific non-success
     * dialog outcome (e.g. {@code CANNOT_REVERT_MONTH_CLOSED}) — {@code RevertApVoucher} asserts
     * the live {@code confirmRevert()} outcome matches this exactly, rather than only branching DB
     * validation on whatever outcome happened to occur. {@code null} for every ordinary positive
     * Revert row, where any of the live-confirmed outcomes is legal. Mirrors
     * {@code MergedFinanceGeneralVoucherData#getExpectedRevertOutcome()}'s exact semantics/field
     * name, renamed to AP's own outcome type.
     */
    private APVoucherRevertOutcome expectedRevertOutcome;

    /**
     * Which tier {@code VoucherListPageBase#selectDocumentFast} starts its cascade at —
     * {@code null} (the common case) means {@link DocumentSearchStrategy#EXACT_MATCH}. Mirrors
     * {@code MergedFinanceGeneralVoucherData#getSearchStrategy()}'s exact semantics; used by
     * {@code RevertApVoucher}'s document-selection step.
     */
    private DocumentSearchStrategy searchStrategy;

    /**
     * Which {@code editApVoucherDetailLine}/{@code editApVoucherPaymentInfo} scenario this row
     * represents — {@code null} for {@code editApVoucherHeader} rows (which dispatch via a fixed
     * {@link com.gsl.utils.dataprovider.fixture.finance.ApVoucherEditScenarioType#HEADER_ONLY}
     * passed directly by the test method, not read from this field, matching how
     * {@code editApVoucherHeader} predates this field entirely). Set by
     * {@code DbApVoucherEditDataSource} for detail-line/payment-info rows, where the scenario
     * (Advance vs Document amount edit, or payment-info) depends on which live candidate was
     * claimed and can't be fixed per-method the way header edits can.
     */
    private com.gsl.utils.dataprovider.fixture.finance.ApVoucherEditScenarioType editScenarioType;

    /** Non-null for {@link com.gsl.utils.dataprovider.fixture.finance.ApVoucherEditScenarioType
     *  #DETAIL_AMOUNT_ADVANCE}/{@code #DETAIL_AMOUNT_DOCUMENT} rows — the row/amount/target-document
     *  this detail-line-edit scenario applies. {@code null} for every other scenario type. */
    private com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.ApVoucherDetailLineEditTarget editedDetailLine;

    /** Non-null for {@link com.gsl.utils.dataprovider.fixture.finance.ApVoucherEditScenarioType
     *  #DETAIL_PAYMENT_INFO} rows — the before/after payment info this scenario applies.
     *  {@code null} for every other scenario type. */
    private com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.ApVoucherPaymentInfoEditTarget paymentEditTarget;

    /**
     * Edit flows only — every live {@code fintag} row captured BEFORE the untag step (the counter-
     * document number + tagged amount of each), so the shared "Apply/Restore Sub-Ledger Adjustment"
     * step can re-tag exactly what the Edit's unpost precondition destroyed. Runtime state written
     * by {@code EditApVoucher}; empty for Create/Revert/Delete and for untagged Edit candidates.
     */
    @Builder.Default
    private List<com.gsl.testdata.Finance.Operations.Vouchers.Common.CapturedTag> capturedTags = new ArrayList<>();

    // ─── Adjustment amount-mismatch Confirmation (Create scenarios) ─────────────

    /**
     * How Adjustment Entry's amount-mismatch Confirmation ("Update payment amount" / "I will adjust
     * more") is answered when a Document-mode scenario deliberately adjusts less than the row's
     * payment amount ({@code SubLedgerInformation#amountDr}). {@code null} = legacy behavior
     * (plain OK, no popup expected) — every pre-existing scenario. See
     * {@code AdjustmentFlowSteps#okAnsweringMismatch}.
     */
    private com.gsl.testdata.Finance.Operations.Vouchers.Common.AdjustmentMismatchPlan adjustmentMismatchPlan;

    /**
     * Payment amount the saved voucher's Sub-Ledger row is expected to carry after the
     * mismatch plan runs — the adjusted total for {@code UPDATE_PAYMENT_AMOUNT}, the original
     * payment for {@code ADJUST_MORE}. {@code null} when no mismatch plan applies.
     */
    private BigDecimal expectedPaymentAmount;

    // ─── Invocation metadata ──────────────────────────────────────────────────

    private int invocationIndex;
    private int totalInvocations;

    /**
     * The TDS amount captured at runtime for sub-ledger {@code lineNo} (see
     * {@link #capturedTdsAmounts}), or empty if {@code lineNo} is {@code null} or nothing has
     * been captured for it yet.
     */
    public Optional<BigDecimal> getTdsAmountForLine(String lineNo) {
        if (lineNo == null || capturedTdsAmounts == null) return Optional.empty();
        return Optional.ofNullable(capturedTdsAmounts.get(lineNo));
    }

    /**
     * The Net Payable amount captured at runtime for sub-ledger {@code lineNo} (see
     * {@link #capturedNetPayables}), or empty if {@code lineNo} is {@code null} or nothing has
     * been captured for it yet.
     */
    public Optional<BigDecimal> getNetPayableForLine(String lineNo) {
        if (lineNo == null || capturedNetPayables == null) return Optional.empty();
        return Optional.ofNullable(capturedNetPayables.get(lineNo));
    }

    // Explicit overrides to prevent Lombok from generating conflicting getter signatures.
    // Lombok aborts all getter generation when it detects a name clash between its inferred
    // return type (concrete) and the interface's declared return type (covariant/wildcard).
    // Adding these explicit methods removes the ambiguity; Lombok skips them and generates
    // all remaining methods (isNeedsPaymentInfoWarning, builder, etc.) normally.
    @Override public DocumentInformation      getDocumentInformation()      { return documentInformation; }
    @Override public List<SubLedgerInformation> getSubLedgerInformationList() { return subLedgerInformationList; }
    @Override public List<SelectDocument>       getSelectDocumentList()       { return selectDocumentList; }
    @Override public List<PaymentInformation>   getPaymentInformationList()   { return paymentInformationList; }

    @Override
    public String getScenarioTypeLabel() {
        return scenarioType != null ? scenarioType.name() : "";
    }

    @Override
    public int getInvocationIndex() {
        return invocationIndex;
    }

    @Override
    public int getTotalInvocations() {
        return totalInvocations;
    }

    /**
     * Scoped invoice numbers for the finpost pre/post snapshot (Save sub-steps 5a/5e).
     * Empty for advance payment (no SelectDocument); the document-payment list otherwise.
     */
    @Override
    public List<String> resolveInvoiceNos() {
        return selectDocumentList != null
                ? selectDocumentList.stream()
                        .map(SelectDocument::getDocumentNumber)
                        .collect(Collectors.toList())
                : Collections.emptyList();
    }
}
