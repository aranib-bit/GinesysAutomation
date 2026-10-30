package com.gsl.pages.pagecomponent.Finance.Operations.APVoucher;

import com.google.common.util.concurrent.Uninterruptibles;
import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.enums.AdjustmentType;
import com.gsl.enums.Finance.Operations.APVoucher.DeductTdsClearConfirmAction;
import com.gsl.enums.Finance.Operations.APVoucher.OnAccountMode;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.enums.WaitType;
import com.gsl.pages.Finance.Operations.Common.AdjustmentEntry;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.enums.VoucherDomain;
import com.gsl.pages.Finance.Operations.Common.SelectDocumentPopup;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.SubLedgerRowLocator;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherSubLedgerInformationBase;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.SelectDocument;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.SubLedgerInformation;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.BigDecimalUtils;
import com.gsl.flow.Finance.Operations.Common.AdjustmentFlowSteps;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.AdjustmentSpec;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.pages.enums.Finance.Operations.APVoucher.APVoucherGridColumnHeader;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherGridColumnHeader;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.reports.SubLedgerRowStepRegistry;
import com.aventstack.extentreports.ExtentTest;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import com.gsl.utils.Selenium.WaitUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.gsl.pages.Finance.Operations.Common.ApPaymentInformationPopup;
import com.gsl.pages.Finance.Operations.Common.PaymentInformationPopup;
import static com.gsl.utils.Selenium.ExtJsUtils.setExtJsComboByName;

public class APVoucherSubLedgerInformation extends VoucherSubLedgerInformationBase {

    public APVoucherSubLedgerInformation() {
        super(VoucherDomain.AP);
    }

    @Override
    protected PaymentInformationPopup createPaymentPopup() {
        return new ApPaymentInformationPopup();
    }

    /** Exposes the base Add Row button click for pre-save validation scenarios. */
    public void clickAddRow() {
        clickAddRowButton();
    }

    private static final String AP_GRID_ITEM_ID = "apvch_grid";

    @Override
    protected String gridItemId() {
        return AP_GRID_ITEM_ID;
    }

    // ── Column ID XPaths for native Deduct TDS cell targeting ────────────────────
    // COL_AP_SL / COL_REF_SITE / COL_SL_ID use columnIdXPath() — those columns have auto-generated ExtJS IDs.
    // COL_DEDUCT_TDS is the literal itemId: data-columnid="deductTDS" in the DOM (not gridcolumn-NNNN).
    private static final String COL_AP_SL      = APVoucherGridColumnHeader.SUB_LEDGER.columnIdXPath();
    private static final String COL_REF_SITE   = CommonVoucherGridColumnHeader.REFERENCE_SITE.resolveFor(VoucherDomain.AP).columnIdXPath();
    private static final String COL_SL_ID      = CommonVoucherGridColumnHeader.SUB_LEDGER_ID.resolveFor(VoucherDomain.AP).columnIdXPath();
    private static final String COL_DEDUCT_TDS = "deductTDS";

    // Row anchored by SL name + ref site (same pattern as PAYMENT_BUTTON_TEMPLATE in VoucherSubLedgerInformationBase)
    private static final String DEDUCT_TDS_CELL =
            "//tr[td[@data-columnid=" + COL_AP_SL + "]/div[text()='%s']"
            + " and td[@data-columnid=" + COL_REF_SITE + "]/div[normalize-space()='%s']]"
            + "//td[@data-columnid='" + COL_DEDUCT_TDS + "']";

    private static final String DEDUCT_TDS_CELL_WITH_SLID =
            "//tr[td[@data-columnid=" + COL_AP_SL + "]/div[text()='%s']"
            + " and td[@data-columnid=" + COL_REF_SITE + "]/div[normalize-space()='%s']"
            + " and td[@data-columnid=" + COL_SL_ID + "]/div[normalize-space()='%s']]"
            + "//td[@data-columnid='" + COL_DEDUCT_TDS + "']";

    // ── TDS Info column — literal itemId from ExtJS store field rec.get('tdsInfo') ──────────────
    // Shows "TDS not appl." for non-TDS rows and "TDS: X.XX" after Calculate TDS & Net Payable.
    // TODO: Wire readTdsInfo() calls in CreateApVoucher after sub-ledger fill to activate this column.
    private static final String COL_TDS_INFO = "tdsInfo";

    private static final String TDS_INFO_CELL =
            "//tr[td[@data-columnid=" + COL_AP_SL + "]/div[text()='%s']"
            + " and td[@data-columnid=" + COL_REF_SITE + "]/div[normalize-space()='%s']]"
            + "//td[@data-columnid='" + COL_TDS_INFO + "']";

    private static final String TDS_INFO_CELL_WITH_SLID =
            "//tr[td[@data-columnid=" + COL_AP_SL + "]/div[text()='%s']"
            + " and td[@data-columnid=" + COL_REF_SITE + "]/div[normalize-space()='%s']"
            + " and td[@data-columnid=" + COL_SL_ID + "]/div[normalize-space()='%s']]"
            + "//td[@data-columnid='" + COL_TDS_INFO + "']";

    // Active ExtJS inline editor trigger — targets the arrow div rendered by ExtJS combo in this app version
    private static final By DEDUCT_TDS_TRIGGER = By.xpath(
            "//div[contains(@class,'x-editor') and not(contains(@class,'x-hide-display'))]" +
            "//div[contains(@class,'x-form-arrow-trigger')]");

    // Picker options scoped to comboDeductTDS — data-boundview prevents cross-dropdown collision
    private static final By DEDUCT_TDS_OPTION_YES = By.xpath(
            "//li[@data-boundview='comboDeductTDS-picker' and normalize-space()='Yes']");

    private static final By DEDUCT_TDS_OPTION_NO = By.xpath(
            "//li[@data-boundview='comboDeductTDS-picker' and normalize-space()='No']");


    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Reads the "Deduct TDS" cell value for a sub-ledger row identified by SL name,
     * reference site, and optional SL ID. Returns the displayed text (e.g. "Yes" / "No"),
     * or null if the cell cannot be located.
     */
    public String readDeductTds(String slName, String refSite, String slId) {
        String xpath = (slId != null && !slId.isBlank())
                ? String.format(DEDUCT_TDS_CELL_WITH_SLID, slName, refSite, slId)
                : String.format(DEDUCT_TDS_CELL, slName, refSite);
        try {
            return SeleniumUtils.getText(By.xpath(xpath), "DEDUCT_TDS_READ_" + slName);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Reads the "TDS Info" cell for a sub-ledger row — shows "TDS not appl." for non-TDS rows
     * and "TDS: X.XX" after Calculate TDS &amp; Net Payable has been run.
     *
     * <p>TODO: Call this after sub-ledger fill in CreateApVoucher and store results in
     * {@link com.gsl.reports.ActualResultsHolder#getUiTdsInfoMap()} to activate the
     * TDS Info column in the Sub Ledger Information report section.</p>
     *
     * @return cell text, or null if the column is absent / element not found
     */
    public String readTdsInfo(String slName, String refSite, String slId) {
        String xpath = (slId != null && !slId.isBlank())
                ? String.format(TDS_INFO_CELL_WITH_SLID, slName, refSite, slId)
                : String.format(TDS_INFO_CELL, slName, refSite);
        try {
            return SeleniumUtils.getText(By.xpath(xpath), "TDS_INFO_READ_" + slName);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Advance payment (On Account = Yes).
     * Sub-ledger + amount + reference site only; no SelectDocument / FIFO adjustments.
     * GL fields (General Ledger, Ledger Group Name) are populated by the app's async
     * handler, triggered by combo.blur() inside the JS layer. The AP advance combo store
     * does not carry GL metadata; the framework waits up to 12 s (30 × 400 ms) for the
     * app's AJAX to land, gated on Ext.Ajax.isLoading() === false.
     */
    public APVoucherSubLedgerInformation fillSubLedgerRowsForAdvance(
            MergedFinanceApVoucherData data, boolean includeRetry) {

        List<SubLedgerInformation> sls = data.getSubLedgerInformationList();
        fillSubLedgerBase(sls, includeRetry, OnAccountMode.ADVANCE);
        if (data.isPaymentProvided()) {
            handlePaymentInformation(sls, data.getPaymentInformationList());
        }
        return this;
    }

    /**
     * Document adjustment (On Account = No).
     * Sub-ledger + amount + reference site + SelectDocument / FIFO adjustments + payment.
     * The AP document combo store carries GL metadata (same as AR), so GL fields are
     * populated directly from the combo record (fast path — polls exit on first iteration).
     * combo.blur() is still called by the shared JS layer; it is harmless when GL is
     * already supplied by the combo.
     */
    public APVoucherSubLedgerInformation fillSubLedgerRowsForDocument(
            MergedFinanceApVoucherData data, boolean includeRetry) {

        List<SubLedgerInformation> sls = data.getSubLedgerInformationList();
        // On Account = No (document adjustment): deductTDS column is never rendered — skip entirely.
        fillSubLedgerBase(sls, includeRetry, OnAccountMode.DOCUMENT);
        fillAdjustments(sls, data);
        if (data.isPaymentProvided()) {
            handlePaymentInformation(sls, data.getPaymentInformationList());
        }
        return this;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Pass 1 (sub-ledger name + amount) and Pass 2 (reference site re-apply). Common to both modes.
     *
     * @param mode {@link OnAccountMode#ADVANCE} when On Account = Yes — the {@code deductTDS} column
     *             is present; TDS applicability is governed by the vendor. Both
     *             {@code isAmountAutoCalculated} and {@code setDeductTdsNative} may send deductTDS
     *             XPaths to the browser.
     *             {@link OnAccountMode#DOCUMENT} when On Account = No — the {@code deductTDS} column
     *             is never rendered in the DOM; both XPath call sites are suppressed so no
     *             deductTDS XPath is ever sent to the browser.
     */
    private void fillSubLedgerBase(List<SubLedgerInformation> sls, boolean includeRetry, OnAccountMode mode) {

        // Start each test's row grouping clean (ThreadLocal — safe under parallel="methods").
        SubLedgerRowStepRegistry.clear();

        // ── Pass 1: set sub-ledger name and amount (Dr) ───────────────────────
        for (int i = 0; i < sls.size(); i++) {
            final int idx = i;
            final SubLedgerInformation sl = sls.get(i);
            String rawSlName = sl.getSubLedger();
            if (rawSlName == null || rawSlName.trim().isEmpty()) {
                throw new IllegalArgumentException(
                    "SubLedger name is null/empty at row index " + i
                    + " (TestCaseID='" + sl.getTestCaseID() + "', LineNo='" + sl.getLineNo() + "').");
            }
            final String slName  = rawSlName.trim();
            final String slId    = sl.getId()            != null ? sl.getId().trim()            : "";
            final String refSite = sl.getReferenceSite() != null ? sl.getReferenceSite().trim() : "";

            // Create the per-row group node and register it so the later passes
            // (reference site, adjustment, payment, TDS) report under this same row.
            final String rowKey = SubLedgerRowStepRegistry.key(sl.getLineNo(), idx);
            final String rowLabel = "SL Row " + (idx + 1) + " — " + slName
                    + " | Dr: " + sl.getAmountDr()
                    + " | GL: " + sl.getGeneralLedger();
            ExtentTest rowNode = StepExecutor.group(rowLabel);
            SubLedgerRowStepRegistry.put(rowKey, rowNode, rowLabel);

            SubLedgerRowStepRegistry.stepUnderRow(rowKey, "Fill — name / amount", () -> {
                if (idx > 0) addSubLedgerRow();
                try {
                    setSubLedgerName(slName, slId, refSite, idx);
                    ReportLogger.trace("[SL Row " + (idx + 1) + "] slName=" + slName
                            + " slId=" + (slId.isEmpty() ? "—" : slId)
                            + " refSite=" + (refSite.isEmpty() ? "—" : refSite));
                    // The Amount (Dr) cell is disabled only when Deduct TDS = Yes — the app then
                    // calculates & populates it via the native "Calc. Amount → Calculate TDS & Net
                    // Payable" popup (handled later by fillTdsAndNetPayable). For Deduct TDS = No
                    // (incl. non-TDS-applicable rows) the cell is editable → type Amount natively.
                    if (isAmountAutoCalculated(slName, refSite, slId, mode)) {
                        ExtentLogger.info("[SL_AMOUNT] Row " + idx + " (" + slName + "): Deduct TDS = Yes — "
                                + "Amount is app-calculated via Calc TDS / Net Payable; skipping native amount entry.");
                        ReportLogger.trace("[SL Row " + (idx + 1) + "] amountDr → auto-calculated (TDS=Yes), skipping native entry");
                    } else {
                        setSubLedgerAmount(sl.getAmountDr().toPlainString(), idx);
                        ReportLogger.trace("[SL Row " + (idx + 1) + "] amountDr → typed=" + sl.getAmountDr());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted setting sub-ledger amount on row " + idx, e);
                }
                Log.debug("Row {} | SL={} | AmtDr={}", idx, sl.getSubLedger(), sl.getAmountDr());
            });
            notifyFill(l -> l.onRowFilled(idx));

            if (mode == OnAccountMode.ADVANCE && sl.getDeductTds() != null && !sl.getDeductTds().trim().isEmpty()) {
                final String deductTds = sl.getDeductTds().trim();
                SubLedgerRowStepRegistry.stepUnderRow(rowKey, "Deduct TDS — " + deductTds, () -> {
                    if ("NATIVE".equalsIgnoreCase(ConfigFactory.getFinanceConfig().subLedgerSelectionMode())) {
                        setDeductTdsNative(deductTds, slName, refSite, slId,
                                sl.getDeductTdsConfirmAction(), sl.getAmountDr(), idx);
                    } else {
                        JavascriptExecutorUtils.setDeductTdsForRow(
                                DriverManager.getDriver(), idx, deductTds, AP_GRID_ITEM_ID);
                    }
                });
            }
        }

        if (includeRetry) {
            applyReferenceSitePass(sls);
        }
    }

    /** SelectDocument / FIFO adjustments. Used only in document adjustment mode. */
    private void fillAdjustments(List<SubLedgerInformation> sls, MergedFinanceApVoucherData data) {

        List<SubLedgerInformation> adjustRows = sls.stream()
                .filter(r -> r.getAdjustmentType() != null)
                .collect(Collectors.toList());

        for (SubLedgerInformation row : adjustRows) {
            final int idx = sls.indexOf(row);
            final String refSite = row.getReferenceSite() != null ? row.getReferenceSite() : "";
            final String slId    = row.getId()            != null ? row.getId()            : "";
            final AdjustmentType adjType = row.getAdjustmentType();
            final String rowKey = SubLedgerRowStepRegistry.key(row.getLineNo(), idx);

            SubLedgerRowStepRegistry.stepUnderRow(rowKey, "Adjust — Type: " + adjType, () -> {
                AdjustmentEntry adjustmentEntry = openAdjustmentEntry(row.getSubLedger(), refSite, slId);
                notifyFill(l -> l.onAdjustmentOpened(idx, adjType));
                String lineNo = row.getLineNo();

                // Shared extraction: Create, Edit restore and "I will adjust more" all drive the
                // three adjustment methods through AdjustmentFlowSteps.applyAdjustment.
                AdjustmentSpec.AdjustmentSpecBuilder spec = AdjustmentSpec.builder().type(adjType);
                if (adjType == AdjustmentType.SELECT_DOCUMENT) {
                    data.getSelectDocumentList().stream()
                            .filter(d -> lineNo != null && lineNo.equalsIgnoreCase(d.getLineNo()))
                            .forEach(d -> spec.doc(new AdjustmentSpec.DocAmount(
                                    d.getDocumentNumber(), d.getAdjustmentAmount())));
                } else if (adjType == AdjustmentType.REFERENCE_SITEWISE_FIFO_SETOFF) {
                    List<com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.ReferenceSitewiseFIFOsetoff> fifoRows =
                            data.getReferenceSitewiseFIFOList() != null
                            ? data.getReferenceSitewiseFIFOList() : List.of();
                    fifoRows.stream()
                            .filter(r -> lineNo != null && lineNo.equalsIgnoreCase(r.getLineNo()))
                            .forEach(r -> spec.referenceSite(r.getReferenceSiteName()));
                }
                AdjustmentFlowSteps.applyAdjustment(adjustmentEntry, spec.build());
                notifyFill(l -> l.onAdjustmentApplied(idx, adjType));

                // Legacy scenarios (no plan): plain OK, exactly as before. The amount-mismatch
                // Confirmation is only scripted when a scenario asks for it via
                // data.getAdjustmentMismatchPlan().
                if (data.getAdjustmentMismatchPlan() == null) {
                    adjustmentEntry.clickOK();
                } else {
                    AdjustmentFlowSteps.okAnsweringMismatch(adjustmentEntry, data.getAdjustmentMismatchPlan());
                }
            });
        }
    }

    /**
     * Public, standalone counterpart to {@link #openAdjustmentEntry(String, String, String)} —
     * same body, but reachable directly instead of only through {@code fillAdjustments} →
     * {@code fillSubLedgerRowsForDocument}, which ALSO re-fills the row's name/amount/reference-
     * site fields from scratch. For re-tagging (or changing the adjustment on) an
     * already-populated, already-saved Edit-mode row, only the adjustment itself needs touching —
     * this method is the "click Adjust" half on its own (the menu is opened by applyAdjustment), with no other field
     * interaction attached. Callers resolve {@code subLedgerName}/{@code referenceSite}/
     * {@code slId} via {@link #readRowIdentity(int)} rather than test data, since an Edit flow has
     * no test-data-supplied row identity to locate the row by.
     */
    public AdjustmentEntry openAdjustmentEntryForExistingRow(String subLedgerName,
                                                              String referenceSite, String slId) {
        return openAdjustmentEntry(subLedgerName, referenceSite, slId);
    }

    /** Public counterpart to {@link VoucherSubLedgerInformationBase#readRowIdentity(int)} — same
     *  protected base method, exposed here so {@code ApVoucherAdd} can delegate to it. */
    public com.gsl.pages.Finance.Operations.Common.SubLedgerRowIdentity readSubLedgerRowIdentity(int rowNumber) {
        return readRowIdentity(rowNumber);
    }

    /**
     * Opens the Cheque/RTGS payment popup for an existing, already-populated Sub-Ledger row and
     * submits {@code payment} — the standalone counterpart to {@code handlePaymentInformation}'s
     * own private per-row loop (which also resolves the row/payment pairing from test data, unlike
     * an Edit flow which already knows exactly which row/payment it's touching). Returns the popup
     * itself (always an {@link ApPaymentInformationPopup}, since {@link #createPaymentPopup()}
     * always constructs one for this domain) so the caller can read
     * {@code getLastObservedPreviousInstrumentNo()} for the same before/after cross-check
     * {@code GeneralVoucherEditPredicateFactory.PAYMENT_INFO_EDIT_BEHAVIOR} already proves live for
     * GV — {@code ApPaymentInformationPopup extends ChequeRegisterPaymentInformationPopup}, so that
     * exact mechanism is already inherited here, not reimplemented.
     */
    public ApPaymentInformationPopup setPaymentInformationForExistingRow(
            String subLedgerName, String referenceSite, String slId,
            com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherPaymentInfo payment) {
        PaymentInformationPopup popup = clickPaymentButton(subLedgerName, referenceSite, slId);
        popup.fillAndSubmit(payment);
        return (ApPaymentInformationPopup) popup;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private AdjustmentEntry openAdjustmentEntry(String subLedgerName,
                                                 String referenceSite, String slid) {
        clickAdjust(subLedgerName, referenceSite, slid);
        // The Adjusted Document menu is opened by AdjustmentFlowSteps.applyAdjustment right before its option is used.
        return new AdjustmentEntry(this);
    }

    private void setSubLedgerName(String subledgerName, String id, String referenceSite, int rowIndex) {
        if ("NATIVE".equalsIgnoreCase(ConfigFactory.getFinanceConfig().subLedgerSelectionMode())) {
            setSubLedgerNameNative(subledgerName, id, referenceSite, rowIndex);
            return;
        }
        if (rowIndex == 0) {
            JavascriptExecutorUtils.setSubLedger(DriverManager.getDriver(), subledgerName, id, referenceSite, AP_GRID_ITEM_ID);
        } else {
            JavascriptExecutorUtils.setSubLedgerForRow(DriverManager.getDriver(), rowIndex, subledgerName, id, referenceSite, AP_GRID_ITEM_ID);
        }
    }

    /**
     * NATIVE Deduct TDS selection: double-click the Deduct TDS cell for the given sub-ledger row,
     * click the dropdown trigger arrow, and select Yes/No — exactly as a user would.
     * Row is located by SL name + reference site (+ optional slId), matching the same
     * {@code data-columnid} anchor pattern as {@code PAYMENT_BUTTON_TEMPLATE}.
     * Skips silently when the cell already shows the requested value.
     *
     * <p>When changing Yes → No, a confirmation popup appears:
     * "Calculated TDS and Payable amount will get cleared. Do you want to proceed?"
     * The {@code confirmAction} parameter controls which button to click:
     * {@code PROCEED} → "Ok, Go Ahead", {@code CANCEL} → "Close", {@code null} → defaults to PROCEED.
     */
    private void setDeductTdsNative(String value, String slName, String refSite, String slId,
                                     DeductTdsClearConfirmAction confirmAction,
                                     BigDecimal amountDr, int rowIndex) {
        By cellLocator = By.xpath(
                (slId != null && !slId.isBlank())
                        ? String.format(DEDUCT_TDS_CELL_WITH_SLID, slName, refSite, slId)
                        : String.format(DEDUCT_TDS_CELL, slName, refSite));

        String current = SeleniumUtils.getText(cellLocator, "DEDUCT_TDS_CELL");
        if (value.equalsIgnoreCase(current != null ? current.trim() : "")) {
            ExtentLogger.info("[DEDUCT_TDS_NATIVE] ALREADY_SET: sl=" + slName + " value=" + value);
            return;
        }

        WaitUtils.fluentWaitBuilder().until(driver -> {
            new Actions(driver).doubleClick(driver.findElement(cellLocator)).perform();
            return true;
        });
        JavascriptExecutorUtils.waitForExtJS();

        // Attempt-and-detect: a non-TDS-applicable row reads 'No' but its Deduct TDS cell is NOT
        // editable, so the inline editor (and its dropdown trigger) never opens. Detect that and skip
        // silently rather than time out trying to change a cell that cannot be changed.
        if (!SeleniumUtils.isElementPresent(DEDUCT_TDS_TRIGGER, 3)) {
            ExtentLogger.info("[DEDUCT_TDS_NATIVE] Row '" + slName + "': Deduct TDS cell not editable "
                    + "(non-TDS-applicable) — skipping change (current='" + current + "', requested='" + value + "').");
            return;
        }

        SeleniumUtils.click(DEDUCT_TDS_TRIGGER, WaitType.CLICKABLE, "DEDUCT_TDS_TRIGGER");

        By option = "Yes".equalsIgnoreCase(value) ? DEDUCT_TDS_OPTION_YES : DEDUCT_TDS_OPTION_NO;
        SeleniumUtils.click(option, WaitType.CLICKABLE, "DEDUCT_TDS_OPTION_" + value);

        // TAB fires the cell blur event which triggers the confirmation popup (Yes→No only).
        // TAB is sent to the cell (not the option) — the dropdown is already closed after the click,
        // so the option <li> is no longer interactable; the cell retains focus.
        // No JS page-ready wait here — the modal is already appearing; JS waits crash when a
        // modal dialog is open. The handler's built-in 3-retry × 3s wait covers popup detection.
        if ("No".equalsIgnoreCase(value)) {
            SeleniumUtils.pressTab(cellLocator, "DEDUCT_TDS_CELL");
            TdsPayableClearConfirmHandler.handle(confirmAction, slName);
            // App clears Amount(Dr) when popup is confirmed — re-apply the original value
            if (amountDr != null) {
                try {
                    setSubLedgerAmount(amountDr.toPlainString(), rowIndex);
                    ExtentLogger.info("[DEDUCT_TDS_NATIVE] Amount(Dr) re-applied after TDS clear: "
                            + amountDr.toPlainString() + " — row=" + rowIndex);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(
                            "Interrupted re-applying Amount(Dr) after TDS clear on row " + rowIndex, e);
                }
            }
        }

        JavascriptExecutorUtils.waitForExtJS();
        ExtentLogger.info("[DEDUCT_TDS_NATIVE] APPLIED: sl=" + slName + " value=" + value);
    }

    /**
     * Clicks the Amount (Dr) cell for the given sub-ledger row and returns the resulting dialog.
     * After a document has been adjusted against the SL, clicking the cell raises an Information
     * dialog ("Document is adjusted against the selected sub ledger, amount can not be modified
     * manually.") — the returned {@link DocumentPopup} wraps that dialog for assertion.
     *
     * <p>The Amount column ID is resolved at runtime via ExtJS so it survives grid re-renders.
     */
    public DocumentPopup clickAmountCell(String slName, String slId) {
        JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();
        String columnId = (String) js.executeScript(
                "try {" +
                "    var grid = Ext.ComponentQuery.query('gridpanel[itemId=" + AP_GRID_ITEM_ID + "]')[0];" +
                "    if (!grid) return null;" +
                "    var cols = grid.getColumns();" +
                "    for (var i = 0; i < cols.length; i++) {" +
                "        if (cols[i].dataIndex === 'amount') return cols[i].el.id;" +
                "    }" +
                "    return null;" +
                "} catch(e) { return null; }"
        );
        if (columnId == null || columnId.isEmpty()) {
            throw new IllegalStateException(
                    "Could not resolve Amount (Dr) column ID in grid [" + AP_GRID_ITEM_ID + "]");
        }
        By cell = SubLedgerRowLocator.amountCell(slName, slId, columnId);
        DriverManager.getDriver().findElement(cell).click();
        return new DocumentPopup();
    }

    /**
     * Detects (natively, from the UI) whether a sub-ledger row is TDS-applicable by reading the
     * Deduct TDS cell. A TDS-applicable row shows a deduct value ({@code Yes}/{@code No}); a
     * non-applicable row shows {@code TDS not appl.} (or blank). For TDS-applicable rows the
     * Amount (Dr) cell is disabled and is populated by the Calc TDS / Net Payable flow, so native
     * amount typing must be skipped.
     *
     * <p>The Amount (Dr) cell is disabled ONLY when TDS is actually being deducted, i.e. the Deduct
     * TDS cell reads {@code Yes}. A non-TDS-applicable row reads {@code No} (NOT "TDS not appl." in
     * native mode) and its Amount cell is editable — so {@code No} (and anything that is not
     * {@code Yes}) means the amount must be typed natively.</p>
     */
    private boolean isAmountAutoCalculated(String slName, String refSite, String slId, OnAccountMode mode) {
        if (mode == OnAccountMode.DOCUMENT) {
            // deductTDS column is never rendered in document mode (On Account = No).
            // Amount is always manually entered; sending the XPath would cause NoSuchElementException.
            ExtentLogger.info("[SL_AMOUNT] Row '" + slName + "': DOCUMENT mode — deductTDS column absent, amount always editable.");
            return false;
        }
        By cell = By.xpath(
                (slId != null && !slId.isBlank())
                        ? String.format(DEDUCT_TDS_CELL_WITH_SLID, slName, refSite, slId)
                        : String.format(DEDUCT_TDS_CELL, slName, refSite));
        String t = SeleniumUtils.getText(cell, "DEDUCT_TDS_CELL");
        t = (t == null) ? "" : t.trim();
        boolean autoCalculated = "Yes".equalsIgnoreCase(t);
        ExtentLogger.info("[SL_AMOUNT] Row '" + slName + "': Deduct TDS cell='" + t
                + "' → amount auto-calculated (skip native amount)=" + autoCalculated);
        return autoCalculated;
    }

    /**
     * Public counterpart to {@link #setSubLedgerAmount(String, int)} — edits an existing,
     * already-populated row's Amount (Dr) cell directly, for Advance-mode (On Account = Yes) Edit
     * scenarios where the cell is a plain, directly-editable field (unlike Document mode, where
     * this same cell is confirmed read-only — see {@code ApVoucherPredicateFactory
     * .DOC_AMOUNT_READ_ONLY_BEHAVIOR} — and must go through {@link #openAdjustmentEntryForExistingRow}
     * instead).
     *
     * @param rowIndex 0-based grid row index (same convention {@link #setSubLedgerAmount} already uses)
     */
    public void setAmountForExistingRow(int rowIndex, String newAmount) {
        try {
            setSubLedgerAmount(newAmount, rowIndex);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted setting sub-ledger amount on row " + rowIndex, e);
        }
    }
}
