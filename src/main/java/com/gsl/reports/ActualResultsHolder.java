package com.gsl.reports;

import com.gsl.model.finance.FinPost;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.contract.TestResultsAccumulator;
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
    private int        detRowCount  = -1;  // -1 = not reached
    private int        postEntryCount = -1;// -1 = not reached
    private List<FinPost> postEntries;     // individual GL rows from finpost
    /** -2 = not reached, -1 = skipped (ADVANCE_PAYMENT), >=0 = actual count */
    private int        tagCount     = -2;
    private boolean    negativeScenario;
    private Boolean    onAccount;          // AP-only (from DB)
    private Boolean    tdsProvided;        // AP-only (from DB)
    private String     uiOwnerSite;        // read from live UI after fillDocumentHeader
    private String     uiCashBank;         // read from live UI after fillDocumentHeader
    private String     uiOnAccount;        // AP-only, read from live UI after fillDocumentHeader
    /** AP-only. Key = slId (or slName when slId is blank). Value = "Yes"/"No" read from live UI grid. */
    private Map<String, String> uiDeductTdsMap = new LinkedHashMap<>();
    /**
     * AP-only. Key = slId (or slName when slId is blank).
     * Value = TDS info text read from live UI grid e.g. "TDS not appl." or "TDS: 6.00".
     * TODO: Populate in CreateApVoucher after sub-ledger fill to activate the TDS Info column.
     */
    private Map<String, String> uiTdsInfoMap = new LinkedHashMap<>();
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
