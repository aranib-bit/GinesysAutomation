package com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher;

import com.gsl.driver.DriverManager;
import com.gsl.enums.AdjustmentType;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.enums.WaitType;
import com.gsl.pages.Finance.Operations.Common.AdjustmentAmountSnapshot;
import com.gsl.pages.Finance.Operations.Common.AdjustmentEntry;
import com.gsl.enums.VoucherDomain;
import com.gsl.pages.Finance.Operations.Common.ReferenceSiteWiseFIFOPopup;
import com.gsl.pages.Finance.Operations.Common.SelectDocumentPopup;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherSubLedgerInformationBase;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.reports.SubLedgerRowStepRegistry;
import com.aventstack.extentreports.ExtentTest;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.ReferenceSitewiseFIFOsetoff;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SelectDocument;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformation;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.dataprovider.fixture.finance.SelectDocumentVariant;
import com.gsl.utils.BigDecimalUtils;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import com.gsl.pages.Finance.Operations.Common.ArPaymentInformationPopup;
import com.gsl.pages.Finance.Operations.Common.PaymentInformationPopup;
import com.gsl.config.ConfigFactory;

public class ARVoucherSubLedgerInformation extends VoucherSubLedgerInformationBase {

    public ARVoucherSubLedgerInformation() {
        super(VoucherDomain.AR);
    }

    @Override
    protected PaymentInformationPopup createPaymentPopup() {
        return new ArPaymentInformationPopup();
    }

    private static final String AR_GRID_ITEM_ID = "arvch_grid";

    private static final By SUBLEDGERNAMEBLANK = By.xpath(
            "(//div[contains(@class,'x-window')]//table[contains(@class,'x-grid-item')])" +
            "[last()]//tr[last()]//td[3]");

    private static final String SUBLEDGERSELECTION_XPATH = "//span[text()='%s']";

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Fills all sub-ledger rows, processes adjustments, and handles payment —
     * the complete sub-ledger section in one call.
     *
     * @param data         full test data (carries subledger list, select docs, payment list)
     * @param includeRetry true  → run Pass 2 reference-site re-apply + row-0 retry + read-back
     *                     false → Pass 1 only (used for DOC_NO_ADJUSTMENT scenarios)
     */
    public ARVoucherSubLedgerInformation fillSubLedgerRows(
            MergedFinanceArVoucherData data, boolean includeRetry) {

        final List<SubLedgerInformation> sls = data.getSubLedgerInformationList();

        SubLedgerRowStepRegistry.clear();

        // ── Pass 1: set sub-ledger name and amount ────────────────────────────
        for (int i = 0; i < sls.size(); i++) {
            final int idx = i;
            final SubLedgerInformation sl = sls.get(i);
            String rawSlName = sl.getSubLedger();
            if (rawSlName == null || rawSlName.trim().isEmpty()) {
                throw new IllegalArgumentException(
                    "SubLedger name is null/empty at row index " + i
                    + " (TestCaseID='" + sl.getTestCaseID() + "', LineNo='" + sl.getLineNo() + "'). "
                    + "Check Excel 'SubLedgerInformation' sheet — column header must be exactly 'Sub Ledger'."
                );
            }
            final String slName  = rawSlName.trim();
            final String slId    = sl.getId()            != null ? sl.getId().trim()            : "";
            final String refSite = sl.getReferenceSite() != null ? sl.getReferenceSite().trim() : "";

            final String rowKey = SubLedgerRowStepRegistry.key(sl.getLineNo(), idx);
            ExtentTest rowNode = StepExecutor.group(
                    "SL Row " + (idx + 1) + " — " + slName
                    + " | Cr: " + sl.getAmountCr()
                    + " | GL: " + sl.getGeneralLedger());
            SubLedgerRowStepRegistry.put(rowKey, rowNode);

            StepExecutor.stepUnder(rowNode, "Fill — name / amount", () -> {
                if (idx > 0) addSubLedgerRow();
                try {
                    setSubLedgerName(slName, slId, refSite, idx);
                    ReportLogger.trace("[SL Row " + (idx + 1) + "] slName=" + slName
                            + " slId=" + (slId.isEmpty() ? "—" : slId)
                            + " refSite=" + (refSite.isEmpty() ? "—" : refSite));
                    setSubLedgerAmount(sl.getAmountCr().toPlainString(), idx);
                    ReportLogger.trace("[SL Row " + (idx + 1) + "] amountCr → typed=" + sl.getAmountCr());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted setting sub-ledger amount on row " + idx, e);
                }
                Log.debug("Row {} | SL={} | Amt={}", idx, sl.getSubLedger(), sl.getAmountCr());
            });
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
                    ExtentLogger.info("[AR SL RefSite] Row " + (idx + 1)
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

        // ── Adjustments ───────────────────────────────────────────────────────
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
                boolean needsConfirmation = data.getGridRelation() == SelectDocumentVariant.GridRelation.GREATER
                        || data.getGridRelation() == SelectDocumentVariant.GridRelation.LESS;
                Log.debug("AdjType: {} | GridRelation: {} | needsConfirmation: {}", adjType, data.getGridRelation(), needsConfirmation);

                if (adjType == AdjustmentType.SELECT_DOCUMENT) {
                    SelectDocumentPopup popup = adjustmentEntry.selectDocument();
                    String lineNo = row.getLineNo();
                    List<SelectDocument> docs = data.getSelectDocumentList().stream()
                            .filter(d -> lineNo != null && lineNo.equalsIgnoreCase(d.getLineNo()))
                            .collect(Collectors.toList());
                    Log.debug("Filtered docs for LineNo {}: {}", lineNo, docs.size());
                    for (SelectDocument doc : docs) {
                        Log.debug("Select Document: {}", doc.getDocumentNumber());
                        popup.selectDocument(doc.getDocumentNumber());
                    }
                    popup.clickSelect();
                    for (SelectDocument doc : docs) {
                        BigDecimal excelAmount = doc.getAdjustmentAmount();
                        if (excelAmount == null || excelAmount.compareTo(BigDecimal.ZERO) == 0) continue;
                        if (excelAmount.compareTo(BigDecimal.ZERO) < 0) {
                            throw new IllegalArgumentException(
                                "Adjustment amount cannot be negative for document ["
                                + doc.getDocumentNumber() + "]. Got: " + excelAmount.toPlainString()
                                + ". Fix the test data (Excel/DB) for this document.");
                        }
                        String uiStr = adjustmentEntry.getAdjustedAmountForDocument(doc.getDocumentNumber());
                        BigDecimal uiAmount = BigDecimalUtils.safeParse(uiStr);
                        Log.debug("Doc {} — UI: {} | Excel: {}", doc.getDocumentNumber(), uiAmount, excelAmount);
                        if (excelAmount.compareTo(uiAmount) != 0) {
                            Log.debug("Mismatch. Updating to: {}", excelAmount.toPlainString());
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
                    List<ReferenceSitewiseFIFOsetoff> fifoRows =
                            data.getReferenceSitewiseFIFOList() != null
                                    ? data.getReferenceSitewiseFIFOList() : List.of();
                    List<ReferenceSitewiseFIFOsetoff> refSites = fifoRows.stream()
                            .filter(r -> lineNo != null && lineNo.equalsIgnoreCase(r.getLineNo()))
                            .collect(Collectors.toList());

                    ReferenceSiteWiseFIFOPopup fifoPopup = adjustmentEntry.openReferenceSiteWiseFIFOPopup();
                    for (int i = 0; i < refSites.size(); i++) {
                        ReferenceSitewiseFIFOsetoff rs = refSites.get(i);
                        ExtentLogger.info("[FIFO] Reference Site: " + rs.getReferenceSiteName()
                                + " | Site: " + rs.getSiteName());
                        if (i > 0) fifoPopup.addRow();
                        fifoPopup.selectReferenceSiteInCombo(rs.getReferenceSiteName())
                                 .selectSubSiteRow(rs.getSiteName());
                    }
                    fifoPopup.clickAutoAdjust();

                    AdjustmentAmountSnapshot snapshot = adjustmentEntry.captureAmounts();
                    Log.info("RefSiteFIFO amounts — txn={} adj={} pending={}",
                             snapshot.getTransactionAmount(),
                             snapshot.getAdjustedAmount(),
                             snapshot.getPendingToAdjust());
                    ExtentLogger.info("[FIFO] Amounts — Txn: " + snapshot.getTransactionAmount()
                            + " | Adjusted: " + snapshot.getAdjustedAmount()
                            + " | Pending: " + snapshot.getPendingToAdjust());

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

        // ── Payment (Cheque/RTGS) ─────────────────────────────────────────────
        if (data.isPaymentProvided()) {
            handlePaymentInformation(sls, data.getPaymentInformationList());
        }

        return this;
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
        setSubLedgerNameNative(subledgerName, id, referenceSite, rowIndex);
    }

    /**
     * NATIVE sub-ledger selection: open the real inline grid combo, type the sub-ledger name,
     * and click the matching dropdown option — exactly as a user would. This makes the
     * application run its own combo {@code select} handler, which evaluates GL fields.
     * AR has no TDS/DeductTDS column so no applicability check is needed after selection.
     */
    private void setSubLedgerNameNative(String subledgerName, String id, String referenceSite, int rowIndex) {
        String inputId = JavascriptExecutorUtils.openSubLedgerComboEditor(
                DriverManager.getDriver(), rowIndex, AR_GRID_ITEM_ID);
        if (inputId == null || inputId.isEmpty()) {
            throw new IllegalStateException(
                    "[SL_NATIVE] Could not open Sub-Ledger combo editor on row " + rowIndex
                    + " for '" + subledgerName + "'");
        }

        By comboInput = By.id(inputId);
        SeleniumUtils.clear(comboInput, "SL_COMBO_INPUT");
        SeleniumUtils.sendKeys(comboInput, WaitType.VISIBLE, subledgerName, "SL_COMBO_INPUT");
        JavascriptExecutorUtils.waitForExtJS();

        By option = subLedgerPickerRow(subledgerName, id);
        WaitUtils.fluentWaitBuilder().until(driver -> {
            driver.findElement(option).click();
            return true;
        });
        ExtentLogger.info("SL_OPTION_" + subledgerName + " is clicked successfully");
        JavascriptExecutorUtils.waitForExtJS();
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForComponentReady();

        Log.debug("[SL_NATIVE] Selected SL='{}' (id='{}') on row {}", subledgerName, id, rowIndex);
        ReportLogger.trace("[SL_NATIVE] row=" + rowIndex + " selected SL='" + subledgerName
                + "' id='" + (id.isEmpty() ? "—" : id) + "'");

        if (referenceSite != null && !referenceSite.trim().isEmpty()) {
            setSubLedgerReferenceSite(referenceSite, rowIndex);
        }
    }

    /**
     * Build the locator for a row in the Sub-Ledger grid picker.
     * When an {@code id} is supplied, match a row that contains BOTH the exact Name and the
     * exact Id (sub-ledger names are not unique). Otherwise match by Name only.
     */
    private By subLedgerPickerRow(String name, String id) {
        String n = name.trim();
        String base = "//div[contains(@class,'x-boundlist-floating')]"
                + "//div[contains(@class,'x-boundlist-item')";
        if (id != null && !id.trim().isEmpty()) {
            return By.xpath(base + " and .//td[@data-qtip=\"" + n + "\"]"
                                 + " and .//td[@data-qtip=\"" + id.trim() + "\"]]");
        }
        return By.xpath(base + " and .//td[@data-qtip=\"" + n + "\"]]");
    }

    private void addSubLedgerRow() {
        JavascriptExecutorUtils.addSubLedgerRow(DriverManager.getDriver(), AR_GRID_ITEM_ID);
    }

    private void setSubLedgerReferenceSite(String referenceSite, int rowIndex) {
        if (referenceSite == null || referenceSite.trim().isEmpty()) return;
        // Poll for auto-population before deciding skip vs. native path.
        String existing = waitForReferenceSiteAutoPopulation(rowIndex);
        if (existing != null && !existing.trim().isEmpty()) {
            ExtentLogger.info("[AR SL RefSite] Row " + (rowIndex + 1) + ": already '" + existing
                    + "' — not changing (skip).");
            return;
        }
        String inputId = JavascriptExecutorUtils.openReferenceSiteCellEditor(
                DriverManager.getDriver(), rowIndex, AR_GRID_ITEM_ID);
        if (inputId == null || inputId.isEmpty()) {
            ExtentLogger.warning("[AR SL RefSite] Row " + (rowIndex + 1)
                    + ": native editor did not open — using JS injection fallback for '" + referenceSite + "'");
            Log.warn("[SL_REFSITE_NATIVE] AR row {} — falling back to JS injection for '{}'", rowIndex + 1, referenceSite);
            JavascriptExecutorUtils.setSubLedgerReferenceSite(DriverManager.getDriver(), rowIndex, referenceSite, AR_GRID_ITEM_ID);
            return;
        }
        ExtentLogger.info("[AR SL RefSite] Row " + (rowIndex + 1) + ": native path — typing '" + referenceSite + "'");
        By comboInput = By.id(inputId);
        SeleniumUtils.clear(comboInput, "SL_REFSITE");
        SeleniumUtils.sendKeys(comboInput, WaitType.VISIBLE, referenceSite, "SL_REFSITE");
        JavascriptExecutorUtils.waitForExtJS();
        By option = By.xpath("//li[@role='option'][normalize-space(.)='" + referenceSite + "']");
        WaitUtils.fluentWaitBuilder().until(driver -> {
            driver.findElement(option).click();
            return true;
        });
        ExtentLogger.info("SL_REFSITE_OPTION_" + referenceSite + " is clicked successfully");
        JavascriptExecutorUtils.waitForExtJS();
        JavascriptExecutorUtils.waitForPageLoad();
        ExtentLogger.info("[AR SL RefSite] Row " + (rowIndex + 1) + ": selected '" + referenceSite + "' via native");
    }

    /**
     * Waits up to {@code shortWaitSeconds} for the UI to auto-populate the Reference Site cell
     * after a sub-ledger is selected. Returns the populated value, or {@code ""} on timeout.
     */
    private String waitForReferenceSiteAutoPopulation(int rowIndex) {
        long timeoutSeconds = ConfigFactory.getWaitConfig().shortWaitSeconds();
        try {
            return WaitUtils.webDriverWait(timeoutSeconds).until(driver -> {
                String val = JavascriptExecutorUtils.getSubLedgerReferenceSiteName(
                        driver, rowIndex, AR_GRID_ITEM_ID);
                return (val != null && !val.trim().isEmpty()) ? val : null;
            });
        } catch (TimeoutException e) {
            ExtentLogger.info("[AR SL RefSite] Row " + (rowIndex + 1)
                    + ": auto-population did not occur within " + timeoutSeconds
                    + "s — will use native path.");
            return "";
        }
    }

    private String getSubLedgerReferenceSite(int rowIndex) {
        return JavascriptExecutorUtils.getSubLedgerReferenceSiteName(DriverManager.getDriver(), rowIndex, AR_GRID_ITEM_ID);
    }

    private void setSubLedgerAmount(String subLedgerAmount, int rowIndex) throws InterruptedException {
        String inputId = JavascriptExecutorUtils.openAmountCellEditor(
                DriverManager.getDriver(), rowIndex, AR_GRID_ITEM_ID);
        if (inputId == null || inputId.isEmpty()) {
            throw new IllegalStateException(
                    "[SL_AMOUNT_NATIVE] Could not open Amount editor on row " + rowIndex
                    + " for AR grid [" + AR_GRID_ITEM_ID + "]");
        }
        By amountInput = By.id(inputId);
        SeleniumUtils.clear(amountInput, "SL_AMOUNT");
        SeleniumUtils.sendKeys(amountInput, WaitType.VISIBLE, subLedgerAmount, "SL_AMOUNT");
        // Tab commits the value to the ExtJS store record — without it the typed amount is never
        // written to the underlying record, causing "Amount is required" on Save.
        SeleniumUtils.pressTab(amountInput, "SL_AMOUNT");
        JavascriptExecutorUtils.waitForExtJS();
    }

    private void handlePaymentInformation(
            List<SubLedgerInformation> subLedgerList,
            List<PaymentInformation> paymentList) {

        for (SubLedgerInformation subLedger : subLedgerList) {
            final String lineNo = subLedger.getLineNo();
            if (lineNo == null || lineNo.isBlank()) continue;

            final String slKey = (subLedger.getId() != null && !subLedger.getId().isBlank())
                    ? subLedger.getId() : subLedger.getSubLedger();
            com.gsl.reports.ActualResultsHolder.get().getUiPaymentInfoPopupMap().put(slKey, false);

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
                clickPaymentButton(subLedger.getSubLedger(), refSite, slid)
                        .fillAndSubmit(payment);
                JavascriptExecutorUtils.waitForExtJS();
                com.gsl.reports.ActualResultsHolder.get().getUiPaymentInfoPopupMap().put(slKey, true);
            });
        }
    }
}
