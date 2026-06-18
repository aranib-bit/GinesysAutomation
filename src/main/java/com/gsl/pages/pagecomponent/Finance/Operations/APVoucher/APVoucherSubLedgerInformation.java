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
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.pages.enums.Finance.Operations.APVoucher.APVoucherGridColumnHeader;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherGridColumnHeader;
import com.gsl.reports.ExtentLogger;
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

    private static final By SUBLEDGERNAMEBLANK = By.xpath(
            "(//div[contains(@class,'x-window')]//table[contains(@class,'x-grid-item')])" +
            "[last()]//tr[last()]//td[3]");

    private static final String SUBLEDGERSELECTION_XPATH = "//span[text()='%s']";

    private static final String AP_GRID_ITEM_ID = "apvch_grid";

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
            ExtentTest rowNode = StepExecutor.group(
                    "SL Row " + (idx + 1) + " — " + slName
                    + " | Dr: " + sl.getAmountDr()
                    + " | GL: " + sl.getGeneralLedger());
            SubLedgerRowStepRegistry.put(rowKey, rowNode);

            StepExecutor.stepUnder(rowNode, "Fill — name / amount", () -> {
                if (idx > 0) addSubLedgerRow();
                try {
                    setSubLedgerName(slName, slId, refSite, idx);
                    // The Amount (Dr) cell is disabled only when Deduct TDS = Yes — the app then
                    // calculates & populates it via the native "Calc. Amount → Calculate TDS & Net
                    // Payable" popup (handled later by fillTdsAndNetPayable). For Deduct TDS = No
                    // (incl. non-TDS-applicable rows) the cell is editable → type Amount natively.
                    if (isAmountAutoCalculated(slName, refSite, slId, mode)) {
                        ExtentLogger.info("[SL_AMOUNT] Row " + idx + " (" + slName + "): Deduct TDS = Yes — "
                                + "Amount is app-calculated via Calc TDS / Net Payable; skipping native amount entry.");
                    } else {
                        setSubLedgerAmount(sl.getAmountDr().toPlainString(), idx);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted setting sub-ledger amount on row " + idx, e);
                }
                Log.debug("Row {} | SL={} | AmtDr={}", idx, sl.getSubLedger(), sl.getAmountDr());
            });

            if (mode == OnAccountMode.ADVANCE && sl.getDeductTds() != null && !sl.getDeductTds().trim().isEmpty()) {
                final String deductTds = sl.getDeductTds().trim();
                StepExecutor.stepUnder(rowNode, "Deduct TDS — " + deductTds, () -> {
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
            // ── Pass 2: change reference site only when explicitly requested ──
            for (int i = 0; i < sls.size(); i++) {
                final int idx = i;
                final SubLedgerInformation sl = sls.get(i);
                final String refSite = sl.getReferenceSite() != null ? sl.getReferenceSite() : "";
                ExtentTest rowNode = SubLedgerRowStepRegistry.get(
                        SubLedgerRowStepRegistry.key(sl.getLineNo(), idx));
                if (sl.isChangeReferenceSite()) {
                    StepExecutor.stepUnder(rowNode,
                            "Change Reference Site — " + (refSite.isEmpty() ? "(none)" : refSite),
                            () -> setSubLedgerReferenceSite(refSite, idx));
                } else {
                    ExtentLogger.info("[AP SL RefSite] Row " + idx
                            + ": skipping Pass-2 set (changeReferenceSite=false) — current value kept");
                }
            }
            // Row-0 retry — only if that row opted in
            {
                final SubLedgerInformation sl0 = sls.get(0);
                final String refSite = sl0.getReferenceSite() != null ? sl0.getReferenceSite() : "";
                ExtentTest rowNode = SubLedgerRowStepRegistry.get(
                        SubLedgerRowStepRegistry.key(sl0.getLineNo(), 0));
                if (sl0.isChangeReferenceSite()) {
                    StepExecutor.stepUnder(rowNode,
                            "Change Reference Site (row-0 retry) — " + (refSite.isEmpty() ? "(none)" : refSite),
                            () -> setSubLedgerReferenceSite(refSite, 0));
                }
            }
            // Read back actual values regardless — captures what the app populated
            for (int i = 0; i < sls.size(); i++) {
                String actual = getSubLedgerReferenceSite(i);
                if (actual != null && !actual.isEmpty()) sls.get(i).setReferenceSite(actual);
            }
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
            ExtentTest rowNode = SubLedgerRowStepRegistry.get(
                    SubLedgerRowStepRegistry.key(row.getLineNo(), idx));

            StepExecutor.stepUnder(rowNode, "Adjust — Type: " + adjType, () -> {
                AdjustmentEntry adjustmentEntry = openAdjustmentEntry(row.getSubLedger(), refSite, slId);
                boolean needsConfirmation = false; // AP voucher has no GridRelation confirmation popup

                if (adjType == AdjustmentType.SELECT_DOCUMENT) {
                    SelectDocumentPopup popup = adjustmentEntry.selectDocument();
                    String lineNo = row.getLineNo();
                    List<SelectDocument> docs = data.getSelectDocumentList().stream()
                            .filter(d -> lineNo != null && lineNo.equalsIgnoreCase(d.getLineNo()))
                            .collect(Collectors.toList());
                    for (SelectDocument doc : docs) {
                        popup.selectDocument(doc.getDocumentNumber());
                    }
                    popup.clickSelect();
                    for (SelectDocument doc : docs) {
                        BigDecimal excelAmount = doc.getAdjustmentAmount();
                        if (excelAmount == null || excelAmount.compareTo(BigDecimal.ZERO) == 0) continue;
                        if (excelAmount.compareTo(BigDecimal.ZERO) < 0) {
                            throw new IllegalArgumentException(
                                "Adjustment amount cannot be negative for document ["
                                + doc.getDocumentNumber() + "]. Got: " + excelAmount.toPlainString());
                        }
                        String uiStr = adjustmentEntry.getAdjustedAmountForDocument(doc.getDocumentNumber());
                        BigDecimal uiAmount = BigDecimalUtils.safeParse(uiStr);
                        if (excelAmount.compareTo(uiAmount) != 0) {
                            adjustmentEntry.setAdjustedAmountForDocument(
                                    doc.getDocumentNumber(), excelAmount.toPlainString());
                        }
                    }
                    if (needsConfirmation) {
                        adjustmentEntry.clickOKExpectingConfirmation().clickProceedAnyway();
                    } else {
                        adjustmentEntry.clickOK();
                    }

                } else if (adjType == AdjustmentType.REFERENCE_SITEWISE_FIFO_SETOFF) {
                    String lineNo = row.getLineNo();
                    List<com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.ReferenceSitewiseFIFOsetoff> fifoRows =
                            data.getReferenceSitewiseFIFOList() != null
                            ? data.getReferenceSitewiseFIFOList() : List.of();
                    List<com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.ReferenceSitewiseFIFOsetoff> refSites =
                            fifoRows.stream()
                                    .filter(r -> lineNo != null && lineNo.equalsIgnoreCase(r.getLineNo()))
                                    .collect(Collectors.toList());
                    com.gsl.pages.Finance.Operations.Common.ReferenceSiteWiseFIFOPopup fifoPopup =
                            adjustmentEntry.openReferenceSiteWiseFIFOPopup();
                    for (com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.ReferenceSitewiseFIFOsetoff rs : refSites) {
                        fifoPopup.selectAndAutoAdjust(rs.getReferenceSiteName(), rs.getSiteName());
                    }
                    com.gsl.pages.Finance.Operations.Common.AdjustmentAmountSnapshot snapshot =
                            adjustmentEntry.captureAmounts();
                    Log.info("RefSiteFIFO amounts — txn={} adj={} pending={}",
                             snapshot.getTransactionAmount(), snapshot.getAdjustedAmount(), snapshot.getPendingToAdjust());
                    if (needsConfirmation) {
                        adjustmentEntry.clickOKExpectingConfirmation().clickProceedAnyway();
                    } else {
                        adjustmentEntry.clickOK();
                    }

                } else if (adjType == AdjustmentType.FIFO_SETOFF) {
                    if (needsConfirmation) {
                        adjustmentEntry.fifoSetOff().clickOKExpectingConfirmation().clickProceedAnyway();
                    } else {
                        adjustmentEntry.fifoSetOff().clickOK();
                    }
                }
            });
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private AdjustmentEntry openAdjustmentEntry(String subLedgerName,
                                                 String referenceSite, String slid) {
        clickAdjust(subLedgerName, referenceSite, slid);
        AdjustmentEntry entry = new AdjustmentEntry(this);
        entry.openAdjustedDocumentMenu();
        return entry;
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
     * NATIVE sub-ledger selection: open the real inline grid combo, type the sub-ledger name,
     * and click the matching dropdown option — exactly as a user would. This makes the
     * application run its own combo {@code select} handler, which evaluates TDS applicability
     * and renders the "Calc. Amount" button (the JS-injection path does not, leaving rows as
     * "TDS not appl."). Reuses the proven {@code <li role='option'>} picker pattern from
     * {@link com.gsl.pages.pagecomponent.Finance.Operations.APVoucher.APVoucherTdsAndNetPayable}.
     */
    private void setSubLedgerNameNative(String subledgerName, String id, String referenceSite, int rowIndex) {
        String inputId = JavascriptExecutorUtils.openSubLedgerComboEditor(
                DriverManager.getDriver(), rowIndex, AP_GRID_ITEM_ID);
        if (inputId == null || inputId.isEmpty()) {
            throw new IllegalStateException(
                    "[SL_NATIVE] Could not open Sub-Ledger combo editor on row " + rowIndex
                    + " for '" + subledgerName + "'");
        }

        // Type the name into the real combo input — the app's remote query filters the
        // grid-style picker (Name | Id | Class Name columns) as we type.
        By comboInput = By.id(inputId);
        SeleniumUtils.clear(comboInput, "SL_COMBO_INPUT");
        SeleniumUtils.sendKeys(comboInput, WaitType.VISIBLE, subledgerName, "SL_COMBO_INPUT");
        JavascriptExecutorUtils.waitForExtJS();

        // Click the matching row in the grid picker. Sub-ledger names are NOT unique
        // (e.g. the same supplier can appear with different Ids), so disambiguate by
        // Name + Id when an Id is provided. Same picker structure as CashBankPopup.
        By option = subLedgerPickerRow(subledgerName, id);
        SeleniumUtils.click(option, WaitType.CLICKABLE, "SL_OPTION_" + subledgerName);
        // Let the application's combo select handler run its GL + TDS-applicability evaluation.
        JavascriptExecutorUtils.waitForExtJS();
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForComponentReady();

        Log.debug("[SL_NATIVE] Selected SL='{}' (id='{}') on row {}", subledgerName, id, rowIndex);

        if (referenceSite != null && !referenceSite.trim().isEmpty()) {
            setSubLedgerReferenceSite(referenceSite, rowIndex);
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

        WebElement cell = DriverManager.getDriver().findElement(cellLocator);
        new Actions(DriverManager.getDriver()).doubleClick(cell).perform();
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
     * Build the locator for a row in the Sub-Ledger grid picker (the dropdown opened from the
     * inline combo). The picker is a paged grid with Name / Id / Class Name columns inside an
     * {@code x-grid-item-container} — the same structure used by
     * {@link com.gsl.pages.pagecomponent.Finance.Common.CashBankPopup}.
     *
     * <p>When an {@code id} is supplied, match a row that contains BOTH the exact Name and the
     * exact Id (sub-ledger names are not unique), then click the Name cell within that row.
     * Otherwise match by Name only.</p>
     */
    private By subLedgerPickerRow(String name, String id) {
        String n = name.trim();
        // The SL dropdown is an ExtJS boundlist: div.x-boundlist-floating → div.x-boundlist-item
        // (role=option) → table.combooverflowfinance with one td per column. Each cell carries a
        // clean @data-qtip value, so matching on data-qtip is highlight-proof (the visible text
        // wraps the typed substring in a highlight span; the attribute does not). Click the
        // x-boundlist-item itself (the option element, top layer) to avoid the grid-view interception.
        String base = "//div[contains(@class,'x-boundlist-floating')]"
                + "//div[contains(@class,'x-boundlist-item')";
        if (id != null && !id.trim().isEmpty()) {       // unique: match Name + Id (slid)
            return By.xpath(base + " and .//td[@data-qtip=\"" + n + "\"]"
                                 + " and .//td[@data-qtip=\"" + id.trim() + "\"]]");
        }
        return By.xpath(base + " and .//td[@data-qtip=\"" + n + "\"]]");   // name only
    }

    private void addSubLedgerRow() {
        JavascriptExecutorUtils.addSubLedgerRow(DriverManager.getDriver(), AP_GRID_ITEM_ID);
    }

    private void setSubLedgerReferenceSite(String referenceSite, int rowIndex) {
        if (referenceSite == null || referenceSite.trim().isEmpty()) return;
        // Poll for auto-population before deciding skip vs. native path.
        // The app asynchronously fills Reference Site after SL selection; reading immediately
        // returns blank and incorrectly routes to the native editor path, causing TimeoutException.
        String existing = waitForReferenceSiteAutoPopulation(rowIndex);
        if (existing != null && !existing.trim().isEmpty()) {
            ExtentLogger.info("[AP SL RefSite] Row " + rowIndex + ": already '" + existing
                    + "' — not changing (skip).");
            return;
        }
        String inputId = JavascriptExecutorUtils.openReferenceSiteCellEditor(
                DriverManager.getDriver(), rowIndex, AP_GRID_ITEM_ID);
        if (inputId == null || inputId.isEmpty()) {
            ExtentLogger.warning("[AP SL RefSite] Row " + rowIndex
                    + ": native editor did not open — using JS injection fallback for '" + referenceSite + "'");
            Log.warn("[SL_REFSITE_NATIVE] AP row {} — falling back to JS injection for '{}'", rowIndex, referenceSite);
            JavascriptExecutorUtils.setSubLedgerReferenceSite(DriverManager.getDriver(), rowIndex, referenceSite, AP_GRID_ITEM_ID);
            return;
        }
        ExtentLogger.info("[AP SL RefSite] Row " + rowIndex + ": native path — typing '" + referenceSite + "'");
        By comboInput = By.id(inputId);
        SeleniumUtils.clear(comboInput, "SL_REFSITE");
        SeleniumUtils.sendKeys(comboInput, WaitType.VISIBLE, referenceSite, "SL_REFSITE");
        JavascriptExecutorUtils.waitForExtJS();
        By option = By.xpath("//li[@role='option'][normalize-space(.)='" + referenceSite + "']");
        SeleniumUtils.click(option, WaitType.CLICKABLE, "SL_REFSITE_OPTION_" + referenceSite);
        JavascriptExecutorUtils.waitForExtJS();
        JavascriptExecutorUtils.waitForPageLoad();
        ExtentLogger.info("[AP SL RefSite] Row " + rowIndex + ": selected '" + referenceSite + "' via native");
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
     * Waits up to {@code shortWaitSeconds} for the UI to auto-populate the Reference Site cell
     * after a sub-ledger is selected.  Returns the populated value, or {@code ""} on timeout.
     *
     * <p>The app fills Reference Site asynchronously via an ExtJS store load after SL selection;
     * calling {@link #getSubLedgerReferenceSite} immediately after returns blank and causes the
     * native editor path to fire — leading to a {@code TimeoutException} when the combo option
     * is never found.  This poll avoids that race condition.
     */
    private String waitForReferenceSiteAutoPopulation(int rowIndex) {
        long timeoutSeconds = ConfigFactory.getWaitConfig().shortWaitSeconds();
        try {
            return WaitUtils.webDriverWait(timeoutSeconds).until(driver -> {
                String val = JavascriptExecutorUtils.getSubLedgerReferenceSiteName(
                        driver, rowIndex, AP_GRID_ITEM_ID);
                return (val != null && !val.trim().isEmpty()) ? val : null;
            });
        } catch (TimeoutException e) {
            ExtentLogger.info("[AP SL RefSite] Row " + rowIndex
                    + ": auto-population did not occur within " + timeoutSeconds
                    + "s — will use native path.");
            return "";
        }
    }

    private String getSubLedgerReferenceSite(int rowIndex) {
        return JavascriptExecutorUtils.getSubLedgerReferenceSiteName(DriverManager.getDriver(), rowIndex, AP_GRID_ITEM_ID);
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

    private void setSubLedgerAmount(String subLedgerAmount, int rowIndex) throws InterruptedException {
        String inputId = JavascriptExecutorUtils.openAmountCellEditor(
                DriverManager.getDriver(), rowIndex, AP_GRID_ITEM_ID);
        if (inputId == null || inputId.isEmpty()) {
            throw new IllegalStateException(
                    "[SL_AMOUNT_NATIVE] Could not open Amount editor on row " + rowIndex
                    + " for AP grid [" + AP_GRID_ITEM_ID + "]");
        }
        By amountInput = By.id(inputId);
        SeleniumUtils.clear(amountInput, "SL_AMOUNT");
        SeleniumUtils.sendKeys(amountInput, WaitType.VISIBLE, subLedgerAmount, "SL_AMOUNT");
        // Tab commits the value to the ExtJS store record — without it the editor stays open
        // and the typed amount is never written to the underlying record, causing "Amount is required".
        SeleniumUtils.pressTab(amountInput, "SL_AMOUNT");
        JavascriptExecutorUtils.waitForExtJS();
    }

    private void handlePaymentInformation(
            List<SubLedgerInformation> subLedgerList,
            List<PaymentInformation> paymentList) {

        for (SubLedgerInformation subLedger : subLedgerList) {
            final String lineNo = subLedger.getLineNo();
            if (lineNo == null || lineNo.isBlank()) continue;

            final PaymentInformation payment = paymentList.stream()
                    .filter(p -> p.getLineNo() != null
                            && p.getLineNo().equalsIgnoreCase(lineNo)
                            && (subLedger.getTestCaseID() == null || p.getTestCaseID() == null
                                || p.getTestCaseID().equalsIgnoreCase(subLedger.getTestCaseID())))
                    .findFirst()
                    .orElse(null);

            if (payment == null) continue;

            final PaymentSelectionType effectiveType = subLedger.getPaymentSelection() != null
                    ? subLedger.getPaymentSelection()
                    : payment.getPaymentMode();

            if (effectiveType == null) continue;
            if (effectiveType == PaymentSelectionType.CASH) continue;

            final String refSite = subLedger.getReferenceSite() != null ? subLedger.getReferenceSite() : "";
            final String slid    = subLedger.getId()            != null ? subLedger.getId()            : "";
            ExtentTest rowNode = SubLedgerRowStepRegistry.get(lineNo);
            StepExecutor.stepUnder(rowNode, "Payment — " + effectiveType.label(), () -> {
                clickPaymentButton(subLedger.getSubLedger(), refSite, slid).fillAndSubmit(payment);
                JavascriptExecutorUtils.waitForExtJS();
            });
        }
    }
}
