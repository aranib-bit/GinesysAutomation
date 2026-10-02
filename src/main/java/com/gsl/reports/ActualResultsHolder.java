package com.gsl.reports;

import com.gsl.model.finance.FinChqDet;
import com.gsl.model.finance.FinChqMain;
import com.gsl.model.finance.FinCostTag;
import com.gsl.model.finance.FinNar;
import com.gsl.model.finance.FinPost;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.contract.TestResultsAccumulator;
import com.gsl.validation.snapshot.finance.AuditStateSummary;
import com.gsl.validation.ui.UiValidationFailure;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ThreadLocal accumulator for Finance Actual Results data.
 *
 * Validators deposit data immediately after fetching (before any assertion) so
 * the holder is populated even when a subsequent assertion throws.
 * {@link com.gsl.reports.FinanceDomainReporter} flushes via
 * {@link ActualResultsLogger} in both onTestSuccess and onTestFailure.
 */
@Data
public final class ActualResultsHolder implements TestResultsAccumulator {

    private String     voucherNo;
    private String     voucherType;        // "AR" or "AP"
    private FinVchMain header;             // deposited after Level 1
    /** Item F (Round 9): the resolved finnar row (narration master) for this document's
     *  glcode/nartext — {@code null} when not registered, mirrors the "Database Validation"
     *  layer's own "not registered" display. Deposited by
     *  {@code FinVchMainBaseValidator.validateCommonHeaderFields}, no new DB call. */
    private FinNar     finnar;
    private int        detRowCount  = -1;  // -1 = not reached
    private int        postEntryCount = -1;// -1 = not reached
    private List<FinPost> postEntries;     // individual GL rows from finpost
    /** -2 = not reached, -1 = skipped (ADVANCE_PAYMENT), >=0 = actual count */
    private int        tagCount     = -2;
    /** Item I: individual site-allocation rows from fincosttag — mirrors {@link #postEntries}'
     *  pattern so ActualResultsLogger can render real per-row site/amount/GL detail instead of
     *  only the {@link #tagCount} row count. {@code null}/empty when not reached or not
     *  site-applicable, same as postEntries. */
    private List<FinCostTag> costTagEntries;
    /** Item AH: the audit-active/trigger-disabled state resolved right before Save — lets
     *  "Actual Results" render its own compact audit-summary line without a second DB call. */
    private AuditStateSummary auditState;
    /** Round 8 Part B: the persisted {@code finvchdet.payment_mode} DB code ('C'/'R'/'H') for
     *  the document's counter-leg payment — deposited by
     *  {@code GeneralVoucherDbValidator.validateCounterLegPaymentInfo} (reusing its own
     *  already-fetched first-detail-row, no new DB call) so "Actual Results" can render a real
     *  Payment Information section instead of having none at all for any payment mode. */
    private String paymentModeActual;
    /** Round 6 — persisted {@code finvchdet.payment_mode} code per Expense/Income Detail row, keyed by the
     *  row's {@code lineNo} (insertion order = grid order); lets "Actual Results" show one payment row per
     *  grid row of a multi-row-payment voucher. Empty when not reached. */
    private final Map<String, String> paymentModeActualByLine = new LinkedHashMap<>();
    /** Round 19 — the resolved cheque-book row(s) for a Cheque-payment voucher's counter-leg
     *  instrument, deposited by {@code GeneralVoucherDbValidator.validateChequeBook} (reusing its
     *  own already-fetched {@code finchqdet}/{@code finchqmain} lookups, no new DB call) so
     *  "Actual Results" can render its own dedicated finchqdet/finchqmain sections instead of
     *  having none at all. {@code null} for every non-Cheque payment mode, and for a Cheque
     *  voucher whose instrument number was never registered in {@code finchqdet} (today's
     *  synthetic/unknown-cheque tests — see {@code GeneralVoucherDbValidator}'s own Javadoc). */
    private FinChqDet chequeDet;
    private FinChqMain chequeMain;
    /** Round 22 — the caller's own already-fetched finvchdet.bookcode value (the voucher's own
     *  row), deposited alongside chequeDet/chequeMain so "Actual Results" can show the real
     *  cross-table comparison (finvchdet.bookcode vs. finchqdet.bookcode), not just finchqdet's
     *  own not-null-ness. null whenever chequeDet is null (same gating). */
    private Long chequeDetBookcodeOnVoucher;
    private boolean    negativeScenario;
    private Boolean    onAccount;          // AP-only (from DB)
    private Boolean    tdsProvided;        // AP-only (from DB)
    private String     uiOwnerSite;        // read from live UI after fillDocumentHeader
    private String     uiCashBank;         // read from live UI after fillDocumentHeader
    private String     uiOnAccount;        // AP-only, read from live UI after fillDocumentHeader
    /** Round 23 — GV Edit page-styling regression checks, deposited by
     *  {@code GeneralVoucherAdd.verifyEditFormFullyLoaded} right after the page is confirmed
     *  genuinely rendered. Each is {@code "<font-size>|<font-family>"} read via
     *  {@code SeleniumUtils.getComputedFont}; {@code null} until that method runs (e.g. Create/
     *  Revert/Delete flows never populate these). Deposited before the matching assertion, same
     *  "deposit before assertion" convention as every other UI field in this holder. */
    private String     uiBreadcrumbFont;
    private String     uiWindowTitleFont;
    private String     uiSavedDocumentNoFont;
    /** Full pass+fail UI-contract check results from {@code validateUiContract(...)} (component
     *  style/state/label/grid checks) — deposited by {@code UiValidationReporter#renderAndAssert}
     *  before its own soft-assert, so the holder is populated even when that assertion throws.
     *  {@code ActualResultsLogger} merges these rows into the existing per-field business-data
     *  tables (Document Information / Sub Ledger) via {@code UiValidationReporter#rowsFor}, not a
     *  separate section. {@code null} for flows that never call {@code validateUiContract}
     *  (Revert/Delete). */
    private List<UiValidationFailure> uiValidationResults;
    /** AP-only. Key = slId (or slName when slId is blank). Value = "Yes"/"No" read from live UI grid. */
    private Map<String, String> uiDeductTdsMap = new LinkedHashMap<>();
    /**
     * AP-only. Key = slId (or slName when slId is blank).
     * Value = TDS info text read from live UI grid e.g. "TDS not appl." or "TDS: 6.00".
     * TODO: Populate in CreateApVoucher after sub-ledger fill to activate the TDS Info column.
     */
    private Map<String, String> uiTdsInfoMap = new LinkedHashMap<>();
    /**
     * AP-only. Key = {@code lineNo}. Value = the counter-document number the row is currently
     * adjusted/tagged against (e.g. "VP/02556/26-27"), read back after the Adjustment popup
     * commits. Genuine reporting gap closed here — until now, neither Create nor Edit had any
     * field carrying the actual adjusted document (only {@link #tagCount}, a bare row count).
     */
    private Map<String, String> uiAdjustedDocumentMap = new LinkedHashMap<>();
    /** AP-only. Key = {@code lineNo}. Value = the amount actually adjusted against
     *  {@link #uiAdjustedDocumentMap}'s same-keyed document, read back after the Adjustment popup
     *  commits — the amount counterpart to {@link #uiAdjustedDocumentMap}. */
    private Map<String, String> uiAdjustmentAmountMap = new LinkedHashMap<>();
    /** AP + AR. Key = slId (or slName when slId is blank). true = payment info popup appeared for this SL row. */
    private Map<String, Boolean> uiPaymentInfoPopupMap = new LinkedHashMap<>();
    /** AP + AR. null = not reached; true = blank-warning dialog appeared on save; false = no warning appeared. */
    private Boolean uiPaymentInfoWarningAppeared;
    /** The UI save-click time, captured just before the save action. */
    private LocalDateTime saveStartTime;
    /** Delay in seconds between saveStartTime and finvchmain.creation_dt. Null = not computed. */
    private Long creationDtDelaySeconds;
    /** Config tolerance in seconds for creation_dt check. Meaningful only when creationDtDelaySeconds is set. */
    private long creationDtToleranceSeconds;
    /** MergedFinanceArVoucherData or MergedFinanceApVoucherData */
    private Object     testData;

    private static final ThreadLocal<ActualResultsHolder> HOLDER =
            ThreadLocal.withInitial(ActualResultsHolder::new);

    public static ActualResultsHolder get()  { return HOLDER.get(); }
    public static void clear()               { HOLDER.remove(); }

    @Override
    public void resetAccumulator()           { HOLDER.remove(); }
}
