package com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher;

import com.gsl.config.ConfigFactory;
import com.gsl.diagnostics.section.HtmlCaptureSection;
import com.gsl.driver.DriverManager;
import com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.enums.VoucherDomain;
import com.gsl.enums.WaitType;
import com.gsl.pages.Finance.Operations.Common.AllocateSitesPopup;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.pages.Finance.Operations.Common.GvPaymentInformationPopup;
import com.gsl.pages.Finance.Operations.Common.GvReceiptPaymentInformationPopup;
import com.gsl.pages.Finance.Operations.Common.PaymentInformationPopup;
import com.gsl.pages.enums.Finance.Operations.Common.SubLedgerGridButtonLabel;
import com.gsl.pages.enums.Finance.Operations.GeneralVoucher.GeneralVoucherGridColumnHeader;
import com.aventstack.extentreports.ExtentTest;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherSubLedgerInformationBase;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.reports.StepSummaryBuffer;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.BigDecimalUtils;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import com.gsl.validation.ui.UiComponentValidator;
import com.gsl.validation.ui.UiValidationFailure;
import com.gsl.validation.ui.UiValidationLevel;
import com.gsl.validation.ui.UiValidationScope;
import com.gsl.pages.enums.Finance.Operations.Common.FormSection;
import com.gsl.pages.enums.Finance.Operations.Common.GridColumnKind;
import com.gsl.pages.enums.Finance.Operations.Common.PaymentPanelField;
import org.openqa.selenium.Keys;
import com.gsl.utils.ui.FieldStyleSpec;
import org.openqa.selenium.By;
import org.openqa.selenium.interactions.Actions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Page component for the General Voucher "Expense/Income Detail" grid.
 *
 * <p><b>Key design decision — row-index-based cell editors, not the base's
 * name-keyed row lookup (see plan §3.3):</b> {@link VoucherSubLedgerInformationBase}'s
 * {@code clickAdjust}/{@code clickPaymentButton} key a row by Sub Ledger name +
 * Reference Site — this grid has no Reference Site column at all, and Sub
 * Ledger is blank for 2 of the 3 {@link GlLineType} row shapes, so no uniform
 * key exists. This class instead uses row-index-based cell editors throughout
 * (the fill loop already knows the row index).
 *
 * <p><b>UI-driven cell editing, no ExtJS injection:</b> every cell is opened via a real
 * Selenium click (or double-click) on the cell itself — located by header text via
 * {@link #rowCellByPosition(int, String)} — then typed into via the active floating cell
 * editor ({@link #ACTIVE_EDITOR_INPUT}). This is the same JS-free pattern already proven by
 * {@code VoucherSubLedgerInformationBase.clickAdjust}/{@code clickPaymentButton},
 * {@code APVoucherSubLedgerInformation.setDeductTdsNative}, and
 * {@code ReferenceSiteWiseFIFOPopup.selectReferenceSiteInCombo} — no {@code Ext.ComponentQuery},
 * no grid {@code itemId}, no column {@code dataIndex} guessing.
 *
 * <p>Still extends {@link VoucherSubLedgerInformationBase} purely to reuse
 * {@code clickAddRowButton()} — the base's own name-keyed {@code clickAdjust}/
 * {@code clickPaymentButton(subLedger, referenceSite, slid)} are never called (this grid has
 * no Reference Site column to key them on). The Cash/Bank counter-leg's Payment Window is
 * instead driven by {@link #handlePaymentInformation(PaymentInformation, boolean)}/
 * {@link #clickCashBankPaymentButton(boolean)} — a single, non-row-scoped trigger, since General
 * Voucher has exactly one counter-leg per voucher (not one per Expense/Income row — see {@code
 * GeneralVoucherPaymentVariant}'s class Javadoc). {@link #createPaymentPopup(boolean)} picks
 * between {@link GvPaymentInformationPopup} (Dr) and {@link GvReceiptPaymentInformationPopup} (Cr)
 * — see those classes' Javadocs.
 *
 * <p><b>Two boundlist shapes, two locators — do not unify them.</b> ExtJS renders a simple
 * single-column combo as {@code <li role="option">} but a multi-column picker as
 * {@code <div class="x-boundlist-item" role="option">} wrapping a
 * {@code table.combooverflowfinance}. General Ledger and Sub Ledger are the latter; matching them
 * with the {@code <li>} shape finds nothing and burns the full fluent-wait timeout. See
 * {@link #gridBoundlistOption(String, String)} vs {@link #simpleBoundlistOption(String)}. The
 * {@code <li>} call sites elsewhere in the repo ({@code PaymentInformationPopup}, the
 * {@code ItemAdd*} components, Reference Site in {@code AP/ARVoucherSubLedgerInformation}) are
 * simple combos and are correct as they stand.
 *
 * <p><b>UNVERIFIED:</b> whether General Ledger / Sub Ledger open on a single click and
 * Ledger Narration / Is Reversal / Expense(Dr) / Income(Cr) need a double-click (the split
 * assumed here, mirroring AR/AP's slCode-vs-Amount split) has never been confirmed against
 * the live DOM — {@link #openCellForEdit(By, String)} tries a single click first and falls
 * back to a double-click, so a wrong guess self-corrects instead of hanging.
 */
public class GeneralVoucherExpenseIncomeDetail extends VoucherSubLedgerInformationBase {

    /**
     * Active inline cell editor — ExtJS renders it as a floating overlay at document level,
     * not inside the cell's own DOM subtree, so this locator is global rather than row-scoped.
     * Only one editor is ever open at a time. Same pattern as {@code AdjustmentEntry} /
     * {@code ReferenceSiteWiseFIFOPopup}'s {@code ACTIVE_EDITOR_INPUT}.
     */
    private static final By ACTIVE_EDITOR_INPUT = By.xpath(
            "//div[contains(@class,'x-editor') and not(contains(@class,'x-hide-display'))]//input");

    /**
     * Root of the floating dropdown ExtJS renders for an open combo. Every option locator below is
     * scoped to this — an unscoped {@code [@role='option']} is not merely imprecise here, it is a
     * cross-contamination hazard: the top navigation renders its own
     * {@code <li role="option" data-menucode="financeoperation">} items at document level, so an
     * unscoped match can resolve to a side-menu entry instead of a dropdown row.
     */
    private static final String BOUNDLIST_ROOT =
            "//div[contains(@class,'x-boundlist-floating') and not(contains(@style,'display: none'))]";

    /**
     * The arrow/picker trigger of the currently-open cell editor. Scoped to the active editor for
     * the same reason as {@link #ACTIVE_EDITOR_INPUT} — ExtJS floats the editor at document level,
     * and its {@code combo-NNNN-trigger-picker} id is regenerated per page load, so the stable
     * anchor is the {@code x-form-arrow-trigger} class inside the one non-hidden editor.
     */
    private static final By ACTIVE_EDITOR_PICKER_TRIGGER = By.xpath(
            "//div[contains(@class,'x-editor') and not(contains(@class,'x-hide-display'))]"
                    + "//div[contains(@class,'x-form-arrow-trigger')]");

    /**
     * The Ledger Narration column's configured ExtJS {@code itemId}, which its cells carry as
     * {@code data-columnid} in place of the generated header id. See
     * {@link #rowCellByPosition(int, String, String)}. Unlike the {@code gridcolumn-NNNN} ids, an
     * itemId is authored in the app source, so it is stable across page loads.
     */
    private static final String LEDGER_NARRATION_COLUMN_ID = "ledgerNarrationCode";

    /**
     * Option row in a <b>grid-style</b> boundlist — the multi-column picker ExtJS builds for the
     * General Ledger and Sub Ledger combos: {@code div.x-boundlist-floating → div.x-boundlist-item
     * (role=option) → table.combooverflowfinance} with one {@code <td>} per column.
     *
     * <p><b>Match on {@code @data-qtip}, never {@code normalize-space(.)}.</b> Two independent
     * reasons, both confirmed against a captured live DOM: the row's visible text wraps the typed
     * substring in a search-highlight span, and it concatenates <i>every</i> column — for
     * "Advance Income Tax" the row text is actually "Advance Income Tax LOANS AND ADVANCES No No".
     * The attribute carries the clean per-cell value. Same reasoning already documented at
     * {@code AddAccountClassPage}'s {@code BOUNDLIST_ITEMS} and proven by
     * {@code APVoucherSubLedgerInformation.subLedgerPickerRow}.
     *
     * <p>Returns the {@code x-boundlist-item} itself (the option element, top layer) rather than
     * the inner {@code <td>}, to avoid the grid-view swallowing the click.
     *
     * @param id optional second column value used to disambiguate — names are not guaranteed unique
     */
    private static By gridBoundlistOption(String name, String id) {
        String base = BOUNDLIST_ROOT + "//div[contains(@class,'x-boundlist-item')"
                + " and .//td[@data-qtip=\"" + name.trim() + "\"]";
        if (id != null && !id.trim().isEmpty()) {
            return By.xpath(base + " and .//td[@data-qtip=\"" + id.trim() + "\"]]");
        }
        return By.xpath(base + "]");
    }

    /**
     * Option row in a <b>simple</b> single-column boundlist, which ExtJS renders as a plain
     * {@code <li role="option">} — the shape the rest of the codebase's combos use
     * (e.g. {@code PaymentInformationPopup}, the {@code ItemAdd*} components,
     * {@code AP/ARVoucherSubLedgerInformation}'s Reference Site picker).
     *
     * <p>Scoped to {@link #BOUNDLIST_ROOT}, unlike those call sites — see its Javadoc for why.
     * Matches either element name so a column-grid variant still resolves.
     */
    private static By simpleBoundlistOption(String value) {
        return By.xpath(BOUNDLIST_ROOT + "//*[self::li or self::div][@role='option']"
                + "[normalize-space(.)='" + value + "']");
    }

    /** The popup instance {@link #clickPaymentButton(int, boolean)} last created — retained (rather
     *  than only ever returned) so a caller that already moved past the fluent-chain call site
     *  (e.g. {@code GeneralVoucherEditPredicateFactory#PAYMENT_INFO_EDIT_BEHAVIOR}) can still reach
     *  back into it afterward, e.g. to read {@code ChequeRegisterPaymentInformationPopup
     *  #getLastObservedPreviousInstrumentNo()} for a live-UI-vs-DB cross-check. {@code null} until
     *  the first Payment popup interaction on this page instance. */
    private PaymentInformationPopup lastPaymentPopup;

    public GeneralVoucherExpenseIncomeDetail() {
        super(VoucherDomain.GV);
    }

    @Override
    protected PaymentInformationPopup createPaymentPopup() {
        return new GvPaymentInformationPopup();
    }

    /**
     * Dr/Cr-aware popup factory — see {@link com.gsl.pages.Finance.Operations.Common.GvPaymentInformationPopup}/
     * {@link GvReceiptPaymentInformationPopup}'s class Javadocs. {@code debitLine} is the
     * Cash/Bank/Cheque line's own Dr/Cr side (typically {@code MergedFinanceGeneralVoucherData
     * #isCashBankPaymentDebit()}) — a Dr line behaves AP-style (cheque-book known/unknown
     * lookup), a Cr line behaves AR-style (plain entry, no cheque-book concept).
     */
    private PaymentInformationPopup createPaymentPopup(boolean debitLine) {
        return debitLine ? new GvPaymentInformationPopup() : new GvReceiptPaymentInformationPopup();
    }

    /** The popup instance created by the most recent {@link #clickPaymentButton(int, boolean)}
     *  call — see {@link #lastPaymentPopup}'s Javadoc. */
    public PaymentInformationPopup getLastPaymentPopup() {
        return lastPaymentPopup;
    }

    /**
     * Clicks the Cheque/RTGS button of grid row {@code rowIndex} (row-scoped via
     * {@code data-recordindex}, see {@link #chequeRtgsButtonForRow}) — every grid row has its own
     * button, so each row can carry its own payment.
     *
     * @param debitLine see {@link #createPaymentPopup(boolean)} — that row's own Dr/Cr side
     */
    public PaymentInformationPopup clickPaymentButton(int rowIndex, boolean debitLine) {
        SeleniumUtils.click(chequeRtgsButtonForRow(rowIndex), WaitType.CLICKABLE,
                "GV_PAYMENT_BUTTON_ROW_" + (rowIndex + 1));
        JavascriptExecutorUtils.waitForExtJS();
        lastPaymentPopup = createPaymentPopup(debitLine);
        return lastPaymentPopup;
    }

    /** Row 1's Cheque/RTGS button — the single-payment entry point Edit still uses. */
    public PaymentInformationPopup clickCashBankPaymentButton(boolean debitLine) {
        return clickPaymentButton(0, debitLine);
    }

    /**
     * Fills the Payment Window of grid row {@code rowIndex} when {@code payment} calls for one.
     * No-op for {@code null} (a row without payment info — the app's own post-save "instrument
     * number missing" warning is expected for a Bank header) and for {@link PaymentSelectionType#CASH}.
     *
     * @param debitLine see {@link #createPaymentPopup(boolean)} — ignored for CASH/{@code null}.
     */
    public void handlePaymentInformation(int rowIndex, PaymentInformation payment, boolean debitLine) {
        if (payment == null || payment.getPaymentMode() == PaymentSelectionType.CASH) return;
        PaymentInformationPopup popup = clickPaymentButton(rowIndex, debitLine);
        notifyUi(l -> l.beforePaymentFill(rowIndex, popup));
        popup.onBeforeSubmit(() -> notifyUi(l -> l.afterPaymentFilled(rowIndex, popup, payment)));
        popup.fillAndSubmit(payment);
        notifyUi(l -> l.afterPaymentSubmitted(rowIndex, payment));
    }

    /** Single-payment overload (Edit): row 1. */
    public void handlePaymentInformation(PaymentInformation payment, boolean debitLine) {
        handlePaymentInformation(0, payment, debitLine);
    }

    /**
     * Fills the Payment Window of EVERY grid row that carries a payment in {@code data}, each through
     * its own row's button and on that row's own Dr/Cr side. Rows are processed in grid order, one
     * report step each; rows without a payment are skipped.
     */
    public void handleRowPayments(MergedFinanceGeneralVoucherData data) {
        List<ExpenseIncomeDetail> rows = data.getExpenseIncomeDetailList();
        for (int i = 0; i < rows.size(); i++) {
            final int rowIndex = i;
            ExpenseIncomeDetail row = rows.get(i);
            PaymentInformation payment = data.paymentForLine(row.getLineNo());
            if (payment == null || payment.getPaymentMode() == PaymentSelectionType.CASH) continue;
            StepExecutor.step("Payment Information — Row " + (rowIndex + 1) + " ("
                    + payment.getPaymentMode() + ")",
                    () -> handlePaymentInformation(rowIndex, payment, data.isPaymentDebit(row.getLineNo())));
        }
    }

    /**
     * Observer for the real (business) fill: lets a UI-validation recorder check each popup while it
     * is already open and each row once it is filled, without the fill code knowing anything about
     * validation. Installed only by flows that ask for it ({@link #setFillUiListener}); every
     * callback is isolated — an exception inside a listener is logged and never reaches the fill.
     */
    public interface FillUiListener {
        default void beforeSiteAllocation(int rowIndex, AllocateSitesPopup popup) {}
        default void afterSiteAllocation(int rowIndex, AllocateSitesPopup popup, List<SiteAllocation> allocations) {}
        default void beforePaymentFill(int rowIndex, PaymentInformationPopup popup) {}
        /** The popup is fully filled and still open (OK not yet clicked). */
        default void afterPaymentFilled(int rowIndex, PaymentInformationPopup popup, PaymentInformation payment) {}
        default void afterPaymentSubmitted(int rowIndex, PaymentInformation payment) {}
        default void afterRowFilled(int rowIndex, ExpenseIncomeDetail row) {}
    }

    private FillUiListener fillUiListener;

    /** Installs (or, with {@code null}, removes) the fill observer — see {@link FillUiListener}. */
    public void setFillUiListener(FillUiListener listener) {
        this.fillUiListener = listener;
    }

    private void notifyUi(java.util.function.Consumer<FillUiListener> call) {
        FillUiListener l = fillUiListener;
        if (l == null) return;
        try {
            call.accept(l);
        } catch (RuntimeException e) {
            ReportLogger.business("[UI validation] checkpoint skipped after an error (fill continues): "
                    + e.getClass().getSimpleName() + " — " + e.getMessage());
        }
    }

    // =========================================================================
    // Fill loop
    // =========================================================================

    /**
     * Fills every {@code ExpenseIncomeDetail} row, branching per row on its
     * (DB-resolved) {@link GlLineType}. Lives in this component, not the test
     * class — matching how AP/AR keep row-branching inside {@code *SubLedgerInformation}.
     *
     * <p>Equivalent to {@link #fillExpenseIncomeRows(MergedFinanceGeneralVoucherData, boolean)}
     * with {@code expectSiteAllocationComplete=true} — every current caller except {@code
     * GeneralVoucherPredicateFactory.NEGATIVE_FORM_VALIDATION_BEHAVIOR} expects each row's site
     * allocation to actually balance, so that's the default rather than something every other
     * call site has to spell out.
     */
    public GeneralVoucherExpenseIncomeDetail fillExpenseIncomeRows(MergedFinanceGeneralVoucherData data) {
        return fillExpenseIncomeRows(data, true);
    }

    /**
     * @param expectSiteAllocationComplete {@code false} only for the two deliberately-incomplete
     *        negative scenarios ({@code NEG_SITE_ALLOCATION_PENDING}/{@code NEG_SITE_NOT_ALLOCATED},
     *        driven through {@code NEGATIVE_FORM_VALIDATION_BEHAVIOR}) that need {@link
     *        #allocateSites}/{@link #commitSiteAllocations} to accept an unbalanced Allocate Sites
     *        popup (Cancel instead of OK) so the flow can reach Save and assert the app's own
     *        validation message. {@code true} for every positive scenario, so a genuine test-data
     *        gap (a missing/short split line) throws here — naming the row and pending amount —
     *        instead of silently Cancelling and surfacing 4 wait-layers later as an opaque
     *        {@code messagebox-1001-msg} timeout (see {@link #commitSiteAllocations}'s Javadoc).
     */
    public GeneralVoucherExpenseIncomeDetail fillExpenseIncomeRows(MergedFinanceGeneralVoucherData data,
            boolean expectSiteAllocationComplete) {
        List<ExpenseIncomeDetail> rows = data.getExpenseIncomeDetailList();
        // Item N: collect each row's own verification rollup (keyed by lineNo, insertion order
        // preserved) so GeneralVoucherPredicateFactory.recordExpenseIncomeDetailSummary can fold
        // it into the ONE consolidated per-line summary already shown at the end of "Fill
        // Expense/Income Detail rows" — instead of only the per-row "Verify Row" drill-down box.
        lastRowVerificationSummaries.clear();
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                clickAddRowButton();
                JavascriptExecutorUtils.waitForExtJS();
            }
            final int rowIndex = i;
            ExpenseIncomeDetail row = rows.get(rowIndex);
            GlLineType type = row.getResolvedGlType();
            if (type == null) {
                throw new IllegalStateException(
                        "[GV ExpenseIncomeDetail] resolvedGlType not set for LineNo='" + row.getLineNo()
                        + "' — the data source (Excel or DB) must resolve it via FinGlDao before fill.");
            }
            BigDecimal amount = row.isDebit() ? row.getExpenseDr() : row.getIncomeCr();
            ReportLogger.trace("[GV ExpenseIncomeDetail] Row " + (rowIndex + 1) + ": GL='" + row.getGeneralLedger()
                    + "', LineNo='" + row.getLineNo() + "', type=" + type);

            // Point 9: one collapsible group node per row — mirrors AP/AR's "SL Row N" grouping
            // (SubLedgerRowStepRegistry) instead of GV's previous flat, ungrouped fills. Label
            // reuses GlLineType.label() so the node title matches the human-readable text
            // GeneralVoucherPredicateFactory.recordExpenseIncomeDetailSummary shows too.
            String rowLabel = "Line " + (row.getLineNo() != null ? row.getLineNo() : String.valueOf(rowIndex + 1))
                    + " — GL: " + row.getGeneralLedger() + " | " + type.label()
                    + " | Amount: " + (amount == null ? "—" : amount);
            ExtentTest rowNode = StepExecutor.group(rowLabel);

            StepExecutor.stepUnder(rowNode, "Fill GL / Narration / Reversal / Amount",
                    () -> fillRowBasicFields(rowIndex, row));

            switch (type) {
                case TYPE1_NONE -> { /* nothing further */ }
                case TYPE2_SITE_ONLY -> StepExecutor.stepUnder(rowNode, "Allocate Sites",
                        () -> allocateSites(rowIndex, data.getSiteAllocationsForLine(row.getLineNo()),
                                expectSiteAllocationComplete));
                case TYPE3_SITE_AND_SUBLEDGER -> {
                    StepExecutor.stepUnder(rowNode, "Set Sub Ledger",
                            () -> setSubLedger(rowIndex, row.getSubLedger(), row.getSubLedgerId()));
                    StepExecutor.stepUnder(rowNode, "Allocate Sites",
                            () -> allocateSites(rowIndex, data.getSiteAllocationsForLine(row.getLineNo()),
                                    expectSiteAllocationComplete));
                }
            }

            // Point 9: UI validation after entry — reads every filled field back and records a
            // tick/cross summary for this row, closing the gap where the grid's fills were never
            // re-confirmed against the live UI (unlike the header fields' own
            // recordDocumentInformationSummary checks). Item N: also returns a compact rollup,
            // collected below for the end-of-section consolidated summary.
            String lineKey = row.getLineNo() != null ? row.getLineNo() : String.valueOf(rowIndex + 1);
            final String[] rollup = new String[1];
            StepExecutor.stepUnder(rowNode, "Verify Row — UI read-back",
                    () -> rollup[0] = recordRowVerification(rowIndex, row, type));
            lastRowVerificationSummaries.put(lineKey, rollup[0]);
            notifyUi(l -> l.afterRowFilled(rowIndex, row));
        }
        return this;
    }

    /** Item N: per-row verification rollups from the most recent {@link #fillExpenseIncomeRows}
     *  call, keyed by line number (insertion order preserved) — each value is a short "✓ all
     *  fields match" / "✗ 1 mismatch: X" summary, folded into
     *  {@code GeneralVoucherPredicateFactory.recordExpenseIncomeDetailSummary}'s own consolidated
     *  "Line N — ..." entry instead of only being visible in each row's own "Verify Row" box. */
    private final java.util.LinkedHashMap<String, String> lastRowVerificationSummaries = new java.util.LinkedHashMap<>();

    public java.util.Map<String, String> getLastRowVerificationSummaries() {
        return lastRowVerificationSummaries;
    }

    /** See {@link #fillExpenseIncomeRows}'s "Verify Row — UI read-back" step.
     *  @return a compact rollup of this row's checks (Item N) — {@code "✓ all fields match"} when
     *          every check matched, otherwise {@code "✗ <n> mismatch(es): field1, field2"}. */
    private String recordRowVerification(int rowIndex, ExpenseIncomeDetail row, GlLineType type) {
        List<String> mismatches = new java.util.ArrayList<>();

        if (!recordCheck("General Ledger", row.getGeneralLedger(), getGeneralLedger(rowIndex))) {
            mismatches.add("General Ledger");
        }
        if (row.getLedgerNarration() != null && !row.getLedgerNarration().isBlank()) {
            if (!recordCheck("Ledger Narration", row.getLedgerNarration(), getLedgerNarration(rowIndex))) {
                mismatches.add("Ledger Narration");
            }
        }
        if (!recordCheck("Is Reversal", row.isReversalYes() ? "Yes" : "No", getIsReversal(rowIndex))) {
            mismatches.add("Is Reversal");
        }

        BigDecimal expectedAmount = row.isDebit() ? row.getExpenseDr() : row.getIncomeCr();
        BigDecimal actualAmount = row.isDebit() ? getExpenseAmount(rowIndex) : getIncomeAmount(rowIndex);
        boolean amountMatch = expectedAmount == null
                || (actualAmount != null && expectedAmount.compareTo(actualAmount) == 0);
        String amountLabel = row.isDebit() ? "Expense (Dr)" : "Income (Cr)";
        StepSummaryBuffer.record(amountLabel
                + " — filled with <b>" + nullSafe(expectedAmount == null ? null : expectedAmount.toPlainString())
                + "</b>, validated in UI as <b>" + nullSafe(actualAmount == null ? null : actualAmount.toPlainString())
                + "</b>", amountMatch);
        if (!amountMatch) mismatches.add(amountLabel);

        if (type.isSubLedgerApplicable() && row.getSubLedger() != null && !row.getSubLedger().isBlank()) {
            if (!recordCheck("Sub Ledger", row.getSubLedger(), getSubLedger(rowIndex))) {
                mismatches.add("Sub Ledger");
            }
        }

        return mismatches.isEmpty()
                ? "&#10003;&nbsp;all fields match"
                : "&#10007;&nbsp;" + mismatches.size() + " mismatch(es): " + String.join(", ", mismatches);
    }

    /** One "label — filled with X, validated in UI as Y" line, tick when they match, cross when
     *  they genuinely differ — same convention as
     *  {@code GeneralVoucherPredicateFactory#recordFieldCheck}.
     *  @return {@code true} when the values matched. */
    private static boolean recordCheck(String label, String expected, String actual) {
        boolean match = expected == null || expected.trim().equalsIgnoreCase(actual == null ? null : actual.trim());
        StepSummaryBuffer.record(label + " — filled with <b>" + nullSafe(expected)
                + "</b>, validated in UI as <b>" + nullSafe(actual) + "</b>", match);
        return match;
    }

    private static String nullSafe(String value) {
        return (value == null || value.isBlank()) ? "&mdash;" : value;
    }

    /**
     * Fills General Ledger, Ledger Narration, Is Reversal, and the Expense/Income amount for one
     * row — every column {@link #fillExpenseIncomeRows} sets before branching on {@link GlLineType}.
     * Extracted so {@code GeneralVoucherPredicateFactory}'s Sub-Ledger-required negative scenario
     * can fill a row up to (but not including) the Site-button click, which for a Type3 row with no
     * Sub Ledger is where the app raises its own blocking dialog — see
     * {@link #clickSiteButtonExpectingBlock(int)}.
     */
    public GeneralVoucherExpenseIncomeDetail fillRowBasicFields(int rowIndex, ExpenseIncomeDetail row) {
        setGeneralLedger(rowIndex, row.getGeneralLedger());
        setLedgerNarration(rowIndex, row.getLedgerNarration());
        setIsReversal(rowIndex, row.isReversalYes());
        if (row.isDebit()) {
            setExpenseAmount(rowIndex, row.getExpenseDr());
        } else {
            setIncomeAmount(rowIndex, row.getIncomeCr());
        }
        return this;
    }

    /**
     * Edits an EXISTING Expense/Income Detail row in place — the Edit-flow counterpart to
     * {@link #fillExpenseIncomeRows}. Deliberately does <b>not</b> click "Add Row" (unlike the
     * create-flow loop, which adds one new row per call after the first) — {@code rowIndex} must
     * already exist in the grid (resolved by the Edit data source/test from an existing
     * {@code finvchdet} row's position). Reuses the same {@link #fillRowBasicFields} helper the
     * create flow uses, applied against the given row index instead of a freshly-added one — sets
     * General Ledger/Ledger Narration/Is Reversal/Expense(Dr)-Income(Cr) only.
     *
     * <p>V1's Edit scope (plan §V1 confirmed scope) is limited to narration/reversal/amount on an
     * already-Site-resolved row — it deliberately does not re-open the Allocate Sites popup or
     * change Sub Ledger, since neither is part of the detail-line-edit scenario and re-touching a
     * live site allocation is unconfirmed/out of scope territory (see {@link #allocateSites}'s own
     * Javadoc on how fragile that popup already is for the create flow).
     */
    public GeneralVoucherExpenseIncomeDetail editRow(int rowIndex, ExpenseIncomeDetail newValues) {
        ExtentLogger.info("[GV ExpenseIncomeDetail] Editing existing row at index " + rowIndex + ": GL='"
                + newValues.getGeneralLedger() + "', LineNo='" + newValues.getLineNo() + "'");
        fillRowBasicFields(rowIndex, newValues);
        return this;
    }

    /**
     * Appends a brand-new row (clicking Add Row first, same trigger
     * {@link #fillExpenseIncomeRows}'s create-loop uses for every row after the first) and fills
     * it — the Edit-flow counterpart to that create-loop, for plan §4B's "add a brand-new line"
     * shape ({@code DetailLineEditTarget.EditShape#ADD_NEW_LINE}).
     *
     * <p>V1 scope is deliberately restricted to a {@code TYPE1_NONE} new line (see
     * {@code DbGeneralVoucherEditDataSource.buildAddNewLine}'s Javadoc for why — the
     * {@code isCostTagApplicable()} pre-edit-resolution trap), so {@code allocations} is expected
     * empty/null for now; accepted anyway so a future Type2/Type3 new-line variant needs no
     * signature change here.
     *
     * @param rowIndex the new row's 0-based grid index — always the last row (grid size before
     *        this call), since Add Row appends
     */
    public GeneralVoucherExpenseIncomeDetail addRow(int rowIndex, ExpenseIncomeDetail newRow,
            List<SiteAllocation> allocations) {
        ExtentLogger.info("[GV ExpenseIncomeDetail] Adding new row at index " + rowIndex + ": GL='"
                + newRow.getGeneralLedger() + "', LineNo='" + newRow.getLineNo() + "'");
        clickAddRowButton();
        JavascriptExecutorUtils.waitForExtJS();
        fillRowBasicFields(rowIndex, newRow);
        if (allocations != null && !allocations.isEmpty()) {
            allocateSites(rowIndex, allocations, true);
        }
        return this;
    }

    /**
     * Fills a {@code TYPE1_NONE} row (no Site, no Sub Ledger) at the create-flow's own position in
     * the grid — {@code rowIndex == 0} reuses the grid's default empty first row (no Add Row click,
     * same as {@link #fillExpenseIncomeRows}'s own {@code i == 0} branch), every later index clicks
     * Add Row first. Unlike {@link #fillExpenseIncomeRows}/{@link #addRow}, this skips the
     * {@code GlLineType}-branching and site-allocation machinery entirely, so it works for rows
     * built directly in a test (no DB/Excel {@code resolvedGlType} resolution) as long as the GL is
     * genuinely {@code TYPE1_NONE} — for business-value checks (e.g. Net Amount computation) that
     * don't need the full combination-matrix fill pipeline.
     *
     * <p>Confirmed live (2026-10-01): row 0 needs its own mask wait too — unlike every later index
     * (which gets one implicitly via the Add Row click's {@code waitForExtJS()}), row 0 is filled
     * immediately after the Add form opens, and the grid's own initial-render mask can still be up,
     * producing an {@code ElementClickIntercepted} on the General Ledger cell.
     */
    public GeneralVoucherExpenseIncomeDetail fillTypeOneNoneRow(int rowIndex, ExpenseIncomeDetail row) {
        if (rowIndex > 0) {
            clickAddRowButton();
            JavascriptExecutorUtils.waitForExtJS();
        } else {
            WaitUtils.waitForExtMask("Fill TYPE1_NONE row 0 — grid ready");
        }
        return fillRowBasicFields(rowIndex, row);
    }

    /**
     * Fills the Allocate Sites popup with {@code allocations}, then commits with OK when the
     * allocation is complete or backs out with Cancel when it deliberately isn't.
     *
     * <p><b>Why this isn't always OK.</b> The app refuses OK outright while Pending Amount != 0 —
     * confirmed live, run 20260807_165243: <i>"Actual amount doesn't match with the tagged amount.
     * There shouldn't be any pending amount."</i> Two negative-boundary scenarios exist precisely
     * to leave the allocation incomplete
     * ({@code GeneralVoucherPredicateFactory.siteAllocationPending()}: a partial amount;
     * {@code siteNotAllocated()}: an empty {@code allocations} list) and go on to assert a
     * <i>different</i>, Save-time message —
     * {@link com.gsl.utils.dataprovider.fixture.finance.NegativeValidation#GV_SITE_ALLOCATION_PENDING}
     * / {@code GV_SITE_NOT_ALLOCATED} — that the app only raises once the row is actually left
     * incomplete. Forcing OK here would throw the popup's own refusal instead and the test would
     * never reach Save to see the message it's meant to assert on. Those two scenarios are the
     * only callers that pass {@code expectComplete=false}; every other caller does, so a genuine
     * test-data gap in a positive scenario surfaces here instead of as a downstream Save timeout.
     *
     * <p>Decided from the popup's own Pending Amount field, read after every Add — not from
     * summing {@code allocations} locally — so this reflects whatever the app actually computed,
     * the same values the diagnostic in {@link AllocateSitesPopup#clickOk()} would report if OK
     * were attempted anyway. An unreadable Pending Amount is treated as incomplete: Cancel is the
     * side that can never make things worse for the {@code expectComplete=false} callers; for
     * {@code expectComplete=true} callers it throws instead, same as a non-zero pending amount.
     */
    private void allocateSites(int rowIndex, List<SiteAllocation> allocations, boolean expectComplete) {
        AllocateSitesPopup popup = clickSiteButton(rowIndex);
        commitSiteAllocations(rowIndex, popup, allocations, "Allocate Sites", expectComplete);
    }

    /**
     * Re-opens the row's Allocate Sites popup and applies {@code newSiteAllocations} — plan §4B's
     * "site-split-only" shape ({@code DetailLineEditTarget.EditShape#SITE_SPLIT_ONLY}): same total
     * amount, only the sitewise split changes.
     *
     * <p><b>UNCONFIRMED against the live DOM</b> — flagged in the plan for confirmation before
     * relying on this for a real run: whether re-adding a site that is ALREADY present in the
     * popup's Allocated Site(s) grid (with a different amount) edits that row in place, inserts a
     * duplicate, or is rejected by the app. No existing call site has ever exercised this —
     * {@link #allocateSites}/the create flow only ever adds to an empty popup. If it does not edit
     * in place, this method needs a "remove an allocated row" locator that doesn't exist anywhere
     * in this codebase today — the same descope reasoning already applied to the dropped
     * "remove-and-re-add a line" shape.
     */
    public GeneralVoucherExpenseIncomeDetail reallocateSites(int rowIndex, List<SiteAllocation> newSiteAllocations) {
        ExtentLogger.info("[GV ExpenseIncomeDetail] Row " + (rowIndex + 1) + ": reallocating sites — "
                + newSiteAllocations.size() + " target allocation(s)");
        AllocateSitesPopup popup = clickSiteButton(rowIndex);
        // Edit's SITE_SPLIT_ONLY shape always expects the re-split to balance (same total amount,
        // only the sitewise split changes) — this is never one of the two intentionally-incomplete
        // negative scenarios, so expectComplete=true.
        commitSiteAllocations(rowIndex, popup, newSiteAllocations, "Site reallocation", true);
        return this;
    }

    /** Shared commit logic behind {@link #allocateSites}/{@link #reallocateSites} — see
     *  {@link #allocateSites}'s Javadoc for why OK isn't always the right action.
     *
     * @param expectComplete {@code false} for the two negative scenarios that deliberately leave
     *        the row incomplete (see {@link #allocateSites}'s Javadoc) — Cancels quietly on a
     *        non-zero/unreadable Pending Amount, same as before. {@code true} for every other
     *        caller — throws instead of silently Cancelling, naming the row and the actual pending
     *        amount, so a genuine test-data gap fails here rather than 4 wait-layers later as an
     *        opaque {@code messagebox-1001-msg} Save timeout. */
    private void commitSiteAllocations(int rowIndex, AllocateSitesPopup popup,
            List<SiteAllocation> allocations, String actionLabel, boolean expectComplete) {
        notifyUi(l -> l.beforeSiteAllocation(rowIndex, popup));
        for (SiteAllocation alloc : allocations) {
            popup.addSiteAllocation(alloc.getSiteName(), alloc.getAmount());
        }
        notifyUi(l -> l.afterSiteAllocation(rowIndex, popup, allocations));

        BigDecimal pending = popup.getPendingAmount();
        if (pending != null && pending.signum() == 0) {
            popup.clickOk();
            // Item O: this step previously had zero summary content (confirmed live — the
            // "Allocate Sites" node showed only its duration, nothing else) — state what was
            // actually allocated instead of leaving the reader to guess from the row's own
            // "Sites: ..." line elsewhere.
            String allocatedList = allocations.isEmpty() ? "&mdash;"
                    : allocations.stream()
                            .map(a -> a.getSiteName() + " (" + a.getAmount() + ")")
                            .reduce((a, b) -> a + ", " + b).orElse("&mdash;");
            StepSummaryBuffer.record("Allocated: <b>" + allocatedList
                    + "</b> — Pending: <b>" + pending.toPlainString() + "</b>");
            return;
        }

        String pendingStr = pending == null ? "<unreadable>" : pending.toPlainString();
        if (expectComplete) {
            throw new IllegalStateException("[GV ExpenseIncomeDetail] Row " + (rowIndex + 1) + ": "
                    + actionLabel + " did not balance after " + allocations.size() + " allocation(s) — "
                    + "Pending Amount = " + pendingStr + ". This row was expected to fully allocate; "
                    + "check the SiteAllocation test data for LineNo " + (rowIndex + 1) + " (a rounding "
                    + "gap or a missing split line will leave the voucher unbalanced and Save silently "
                    + "disabled).");
        }

        ExtentLogger.warning("[GV ExpenseIncomeDetail] Row " + (rowIndex + 1) + ": " + actionLabel + " left "
                + "incomplete after " + allocations.size() + " allocation(s) — Pending Amount = "
                + pendingStr
                + ". Cancelling instead of OK; Save is expected to raise its own site-allocation "
                + "validation.");
        popup.clickCancel();
    }

    /**
     * The row's "..." indicator button — ExtJS's generic {@code <i class="mdi-dots-horizontal">}
     * grid-row-menu icon, rendered in the {@code gslTransCol_Multipurpose} column.
     *
     * <p><b>This is where a failed Save's per-row detail actually lives.</b> When Save rejects a
     * row, the popup shows only a generic wrapper — <i>"Some problems encountered in the detail
     * section. Please view the detail on the left pane tooltip."</i> — and hovering this icon reveals
     * the real reason as an ExtJS QuickTip. Confirmed live, run 20260807_171711: a Type2 row left
     * under-allocated showed exactly that generic text in the Save dialog, with the specific
     * "Site allocation is not complete..." reason only reachable by hovering here. Read via
     * {@link com.gsl.utils.Selenium.ExtJsQuickTipUtils#hoverAndVerify}, same mechanism AP/AR already
     * use for their own sub-ledger-row tooltips ({@code SubLedgerRowLocator.dotsButton} +
     * {@code INLINE_VALIDATION_BEHAVIOR} in {@code Ap/ArVoucherPredicateFactory}) — same icon class,
     * confirmed identical in this grid's own captured DOM.
     *
     * <p>Row-scoped by the real {@code data-recordindex} + {@code data-columnid}, same idiom as
     * {@link #siteButtonForRow(int)} / {@link #rowCellByPosition(int, String)} — {@code
     * gslTransCol_Multipurpose} is this grid's own columnid for the button (AP/AR anchor by Sub
     * Ledger name instead, since their grid has no per-row index identity as stable as this one's).
     */
    public static By rowIndicatorButton(int rowIndex) {
        return By.xpath("//table[@data-recordindex='" + rowIndex + "']"
                + "//td[@data-columnid='gslTransCol_Multipurpose']//i[contains(@class,'mdi-dots-horizontal')]");
    }

    // =========================================================================
    // Row actions
    // =========================================================================

    /** Clicks the row's "Site" button, opening the Allocate Sites popup. */
    public AllocateSitesPopup clickSiteButton(int rowIndex) {
        // clickWithStaleRetry, not the plain variant: this is the entry point into
        // AllocateSitesPopup's own DOM-churning flow — same grid-re-render exposure.
        SeleniumUtils.clickWithStaleRetry(siteButtonForRow(rowIndex), WaitType.CLICKABLE, "GV_SITE_BUTTON_ROW_" + rowIndex);
        JavascriptExecutorUtils.waitForExtJS();
        return new AllocateSitesPopup();
    }

    /**
     * Clicks the row's "Site" button without assuming Allocate Sites opens — for the one case
     * where it deliberately can't: a Type3 row (Site {@code AND} Sub Ledger applicable) with no
     * Sub Ledger selected. Confirmed live, run 20260807_171711: the app blocks the button itself
     * with its own dialog — header <i>"No sub-ledger selected"</i>, body
     * <i>"Please select sub-ledger."</i> — instead of ever opening Allocate Sites, which is why the
     * earlier attempt to drive {@link #clickSiteButton(int)} + {@link AllocateSitesPopup} for this
     * exact scenario timed out waiting for a window that was never going to appear.
     *
     * <p>Returns a {@link DocumentPopup} so the caller decides what actually showed up — same shape
     * as {@code ApVoucherAdd.clickAddSubLedgerRow()}, which returns a bare {@code DocumentPopup}
     * for its own analogous "grid action blocked by a prerequisite" case (Add Row before Cash/Bank
     * is set).
     */
    public DocumentPopup clickSiteButtonExpectingBlock(int rowIndex) {
        SeleniumUtils.clickWithStaleRetry(siteButtonForRow(rowIndex), WaitType.CLICKABLE, "GV_SITE_BUTTON_ROW_" + rowIndex);
        JavascriptExecutorUtils.waitForExtJS();
        return new DocumentPopup();
    }

    /**
     * Row scoped by the real {@code data-recordindex} attribute and the "Site" button column's
     * real {@code data-columnid} (confirmed live: {@code data-columnid="siteButton"}) — same
     * reasoning as {@link #rowCellByPosition(int, String)}; the button's own inner markup wasn't
     * separately verified so the existing button-text match is kept inside the scoped cell.
     */
    private static By siteButtonForRow(int rowIndex) {
        return By.xpath("//table[@data-recordindex='" + rowIndex + "']" +
                "//td[@data-columnid='siteButton']" +
                "//button[normalize-space()='" + SubLedgerGridButtonLabel.SITE.label() + "']");
    }

    /**
     * Opens a grid cell's inline editor via a real Selenium click — no {@code Ext.ComponentQuery},
     * no grid {@code itemId}. Tries a single click first (matches AR/AP's single-click combo
     * columns, e.g. {@code slCode}); if {@link #ACTIVE_EDITOR_INPUT} doesn't appear within the
     * short-wait window, falls back to a real double-click on the same cell (matches AR/AP's
     * double-click plain/combo columns, e.g. Amount, Deduct TDS). Self-corrects a wrong
     * single-vs-double-click guess instead of hanging on it.
     */
    private void openCellForEdit(By cell, String logTag) {
        SeleniumUtils.click(cell, WaitType.CLICKABLE, logTag);
        JavascriptExecutorUtils.waitForExtJS();
        int shortWaitSeconds = (int) ConfigFactory.getWaitConfig().shortWaitSeconds();
        if (!SeleniumUtils.isElementPresent(ACTIVE_EDITOR_INPUT, shortWaitSeconds)) {
            // Item AD (Round 5): debug-gated UI_ACTION, not a plain uncategorized log.
            ReportLogger.uiAction(logTag + ": single click did not open the editor — retrying with double-click");
            WaitUtils.fluentWaitBuilder()
                    .withMessage("grid cell " + logTag + " to accept a double-click — locator: " + cell)
                    .until(driver -> {
                        new Actions(driver).doubleClick(driver.findElement(cell)).perform();
                        return true;
                    });
            JavascriptExecutorUtils.waitForExtJS();
        }
    }

    private void setGeneralLedger(int rowIndex, String generalLedger) {
        // Equals-guard, same idiom as setSubLedger below — an unedited row's GL already shows the
        // target value (e.g. a detail-line edit that only changes amount/narration, not GL), so
        // without this check every such row unconditionally re-opened the combo and re-selected
        // the same option for no reason.
        String current = getGeneralLedger(rowIndex);
        if (current != null && current.trim().equalsIgnoreCase(generalLedger.trim())) {
            // Item AD (Round 5): debug-gated UI_ACTION, same as the other "already 'X' — skipping
            // update" guards in this class.
            ReportLogger.uiAction("[GV ExpenseIncomeDetail] Row " + rowIndex + ": General Ledger already '"
                    + current + "' — skipping update");
            return;
        }

        SeleniumUtils.fillAndVerifyCommit("GV_GENERAL_LEDGER_ROW_" + rowIndex, generalLedger, () -> {
            openCellForEdit(rowCellByPosition(rowIndex, "General ledger"), "GV_GL_CELL_ROW_" + rowIndex);

            SeleniumUtils.clear(ACTIVE_EDITOR_INPUT, "GV_GL_COMBO");
            SeleniumUtils.sendKeys(ACTIVE_EDITOR_INPUT, WaitType.VISIBLE, generalLedger, "GV_GL_COMBO");
            JavascriptExecutorUtils.waitForExtJS();

            // Pages forward through the picker's own Next-Page control if the exact match isn't
            // on the currently-rendered page — a live-typed search here is a CONTAINS match, not
            // exact, and can return more rows than fit on one page (confirmed live, run
            // 20260903_100559, FIN-GV-EDIT-DETAIL-02: "Page 1 of 4" with Next Page enabled while
            // the old single-page wait below timed out at 30s). See
            // SeleniumUtils.clickBoundlistOptionAcrossPages's own Javadoc.
            By option = gridBoundlistOption(generalLedger, null);
            SeleniumUtils.clickBoundlistOptionAcrossPages(BOUNDLIST_ROOT, option, "GV_GL_OPTION_" + generalLedger);
            ReportLogger.fieldEntry(generalLedger + " selected from dropdown");
            JavascriptExecutorUtils.waitForExtJS();
            JavascriptExecutorUtils.waitForPageLoad();
        }, () -> getGeneralLedger(rowIndex));
    }

    private void setSubLedger(int rowIndex, String subLedger, String subLedgerId) {
        if (subLedger == null || subLedger.isBlank()) {
            // Deliberately left blank — e.g. a negative-boundary scenario testing the app's own
            // "Sub Ledger required" validation on a Type3 row. Same no-op-on-blank guard as
            // setLedgerNarration/setExpenseAmount/setIncomeAmount.
            ExtentLogger.info("[GV ExpenseIncomeDetail] Row " + rowIndex
                    + ": Sub Ledger left blank (Type3 row) — skipping fill, Save is expected to raise "
                    + "the app's own Sub Ledger required validation.");
            return;
        }

        // Auto-populate rule (user-confirmed): a Type3 GL with exactly one eligible Sub Ledger
        // (finsl_gl, ext='N') has the app fill it in the instant the General Ledger is set — no
        // dropdown ever opens for it. A GL with more than one leaves the field blank and genuinely
        // requires the click-search-select flow below. Checking the committed value first, rather
        // than always driving the UI, handles both cases with the same code: proceeding straight to
        // openCellForEdit against an already-auto-filled cell drove that exact failure live — run
        // 20260807_165243, positive test, GL had exactly one match ("WB-INPUT IGST" already
        // committed), and the wait for a boundlist row that was never going to render again timed
        // out at 30s: "Sub Ledger option 'WB-INPUT IGST' to be clickable in the open combo dropdown".
        String current = getSubLedger(rowIndex);
        if (current != null && current.trim().equalsIgnoreCase(subLedger.trim())) {
            // Item AD (Round 5): debug-gated UI_ACTION, same as the other skip-guard diagnostics.
            ReportLogger.uiAction("[GV ExpenseIncomeDetail] Row " + rowIndex + ": Sub Ledger already '"
                    + current + "' — GL has exactly one eligible Sub Ledger and the app auto-populated "
                    + "it, skipping the select flow");
            return;
        }

        SeleniumUtils.fillAndVerifyCommit("GV_SUB_LEDGER_ROW_" + rowIndex, subLedger, () -> {
            openCellForEdit(rowCellByPosition(rowIndex, "Sub Ledger"), "GV_SL_CELL_ROW_" + rowIndex);

            SeleniumUtils.clear(ACTIVE_EDITOR_INPUT, "GV_SL_COMBO");
            SeleniumUtils.sendKeys(ACTIVE_EDITOR_INPUT, WaitType.VISIBLE, subLedger, "GV_SL_COMBO");
            JavascriptExecutorUtils.waitForExtJS();

            // Sub Ledger names are not guaranteed unique — disambiguate by Id when provided. Both
            // branches hit the SAME grid-style picker, so both use the data-qtip locator; the Id is
            // only an extra column predicate, never a different element shape. Same cross-page
            // reasoning as setGeneralLedger above — this picker is the identical paged widget.
            By option = gridBoundlistOption(subLedger, subLedgerId);
            SeleniumUtils.clickBoundlistOptionAcrossPages(BOUNDLIST_ROOT, option, "GV_SL_OPTION_" + subLedger);
            ReportLogger.fieldEntry(subLedger + " selected from dropdown");
            JavascriptExecutorUtils.waitForExtJS();
            JavascriptExecutorUtils.waitForPageLoad();
        }, () -> getSubLedger(rowIndex));
    }

    private void setLedgerNarration(int rowIndex, String narration) {
        if (narration == null || narration.isBlank()) return;
        String current = getLedgerNarration(rowIndex);
        if (current != null && current.trim().equalsIgnoreCase(narration.trim())) {
            // Item AD (Round 5): debug-gated UI_ACTION.
            ReportLogger.uiAction("[GV ExpenseIncomeDetail] Row " + rowIndex + ": Ledger Narration already '"
                    + current + "' — skipping update");
            return;
        }
        SeleniumUtils.fillAndVerifyCommit("GV_LEDGER_NARRATION_ROW_" + rowIndex, narration, () -> {
            openCellForEdit(rowCellByPosition(rowIndex, "Ledger Narration", LEDGER_NARRATION_COLUMN_ID),
                    "GV_NARRATION_CELL_ROW_" + rowIndex);

            SeleniumUtils.clear(ACTIVE_EDITOR_INPUT, "GV_NARRATION");
            SeleniumUtils.sendKeys(ACTIVE_EDITOR_INPUT, WaitType.VISIBLE, narration, "GV_NARRATION");
            SeleniumUtils.pressTab(ACTIVE_EDITOR_INPUT, "GV_NARRATION");
            JavascriptExecutorUtils.waitForExtJS();
        }, () -> getLedgerNarration(rowIndex));
    }

    private void setIsReversal(int rowIndex, boolean reversal) {
        String value = reversal ? "Yes" : "No";
        String current = getIsReversal(rowIndex);
        if (current != null && current.trim().equalsIgnoreCase(value)) {
            // Item AD (Round 5): debug-gated UI_ACTION.
            ReportLogger.uiAction("[GV ExpenseIncomeDetail] Row " + rowIndex + ": Is Reversal already '"
                    + current + "' — skipping update");
            return;
        }
        By cell = rowCellByPosition(rowIndex, "Is Reversal");
        SeleniumUtils.fillAndVerifyCommit("GV_IS_REVERSAL_ROW_" + rowIndex, value, () -> {
            // Native UI, direct double-click every time — mirrors
            // APVoucherSubLedgerInformation.setDeductTdsNative (this codebase's one
            // live-confirmed closed Yes/No grid-cell combo), rather than openCellForEdit's
            // single-click-then-probe. The preceding field (Ledger Narration) commits via a Tab
            // keystroke, which ExtJS's default CellEditing auto-advances to the row's next
            // column — plausibly this very cell — so openCellForEdit's "is SOME editor visible?"
            // check can't tell a genuinely-fresh Is Reversal editor from that leftover
            // auto-advanced one; it reported "success" while the picker never actually, freshly
            // opened (confirmed live, run 20260901_170830, FIN-GV-ADD-COMBO-09: the boundlist
            // option never rendered across all 3 fillAndVerifyCommit retries). Driving the
            // double-click directly here — the same real interaction a user gives this closed
            // combo — sidesteps that ambiguity entirely instead of trusting a prior click's side
            // effect.
            WaitUtils.fluentWaitBuilder()
                    .withMessage("Is Reversal cell (row " + rowIndex + ") to accept a double-click "
                            + "— locator: " + cell)
                    .until(driver -> {
                        new Actions(driver).doubleClick(driver.findElement(cell)).perform();
                        return true;
                    });
            JavascriptExecutorUtils.waitForExtJS();

            // ROOT CAUSE (finally established, run 20260902_224915 — 9 DOM captures + 9 matching
            // log lines): the option click was ALWAYS working. Every capture taken at the moment
            // this method reported failure contains
            //     <li role="option" class="x-boundlist-item x-boundlist-selected" ...>Yes</li>
            // i.e. "Yes" WAS selected in the combo — while the grid CELL still read "No".
            //
            // In ExtJS CellEditing, selecting a combo option sets the EDITOR's value; the cell's
            // rendered text only repaints once the edit is COMMITTED (editor blur / ENTER / TAB).
            // This method never committed — nothing blurred the editor before getIsReversal()
            // read the cell (the input still carries x-form-focus in every capture) — so the
            // readback returned the stale "No" forever and all 3 outer retries reproduced it
            // identically. Every earlier theory (locator scoping, boundlist rebuild churn, the
            // trigger's open/close toggle) was chasing a symptom: the 9 log lines all said
            // "picker now closed", which disproves the toggle theory outright and confirms the
            // aria-expanded read below is trustworthy.
            //
            // Why the sibling fields never hit this: setLedgerNarration presses TAB, and AP's
            // proven setDeductTdsNative presses TAB too; setGeneralLedger/setSubLedger get away
            // with no keystroke only because selecting a GL triggers an app-side server
            // round-trip that repaints the whole row (hence their waitForPageLoad()).
            //
            // So: select the option, then EXPLICITLY COMMIT. ENTER is preferred over TAB because
            // Ext.grid.CellEditor's completeOnEnter closes the editor in place, whereas TAB also
            // auto-advances to the next editable column — and that auto-advance is precisely what
            // made this method's own entry state ambiguous in the first place (the preceding
            // field's TAB lands the active editor on this very cell). The self-verifying inner
            // loop is kept as the safety net: it reads the CELL back after committing, and if
            // ENTER didn't take, escalates to TAB on the next attempt.
            By option = simpleBoundlistOption(value);
            boolean committed = false;
            for (int innerAttempt = 1; innerAttempt <= 3 && !committed; innerAttempt++) {
                if (!isReversalPickerOpen()) {
                    SeleniumUtils.click(ACTIVE_EDITOR_PICKER_TRIGGER, WaitType.CLICKABLE, "GV_ISREVERSAL_TRIGGER");
                    JavascriptExecutorUtils.waitForExtJS();
                }

                int attemptRef = innerAttempt;
                WaitUtils.fluentWaitBuilder()
                        .withMessage("Is Reversal option '" + value + "' to be clickable in the open combo "
                                + "dropdown (row " + rowIndex + ", inner attempt " + attemptRef + ") — locator: "
                                + option)
                        .until(driver -> {
                            driver.findElement(option).click();
                            return true;
                        });
                JavascriptExecutorUtils.waitForExtJS();

                // THE FIX: commit the cell edit so the grid cell actually repaints. ENTER first
                // (commit in place); if a previous attempt already proved ENTER insufficient on
                // this build, escalate to TAB — a two-strategy escalation validated by the real
                // cell readback below, not a blind guess.
                if (innerAttempt == 1) {
                    SeleniumUtils.pressEnter(ACTIVE_EDITOR_INPUT, "GV_ISREVERSAL_COMMIT_ENTER");
                } else {
                    SeleniumUtils.pressTab(ACTIVE_EDITOR_INPUT, "GV_ISREVERSAL_COMMIT_TAB");
                }
                JavascriptExecutorUtils.waitForExtJS();

                String readBack = getIsReversal(rowIndex);
                committed = readBack != null && readBack.equalsIgnoreCase(value);
                if (!committed) {
                    // Capture only on an actual inner-attempt failure — the always-fires capture
                    // served its purpose (it produced the evidence that led to this fix); keeping
                    // it failure-only avoids permanent report clutter while still giving the next
                    // investigation round fresh evidence if this fix is somehow also wrong.
                    new HtmlCaptureSection(DriverManager.getDriver(),
                            "IsReversalPicker_row" + rowIndex + "_innerAttempt" + innerAttempt);
                    ExtentLogger.info("[GV ExpenseIncomeDetail] Row " + rowIndex + ": Is Reversal did not "
                            + "commit on inner attempt " + innerAttempt + " (committed with "
                            + (innerAttempt == 1 ? "ENTER" : "TAB") + ") — cell still reads '" + readBack
                            + "', picker now "
                            + (isReversalPickerOpen() ? "still open, retrying" : "closed, will reopen"));
                }
            }
        }, () -> getIsReversal(rowIndex));
    }

    /**
     * True if the Is Reversal combo's own picker is genuinely open right now, read via the ARIA
     * combobox pattern's {@code aria-expanded} attribute on the active editor's input — a
     * component-specific signal (not {@code Ext.ComponentQuery}/internal-state — see
     * feedback_native_ui_over_extjs), not a generic "is any boundlist visible" guess (which risked
     * matching a stale leftover picker from a different combo — see {@link #BOUNDLIST_ROOT}'s
     * Javadoc). Used to decide whether the trigger genuinely needs clicking: the trigger TOGGLES
     * open/closed, so clicking it while already open would close it instead of opening it.
     */
    private boolean isReversalPickerOpen() {
        try {
            String expanded = SeleniumUtils.getAttribute(ACTIVE_EDITOR_INPUT, "aria-expanded",
                    "GV_ISREVERSAL_EXPANDED_STATE");
            return "true".equalsIgnoreCase(expanded);
        } catch (Exception e) {
            return false;
        }
    }

    private void setExpenseAmount(int rowIndex, BigDecimal amount) {
        if (amount == null) return;
        BigDecimal current = getExpenseAmount(rowIndex);
        if (current != null && current.compareTo(amount) == 0) {
            // Item AD (Round 5): debug-gated UI_ACTION.
            ReportLogger.uiAction("[GV ExpenseIncomeDetail] Row " + rowIndex + ": Expense (Dr) already '"
                    + current + "' — skipping update");
            return;
        }
        SeleniumUtils.fillAndVerifyCommit("GV_EXPENSE_AMOUNT_ROW_" + rowIndex, amount, () -> {
            openCellForEdit(rowCellByPosition(rowIndex, "Expense (Dr)"), "GV_EXPENSE_AMOUNT_CELL_ROW_" + rowIndex);

            SeleniumUtils.clear(ACTIVE_EDITOR_INPUT, "GV_EXPENSE_AMOUNT");
            SeleniumUtils.sendKeys(ACTIVE_EDITOR_INPUT, WaitType.VISIBLE, amount.toPlainString(), "GV_EXPENSE_AMOUNT");
            SeleniumUtils.pressTab(ACTIVE_EDITOR_INPUT, "GV_EXPENSE_AMOUNT");
            JavascriptExecutorUtils.waitForExtJS();
        }, () -> getExpenseAmount(rowIndex));
    }

    private void setIncomeAmount(int rowIndex, BigDecimal amount) {
        if (amount == null) return;
        BigDecimal current = getIncomeAmount(rowIndex);
        if (current != null && current.compareTo(amount) == 0) {
            // Item AD (Round 5): debug-gated UI_ACTION.
            ReportLogger.uiAction("[GV ExpenseIncomeDetail] Row " + rowIndex + ": Income (Cr) already '"
                    + current + "' — skipping update");
            return;
        }
        SeleniumUtils.fillAndVerifyCommit("GV_INCOME_AMOUNT_ROW_" + rowIndex, amount, () -> {
            openCellForEdit(rowCellByPosition(rowIndex, "Income (Cr)"), "GV_INCOME_AMOUNT_CELL_ROW_" + rowIndex);

            SeleniumUtils.clear(ACTIVE_EDITOR_INPUT, "GV_INCOME_AMOUNT");
            SeleniumUtils.sendKeys(ACTIVE_EDITOR_INPUT, WaitType.VISIBLE, amount.toPlainString(), "GV_INCOME_AMOUNT");
            SeleniumUtils.pressTab(ACTIVE_EDITOR_INPUT, "GV_INCOME_AMOUNT");
            JavascriptExecutorUtils.waitForExtJS();
        }, () -> getIncomeAmount(rowIndex));
    }

    // =========================================================================
    // Read-back getters
    // =========================================================================

    public String getGeneralLedger(int rowIndex) {
        return SeleniumUtils.getAttribute(rowCellByPosition(rowIndex, "General ledger"), "textContent",
                "GV_GENERAL_LEDGER_ROW_" + rowIndex);
    }

    public String getSubLedger(int rowIndex) {
        return SeleniumUtils.getAttribute(rowCellByPosition(rowIndex, "Sub Ledger"), "textContent",
                "GV_SUB_LEDGER_ROW_" + rowIndex);
    }

    public String getLedgerNarration(int rowIndex) {
        return SeleniumUtils.getAttribute(
                rowCellByPosition(rowIndex, "Ledger Narration", LEDGER_NARRATION_COLUMN_ID), "textContent",
                "GV_LEDGER_NARRATION_ROW_" + rowIndex);
    }

    public String getIsReversal(int rowIndex) {
        // Trimmed here so this read-back is comparable both ways it's actually used: setIsReversal's
        // skip-check already trims + case-insensitively compares it, but fillAndVerifyCommit's own
        // pass/fail check is a strict value.equals(actual) — an untrimmed "Yes " vs "Yes" (ExtJS
        // grid cells commonly render incidental surrounding whitespace) would visually read as
        // correct yet fail that strict check after MAX_COMMIT_ATTEMPTS retries (confirmed live, run
        // 20260901_151519, FIN-GV-ADD-COMBO-09).
        String value = SeleniumUtils.getAttribute(rowCellByPosition(rowIndex, "Is Reversal"), "textContent",
                "GV_IS_REVERSAL_ROW_" + rowIndex);
        return value == null ? null : value.trim();
    }

    public BigDecimal getExpenseAmount(int rowIndex) {
        return BigDecimalUtils.safeParse(
                SeleniumUtils.getAttribute(rowCellByPosition(rowIndex, "Expense (Dr)"), "textContent",
                        "GV_EXPENSE_AMOUNT_ROW_" + rowIndex));
    }

    public BigDecimal getIncomeAmount(int rowIndex) {
        return BigDecimalUtils.safeParse(
                SeleniumUtils.getAttribute(rowCellByPosition(rowIndex, "Income (Cr)"), "textContent",
                        "GV_INCOME_AMOUNT_ROW_" + rowIndex));
    }

    /**
     * Row scoped by the real {@code data-recordindex} attribute, column scoped by the real
     * {@code data-columnid} — resolved dynamically from the header's visible text, same
     * {@code columnIdXPath()}-style resolution AR/AP already use via
     * {@code APVoucherGridColumnHeader.SUB_LEDGER.columnIdXPath()} (never a hardcoded id — the
     * generated {@code gridcolumn-NNNN} id differs per page load).
     *
     * <p>Confirmed live: each row renders as its own {@code <table data-recordindex="N">}
     * wrapping a single {@code <tr class="x-grid-row">}, and every {@code <td>} carries
     * {@code data-columnid} matching its header cell's own {@code id}. This replaces an earlier
     * position-counting locator that (a) searched {@code //tr[...]} unscoped, so any other
     * {@code x-grid-row} elsewhere on the page (e.g. a combo/boundlist grid-style picker
     * template) could make {@code [N]} pick the wrong row, and (b) miscounted anyway because
     * this grid has 2 hidden columns preceding "General ledger" that don't contribute a
     * preceding-sibling header div to count.
     *
     * <p><b>The ancestor match must be an exact class token, not a substring.</b> ExtJS nests the
     * header text as
     * {@code div#gridcolumn-1247.x-column-header > div#gridcolumn-1247-titleEl.x-column-header-inner
     * > span.x-column-header-text > span.x-column-header-text-inner}. Since
     * {@code x-column-header-inner} *contains the substring* {@code x-column-header}, a
     * {@code contains(@class,'x-column-header')} match on a nearest-first reverse axis resolves to
     * {@code gridcolumn-1247-titleEl} — an id no {@code <td>} ever carries. The
     * {@code concat(' ', normalize-space(@class), ' ')} idiom matches the whole token only, giving
     * the real {@code gridcolumn-1247}.
     */
    private static By rowCellByPosition(int rowIndex, String headerText) {
        return rowCellByPosition(rowIndex, headerText, null);
    }

    /**
     * {@link #rowCellByPosition(int, String)} plus an explicit {@code data-columnid} fallback, for
     * the columns where the header-id ↔ cell-columnid correspondence does not hold.
     *
     * <p>ExtJS writes {@code column.getItemId()} into each cell's {@code data-columnid}, and
     * {@code getItemId()} returns the component's auto-generated id <i>only when no explicit
     * {@code itemId} was configured</i>. Most columns in this grid have none, so header
     * {@code id="gridcolumn-1247"} pairs with cell {@code data-columnid="gridcolumn-1247"} — but
     * Ledger Narration is declared with {@code itemId: 'ledgerNarrationCode'}, so its header is
     * {@code gridcolumn-1257} while its cell is {@code data-columnid="ledgerNarrationCode"}, and
     * nothing in the header's markup carries that itemId to bridge the two.
     *
     * <p>Column <i>order</i> is not a usable fallback: the header container renders 15 columns
     * where the row renders only 10 cells (hidden columns emit a header but no {@code <td>}), so
     * an index computed from headers does not address the intended cell. Both facts come from the
     * DOM captured at the failure in run 20260807_104215.
     *
     * @param explicitColumnId the column's configured ExtJS {@code itemId}, or {@code null} to
     *                         match on the header id alone
     */
    private static By rowCellByPosition(int rowIndex, String headerText, String explicitColumnId) {
        String colId = "//span[normalize-space()='" + headerText + "']"
                + "/ancestor::div[contains(concat(' ', normalize-space(@class), ' '), ' x-column-header ')][1]/@id";
        String predicate = "@data-columnid=" + colId;
        if (explicitColumnId != null && !explicitColumnId.isBlank()) {
            predicate = "(" + predicate + " or @data-columnid='" + explicitColumnId + "')";
        }
        return By.xpath("//table[@data-recordindex='" + rowIndex + "']//td[" + predicate + "]");
    }

    // ── UI-contract validation — Expense/Income Detail grid ─────────────────────────────────────
    // Every column of the grid comes from GeneralVoucherGridColumnHeader (single source of truth):
    // DEFAULT columns must be shown (FAIL if not), USER columns are validated only when displayed
    // (SKIP "not displayed" otherwise). All results are tagged via UiValidationScope nodes so the
    // report nests them: Grid header and toolbar / Row N (cells, Allocate Sites, Payment
    // Information) / Grid footer. Callers wrap the whole thing in an "Expense/Income Detail" node.

    /** What one grid row is expected to show and which popups apply to it — built by the test from
     *  its own {@code ExpenseIncomeDetail} data, never read back from the page.
     *
     *  @param allocations Allocate Sites rows for this grid row, or empty when no Site applies
     *  @param payment     Cheque/RTGS payment entered for this row, or {@code null} when none
     *  @param paymentDebit whether the payment's Cash/Bank line is a debit (picks the popup variant) */
    public record RowUiPlan(ExpenseIncomeDetail row, List<SiteAllocation> allocations,
                            PaymentInformation payment, boolean paymentDebit) {
        public RowUiPlan {
            allocations = allocations == null ? List.of() : allocations;
        }
        public boolean hasSite() { return !allocations.isEmpty(); }
        public boolean hasPayment() {
            return payment != null && payment.getPaymentMode() != PaymentSelectionType.CASH;
        }
        public BigDecimal amount() { return row.isDebit() ? row.getExpenseDr() : row.getIncomeCr(); }
    }

    private static final java.text.DecimalFormat TWO_DECIMALS = new java.text.DecimalFormat("0.00");

    private static String fmt(BigDecimal v) {
        return TWO_DECIMALS.format(v == null ? BigDecimal.ZERO : v);
    }

    /** The report node title for one grid row: GL, SL (only if present), amount, and the
     *  Site/Payment applicability tags — the same string every result for that row nests under, so
     *  callers adding a checkpoint under a row reuse it and merge into the same node. */
    public static String rowNodeLabel(int rowIndex, RowUiPlan plan) {
        ExpenseIncomeDetail row = plan.row();
        boolean hasSl = row.getSubLedger() != null && !row.getSubLedger().isBlank();
        return "Row " + (rowIndex + 1) + " · GL: " + row.getGeneralLedger()
                + (hasSl ? " · SL: " + row.getSubLedger() : "")
                + " · " + (row.isDebit() ? "Dr " : "Cr ") + fmt(plan.amount())
                + " · Sub Ledger: " + (hasSl ? "yes" : "no")
                + " · Site: " + (plan.hasSite() ? "yes" : "no") + " · Payment: " + (plan.hasPayment() ? "yes" : "no");
    }

    /** NBSP / zero-width / surrounding whitespace removed — an empty ExtJS grid cell typically reads
     *  as a lone {@code &nbsp;}, which {@link String#isBlank()} does NOT treat as blank. */
    private static String cleanCell(String s) {
        return s == null ? "" : s.replace(' ', ' ').replace("​", "").strip();
    }

    /** Generic sweep used by {@code validateUiContract} (Create/Edit flows, nothing to fill): header
     *  and toolbar plus one already-filled row's cells, style only. */
    public List<UiValidationFailure> validateExpenseIncomeGridUi(int rowIndex, UiValidationLevel level) {
        List<UiValidationFailure> failures = new ArrayList<>(validateGridHeaderAndToolbarUi(level));
        failures.addAll(UiValidationScope.node(rowTitleFromGrid(rowIndex), () ->
                UiValidationScope.node("Cells and row buttons", () -> validateRowCellsUi(rowIndex, null, level))));
        return failures;
    }

    /** Row node title for the generic sweep (no test plan available): GL, SL (only if present) and
     *  the Dr/Cr amount read from the row itself. Falls back to just "Row N" if any read fails —
     *  a title must never abort the sweep. */
    private String rowTitleFromGrid(int rowIndex) {
        String title = "Row " + (rowIndex + 1);
        try {
            String gl = cleanCell(getGeneralLedger(rowIndex));
            if (gl.isEmpty()) {
                return title + " (blank)";   // nothing filled yet: no GL / SL / amount fragments to show
            }
            String sl = cleanCell(getSubLedger(rowIndex));
            BigDecimal dr = getExpenseAmount(rowIndex);
            BigDecimal cr = getIncomeAmount(rowIndex);
            boolean debit = dr != null && dr.signum() != 0;
            BigDecimal amount = debit ? dr : cr;
            return title + " · GL: " + gl
                    + (sl.isEmpty() ? "" : " · SL: " + sl)
                    + (amount == null ? "" : " · " + (debit ? "Dr " : "Cr ") + fmt(amount))
                    + " · Sub Ledger: " + (sl.isEmpty() ? "no" : "yes");
        } catch (RuntimeException e) {
            return title;
        }
    }

    /** Header text/style for every column plus the toolbar chrome (no row data needed). Delete Row's
     *  state is not asserted (it depends on the grid's selection). */
    public List<UiValidationFailure> validateGridHeaderAndToolbarUi(UiValidationLevel level) {
        return validateGridHeaderAndToolbarUi(level, null);
    }

    /** @param deleteRowExpectedEnabled expected enabled state of the toolbar's Delete Row button —
     *         {@code false} on a blank grid (nothing to delete), {@code null} to skip the state check */
    public List<UiValidationFailure> validateGridHeaderAndToolbarUi(UiValidationLevel level,
            Boolean deleteRowExpectedEnabled) {
        return UiValidationScope.node("Grid header and toolbar", () -> {
            List<UiValidationFailure> failures = new ArrayList<>();
            for (GeneralVoucherGridColumnHeader col : GeneralVoucherGridColumnHeader.values()) {
                String name = "GRID_HEADER_" + col.name();
                failures.addAll(col.kind() == GridColumnKind.USER
                        ? UiComponentValidator.validateGridHeaderOptional(col.headerLocator(), name, col.headerText(), level)
                        : UiComponentValidator.validateGridHeader(col.headerLocator(), name, col.headerText(), level));
            }
            failures.addAll(validateGridToolbarUi(deleteRowExpectedEnabled));
            return failures;
        });
    }

    /** Scope of this form's Add/Edit window, so the list page behind it is never matched. */
    private static final String WINDOW_SCOPE = FormSection.voucherWindowScope(VoucherDomain.GV);

    // Toolbar buttons are icon-only: located by their mdi icon class, then up to the button anchor
    // (confirmed from the live DOM; Delete Row's XPath verified to match exactly once).
    private static By toolbarIcon(String mdiIconClass) {
        return By.xpath(WINDOW_SCOPE + "//i[contains(@class,'" + mdiIconClass
                + "')]/ancestor::a[contains(@class,'x-btn')]");
    }

    private static final By TOOLBAR_ADD_ROW    = toolbarIcon("mdi-playlist-plus");
    private static final By TOOLBAR_SELECT     = toolbarIcon("mdi-playlist-check");
    private static final By TOOLBAR_DELETE_ROW = toolbarIcon("mdi-playlist-remove");
    private static final By TOOLBAR_FILTER     = toolbarIcon("mdi-filter-variant");
    /** The grid's Search icon reuses the {@code panelheaderarrow} class (same as section chevrons). */
    private static final By TOOLBAR_SEARCH = By.xpath(WINDOW_SCOPE
            + "//a[contains(@class,'panelheaderarrow')][.//i[contains(@class,'mdi-magnify')]]");

    /** Toolbar icons (Add Row, Select, Delete Row, Filter) and the Search icon: presence and state. */
    public List<UiValidationFailure> validateGridToolbarUi(Boolean deleteRowExpectedEnabled) {
        List<UiValidationFailure> failures = new ArrayList<>();
        failures.addAll(UiComponentValidator.validateIconButton(TOOLBAR_ADD_ROW, "GRID_TOOLBAR_ADD_ROW", true));
        failures.addAll(UiComponentValidator.validateIconButton(TOOLBAR_SELECT, "GRID_TOOLBAR_SELECT", true));
        failures.addAll(UiComponentValidator.validateIconButton(TOOLBAR_DELETE_ROW, "GRID_TOOLBAR_DELETE_ROW",
                deleteRowExpectedEnabled));
        failures.addAll(UiComponentValidator.validateIconButton(TOOLBAR_FILTER, "GRID_TOOLBAR_FILTER", true));
        failures.addAll(UiComponentValidator.validateIconButton(TOOLBAR_SEARCH, "GRID_TOOLBAR_SEARCH", true));
        return failures;
    }

    /** Row-scoped Cheque/RTGS button (same {@code data-recordindex} scoping as {@link #siteButtonForRow}). */
    private static By chequeRtgsButtonForRow(int rowIndex) {
        return By.xpath("//table[@data-recordindex='" + rowIndex + "']//button[normalize-space()='"
                + SubLedgerGridButtonLabel.CHEQUE_RTGS.label() + "']");
    }

    /**
     * Fills one grid row (adding it first when {@code rowIndex > 0}) AND validates it in place:
     * cells (style + value), row buttons, then — only where the plan says they apply — the Allocate
     * Sites popup (before and after fill) and the Payment Information popup (before and after
     * fill). A row with no Site / no Payment gets one SKIP row each instead of an empty group.
     * Fill and validation interleave because the popup states only exist while the popup is open.
     *
     * @return the results, nested under a {@code "Row N · GL … · SL … · Dr/Cr amount"} node
     */
    public List<UiValidationFailure> fillAndValidateRowUi(int rowIndex, RowUiPlan plan, UiValidationLevel level) {
        ExpenseIncomeDetail row = plan.row();
        if (rowIndex > 0) {
            clickAddRowButton();
            JavascriptExecutorUtils.waitForExtJS();
        } else {
            WaitUtils.waitForExtMask("Fill row 1 — grid ready");
        }
        fillRowBasicFields(rowIndex, row);
        boolean hasSl = row.getSubLedger() != null && !row.getSubLedger().isBlank();
        if (hasSl) {
            setSubLedger(rowIndex, row.getSubLedger(), row.getSubLedgerId());
        }

        return UiValidationScope.node(rowNodeLabel(rowIndex, plan), () -> {
            List<UiValidationFailure> failures = new ArrayList<>();
            failures.addAll(UiValidationScope.node("Cells and row buttons", () -> validateRowCellsUi(rowIndex, plan, level)));
            failures.addAll(validateRowAllocateSitesUi(rowIndex, plan, hasSl, level));
            failures.addAll(validateRowPaymentUi(rowIndex, plan, level));
            return failures;
        });
    }

    /** {@code plan == null} = style-only (no expected values known — generic Create/Edit sweep). */
    public List<UiValidationFailure> validateRowCellsUi(int rowIndex, RowUiPlan plan, UiValidationLevel level) {
        ExpenseIncomeDetail row = plan == null ? null : plan.row();
        int n = rowIndex + 1;
        List<UiValidationFailure> failures = new ArrayList<>();
        for (GeneralVoucherGridColumnHeader col : GeneralVoucherGridColumnHeader.values()) {
            String expected = row == null ? null : switch (col) {
                case GENERAL_LEDGER -> row.getGeneralLedger();
                case SUB_LEDGER -> row.getSubLedger() == null ? "" : row.getSubLedger();
                case LEDGER_NARRATION -> row.getLedgerNarration() == null ? "" : row.getLedgerNarration();
                case IS_REVERSAL -> row.isReversalYes() ? "Yes" : "No";
                case EXPENSE_DR -> fmt(row.getExpenseDr());
                case INCOME_CR -> fmt(row.getIncomeCr());
                default -> null; // USER columns: style only, no expected value in the test data
            };
            By cell = col == GeneralVoucherGridColumnHeader.LEDGER_NARRATION
                    ? rowCellByPosition(rowIndex, col.headerText(), LEDGER_NARRATION_COLUMN_ID)
                    : rowCellByPosition(rowIndex, col.headerText());
            String name = "GRID_CELL_" + col.name() + "_ROW_" + n;
            failures.addAll(col.kind() == GridColumnKind.USER
                    ? UiComponentValidator.validateGridCellOptional(cell, name, col.numeric(), expected, level)
                    : UiComponentValidator.validateGridCellWithValue(cell, name, col.numeric(), expected, level));
        }
        failures.addAll(UiComponentValidator.validateButton(siteButtonForRow(rowIndex),
                "GRID_ROW_BUTTON_SITE_ROW_" + n, SubLedgerGridButtonLabel.SITE.label(), true,
                FieldStyleSpec.GRID_ROW_BUTTON, level));
        failures.addAll(UiComponentValidator.validateButton(chequeRtgsButtonForRow(rowIndex),
                "GRID_ROW_BUTTON_CHEQUE_RTGS_ROW_" + n, SubLedgerGridButtonLabel.CHEQUE_RTGS.label(), true,
                FieldStyleSpec.GRID_ROW_BUTTON, level));
        return failures;
    }

    private List<UiValidationFailure> validateRowAllocateSitesUi(int rowIndex, RowUiPlan plan, boolean hasSl,
            UiValidationLevel level) {
        if (!plan.hasSite()) {
            return UiComponentValidator.skip("ALLOCATE_SITES_ROW_" + (rowIndex + 1), "applicability",
                    "not applicable (no Site on this row)", "not applicable");
        }
        ExpenseIncomeDetail row = plan.row();
        String header = "Allocate Sites · GL: " + row.getGeneralLedger() + (hasSl ? " · SL: " + row.getSubLedger() : "");
        return UiValidationScope.node(header, () -> {
            List<UiValidationFailure> failures = new ArrayList<>();
            AllocateSitesPopup popup = clickSiteButton(rowIndex);
            failures.addAll(UiValidationScope.node("Before fill", () -> {
                List<UiValidationFailure> before = new ArrayList<>(popup.collectUiContract(level));
                before.addAll(popup.collectValuesUi(List.of(), plan.amount(), level));
                return before;
            }));
            for (SiteAllocation alloc : plan.allocations()) {
                popup.addSiteAllocation(alloc.getSiteName(), alloc.getAmount());
            }
            failures.addAll(UiValidationScope.node("After fill",
                    () -> popup.collectValuesUi(plan.allocations(), plan.amount(), level)));
            popup.clickOk();
            return failures;
        });
    }

    private List<UiValidationFailure> validateRowPaymentUi(int rowIndex, RowUiPlan plan, UiValidationLevel level) {
        if (!plan.hasPayment()) {
            return UiComponentValidator.skip("PAYMENT_INFO_ROW_" + (rowIndex + 1), "applicability",
                    "not applicable (no Cheque/RTGS data)", "not applicable");
        }
        PaymentInformation payment = plan.payment();
        return UiValidationScope.node("Payment Information · " + payment.getPaymentMode()
                + (payment.getInstrumentNo() == null ? "" : " · " + payment.getInstrumentNo()), () -> {
            List<UiValidationFailure> failures = new ArrayList<>();
            PaymentInformationPopup popup = clickPaymentButton(rowIndex, plan.paymentDebit());
            failures.addAll(UiValidationScope.node("Before fill", () -> popup.collectUiContract(level)));
            popup.selectPaymentMode(payment.getPaymentMode())
                    .setInstrumentNo(payment.getInstrumentNo())
                    .setInstrumentDate(payment.getInstrumentDate())
                    .setDrawnOn(payment.getDrawnOn())
                    .setReference(payment.getReference());
            failures.addAll(UiValidationScope.node("After fill", () -> popup.collectValuesUi(payment)));
            popup.clickOk();
            // Select the row (click its General ledger cell), then validate the Payment Information
            // section below the grid: it shows the selected row's payment.
            selectRow(rowIndex);
            failures.addAll(UiValidationScope.node("Static panel (Row " + (rowIndex + 1) + " selected)",
                    () -> validatePaymentPanelUi(payment, level)));
            return failures;
        });
    }

    /** Selects a grid row by clicking its General ledger cell, then cancels the cell editor that the
     *  click opens (Escape — no value change) so the row stays selected and nothing is left in edit
     *  mode. The static Payment Information panel then shows that row's payment. */
    public void selectRow(int rowIndex) {
        SeleniumUtils.click(rowCellByPosition(rowIndex, "General ledger"), WaitType.CLICKABLE,
                "GV_SELECT_ROW_" + rowIndex);
        JavascriptExecutorUtils.waitForExtJS();
        if (UiComponentValidator.quickPresent(ACTIVE_EDITOR_INPUT)) {
            new Actions(DriverManager.getDriver()).sendKeys(Keys.ESCAPE).perform();
            JavascriptExecutorUtils.waitForExtJS();
        }
    }

    /**
     * The static Payment Information panel (section below the grid; fields from
     * {@link PaymentPanelField}): for every field the panel shows — label styled, field DISABLED and,
     * when the expected value is known, the displayed value equal to it. A caption the panel does not
     * show for this mode is a SKIP ("not displayed"), never a FAIL. {@code payment == null} = nothing
     * entered yet (blank form / row without payment): every shown field is expected empty.
     */
    public List<UiValidationFailure> validatePaymentPanelUi(PaymentInformation payment, UiValidationLevel level) {
        List<UiValidationFailure> failures = new ArrayList<>();
        for (PaymentPanelField field : PaymentPanelField.values()) {
            By label = field.labelLocator(WINDOW_SCOPE);
            By value = field.valueLocator(WINDOW_SCOPE);
            if (!UiComponentValidator.quickPresent(label)) {
                failures.addAll(UiComponentValidator.skip(field.key(), "visibility",
                        "shown when the mode uses it", "not displayed for this payment mode"));
                continue;
            }
            failures.addAll(UiComponentValidator.validateLabel(label, field.key() + "_LABEL", field.caption(),
                    FieldStyleSpec.FIELD_LABEL, level));
            failures.addAll(UiComponentValidator.validateFieldState(value, field.key(),
                    com.gsl.utils.ui.UiFieldState.DISABLED));
            String expected = expectedPanelValue(field, payment);
            if (expected != null) {
                failures.addAll(UiComponentValidator.validateValue(value, field.key(), expected));
            }
        }
        return failures;
    }

    /** Expected displayed value for {@code field}, or {@code null} when it cannot be compared
     *  reliably (e.g. a test-data date that is not dd/MM/yyyy). */
    private static String expectedPanelValue(PaymentPanelField field, PaymentInformation payment) {
        if (payment == null) {
            // Nothing entered: only the free-text fields are reliably empty (Payment Mode / date may
            // show an app default), so those are not compared.
            return field == PaymentPanelField.INSTRUMENT_NO || field == PaymentPanelField.REFERENCE ? "" : null;
        }
        return switch (field) {
            case PAYMENT_MODE -> payment.getPaymentMode() == null ? "" : payment.getPaymentMode().label();
            case INSTRUMENT_NO -> nz(payment.getInstrumentNo());
            case INSTRUMENT_DATE -> {
                String d = nz(payment.getInstrumentDate()).trim();
                yield d.isEmpty() || d.matches("\\d{2}/\\d{2}/\\d{4}") ? d : null;
            }
            case CHEQUE_LABEL, DRAWN_ON -> nz(payment.getDrawnOn());
            case REFERENCE -> nz(payment.getReference());
        };
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }


    // ── Grid footer (Record Count / Total Amount (Dr) / Total Amount (Cr)) ──────────────────
    // Confirmed from the live DOM: a toolbar (div.gridItems-toolbar) of gsl_FieldLabel display fields;
    // each has a label span (x-form-item-label-text) and a value div (role=textbox) holding a <b>.
    private static final String FOOTER_FIELD = WINDOW_SCOPE
            + "//div[contains(@class,'gridItems-toolbar')]//div[contains(@class,'gsl_FieldLabel')]"
            + "[.//span[contains(@class,'x-form-item-label-text')][normalize-space()='%s']]";

    private static By footerLabel(String text) {
        return By.xpath(String.format(FOOTER_FIELD, text) + "//span[contains(@class,'x-form-item-label-text')]");
    }

    private static By footerValue(String text) {
        return By.xpath(String.format(FOOTER_FIELD, text) + "//div[@role='textbox']//b");
    }

    /** Label text (always) and value (when the expected value is non-null) of the grid footer.
     *  {@code checkpoint} names the state, e.g. "blank grid" or "after Row 2". */
    public List<UiValidationFailure> validateGridFooterUi(String checkpoint, Integer expectedRecordCount,
            BigDecimal expectedTotalDr, BigDecimal expectedTotalCr) {
        List<UiValidationFailure> failures = new ArrayList<>();
        String tag = checkpoint == null ? "" : " (" + checkpoint + ")";
        failures.addAll(UiComponentValidator.validateStatusIndicator(footerLabel("Record Count:"),
                "GRID_FOOTER_RECORD_COUNT_LABEL" + tag, "Record Count:", UiValidationLevel.SMOKE));
        failures.addAll(UiComponentValidator.validateStatusIndicator(footerLabel("Total Amount (Dr):"),
                "GRID_FOOTER_TOTAL_DR_LABEL" + tag, "Total Amount (Dr):", UiValidationLevel.SMOKE));
        failures.addAll(UiComponentValidator.validateStatusIndicator(footerLabel("Total Amount (Cr):"),
                "GRID_FOOTER_TOTAL_CR_LABEL" + tag, "Total Amount (Cr):", UiValidationLevel.SMOKE));
        if (expectedRecordCount != null) {
            failures.addAll(UiComponentValidator.validateAmountValue(footerValue("Record Count:"),
                    "GRID_FOOTER_RECORD_COUNT" + tag, String.valueOf(expectedRecordCount), null, null,
                    UiValidationLevel.SMOKE));
        }
        if (expectedTotalDr != null) {
            failures.addAll(UiComponentValidator.validateAmountValue(footerValue("Total Amount (Dr):"),
                    "GRID_FOOTER_TOTAL_DR" + tag, expectedTotalDr.toPlainString(), null, null,
                    UiValidationLevel.SMOKE));
        }
        if (expectedTotalCr != null) {
            failures.addAll(UiComponentValidator.validateAmountValue(footerValue("Total Amount (Cr):"),
                    "GRID_FOOTER_TOTAL_CR" + tag, expectedTotalCr.toPlainString(), null, null,
                    UiValidationLevel.SMOKE));
        }
        return failures;
    }

    /** Label-only footer check — used by the generic {@code validateUiContract} sweep (Create/Edit
     *  business flows), where the expected sums are not known to the validator. */
    public List<UiValidationFailure> validateGridFooterUi() {
        return validateGridFooterUi(null, null, null, null);
    }
}
