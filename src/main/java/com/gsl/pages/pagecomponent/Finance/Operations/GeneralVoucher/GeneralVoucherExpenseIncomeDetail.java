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
import com.gsl.pages.Finance.Operations.Common.PaymentInformationPopup;
import com.gsl.pages.enums.Finance.Operations.Common.SubLedgerGridButtonLabel;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherSubLedgerInformationBase;
import com.gsl.reports.ExtentLogger;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.BigDecimalUtils;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.interactions.Actions;

import java.math.BigDecimal;
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
 * instead driven by {@link #handlePaymentInformation}/{@link #clickCashBankPaymentButton()} —
 * a single, non-row-scoped trigger, since General Voucher has exactly one counter-leg per
 * voucher (not one per Expense/Income row — see {@code GeneralVoucherPaymentVariant}'s class
 * Javadoc). {@link #createPaymentPopup()} now returns a real {@link GvPaymentInformationPopup}
 * — see that class's Javadoc for its **UNVERIFIED, best-effort** status.
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

    /**
     * The Cash/Bank counter-leg's Payment/Cheque-RTGS button. UNVERIFIED — best-effort guess
     * reusing AP/AR's button label ({@link SubLedgerGridButtonLabel#CHEQUE_RTGS}); General
     * Voucher has exactly one Cash/Bank counter-leg per voucher (confirmed by
     * {@code DbGeneralVoucherDataSource} resolving it independently of the Expense/Income
     * rows, and by {@code GeneralVoucher.PaymentInformation}'s own Javadoc), so this is a
     * single control, not scoped to any grid row — unlike AP/AR's per-sub-ledger-row button.
     * Fix this locator first if the first live CHEQUE/NEFT_RTGS run fails here.
     */
    private static final By CASH_BANK_PAYMENT_BUTTON = By.xpath(
            "//button[normalize-space()='" + SubLedgerGridButtonLabel.CHEQUE_RTGS.label() + "']");

    public GeneralVoucherExpenseIncomeDetail() {
        super(VoucherDomain.GV);
    }

    @Override
    protected PaymentInformationPopup createPaymentPopup() {
        return new GvPaymentInformationPopup();
    }

    /**
     * Clicks the Cash/Bank counter-leg's Payment/Cheque-RTGS button — see
     * {@link #CASH_BANK_PAYMENT_BUTTON}'s Javadoc for why this isn't row-scoped.
     */
    public PaymentInformationPopup clickCashBankPaymentButton() {
        SeleniumUtils.click(CASH_BANK_PAYMENT_BUTTON, WaitType.CLICKABLE, "GV_CASHBANK_PAYMENT_BUTTON");
        JavascriptExecutorUtils.waitForExtJS();
        return createPaymentPopup();
    }

    /**
     * Fills the Cash/Bank counter-leg's Payment Window when {@code payment} calls for one.
     * No-op for {@code null} (the {@code NONE} variant deliberately carries no payment info —
     * the app's own post-save "instrument number missing" warning is expected instead) and for
     * {@link PaymentSelectionType#CASH} (no instrument entry required at all).
     */
    public void handlePaymentInformation(PaymentInformation payment) {
        if (payment == null || payment.getPaymentMode() == PaymentSelectionType.CASH) return;
        clickCashBankPaymentButton().fillAndSubmit(payment);
    }

    // =========================================================================
    // Fill loop
    // =========================================================================

    /**
     * Fills every {@code ExpenseIncomeDetail} row, branching per row on its
     * (DB-resolved) {@link GlLineType}. Lives in this component, not the test
     * class — matching how AP/AR keep row-branching inside {@code *SubLedgerInformation}.
     */
    public GeneralVoucherExpenseIncomeDetail fillExpenseIncomeRows(MergedFinanceGeneralVoucherData data) {
        List<ExpenseIncomeDetail> rows = data.getExpenseIncomeDetailList();
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                clickAddRowButton();
                JavascriptExecutorUtils.waitForExtJS();
            }
            ExpenseIncomeDetail row = rows.get(i);
            ExtentLogger.info("[GV ExpenseIncomeDetail] Row " + (i + 1) + ": GL='" + row.getGeneralLedger()
                    + "', LineNo='" + row.getLineNo() + "', type=" + row.getResolvedGlType());

            fillRowBasicFields(i, row);

            GlLineType type = row.getResolvedGlType();
            if (type == null) {
                throw new IllegalStateException(
                        "[GV ExpenseIncomeDetail] resolvedGlType not set for LineNo='" + row.getLineNo()
                        + "' — the data source (Excel or DB) must resolve it via FinGlDao before fill.");
            }

            switch (type) {
                case TYPE1_NONE -> { /* nothing further */ }
                case TYPE2_SITE_ONLY -> allocateSites(i, data.getSiteAllocationsForLine(row.getLineNo()));
                case TYPE3_SITE_AND_SUBLEDGER -> {
                    setSubLedger(i, row.getSubLedger(), row.getSubLedgerId());
                    allocateSites(i, data.getSiteAllocationsForLine(row.getLineNo()));
                }
            }
        }
        return this;
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
            allocateSites(rowIndex, allocations);
        }
        return this;
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
     * never reach Save to see the message it's meant to assert on.
     *
     * <p>Decided from the popup's own Pending Amount field, read after every Add — not from
     * summing {@code allocations} locally — so this reflects whatever the app actually computed,
     * the same values the diagnostic in {@link AllocateSitesPopup#clickOk()} would report if OK
     * were attempted anyway. An unreadable Pending Amount is treated as incomplete: Cancel is the
     * side that can never make things worse.
     */
    private void allocateSites(int rowIndex, List<SiteAllocation> allocations) {
        AllocateSitesPopup popup = clickSiteButton(rowIndex);
        commitSiteAllocations(rowIndex, popup, allocations, "Allocate Sites");
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
        commitSiteAllocations(rowIndex, popup, newSiteAllocations, "Site reallocation");
        return this;
    }

    /** Shared commit logic behind {@link #allocateSites}/{@link #reallocateSites} — see
     *  {@link #allocateSites}'s Javadoc for why OK isn't always the right action. */
    private void commitSiteAllocations(int rowIndex, AllocateSitesPopup popup,
            List<SiteAllocation> allocations, String actionLabel) {
        for (SiteAllocation alloc : allocations) {
            popup.addSiteAllocation(alloc.getSiteName(), alloc.getAmount());
        }

        BigDecimal pending = popup.getPendingAmount();
        if (pending != null && pending.signum() == 0) {
            popup.clickOk();
            return;
        }

        ExtentLogger.warning("[GV ExpenseIncomeDetail] Row " + (rowIndex + 1) + ": " + actionLabel + " left "
                + "incomplete after " + allocations.size() + " allocation(s) — Pending Amount = "
                + (pending == null ? "<unreadable>" : pending.toPlainString())
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
            ExtentLogger.info(logTag + ": single click did not open the editor — retrying with double-click");
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
            ExtentLogger.info("[GV ExpenseIncomeDetail] Row " + rowIndex + ": General Ledger already '"
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
            ExtentLogger.info("GV_GL_OPTION_" + generalLedger + " clicked successfully");
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
            ExtentLogger.info("[GV ExpenseIncomeDetail] Row " + rowIndex + ": Sub Ledger already '"
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
            ExtentLogger.info("GV_SL_OPTION_" + subLedger + " clicked successfully");
            JavascriptExecutorUtils.waitForExtJS();
            JavascriptExecutorUtils.waitForPageLoad();
        }, () -> getSubLedger(rowIndex));
    }

    private void setLedgerNarration(int rowIndex, String narration) {
        if (narration == null || narration.isBlank()) return;
        String current = getLedgerNarration(rowIndex);
        if (current != null && current.trim().equalsIgnoreCase(narration.trim())) {
            ExtentLogger.info("[GV ExpenseIncomeDetail] Row " + rowIndex + ": Ledger Narration already '"
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
            ExtentLogger.info("[GV ExpenseIncomeDetail] Row " + rowIndex + ": Is Reversal already '"
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
            ExtentLogger.info("[GV ExpenseIncomeDetail] Row " + rowIndex + ": Expense (Dr) already '"
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
            ExtentLogger.info("[GV ExpenseIncomeDetail] Row " + rowIndex + ": Income (Cr) already '"
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
}
